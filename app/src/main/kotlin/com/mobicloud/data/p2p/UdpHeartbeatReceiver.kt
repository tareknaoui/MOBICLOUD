package com.mobicloud.data.p2p

import com.mobicloud.domain.models.NodeIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.net.DatagramPacket
import java.net.DatagramSocket

class UdpHeartbeatReceiver(
    private val protoBuf: ProtoBuf,
    private val socket: DatagramSocket,
    private val bufferSize: Int = 1024,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    @OptIn(ExperimentalSerializationApi::class)
    fun receiveHeartbeats(): Flow<Result<NodeIdentity>> = flow {
        val buffer = ByteArray(bufferSize)
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val result = try {
                val packet = DatagramPacket(buffer, buffer.size)
                withContext(ioDispatcher) {
                    socket.receive(packet)
                }
                
                val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                val identity = protoBuf.decodeFromByteArray<NodeIdentity>(payload)
                Result.success(identity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            
            if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                emit(result)
                if (result.isFailure) {
                    kotlinx.coroutines.delay(1000L) // Prevent tight loop on permanent IO failure
                }
            }
        }
    }
}
