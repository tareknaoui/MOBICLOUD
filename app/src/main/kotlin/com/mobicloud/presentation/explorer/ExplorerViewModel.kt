package com.mobicloud.presentation.explorer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobicloud.core.security.FragmentCipherUseCase
import android.util.Log
import com.mobicloud.domain.models.CatalogEntry
import com.mobicloud.domain.models.ErasureParameters
import com.mobicloud.domain.models.ResolvedBlockLocation
import com.mobicloud.domain.repository.CatalogRepository
import com.mobicloud.domain.repository.SecurityRepository
import com.mobicloud.domain.usecase.m03_m04_gossip_heartbeat.GossipSyncUseCase
import com.mobicloud.domain.usecase.m05_dht_catalog.LocalizeFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadProgressState
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncodeErasureFragmentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val gossipSyncUseCase: GossipSyncUseCase,
    private val encodeErasureFragmentsUseCase: EncodeErasureFragmentsUseCase,
    private val fragmentCipherUseCase: FragmentCipherUseCase,
    private val distributeEncryptedBlocksUseCase: DistributeEncryptedBlocksUseCase,
    private val securityRepository: SecurityRepository,
    private val localizeFileBlocksUseCase: LocalizeFileBlocksUseCase,
    private val downloadFileBlocksUseCase: DownloadFileBlocksUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val catalogEntries: StateFlow<List<CatalogEntry>> = catalogRepository.getAllEntriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _storeState = MutableStateFlow<StoreState>(StoreState.Idle)
    val storeState: StateFlow<StoreState> = _storeState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var resetJob: Job? = null

    fun refreshCatalog() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                gossipSyncUseCase.runGossipCycle()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun initiateDownload(fileHash: String) {
        var shouldLaunch = false
        _downloadState.update { current ->
            if (current is DownloadState.Locating) current
            else { shouldLaunch = true; DownloadState.Locating(fileHash) }
        }
        if (!shouldLaunch) return
        viewModelScope.launch {
            localizeFileBlocksUseCase.invoke(fileHash)
                .onSuccess { map ->
                    _downloadState.value = DownloadState.Located(fileHash, map)
                    startDownload(fileHash, map)
                }
                .onFailure { e ->
                    _downloadState.value = DownloadState.Error(fileHash, e.message ?: "Localisation échouée")
                }
        }
    }

    /**
     * Story 6.2 — chaîne automatique après `Located` : déclenche la course K+2 et met à jour
     * `_downloadState` à chaque progression. `k` provient de `ErasureParameters` (mêmes
     * paramètres que l'encodage côté upload — symétrique).
     */
    private fun startDownload(fileHash: String, blockMap: Map<String, ResolvedBlockLocation>) {
        val k = ErasureParameters().k
        viewModelScope.launch {
            downloadFileBlocksUseCase.invoke(blockMap, k).collect { state ->
                when (state) {
                    is DownloadProgressState.Progress -> {
                        Log.i(
                            "MobiCloud:DL",
                            "fileHash=${fileHash.take(8)} progress=${state.received}/${state.k} failed=${state.failed}"
                        )
                        _downloadState.value = DownloadState.Downloading(
                            fileHash = fileHash,
                            received = state.received,
                            k = state.k,
                            failed = state.failed
                        )
                    }
                    is DownloadProgressState.Completed ->
                        _downloadState.value = DownloadState.Downloaded(fileHash, state.blocks)
                    is DownloadProgressState.Failed ->
                        _downloadState.value = DownloadState.Error(fileHash, state.reason)
                }
            }
        }
    }

    fun storeFile(uri: Uri) {
        if (_storeState.value is StoreState.InProgress) return
        resetJob?.cancel()
        _storeState.value = StoreState.InProgress.Encoding  // P7: set before launch to close TOCTOU window
        viewModelScope.launch {
            val fileSizeBytes = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                        else null
                    }
            }
            if (fileSizeBytes != null && fileSizeBytes > MAX_FILE_SIZE_BYTES) {
                _storeState.value = StoreState.Error("Fichier trop volumineux (max ${MAX_FILE_SIZE_MB} Mo)")
                scheduleReset()
                return@launch
            }

            val fileBytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: run {
                _storeState.value = StoreState.Error("Impossible de lire le fichier")
                scheduleReset()
                return@launch
            }

            val fileHash = sha256Hex(fileBytes)

            val tempFile = File(context.cacheDir, "mobicloud_store_${System.currentTimeMillis()}.tmp")
            try {
                withContext(Dispatchers.IO) { tempFile.writeBytes(fileBytes) }

                val params = ErasureParameters()
                val fragments = encodeErasureFragmentsUseCase(tempFile, params)
                    .getOrElse { e ->
                        _storeState.value = StoreState.Error("Échec encodage: ${e.message ?: "erreur inconnue"}")
                        scheduleReset()
                        return@launch
                    }

                _storeState.value = StoreState.InProgress.Encrypting

                val localIdentity = securityRepository.getIdentity()
                    .getOrElse { e ->
                        _storeState.value = StoreState.Error("Identité locale indisponible: ${e.message ?: "erreur inconnue"}")
                        scheduleReset()
                        return@launch
                    }

                val bundle = fragmentCipherUseCase.encrypt(fragments, localIdentity.publicKeyBytes)
                    .getOrElse { e ->
                        _storeState.value = StoreState.Error("Échec chiffrement: ${e.message ?: "erreur inconnue"}")
                        scheduleReset()
                        return@launch
                    }

                val total = bundle.encryptedFragments.size
                val dataBlockCount = params.k
                _storeState.value = StoreState.InProgress.Distributing(total = total, dataBlockCount = dataBlockCount)

                distributeEncryptedBlocksUseCase.distribute(
                    encryptedBundle = bundle,
                    fileHash = fileHash,
                    k = params.k
                ) { blockIndex, success ->
                    _storeState.update { current ->
                        if (current !is StoreState.InProgress.Distributing) return@update current
                        current.copy(
                            confirmedIndices = if (success) current.confirmedIndices + blockIndex else current.confirmedIndices,
                            failedIndices = if (!success) current.failedIndices + blockIndex else current.failedIndices
                        )
                    }
                }
                    .onSuccess { entry ->
                        _storeState.value = StoreState.Success(entry, entry.fragmentLocations.size)
                        scheduleReset()
                    }
                    .onFailure { e ->
                        _storeState.value = StoreState.Error("Échec distribution: ${e.message ?: "erreur inconnue"}")
                        scheduleReset()
                    }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_storeState.value is StoreState.InProgress) {
                    _storeState.value = StoreState.Error("Erreur inattendue: ${e.message ?: "erreur inconnue"}")
                    scheduleReset()
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    private fun scheduleReset() {
        resetJob = viewModelScope.launch {
            delay(5000L)  // > Snackbar Short duration to avoid cancelling in-flight snackbar
            _storeState.value = StoreState.Idle
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_FILE_SIZE_MB = 100
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024L
    }
}
