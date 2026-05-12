package com.mobicloud.data.p2p.relay

import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.di.ApplicationScope
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

    private val pendingDeltaResps = ConcurrentHashMap<String, CompletableDeferred<DeltaSyncResponse>>()

    companion object {
        private const val GOSSIP_BLOOM: Byte = 0x01
        private const val GOSSIP_DELTA_REQ: Byte = 0x02
        private const val GOSSIP_DELTA_RESP: Byte = 0x03
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

    private fun buildSignalData(type: Byte, payload: ByteArray): ByteArray {
        val buf = ByteArray(5 + payload.size)
        buf[0] = type
        ByteBuffer.wrap(buf, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size)
        payload.copyInto(buf, 5)
        return buf
    }
}
