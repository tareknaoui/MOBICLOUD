package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import com.mobicloud.domain.models.BlockTransferMessage
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.EncryptedBundle
import com.mobicloud.domain.models.EncryptedFragment
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.models.FragmentLocation
import com.mobicloud.domain.models.NodeIdentity
import com.mobicloud.domain.models.Peer
import com.mobicloud.domain.repository.BlockSender
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.NodeSettingsRepository
import com.mobicloud.domain.repository.PeerRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.InsertDhtEntryUseCase
import com.mobicloud.domain.util.HybridLogicalClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.random.Random
import javax.inject.Singleton

@Singleton
class DistributeEncryptedBlocksUseCase @Inject constructor(
    private val peerRepository: PeerRepository,
    private val blockSender: BlockSender,
    private val catalogRepository: CatalogRepository,
    private val gossipSyncUseCase: GossipSyncUseCase,
    private val securityRepository: SecurityRepository,
    private val insertDhtEntryUseCase: InsertDhtEntryUseCase,
    private val requestInterClusterHostingUseCase: RequestInterClusterHostingUseCase,
    private val nodeSettingsRepository: NodeSettingsRepository
) {

    private companion object {
        const val BASE_ACK_TIMEOUT_MS = 10_000L
        const val MAX_ACK_TIMEOUT_MS = 30_000L
        // Bornage du retry inter-cluster : on tente jusqu'à 3 Super-Pairs distants distincts
        // avant d'abandonner le fragment. Au-delà, le coût (latence × tentatives) dépasse
        // le gain marginal de placement.
        const val MAX_INTER_CLUSTER_ATTEMPTS = 3
    }

    private data class DeliveryRecord(
        val frag: EncryptedFragment,
        val blockId: String,
        val nodeId: String,
        val peer: Peer
    )

    suspend fun distribute(
        encryptedBundle: EncryptedBundle,
        fileHash: String,
        params: ErasureParameters,
        selectedPeers: List<Peer> = emptyList(),
        originalFileName: String = "",
        onBlockResult: ((blockIndex: Int, success: Boolean) -> Unit)? = null
    ): Result<CatalogEntry> = withContext(Dispatchers.IO) {
        val k = params.k
        android.util.Log.i("MobiCloud:Distribute", "[DIAG] distribute START fileHash=${fileHash.take(8)} fragments=${encryptedBundle.encryptedFragments.size} k=$k n=${params.n}")
        val resolvedPeers: List<Peer> = selectedPeers
        if (resolvedPeers.isEmpty()) {
            android.util.Log.w(
                "MobiCloud:Distribute",
                "[DIAG] selectedPeers vide — placement local désactivé, dépendance Niveau 3 inter-cluster"
            )
        }

        val localIdentity = securityRepository.getIdentity()
            .getOrElse { return@withContext Result.failure(it) }

        // Lu UNE fois (guardrail 9.3 Subtask 4.2 — éviter une lecture DB par fragment).
        val localClusterId = nodeSettingsRepository.getSettings().clusterId
        if (localClusterId.isBlank()) {
            android.util.Log.w(
                "MobiCloud:Distribute",
                "[INTER-CLUSTER] désactivé : localClusterId blank (cluster pas encore provisionné)"
            )
        }

        // [Fix #2 — Anti-corrélation] Shuffle déterministe basé sur fileHash :
        // évite que plusieurs fragments atterrissent sur le même peer physique
        // quand fragments > peers, tout en restant reproductible pour le débogage.
        val seed = fileHash.hashCode().toLong()
        val shuffledPeers = if (resolvedPeers.isEmpty()) emptyList()
                            else resolvedPeers.shuffled(Random(seed))

        // [Priorité #1 — Parallélisation] Tous les fragments envoyés en parallèle.
        // Chaque coroutine retourne un DeliveryRecord ou null si tous niveaux échouent.
        val deliveries: List<DeliveryRecord> = coroutineScope {
            encryptedBundle.encryptedFragments.mapIndexed { i, frag ->
                async {
                    // [P3-Fix] runCatching isole les exceptions inattendues : une exception non-catchée
                    // dans un async{} annulerait toute la coroutineScope (et tous les fragments en vol).
                    runCatching {
                    val blockId = sha256Hex(frag.ciphertext)

                    val msg = BlockTransferMessage(
                        blockId = blockId,
                        ownerId = localIdentity.nodeId,
                        fragmentIndex = frag.index,
                        isParity = frag.isParity,
                        ciphertext = frag.ciphertext,
                        iv = frag.iv,
                        originalFileSize = frag.originalFileSize
                    )

                    var confirmedPeer: Peer? = null
                    var result: Result<com.mobicloud.domain.models.BlockAckMessage> =
                        Result.failure(IllegalStateException("not attempted"))
                    var placedInterCluster = false

                    // Niveau 1 — placement local primary (si cluster local non vide).
                    if (shuffledPeers.isNotEmpty()) {
                        // [Priorité #2 — Stride coprime] Permutation injective : pour i ∈ [0, size-1]
                        // tous les pairs sont touchés exactement une fois. Élimine les collisions de
                        // l'ancien `i % size` qui faisait atterrir frag i et frag i+size sur le même pair.
                        val primaryIndex = stride(i, shuffledPeers.size, seed)
                        val primaryPeer = shuffledPeers[primaryIndex]
                        android.util.Log.i("MobiCloud:Distribute", "[DIAG] sendBlock #${frag.index} parity=${frag.isParity} → ${primaryPeer.identity.nodeId.take(8)}@${primaryPeer.ipAddress}:${primaryPeer.port}")
                        result = blockSender.sendBlock(msg, primaryPeer, BASE_ACK_TIMEOUT_MS)
                        if (result.isSuccess) confirmedPeer = primaryPeer
                        android.util.Log.i("MobiCloud:Distribute", "[DIAG] sendBlock #${frag.index} result=${if (result.isSuccess) "OK" else "FAIL: ${result.exceptionOrNull()?.message}"}")

                        // Niveau 2 — fallback local (pair de cluster différent physiquement si possible).
                        if (result.isFailure && shuffledPeers.size > 1) {
                            // [Fix R2 — Anti-double] On cherche d'abord un pair dont l'IP diffère du
                            // primary pour éviter d'envoyer deux tentatives au même nœud physique.
                            // Fallback sur (primaryIndex + 1) si tous les pairs partagent la même IP
                            // (scénario de test ou réseau extrêmement réduit).
                            val differentIpFallback = shuffledPeers
                                .filterIndexed { idx, peer -> idx != primaryIndex && peer.ipAddress != primaryPeer.ipAddress }
                                .firstOrNull()
                            val level2Peer = differentIpFallback
                                ?: shuffledPeers[(primaryIndex + 1) % shuffledPeers.size]
                            // Niveau 2 — spec AC#5 : timeout retry = MAX_ACK_TIMEOUT_MS (30s).
                            result = blockSender.sendBlock(msg, level2Peer, MAX_ACK_TIMEOUT_MS)
                            if (result.isSuccess) confirmedPeer = level2Peer
                        }
                    }

                    // Niveau 3 — fallback inter-cluster (Story 9.3) si local vide ou local échoue.
                    // excludeNodeIds croît à chaque échec pour éviter de retenter un pair mort.
                    if (result.isFailure) {
                        val triedRemoteNodeIds = mutableSetOf<String>()
                        var interAttempt = 0
                        while (interAttempt < MAX_INTER_CLUSTER_ATTEMPTS && result.isFailure) {
                            // [P6-Fix] withTimeout borne l'appel réseau au tracker — sans deadline,
                            // un tracker lent gèle l'awaitAll() entier.
                            val remote = runCatching {
                                withTimeout(MAX_ACK_TIMEOUT_MS) {
                                    requestInterClusterHostingUseCase.selectRemoteHosts(
                                        msg.ciphertext.size.toLong(),
                                        localClusterId,
                                        excludeNodeIds = triedRemoteNodeIds
                                    ).firstOrNull()
                                }
                            }.getOrElse {
                                android.util.Log.w("MobiCloud:Distribute", "[INTER-CLUSTER] selectRemoteHosts timeout/erreur : ${it.message}")
                                null
                            } ?: break
                            triedRemoteNodeIds += remote.nodeId
                            interAttempt++

                            // [Story 10.1] Décodage de la clé publique propagée par GET_PEERS.
                            // Si vide / mal formée → ByteArray(0), la vérification ACK retournera
                            // false et la boucle passera au candidat suivant.
                            val remotePubKeyBytes: ByteArray = if (remote.pubKeySpkiDerB64.isNotBlank()) {
                                runCatching {
                                    android.util.Base64.decode(remote.pubKeySpkiDerB64, android.util.Base64.NO_WRAP)
                                }.getOrElse {
                                    android.util.Log.w(
                                        "MobiCloud:Distribute",
                                        "[INTER-CLUSTER] pubKeySpkiDerB64 invalide pour ${remote.nodeId.take(8)} : ${it.message}"
                                    )
                                    ByteArray(0)
                                }
                            } else ByteArray(0)

                            val remotePeer = Peer(
                                identity = NodeIdentity(remote.nodeId, remotePubKeyBytes),
                                lastSeenTimestampMs = System.currentTimeMillis(),
                                source = com.mobicloud.domain.models.DiscoverySource.RELAY_HA,
                                ipAddress = remote.ip,
                                port = remote.port,
                                isActive = true,
                                isSuperPair = true
                            )
                            if (remotePubKeyBytes.isEmpty()) {
                                android.util.Log.w(
                                    "MobiCloud:Distribute",
                                    "[INTER-CLUSTER] peer ${remote.nodeId.take(8)} sans clé publique " +
                                    "— ACK non vérifiable (relay legacy ou session fermée)"
                                )
                            }
                            android.util.Log.i(
                                "MobiCloud:Distribute",
                                "[INTER-CLUSTER] tentative #${frag.index} → ${remote.nodeId.take(8)}@${remote.ip}:${remote.port} cluster=${remote.clusterId.take(8)} freeBytes=${remote.freeBytes}"
                            )
                            result = blockSender.sendBlock(msg, remotePeer, MAX_ACK_TIMEOUT_MS)
                            if (result.isSuccess) {
                                confirmedPeer = remotePeer
                                placedInterCluster = true
                            } else {
                                android.util.Log.w(
                                    "MobiCloud:Distribute",
                                    "[INTER-CLUSTER] echec sur ${remote.nodeId.take(8)} : ${result.exceptionOrNull()?.message} -- candidat suivant"
                                )
                            }
                        }
                    }

                    val success = result.isSuccess
                    // [P8-Fix] Isoler le callback UI — une exception dans onBlockResult annulerait
                    // la coroutine courante et se propagerait dans awaitAll().
                    runCatching { onBlockResult?.invoke(frag.index, success) }
                    if (success && confirmedPeer != null) {
                        val ack = result.getOrNull()!!
                        // [P4-Fix] Vérifier que le hash de l'ACK correspond au blockId calculé localement.
                        // [P5-Fix] Rejeter les ACK avec receiverNodeId vide — fragmente le DHT sinon.
                        if (ack.blockHash != blockId || ack.receiverNodeId.isBlank()) {
                            android.util.Log.w(
                                "MobiCloud:Distribute",
                                "[ACK] fragment #${frag.index} ACK invalide — blockHash match=${ack.blockHash == blockId} receiverNodeId non-blank=${ack.receiverNodeId.isNotBlank()}"
                            )
                            null
                        } else {
                            android.util.Log.i(
                                "MobiCloud:Distribute",
                                "[DIAG] fragment #${frag.index} placé ${if (placedInterCluster) "INTER-CLUSTER" else "LOCAL"} sur ${confirmedPeer.identity.nodeId.take(8)}"
                            )
                            DeliveryRecord(
                                frag = frag,
                                blockId = blockId,
                                nodeId = ack.receiverNodeId,
                                peer = confirmedPeer
                            )
                        }
                    } else {
                        android.util.Log.e(
                            "MobiCloud:Distribute",
                            "[DROP] fragment #${frag.index} parity=${frag.isParity} ÉCHEC tous niveaux : ${result.exceptionOrNull()?.message}"
                        )
                        null
                    }
                    }.getOrElse { e ->
                        android.util.Log.e("MobiCloud:Distribute", "[CRASH] fragment #${frag.index} exception inattendue : ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val dataBlocksConfirmed = deliveries.count { !it.frag.isParity }
        if (dataBlocksConfirmed < k) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Distribution partielle — seulement $dataBlocksConfirmed/$k blocs de données confirmés"
                )
            )
        }

        // [Priorité #3 — Quorum parité proportionnel] Au moins ⌈n/2⌉ blocs de parité
        // confirmés. Tient la promesse EC : avec n=4 attendus, accepter 1 seul = 75%
        // de redondance perdue silencieusement.
        val expectedParity = params.n
        val parityQuorum = (expectedParity + 1) / 2
        val parityBlocksConfirmed = deliveries.count { it.frag.isParity }
        if (parityBlocksConfirmed < parityQuorum) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Quorum parité non atteint — $parityBlocksConfirmed/$parityQuorum confirmés (n=${params.n}, k=${params.k})"
                )
            )
        }

        val catalogEntry = CatalogEntry(
            fileHash = fileHash,
            ownerPubKeyHash = sha256Hex(localIdentity.publicKeyBytes),
            // [Fix #3 — HLC] Horloge logique hybride monotone — résiste aux dérives
            // et retours en arrière de l'horloge système Android.
            versionClock = HybridLogicalClock.now(),
            fragmentLocations = deliveries.map { delivery ->
                FragmentLocation(
                    fragmentIndex = delivery.frag.index,
                    fragmentHash = delivery.blockId,
                    nodeIds = listOf(delivery.nodeId)
                )
            },
            wrappedMasterKey = encryptedBundle.wrappedFileMasterKey,
            originalFileSize = encryptedBundle.encryptedFragments.firstOrNull()?.originalFileSize ?: 0L,
            originalFileName = originalFileName,
            k = params.k,
            n = params.n
        )

        // [Atomicité DHT/catalog] Le catalog est la source de vérité — on l'écrit AVANT la DHT.
        // Si le catalog échoue, on n'insère aucune entrée DHT → pas d'orphelins. Si la DHT
        // échoue partiellement après catalog, le fichier reste recouvrable (la DHT est un
        // index reconstructible via gossip).
        catalogRepository.insertOwnerEntry(catalogEntry)
            .getOrElse { return@withContext Result.failure(it) }

        deliveries.forEach { delivery ->
            val ip = delivery.peer.ipAddress ?: return@forEach
            val port = delivery.peer.port ?: return@forEach
            val dhtResult = insertDhtEntryUseCase(delivery.blockId, delivery.nodeId, ip, port)
            if (dhtResult.isFailure) {
                android.util.Log.w(
                    "MobiCloud:Distribute",
                    "[DHT] insertion echouee pour blockId=${delivery.blockId.take(8)} (catalog deja persiste, recouvrable via gossip) : ${dhtResult.exceptionOrNull()?.message}"
                )
            }
        }

        // [P7-Fix] Gossip fire-and-forget — un échec gossip ne doit pas faire croire à l'appelant
        // que la distribution a échoué alors que le catalogue est déjà persisté.
        runCatching { gossipSyncUseCase.runGossipCycle() }
            .onFailure { android.util.Log.w("MobiCloud:Distribute", "[GOSSIP] runGossipCycle echec (catalogue persisté, gossip sera retenté) : ${it.message}") }

        Result.success(catalogEntry)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    // [Priorité #2 — Stride coprime] Permutation injective de [0, poolSize) :
    //   index → (index * step + offset) mod poolSize
    // avec step coprime de poolSize → tous les pairs sont touchés exactement une fois
    // sur i ∈ [0, poolSize-1], au lieu du modulo qui collisionne dès que i ≥ poolSize.
    private fun stride(index: Int, poolSize: Int, seed: Long): Int {
        if (poolSize <= 1) return 0
        val absSeed = if (seed == Long.MIN_VALUE) Long.MAX_VALUE else if (seed < 0) -seed else seed
        // [P2-Fix] Incrémentation linéaire bornée — garantit la terminaison pour tout poolSize.
        // gcd(1, n)=1 toujours donc la boucle s'arrête en au plus poolSize itérations.
        var step = ((absSeed % (poolSize - 1)) + 1).toInt()
        while (gcd(step, poolSize) != 1) {
            step++
            if (step >= poolSize) step = 1
        }
        val offset = ((absSeed / poolSize) % poolSize).toInt()
        // [P1-Fix] Résultat garanti non-négatif : % JVM conserve le signe du dividende.
        return (((index.toLong() * step + offset) % poolSize) + poolSize).toInt() % poolSize
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
