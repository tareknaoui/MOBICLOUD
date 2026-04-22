package com.mobicloud.data.repository

import com.mobicloud.data.local.dao.DhtDao
import com.mobicloud.data.local.entity.DhtEntryEntity
import com.mobicloud.data.local.entity.toDomain
import com.mobicloud.data.local.entity.toEntity
import com.mobicloud.data.p2p.tcp.BlockTransferChannel
import com.mobicloud.domain.models.DhtEntry
import com.mobicloud.domain.models.DhtLookupRequestMessage
import com.mobicloud.domain.models.DhtLookupResponseMessage
import com.mobicloud.domain.repository.DhtRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

class DhtRepositoryImpl @Inject constructor(
    private val dhtDao: DhtDao
) : DhtRepository {

    override suspend fun insertEntry(blockId: String, nodeId: String, ipAddress: String, port: Int): Result<Unit> =
        runCatching {
            val timestamp = System.currentTimeMillis()
            val entry = DhtEntryEntity(
                blockId = blockId,
                nodeId = nodeId,
                ipAddress = ipAddress,
                port = port,
                timestamp = timestamp
            )
            dhtDao.insert(entry)
        }

    override suspend fun insertEntryWithTimestamp(
        blockId: String,
        nodeId: String,
        ipAddress: String,
        port: Int,
        timestamp: Long
    ): Result<Unit> = runCatching {
        dhtDao.insert(DhtEntryEntity(
            blockId = blockId,
            nodeId = nodeId,
            ipAddress = ipAddress,
            port = port,
            timestamp = timestamp
        ))
    }

    override suspend fun findByBlockId(blockId: String): Result<DhtEntry?> =
        runCatching {
            dhtDao.findByBlockId(blockId)?.toDomain()
        }

    override suspend fun findByNodeId(nodeId: String): Result<List<DhtEntry>> =
        runCatching {
            dhtDao.findByNodeId(nodeId).map { it.toDomain() }
        }

    override suspend fun deleteByNodeId(nodeId: String): Result<Unit> =
        runCatching {
            dhtDao.deleteByNodeId(nodeId)
        }

    override fun observeAllEntries(): Flow<List<DhtEntry>> =
        dhtDao.observeAllEntries()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun remoteLookup(blockId: String, peerIp: String, peerPort: Int): Result<DhtEntry?> =
        runCatching {
            withContext(Dispatchers.IO) {
                Socket().use { socket ->
                    socket.soTimeout = 3_000
                    socket.connect(InetSocketAddress(peerIp, peerPort), 3_000)
                    val out = DataOutputStream(socket.getOutputStream())
                    val requestBytes = ProtoBuf.encodeToByteArray(DhtLookupRequestMessage.serializer(), DhtLookupRequestMessage(blockId))
                    out.writeByte(BlockTransferChannel.DHT_LOOKUP_REQ.toInt())
                    out.writeInt(requestBytes.size)
                    out.write(requestBytes)
                    out.flush()
                    val inp = DataInputStream(socket.getInputStream())
                    val disc = inp.readByte()
                    if (disc != BlockTransferChannel.DHT_LOOKUP_RESP) return@use null
                    val len = inp.readInt()
                    if (len <= 0 || len > 1024) return@use null
                    val respBytes = ByteArray(len).also { inp.readFully(it) }
                    val resp = ProtoBuf.decodeFromByteArray(DhtLookupResponseMessage.serializer(), respBytes)
                    if (!resp.found) null
                    else if (resp.nodeId.isEmpty() || resp.ipAddress.isEmpty() || resp.port <= 0) null
                    else DhtEntry(resp.blockId, resp.nodeId, resp.ipAddress, resp.port, resp.timestamp)
                }
            }
        }
}
