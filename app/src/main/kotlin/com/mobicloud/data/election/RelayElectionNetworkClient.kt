package com.mobicloud.data.election

import android.util.Log
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.ElectionMessageType
import com.mobicloud.domain.models.ElectionPayload
import com.mobicloud.domain.repository.IElectionNetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RelayElectionClient"

/**
 * Implémentation de [IElectionNetworkClient] via le relay WebSocket.
 *
 * - `broadcastElectionMessage` sérialise le payload en JSON et l'envoie via
 *   ELECTION_BROADCAST (0x10). Le relay forward à tous les nœuds connectés sauf l'émetteur.
 * - `incomingMessages` désérialise les frames ELECTION_RECEIVED (0x11) en [ElectionPayload].
 *
 * La logique ALIVE auto-reply et COORDINATOR FSM transition est déléguée à
 * [ProcessIncomingElectionEventUseCase] qui collecte ce flow dans [MobicloudP2PService].
 */
@Singleton
class RelayElectionNetworkClient @Inject constructor(
    private val relayClient: RelayWebSocketClient
) : IElectionNetworkClient {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val incomingMessages: SharedFlow<ElectionPayload> =
        relayClient.incomingElectionMessages
            .mapNotNull { raw -> electionPayloadFromJson(raw) }
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)

    override suspend fun broadcastElectionMessage(payload: ElectionPayload): Result<Unit> =
        runCatching {
            val json = electionPayloadToJson(payload)
            val sent = relayClient.sendElectionBroadcast(json)
            if (!sent) error("RelayWebSocketClient non connecté — ELECTION_BROADCAST non envoyé")
            Log.d(TAG, "[ELECTION] BROADCAST type=${payload.type.name} score=${payload.reliabilityScore} sender=${payload.senderNodeId.take(8)}")
            Unit
        }.onFailure { e ->
            Log.w(TAG, "broadcastElectionMessage échoué : ${e.message}")
        }
}

internal fun electionPayloadToJson(payload: ElectionPayload): ByteArray =
    JSONObject().apply {
        put("senderNodeId",    payload.senderNodeId)
        put("type",            payload.type.name)
        put("reliabilityScore", payload.reliabilityScore.toDouble())
        put("signatureBytes",  java.util.Base64.getEncoder().encodeToString(payload.signatureBytes))
        put("clusterId",       payload.clusterId)
        put("timestampMs",     payload.timestampMs)
    }.toString().toByteArray(Charsets.UTF_8)

internal fun electionPayloadFromJson(raw: ByteArray): ElectionPayload? = runCatching {
    val obj = JSONObject(raw.toString(Charsets.UTF_8))
    ElectionPayload(
        senderNodeId     = obj.getString("senderNodeId"),
        type             = ElectionMessageType.valueOf(obj.getString("type")),
        reliabilityScore = obj.getDouble("reliabilityScore").toFloat(),
        signatureBytes   = java.util.Base64.getDecoder().decode(obj.getString("signatureBytes")),
        clusterId        = obj.optString("clusterId", ""),
        timestampMs      = obj.getLong("timestampMs")
    )
}.onFailure { e ->
    Log.w(TAG, "electionPayloadFromJson failed : ${e.message}")
}.getOrNull()
