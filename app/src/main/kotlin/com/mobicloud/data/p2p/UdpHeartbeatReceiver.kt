package com.mobicloud.data.p2p

import com.mobicloud.domain.models.HeartbeatMessage
import com.mobicloud.domain.models.HeartbeatPayload
import com.mobicloud.domain.models.NodeIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
    fun receiveHeartbeats(): Flow<Result<HeartbeatMessage>> = flow {
        val buffer = ByteArray(bufferSize)
        while (currentCoroutineContext().isActive) {
            val result = try {
                val packet = DatagramPacket(buffer, buffer.size)
                withContext(ioDispatcher) {
                    socket.receive(packet)
                }

                val payload = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                val heartbeatPayload = protoBuf.decodeFromByteArray<HeartbeatPayload>(payload)
                val identity = NodeIdentity(
                    nodeId = heartbeatPayload.nodeId,
                    publicKeyBytes = heartbeatPayload.publicKeyBytes,
                    reliabilityScore = heartbeatPayload.reliabilityScore
                )
                // P4: IP null → paquet inutilisable pour TCP, rejeté proprement via le catch
                val senderIp = packet.address?.hostAddress
                    ?: throw IllegalStateException("Paquet UDP sans adresse source — ignoré")
                Result.success(HeartbeatMessage(identity, senderIp, heartbeatPayload.tcpPort))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (currentCoroutineContext().isActive) {
                emit(result)
                if (result.isFailure) {
                    delay(1000L) // Evite une boucle CPU serrée en cas d'erreur IO permanente
                }
            }
        }
    }
}
