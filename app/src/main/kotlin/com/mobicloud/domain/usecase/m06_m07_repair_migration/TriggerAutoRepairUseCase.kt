package com.mobicloud.domain.usecase.m06_m07_repair_migration

import android.util.Log
import com.mobicloud.data.p2p.relay.GossipRelayChannel
import com.mobicloud.domain.models.MigrateBlockDirective
import com.mobicloud.domain.models.RepairRequest
import com.mobicloud.domain.models.ReplicationPlanMessage
import com.mobicloud.domain.repository.DhtRepository
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.util.toSigHex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 7.3 — Scanner périodique des blocs sous-répliqués, côté Super-Pair.
 *
 * Parcourt la DHT à la recherche de blockId dont tous les hôtes connus sont devenus
 * INACTIVE et émet un `REPLICATE_PLAN` signé vers un donneur survivant (le cas échéant).
 * Si le Circuit-Breaker est actif, les directives sont enfilées dans [LocalRepairBuffer]
 * au lieu d'être émises immédiatement (AC#3).
 */
@Singleton
class TriggerAutoRepairUseCase @Inject constructor(
    private val peerRepository: PeerRepository,
    private val dhtRepository: DhtRepository,
    private val securityRepository: SecurityRepository,
    private val gossipRelayChannel: GossipRelayChannel,
    private val gossipSyncUseCase: GossipSyncUseCase,
    private val circuitBreakerUseCase: CircuitBreakerUseCase,
    private val localRepairBuffer: LocalRepairBuffer,
    private val networkEventRepository: NetworkEventRepository
) {

    companion object {
        // 2 hôtes actifs requis par fragment. Pertinent surtout pour les uploads à n<=1
        // (RS(2,1), petits clusters cold-start) : DistributeEncryptedBlocksUseCase y place une
        // copie secondaire best-effort par fragment (2 hôtes distincts dès l'upload), donc la
        // branche donneur ci-dessous devient enfin atteignable : un hôte part → l'autre sert de
        // donneur avant que le fragment ne soit orphelin. Avec seuil=1 (ancien MVP), la réparation
        // ne se déclenchait qu'à 0 hôte actif restant — donc jamais de donneur disponible.
        // Pour n>=2 (dynamicN déjà relevé), aucune copie secondaire n'est créée — la tolérance
        // vient des vrais fragments de parité erasure-coding, moins cher qu'une duplication brute.
        const val UNDER_REPLICATION_THRESHOLD = 2
        private const val LOGTAG = "MobiCloud:Migrate"
    }

    /**
     * Seuil effectif appliqué par [scanAndRepair]. Instance var pour permettre :
     *  - les tests de la branche `sendReplicationPlan` (inaccessible avec threshold=1 MVP)
     *  - la future transition multi-réplica sans modifier le const public.
     */
    internal var threshold: Int = UNDER_REPLICATION_THRESHOLD

    /**
     * Scanne les nœuds INACTIVE et planifie une réplication pour chaque blockId
     * qu'ils hébergeaient et qui n'a plus assez de copies actives.
     *
     * Retourne Success quelle que soit l'issue des directives (fire-and-forget) —
     * un Failure indique uniquement un bug bloquant (identité inaccessible, etc.).
     */
    suspend fun scanAndRepair(): Result<Unit> = runCatching {
        val identity = securityRepository.getIdentity().getOrElse { return@runCatching }

        val peersSnapshot = peerRepository.peers.value
        val selfIsSuperPair = peersSnapshot.any {
            it.identity.nodeId == identity.nodeId && it.isSuperPair && it.isActive
        }
        if (!selfIsSuperPair) return@runCatching  // scan silencieux pour non-SP

        val activePeers = peersSnapshot.filter { it.isActive }
        val activeNodeIds = activePeers.map { it.identity.nodeId }.toSet()
        val inactivePeers = peersSnapshot.filter { !it.isActive }
        if (inactivePeers.isEmpty()) return@runCatching

        val circuitOpen = circuitBreakerUseCase.isCircuitOpen.value
        if (circuitOpen) {
            networkEventRepository.pushEvent("[REPAIR] Circuit-Breaker OPEN — directives enfilées dans LocalRepairBuffer")
        }

        // Gossip uniquement si une mutation DHT a réellement eu lieu (insertEntry/deleteByNodeId
        // réussis). Évite une tempête gossip sur scans no-op (tous les 10s).
        var mutationHappened = false

        for (inactive in inactivePeers) {
            val orphanedEntries = dhtRepository.findByNodeId(inactive.identity.nodeId)
                .getOrElse { emptyList() }
            if (orphanedEntries.isEmpty()) continue

            for (entry in orphanedEntries) {
                val hostNodeIds = dhtRepository
                    .findHostNodeIdsByBlockId(entry.blockId)
                    .getOrElse { emptyList() }
                val activeHosts = hostNodeIds.filter { it in activeNodeIds }

                if (activeHosts.size >= threshold) continue

                Log.i(LOGTAG, "[REPAIR] Bloc sous-replique ${entry.blockId.take(16)} — hotes actifs=${activeHosts.size}")
                if (activeHosts.isEmpty()) {
                    networkEventRepository.pushEvent(
                        "[REPAIR] ${entry.blockId.take(16)} PERDU — aucun hôte actif (nœud ${inactive.identity.nodeId.take(8)} INACTIVE)"
                    )
                    continue  // impossible à réparer sans source
                }

                // Sélection donneur : premier hôte actif, hors soi-même (un SP peut co-héberger).
                val donorNodeId = activeHosts.firstOrNull { it != identity.nodeId }
                if (donorNodeId == null) {
                    networkEventRepository.pushEvent(
                        "[REPAIR] ${entry.blockId.take(16)} — seul hôte actif = self, pas de donneur tiers"
                    )
                    continue
                }
                val donor = activePeers.firstOrNull { it.identity.nodeId == donorNodeId }
                if (donor == null) {
                    networkEventRepository.pushEvent(
                        "[REPAIR] Donneur ${donorNodeId.take(8)} introuvable — ${entry.blockId.take(16)} ignoré"
                    )
                    continue
                }

                // Sélection destination : actif, hors donneur, hors soi-même, pas déjà hôte.
                // Relay route par nodeId — IP/port non requis.
                val destination = activePeers.firstOrNull { p ->
                    p.identity.nodeId !in hostNodeIds &&
                    p.identity.nodeId != identity.nodeId &&
                    p.identity.nodeId != donorNodeId
                }
                if (destination == null) {
                    networkEventRepository.pushEvent(
                        "[REPAIR] ${entry.blockId.take(16)} — aucune destination libre"
                    )
                    continue
                }

                val directive = MigrateBlockDirective(
                    blockId = entry.blockId,
                    destinationNodeId = destination.identity.nodeId,
                    destinationIp = destination.ipAddress!!,
                    destinationPort = destination.port!!,
                    destinationPublicKeyBytes = destination.identity.publicKeyBytes
                )

                if (circuitOpen) {
                    // AC#3 — Circuit ouvert : enfiler au lieu d'émettre
                    val dropped = localRepairBuffer.enqueue(
                        RepairRequest(
                            blockId = directive.blockId,
                            destinationIp = directive.destinationIp,
                            port = directive.destinationPort
                        )
                    )
                    if (dropped != null) {
                        networkEventRepository.pushEvent(
                            "[REPAIR] Buffer plein — ${dropped.blockId.take(16)} droppée (FIFO)"
                        )
                    }
                    // AC#4 — MàJ DHT optimiste même sur branche enqueue (la directive est
                    // "engagée" via le buffer) — l'état DHT reflète l'intention de réparation.
                    dhtRepository.insertEntry(
                        directive.blockId,
                        directive.destinationNodeId,
                        directive.destinationIp,
                        directive.destinationPort
                    ).onSuccess { mutationHappened = true }
                        .onFailure {
                            networkEventRepository.pushEvent(
                                "[REPAIR] Insert DHT ${directive.blockId.take(16)} échoué : ${it.message}"
                            )
                        }
                    continue
                }

                // Signature du plan — domain separation avec tag "REPAIR" + timestamp anti-replay
                val planTimestampMs = System.currentTimeMillis()
                val sigPayload = buildString {
                    append(identity.nodeId); append("|REPAIR|")
                    append(directive.blockId); append(":")
                    append(directive.destinationNodeId); append(":")
                    append(directive.destinationIp); append(":")
                    append(directive.destinationPort); append(":")
                    append(directive.destinationPublicKeyBytes.toSigHex())
                    append("|ts="); append(planTimestampMs)
                }.toByteArray()
                val signature = securityRepository.signData(sigPayload).getOrNull()
                if (signature == null) {
                    networkEventRepository.pushEvent(
                        "[REPAIR] Signature plan échouée ${entry.blockId.take(16)}"
                    )
                    continue
                }

                val plan = ReplicationPlanMessage(
                    superPeerNodeId = identity.nodeId,
                    directive = directive,
                    signatureBytes = signature,
                    timestampMs = planTimestampMs
                )

                Log.i(LOGTAG, "[REPAIR] Plan → donneur=${donorNodeId.take(8)} dest=${destination.identity.nodeId.take(8)} bloc=${entry.blockId.take(16)}")
                gossipRelayChannel.sendReplicationPlan(donorNodeId, plan)
                    .onSuccess {
                        val repairMsg = "[REPAIR] ${entry.blockId.take(16)} donneur=${donorNodeId.take(8)} → dest=${destination.identity.nodeId.take(8)}"
                        networkEventRepository.pushEvent(repairMsg)
                        Log.i(LOGTAG, repairMsg)
                        dhtRepository.insertEntry(
                            directive.blockId,
                            directive.destinationNodeId,
                            directive.destinationIp,
                            directive.destinationPort
                        ).onSuccess { mutationHappened = true }
                            .onFailure {
                                networkEventRepository.pushEvent(
                                    "[REPAIR] Insert DHT ${directive.blockId.take(16)} échoué : ${it.message}"
                                )
                            }
                    }
                    .onFailure {
                        networkEventRepository.pushEvent(
                            "[REPAIR] Envoi plan → ${donorNodeId.take(8)} échoué : ${it.message}"
                        )
                    }
            }

            // AC#4 — purge des entrées du nœud INACTIVE uniquement hors churn storm.
            // Sous circuit OPEN, on préserve l'info locator : les directives sont bufferisées
            // et la purge se fera naturellement au prochain scan une fois le circuit fermé.
            if (!circuitOpen) {
                dhtRepository.deleteByNodeId(inactive.identity.nodeId)
                    .onSuccess { mutationHappened = true }
                    .onFailure {
                        networkEventRepository.pushEvent(
                            "[REPAIR] Purge DHT ${inactive.identity.nodeId.take(8)} échouée : ${it.message}"
                        )
                    }
            }
        }

        // Gossip UNE fois à la fin, uniquement si la DHT a été mutée (évite tempête sur scans no-op).
        if (mutationHappened) {
            gossipSyncUseCase.runGossipCycle()
                .onFailure {
                    networkEventRepository.pushEvent("[REPAIR] Gossip post-scan échoué : ${it.message}")
                }
        }

        Unit
    }
}
