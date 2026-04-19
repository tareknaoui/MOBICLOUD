package com.mobicloud.data.p2p.tcp

import android.util.Log
import com.mobicloud.core.format.MobiCloudProtoBuf
import com.mobicloud.domain.models.gossip.BloomFilterGossip
import com.mobicloud.domain.models.gossip.DeltaSyncRequest
import com.mobicloud.domain.models.gossip.DeltaSyncResponse
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipOutboundPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GossipChannel @Inject constructor() : GossipOutboundPort {

    companion object {
        const val GOSSIP_BLOOM: Byte = 0x01
        const val GOSSIP_DELTA_REQ: Byte = 0x02
        const val GOSSIP_DELTA_RESP: Byte = 0x03
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 3000  // F11: timeout de lecture pour éviter blocage indéfini
        private const val LOGTAG = "MobiCloud:Gossip"
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun sendBloomGossip(
        targetIp: String,
        targetPort: Int,
        msg: BloomFilterGossip
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(targetIp, targetPort), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val out = DataOutputStream(socket.getOutputStream())
            val bytes = MobiCloudProtoBuf.encodeToByteArray(BloomFilterGossip.serializer(), msg)
            out.writeByte(GOSSIP_BLOOM.toInt())
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(LOGTAG, "sendBloomGossip failed to $targetIp:$targetPort", e)
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun sendDeltaSyncRequest(
        targetIp: String,
        targetPort: Int,
        req: DeltaSyncRequest
    ): Result<DeltaSyncResponse> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(targetIp, targetPort), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val out = DataOutputStream(socket.getOutputStream())
            val reqBytes = MobiCloudProtoBuf.encodeToByteArray(DeltaSyncRequest.serializer(), req)
            out.writeByte(GOSSIP_DELTA_REQ.toInt())
            out.writeInt(reqBytes.size)
            out.write(reqBytes)
            out.flush()

            val inp = DataInputStream(socket.getInputStream())
            val discriminant = inp.readByte()
            if (discriminant != GOSSIP_DELTA_RESP) {
                return@withContext Result.failure(IllegalStateException("Unexpected discriminant: $discriminant"))
            }
            val respLen = inp.readInt()
            val respBytes = ByteArray(respLen)
            inp.readFully(respBytes)
            val response = MobiCloudProtoBuf.decodeFromByteArray(DeltaSyncResponse.serializer(), respBytes)
            Result.success(response)
        } catch (e: Exception) {
            Log.w(LOGTAG, "sendDeltaSyncRequest failed to $targetIp:$targetPort", e)
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
