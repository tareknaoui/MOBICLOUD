package com.mobicloud.domain.usecase.m11_join

import com.mobicloud.domain.models.m11_join.MEMBER_UPDATE_TIMESTAMP_WINDOW_MS
import com.mobicloud.domain.models.m11_join.MemberRole
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.MemberUpdateEvent
import com.mobicloud.domain.models.m11_join.hexToByteArray
import com.mobicloud.domain.models.m11_join.memberUpdateSignedBytes
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.NetworkEventRepository
import com.mobicloud.domain.repository.SecurityRepository
import javax.inject.Inject

/**
 * T1 (review 2026-05-12) : validation AC14 d'un MEMBER_UPDATE entrant extraite du collector
 * inline de `MobicloudP2PService` pour la rendre testable en JVM pur (le service est Android).
 *
 * Trois branches d'ignorance silencieuse (log + retour `Ignored`) :
 *  1. `fromNodeId` n'est pas un SUPER_PAIR connu de `inMemoryRegistry` (anti-bypass H3 + C11).
 *  2. Signature EC P-256 invalide vs la pubkey du SP émetteur.
 *  3. Timestamp hors fenêtre `±MEMBER_UPDATE_TIMESTAMP_WINDOW_MS` (anti-replay, 90s vs 30s élection).
 *
 * Un retour `Applied` indique que le caller doit appliquer l'update + `markSpSeen()`.
 */
class ProcessMemberUpdateUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val memberSnapshotCacheUseCase: MemberSnapshotCacheUseCase,
    private val networkEventRepository: NetworkEventRepository,
    val clock: () -> Long = { System.currentTimeMillis() }
) {
    sealed interface Result {
        data object Applied : Result
        data class Ignored(val reason: String) : Result
    }

    suspend operator fun invoke(fromNodeIdHex: String, update: MemberUpdate): Result {
        val fromHex = fromNodeIdHex.lowercase()
        val spPublicKey = memberSnapshotCacheUseCase.inMemory.value
            .firstOrNull {
                it.role == MemberRole.SUPER_PAIR && it.nodeId.toHexString().lowercase() == fromHex
            }
            ?.publicKey
        if (spPublicKey == null) {
            val reason = "fromNodeId=${fromHex.take(8)} pas SP courant"
            networkEventRepository.pushEvent("[MEMBER-UPDATE-RX] $reason — ignoré")
            return Result.Ignored(reason)
        }

        // P6 (review R2) : validation pré-applyUpdate de la longueur du nodeId cible. Un attaquant
        // (ou un wire mal-formé) avec un nodeId de longueur 9..31 ou 33+ bytes ferait crasher
        // `MemberMapper.toEntity` plus tard via le `require(hex.length == 64 || ≤16)`. On rejette
        // avant validation crypto pour éviter de gaspiller un verify pour rien.
        update.member?.nodeId?.let { targetBytes ->
            if (targetBytes.size != 32 && targetBytes.size > 8) {
                val reason = "target nodeId longueur invalide (${targetBytes.size} bytes)"
                networkEventRepository.pushEvent("[MEMBER-UPDATE-RX] $reason — ignoré")
                return Result.Ignored(reason)
            }
        }
        if (update.event == MemberUpdateEvent.LEFT && update.leftNodeId.isNotEmpty()
            && update.leftNodeId.size != 32 && update.leftNodeId.size > 8
        ) {
            val reason = "leftNodeId longueur invalide (${update.leftNodeId.size} bytes)"
            networkEventRepository.pushEvent("[MEMBER-UPDATE-RX] $reason — ignoré")
            return Result.Ignored(reason)
        }

        val memberOrNodeIdHex = update.member?.nodeId?.toHexString()
            ?: update.leftNodeId.takeIf { it.isNotEmpty() }?.toHexString()
            ?: return Result.Ignored("target nodeId absent")

        // Timestamp O(1) vérifié AVANT la crypto EC P-256 (coûteuse) pour rejeter les replays bon marché.
        // MEMBER_UPDATE_TIMESTAMP_WINDOW_MS (90s) > BULLY_TIMESTAMP_WINDOW_MS (30s) : le relay Render
        // peut introduire 10-40s de latence queue — un window de 30s rejetait les keepalives légitimes
        // comme "stale", empêchant markSpSeen() → faux SpTimeoutDetected → Bully → cluster éclaté.
        val now = clock()
        if (update.timestampMs < now - MEMBER_UPDATE_TIMESTAMP_WINDOW_MS
            || update.timestampMs > now + MEMBER_UPDATE_TIMESTAMP_WINDOW_MS
        ) {
            networkEventRepository.pushEvent("[MEMBER-UPDATE-RX] Timestamp stale — ignoré")
            return Result.Ignored("timestamp stale")
        }

        val signedBytes = memberUpdateSignedBytes(
            senderNodeId = fromHex.hexToByteArray(),
            event = update.event,
            memberOrNodeIdHex = memberOrNodeIdHex,
            ts = update.timestampMs
        )
        val valid = securityRepository.verifySignature(signedBytes, update.signatureBytes, spPublicKey)
            .getOrDefault(false)
        if (!valid) {
            networkEventRepository.pushEvent("[MEMBER-UPDATE-RX] Signature invalide — ignoré")
            return Result.Ignored("signature invalide")
        }

        networkEventRepository.pushEvent(
            "[MEMBER-UPDATE-RX] ${update.event} ${memberOrNodeIdHex.take(8)} reçu du SP ${fromHex.take(8)}"
        )
        memberSnapshotCacheUseCase.applyUpdate(update)
        return Result.Applied
    }
}
