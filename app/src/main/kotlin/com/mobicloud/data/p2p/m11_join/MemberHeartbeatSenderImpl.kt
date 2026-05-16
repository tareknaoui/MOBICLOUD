package com.mobicloud.data.p2p.m11_join

import com.mobicloud.data.p2p.join.JoinNetworkClientImpl
import com.mobicloud.data.p2p.websocket.RelayWebSocketClient
import com.mobicloud.domain.models.m11_join.Heartbeat
import com.mobicloud.domain.models.m11_join.JoinSubType
import com.mobicloud.domain.models.m11_join.Leave
import com.mobicloud.domain.models.m11_join.MemberUpdate
import com.mobicloud.domain.models.m11_join.toHexString
import com.mobicloud.domain.repository.IMemberHeartbeatSender
import com.mobicloud.domain.repository.NetworkEventRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class MemberHeartbeatSenderImpl @Inject constructor(
    private val relayWebSocketClient: RelayWebSocketClient,
    private val networkEventRepository: NetworkEventRepository
) : IMemberHeartbeatSender {

    override suspend fun send(destNodeId: ByteArray, hb: Heartbeat): Result<Unit> = runCatching {
        val payload = byteArrayOf(JoinNetworkClientImpl.JOIN_MAGIC, JoinSubType.HEARTBEAT.byte) +
            ProtoBuf.encodeToByteArray(hb)
        val dest = destNodeId.toHexString().lowercase()
        // blockId 64 hex chars — contrainte relay ^[0-9a-fA-F]{64}$
        val blockId = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        relayWebSocketClient.uploadBlock(dest, blockId, payload).getOrThrow()
    }

    /**
     * H22 : fan-out parallèle pour ne pas latence-cumuler N×RTT sur 50 membres.
     * H23 : log per-dest des échecs via NetworkEventRepository + retourne le décompte
     * succès/échec, pas seulement `Result.success` aveugle.
     */
    override suspend fun broadcast(memberNodeIds: List<ByteArray>, update: MemberUpdate): Result<Unit> = runCatching {
        if (memberNodeIds.isEmpty()) return@runCatching
        val payload = byteArrayOf(JoinNetworkClientImpl.JOIN_MAGIC, JoinSubType.MEMBER_UPDATE.byte) +
            ProtoBuf.encodeToByteArray(update)
        val results = coroutineScope {
            memberNodeIds.map { dest ->
                async {
                    val blockId = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
                    val outcome = relayWebSocketClient.uploadBlock(dest.toHexString().lowercase(), blockId, payload)
                    outcome.onFailure {
                        networkEventRepository.pushEvent(
                            "[MEMBER-UPDATE] WARN upload échoué dest=${dest.toHexString().take(8)}: ${it.message}"
                        )
                    }
                    outcome.isSuccess
                }
            }.awaitAll()
        }
        val ok = results.count { it }
        val ko = results.size - ok
        networkEventRepository.pushEvent("[MEMBER-UPDATE] broadcast: $ok OK / $ko échecs sur ${results.size} dests")
    }

    override suspend fun sendLeave(spNodeId: ByteArray, leave: Leave): Result<Unit> = runCatching {
        val payload = byteArrayOf(JoinNetworkClientImpl.JOIN_MAGIC, JoinSubType.LEAVE.byte) +
            ProtoBuf.encodeToByteArray(leave)
        val blockId = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        relayWebSocketClient.uploadBlock(spNodeId.toHexString().lowercase(), blockId, payload).getOrThrow()
    }
}
