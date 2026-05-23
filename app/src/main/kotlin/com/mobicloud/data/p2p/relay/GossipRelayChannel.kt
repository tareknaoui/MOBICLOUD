package com.mobicloud.data.p2p.relay

import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.di.ApplicationScope
import com.mobicloud.domain.models.DepartureNoticeMessage
import com.mobicloud.domain.models.MigrationPlanMessage
import com.mobicloud.domain.models.ReplicationPlanMessage
import com.mobicloud.domain.models.RevokeBlockMessage
import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipIncomingHandler
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipOutboundPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class GossipRelayChannel @Inject constructor(
    private val relayWsClient: RelayWebSocketClient,
    @ApplicationScope private val scope: CoroutineScope
) : GossipOutboundPort {

    var gossipHandler: GossipIncomingHandler? = null

    // Story 13.2 — handler branché par le service pour exécuter la révocation reçue
    @Volatile
    var revokeHandler: (suspend (RevokeBlockMessage) -> Unit)? = null

    // M06 — handlers migration branchés par le service
    @Volatile
    var departureNoticeHandler: (suspend (DepartureNoticeMessage) -> Unit)? = null
    @Volatile
    var migrationPlanHandler: (suspend (MigrationPlanMessage) -> Unit)? = null

    // M07 — handler réplication branché par le service
    @Volatile
    var replicationPlanHandler: (suspend (ReplicationPlanMessage) -> Unit)? = null

    private val pendingDeltaResps = ConcurrentHashMap<String, CompletableDeferred<DeltaSyncResponse>>()

    companion object {
        private const val GOSSIP_BLOOM: Byte = 0x01
        private const val GOSSIP_DELTA_REQ: Byte = 0x02
        private const val GOSSIP_DELTA_RESP: Byte = 0x03
        private const val REVOKE_BLOCK: Byte = 0x04
        private const val DEPARTURE_NOTICE: Byte = 0x05
        private const val MIGRATION_PLAN: Byte = 0x06
        private const val REPLICATE_PLAN: Byte = 0x07
        private const val DELTA_TIMEOUT_MS = 3000L
        private const val LOGTAG = "MobiCloud:Gossip"
    }

    fun startIncomingDispatch() {
        scope.launch {
            relayWsClient.incomingSignals.collect { (fromNodeId, data) ->
                if (data.size < 5) return@collect
                val type = data[0]
                val payloadLen = ByteBuffer.wrap(data, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (payloadLen < 0 || data.size < 5 + payloadLen) return@collect
                val payload = data.copyOfRange(5, 5 + payloadLen)

                when (type) {
                    GOSSIP_BLOOM -> runCatching {
                        val msg = MobiCloudProtoBuf.decodeFromByteArray(BloomFilterGossip.serializer(), payload)
                        gossipHandler?.onBloomGossipReceived(msg)
                    }.onFailure { Log.w(LOGTAG, "[RELAY] GOSSIP_BLOOM decode échoué depuis ${fromNodeId.take(8)}", it) }

                    GOSSIP_DELTA_REQ -> runCatching {
                        val req = MobiCloudProtoBuf.decodeFromByteArray(DeltaSyncRequest.serializer(), payload)
                        val response = gossipHandler?.onDeltaSyncRequestReceived(req) ?: return@collect
                        val respBytes = MobiCloudProtoBuf.encodeToByteArray(DeltaSyncResponse.serializer(), response)
                        relayWsClient.sendSignal(fromNodeId, buildSignalData(GOSSIP_DELTA_RESP, respBytes))
                            .onFailure { Log.w(LOGTAG, "[RELAY] GOSSIP_DELTA_RESP envoi échoué vers ${fromNodeId.take(8)}", it) }
                    }.onFailure { Log.w(LOGTAG, "[RELAY] GOSSIP_DELTA_REQ traitement échoué depuis ${fromNodeId.take(8)}", it) }

                    GOSSIP_DELTA_RESP -> runCatching {
                        val response = MobiCloudProtoBuf.decodeFromByteArray(DeltaSyncResponse.serializer(), payload)
                        pendingDeltaResps[fromNodeId]?.complete(response)
                    }.onFailure { Log.w(LOGTAG, "[RELAY] GOSSIP_DELTA_RESP decode échoué depuis ${fromNodeId.take(8)}", it) }

                    REVOKE_BLOCK -> runCatching {
                        val msg = MobiCloudProtoBuf.decodeFromByteArray(RevokeBlockMessage.serializer(), payload)
                        revokeHandler?.invoke(msg)
                    }.onFailure { Log.w(LOGTAG, "[RELAY] REVOKE_BLOCK traitement échoué depuis ${fromNodeId.take(8)}", it) }

                    DEPARTURE_NOTICE -> runCatching {
                        val msg = MobiCloudProtoBuf.decodeFromByteArray(DepartureNoticeMessage.serializer(), payload)
                        departureNoticeHandler?.invoke(msg)
                    }.onFailure { Log.w(LOGTAG, "[RELAY] DEPARTURE_NOTICE traitement échoué depuis ${fromNodeId.take(8)}", it) }

                    MIGRATION_PLAN -> runCatching {
                        val msg = MobiCloudProtoBuf.decodeFromByteArray(MigrationPlanMessage.serializer(), payload)
                        migrationPlanHandler?.invoke(msg)
                    }.onFailure { Log.w(LOGTAG, "[RELAY] MIGRATION_PLAN traitement échoué depuis ${fromNodeId.take(8)}", it) }

                    REPLICATE_PLAN -> runCatching {
                        val msg = MobiCloudProtoBuf.decodeFromByteArray(ReplicationPlanMessage.serializer(), payload)
                        replicationPlanHandler?.invoke(msg)
                    }.onFailure { Log.w(LOGTAG, "[RELAY] REPLICATE_PLAN traitement échoué depuis ${fromNodeId.take(8)}", it) }
                }
            }
        }
    }

    override suspend fun sendBloomGossip(targetNodeId: String, msg: BloomFilterGossip): Result<Unit> {
        val bytes = MobiCloudProtoBuf.encodeToByteArray(BloomFilterGossip.serializer(), msg)
        return relayWsClient.sendSignal(targetNodeId, buildSignalData(GOSSIP_BLOOM, bytes))
    }

    override suspend fun sendDeltaSyncRequest(targetNodeId: String, req: DeltaSyncRequest): Result<DeltaSyncResponse> {
        val bytes = MobiCloudProtoBuf.encodeToByteArray(DeltaSyncRequest.serializer(), req)
        val deferred = CompletableDeferred<DeltaSyncResponse>()
        pendingDeltaResps[targetNodeId] = deferred
        return try {
            relayWsClient.sendSignal(targetNodeId, buildSignalData(GOSSIP_DELTA_REQ, bytes)).getOrThrow()
            Result.success(withTimeout(DELTA_TIMEOUT_MS) { deferred.await() })
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pendingDeltaResps.remove(targetNodeId)
        }
    }

    suspend fun sendRevokeBlock(targetNodeId: String, msg: RevokeBlockMessage): Result<Unit> {
        val bytes = MobiCloudProtoBuf.encodeToByteArray(RevokeBlockMessage.serializer(), msg)
        return relayWsClient.sendSignal(targetNodeId, buildSignalData(REVOKE_BLOCK, bytes))
    }

    suspend fun sendDepartureNotice(targetNodeId: String, msg: DepartureNoticeMessage): Result<Unit> {
        val bytes = MobiCloudProtoBuf.encodeToByteArray(DepartureNoticeMessage.serializer(), msg)
        return relayWsClient.sendSignal(targetNodeId, buildSignalData(DEPARTURE_NOTICE, bytes))
    }

    suspend fun sendMigrationPlan(targetNodeId: String, msg: MigrationPlanMessage): Result<Unit> {
        val bytes = MobiCloudProtoBuf.encodeToByteArray(MigrationPlanMessage.serializer(), msg)
        return relayWsClient.sendSignal(targetNodeId, buildSignalData(MIGRATION_PLAN, bytes))
    }

    suspend fun sendReplicationPlan(targetNodeId: String, msg: ReplicationPlanMessage): Result<Unit> {
        val bytes = MobiCloudProtoBuf.encodeToByteArray(ReplicationPlanMessage.serializer(), msg)
        return relayWsClient.sendSignal(targetNodeId, buildSignalData(REPLICATE_PLAN, bytes))
    }

    private fun buildSignalData(type: Byte, payload: ByteArray): ByteArray {
        val buf = ByteArray(5 + payload.size)
        buf[0] = type
        ByteBuffer.wrap(buf, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size)
        payload.copyInto(buf, 5)
        return buf
    }
}
