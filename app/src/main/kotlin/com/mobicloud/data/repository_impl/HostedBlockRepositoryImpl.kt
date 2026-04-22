package com.mobicloud.data.repository_impl

import android.content.Context
import com.mobicloud.data.local.dao.HostedBlockDao
import com.mobicloud.data.local.entity.HostedBlockEntity
import com.mobicloud.domain.repository.HostedBlockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostedBlockRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hostedBlockDao: HostedBlockDao
) : HostedBlockRepository {

    // [Review][Patch] Verrou par blockId pour sérialiser les écritures concurrentes du même bloc.
    private val blockLocks = ConcurrentHashMap<String, Mutex>()
    private fun lockFor(blockId: String): Mutex =
        blockLocks.computeIfAbsent(blockId) { Mutex() }

    override suspend fun saveBlock(
        blockId: String,
        ownerId: String,
        fragmentIndex: Int,
        isParity: Boolean,
        ciphertext: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        lockFor(blockId).withLock {
            runCatching {
                val blocksDir = File(context.filesDir, "blocks").also { it.mkdirs() }
                val blockFile = File(blocksDir, blockId)
                // [Review][Patch] Écriture atomique : tmp → rename. Un crash mid-write laisse
                // un .tmp nettoyable, jamais un blockFile partiel lisible.
                val tmpFile = File(blocksDir, "$blockId.tmp")
                try {
                    tmpFile.writeBytes(ciphertext)
                    if (!tmpFile.renameTo(blockFile)) {
                        // renameTo échoue si la cible existe (ex: re-réception) — fallback copy
                        tmpFile.copyTo(blockFile, overwrite = true)
                        tmpFile.delete()
                    }
                } catch (e: Exception) {
                    tmpFile.delete()
                    throw e
                }

                try {
                    hostedBlockDao.insertHostedBlock(
                        HostedBlockEntity(
                            blockId = blockId,
                            ownerId = ownerId,
                            fragmentIndex = fragmentIndex,
                            isParity = isParity,
                            filePath = blockFile.absolutePath,
                            sizeBytes = ciphertext.size.toLong()
                        )
                    )
                } catch (e: Exception) {
                    // Rollback : le fichier est sur disque mais sans référence DB — le supprimer.
                    runCatching { blockFile.delete() }
                    throw e
                }
                blockFile.absolutePath
            }
        }
    }

    override suspend fun blockExists(blockId: String): Boolean = withContext(Dispatchers.IO) {
        hostedBlockDao.getHostedBlock(blockId) != null
    }

    override suspend fun deleteBlock(blockId: String) = withContext(Dispatchers.IO) {
        val entity = hostedBlockDao.getHostedBlock(blockId)
        if (entity != null) {
            runCatching { File(entity.filePath).delete() }
            hostedBlockDao.deleteHostedBlock(blockId)
        }
    }
}
