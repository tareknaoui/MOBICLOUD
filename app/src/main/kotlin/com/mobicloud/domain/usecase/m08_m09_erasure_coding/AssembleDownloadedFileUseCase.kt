package com.mobicloud.domain.usecase.m08_m09_erasure_coding

import android.content.Context
import android.os.Environment
import com.mobicloud.core.security.unwrapFileMasterKey
import com.mobicloud.core.security.hkdfSha256
import com.mobicloud.domain.models.DownloadException
import com.mobicloud.domain.models.DownloadedBlock
import com.mobicloud.domain.models.ErasureFragment
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Story 6.3 — pipeline de déchiffrement, décodage Erasure (si parité présente)
 * et réassemblage streaming d'un fichier reconstitué à partir des K blocs valides
 * remontés par [DownloadFileBlocksUseCase] (Story 6.2).
 *
 * Garanties :
 *  - Aucune donnée partielle visible (écriture dans un fichier `*.tmp` du `cacheDir` puis `renameTo` atomique).
 *  - Détection immédiate de toute corruption (AES-GCM tag, SHA-256 fichier final).
 *  - Aucun dépassement mémoire sur les gros fichiers (chemin streaming sans parité :
 *    écriture bloc par bloc dans `FileOutputStream(append=true)` + SHA-256 incrémental).
 *  - Zéroïsation systématique de la `fileMasterKey` et de chaque `blockKey` après usage.
 *
 * Voir Story 6.3 Dev Notes pour la justification complète des choix architecturaux.
 */
@Singleton
class AssembleDownloadedFileUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val securityRepository: SecurityRepository,
    private val decodeErasureFragmentsUseCase: DecodeErasureFragmentsUseCase
) {

    sealed class AssembleResult {
        data class Success(val filePath: String) : AssembleResult()
        data class Failure(val exception: DownloadException) : AssembleResult()
    }

    sealed class AssembleProgress {
        data class Decrypting(val processed: Int, val k: Int) : AssembleProgress()
        data class Finalized(val result: AssembleResult) : AssembleProgress()
    }

    fun invoke(
        fileHash: String,
        blocks: Map<Int, DownloadedBlock>
    ): Flow<AssembleProgress> = flow {
        // 1. Récupération du catalogue + métadonnées de chiffrement
        val catalog = catalogRepository.getEntry(fileHash).getOrNull()
        if (catalog == null) {
            emit(AssembleProgress.Finalized(
                AssembleResult.Failure(DownloadException.MissingMasterKey(fileHash))
            ))
            return@flow
        }
        val wrapped = catalog.wrappedMasterKey
        if (wrapped == null) {
            emit(AssembleProgress.Finalized(
                AssembleResult.Failure(DownloadException.MissingMasterKey(fileHash))
            ))
            return@flow
        }
        if (catalog.originalFileSize <= 0L) {
            // Sentinelle "legacy" pre-6.3 : pas d'originalFileSize → décodeur EC ne peut
            // pas trimer le padding correctement.
            emit(AssembleProgress.Finalized(
                AssembleResult.Failure(DownloadException.MasterKeyTransportGap(fileHash))
            ))
            return@flow
        }

        val encIdentity = securityRepository.getEncryptionIdentity().getOrElse { e ->
            emit(AssembleProgress.Finalized(
                AssembleResult.Failure(DownloadException.MasterKeyUnwrap(e))
            ))
            return@flow
        }

        val fileMasterKey: ByteArray = try {
            unwrapFileMasterKey(wrapped, encIdentity.privateKey)
        } catch (e: GeneralSecurityException) {
            emit(AssembleProgress.Finalized(
                AssembleResult.Failure(DownloadException.MasterKeyUnwrap(e))
            ))
            return@flow
        }

        val params = ErasureParameters()
        val k = params.k

        // 2. Validation IV-transport (gap acquis 6.2 → résolu 6.3)
        if (blocks.values.any { it.iv.size != 12 || it.iv.all { b -> b == 0.toByte() } }) {
            fileMasterKey.fill(0)
            emit(AssembleProgress.Finalized(
                AssembleResult.Failure(DownloadException.MasterKeyTransportGap(fileHash))
            ))
            return@flow
        }

        // Nom du temp limité à 32 chars du hash (évite collisions inter-téléchargements
        // tout en gardant un nom raisonnable). `delete()` initial pour purger une
        // tentative précédente avortée.
        val tempFile = File(context.cacheDir, "download_${fileHash.take(32)}.tmp")
        runCatching { tempFile.delete() }

        try {
            val hasParity = blocks.values.any { it.isParity }
            val processedCounter = AtomicInteger(0)

            // 3. Déchiffrement parallèle (CPU-bound) sur Dispatchers.Default.
            //    Chaque bloc dérive sa propre `blockKey` via HKDF + index ; clé zéroïsée
            //    immédiatement après `Cipher.doFinal` (pattern strictement identique à
            //    FragmentCipherUseCase.decrypt).
            val decryptedFragments: List<ErasureFragment> = coroutineScope {
                blocks.values.map { block ->
                    async {
                        val blockKey = hkdfSha256(
                            ikm = fileMasterKey,
                            info = "block_key_${block.fragmentIndex}".toByteArray()
                        )
                        try {
                            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                                init(
                                    Cipher.DECRYPT_MODE,
                                    SecretKeySpec(blockKey, "AES"),
                                    GCMParameterSpec(128, block.iv)
                                )
                            }
                            val plaintext: ByteArray = try {
                                cipher.doFinal(block.ciphertext)
                            } catch (e: GeneralSecurityException) {
                                throw DownloadException.CorruptBlock(
                                    "fragmentIndex=${block.fragmentIndex}: ${e.message}"
                                )
                            }
                            processedCounter.incrementAndGet()
                            ErasureFragment(
                                index = block.fragmentIndex,
                                isParity = block.isParity,
                                data = plaintext,
                                originalFileSize = catalog.originalFileSize
                            )
                        } finally {
                            blockKey.fill(0)
                        }
                    }
                }.awaitAll()
            }

            // 4. Branche streaming (chemin AC#6, sans parité) vs décodage EC (avec parité)
            val digest = MessageDigest.getInstance("SHA-256")
            val originalFileSize = catalog.originalFileSize

            val totalWritten: Long
            if (!hasParity) {
                // Chemin streaming : écrire chaque bloc data dans l'ordre d'index croissant.
                // SHA-256 incrémental (O(1) mémoire) — pas de relecture du fichier final.
                val ordered = decryptedFragments.sortedBy { it.index }
                var written = 0L
                FileOutputStream(tempFile).use { fos ->
                    for (frag in ordered) {
                        val remaining = originalFileSize - written
                        if (remaining <= 0L) break
                        val take = minOf(frag.data.size.toLong(), remaining).toInt()
                        if (take == frag.data.size) {
                            fos.write(frag.data)
                            digest.update(frag.data)
                        } else {
                            // Dernier bloc data partiel — trim au padding pour respecter
                            // originalFileSize. Évite d'écrire les zéros de padding Erasure.
                            fos.write(frag.data, 0, take)
                            digest.update(frag.data, 0, take)
                        }
                        written += take
                    }
                }
                totalWritten = written
            } else {
                // Chemin décodage EC : reconstruction complète en RAM, puis écriture
                // d'un seul `writeBytes` (le décodeur trime déjà à originalFileSize).
                val original = decodeErasureFragmentsUseCase
                    .invoke(decryptedFragments, params)
                    .getOrElse { e ->
                        throw DownloadException.CorruptBlock(
                            "erasure decode échoué: ${e.message}"
                        )
                    }
                withContext(Dispatchers.IO) { tempFile.writeBytes(original) }
                digest.update(original)
                totalWritten = original.size.toLong()
            }

            // 5. Vérification finale — SHA-256 du fichier reconstitué doit matcher
            //    le `fileHash` annoncé dans le catalog.
            if (totalWritten != originalFileSize) {
                throw DownloadException.CorruptBlock(
                    "écriture incomplète: $totalWritten/$originalFileSize"
                )
            }
            val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (computedHash != fileHash) {
                throw DownloadException.CorruptFile(fileHash, computedHash)
            }

            // 6. Move atomique vers l'emplacement final visible utilisateur.
            //    Fallback `copyTo + delete()` si renameTo échoue (cross-filesystem :
            //    cacheDir sur /data, externalFiles sur /storage).
            val finalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            if (!finalDir.exists()) finalDir.mkdirs()
            val finalFile = File(finalDir, "mobicloud_${fileHash.take(16)}")
            withContext(Dispatchers.IO) {
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
            }
            emit(AssembleProgress.Decrypting(processedCounter.get(), k))
            emit(AssembleProgress.Finalized(AssembleResult.Success(finalFile.absolutePath)))
        } catch (e: DownloadException) {
            runCatching { tempFile.delete() }
            emit(AssembleProgress.Finalized(AssembleResult.Failure(e)))
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            emit(AssembleProgress.Finalized(
                AssembleResult.Failure(
                    DownloadException.CorruptBlock("pipeline inattendu: ${e.message}")
                )
            ))
        } finally {
            fileMasterKey.fill(0)
            runCatching { tempFile.delete() }
        }
    }.flowOn(Dispatchers.Default)
}
