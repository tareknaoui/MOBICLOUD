package com.mobicloud.data.repository_impl

import android.content.Context
import com.mobicloud.data.local.dao.HostedBlockDao
import com.mobicloud.data.local.entity.HostedBlockEntity
import com.mobicloud.domain.models.HostedBlockPayload
import com.mobicloud.domain.repository.HostedBlockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
        ciphertext: ByteArray,
        iv: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        require(iv.size == 12) { "iv must be 12 bytes (AES-GCM nonce), got ${iv.size}" }
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
                            sizeBytes = ciphertext.size.toLong(),
                            iv = iv
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

    /**
     * Story 6.2 — lecture sous verrou partagé pour éviter une lecture pendant
     * l'écriture atomique de [saveBlock]. Toute entrée invalide / orpheline → `Success(null)`.
     */
    override suspend fun getBlock(blockId: String): Result<HostedBlockPayload?> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!BLOCK_ID_REGEX.matches(blockId)) return@runCatching null
                val entity = hostedBlockDao.getHostedBlock(blockId) ?: return@runCatching null
                // [Review][Patch] P5 — existence check sous lock pour éviter TOCTOU avec
                // deleteBlock/saveBlock concurrents : un `file.exists()=true` hors lock puis
                // `readBytes` après suppression convertissait `null` (orphan) en `Result.failure`.
                lockFor(blockId).withLock {
                    val file = File(entity.filePath)
                    if (!file.exists() || !file.isFile) return@runCatching null
                    val bytes = file.readBytes()
                    // Story 6.3 — `entity.iv` peut être la sentinelle "legacy" (12 × 0x00) sur
                    // les blocs hébergés avant la migration 7→8. On le retourne tel quel ; le
                    // client lèvera DownloadException.MasterKeyTransportGap (jamais de tentative
                    // de déchiffrement avec un IV non fourni).
                    HostedBlockPayload(
                        blockId = entity.blockId,
                        fragmentIndex = entity.fragmentIndex,
                        isParity = entity.isParity,
                        ciphertext = bytes,
                        iv = entity.iv
                    )
                }
            }
        }

    override suspend fun getAllBlockIds(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching { hostedBlockDao.getAllBlockIds() }
        }

    override fun observeTotalHostedBytes(): Flow<Long> = hostedBlockDao.observeTotalSizeBytes()

    companion object {
        // Story 6.2 — défense en profondeur identique à ReceiveAndHostBlockUseCase
        private val BLOCK_ID_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
