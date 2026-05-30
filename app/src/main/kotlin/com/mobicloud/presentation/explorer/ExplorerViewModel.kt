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
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.AssembleDownloadedFileUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DistributeEncryptedBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadFileBlocksUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.DownloadProgressState
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.EncodeErasureFragmentsUseCase
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.PeerSelectionException
import com.mobicloud.domain.usecase.m08_m09_erasure_coding.SelectOptimalPeersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val assembleDownloadedFileUseCase: AssembleDownloadedFileUseCase,
    private val selectOptimalPeersUseCase: SelectOptimalPeersUseCase,
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All") // "All", "Images", "Videos", "Audio", "Documents"
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Story 13.5 — navigation dossiers
    private val _currentFolder = MutableStateFlow<String?>(null)
    val currentFolder: StateFlow<String?> = _currentFolder.asStateFlow()

    val folders: StateFlow<List<String>> = catalogRepository.getActiveFolderNamesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val catalogEntries: StateFlow<List<CatalogEntry>> = combine(
        catalogRepository.getActiveEntriesFlow(),
        _searchQuery,
        _selectedCategory,
        _currentFolder
    ) { entries, query, category, folder ->
        var filtered = entries.filter { it.folderPath == folder }
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.originalFileName.contains(query, ignoreCase = true) }
        }
        if (category != "All") {
            filtered = filtered.filter { entry ->
                val name = entry.originalFileName
                when (category) {
                    "Images" -> name.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp)$", RegexOption.IGNORE_CASE))
                    "Videos" -> name.matches(Regex(".*\\.(mp4|mkv|avi|mov)$", RegexOption.IGNORE_CASE))
                    "Audio" -> name.matches(Regex(".*\\.(mp3|aac|flac|wav)$", RegexOption.IGNORE_CASE))
                    "Documents" -> !name.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp|mp4|mkv|avi|mov|mp3|aac|flac|wav)$", RegexOption.IGNORE_CASE))
                    else -> true
                }
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Story 13.2 — corbeille : fileHash émis pour déclencher la snackbar "Annuler"
    private val _undoEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val undoEvent: SharedFlow<String> = _undoEvent.asSharedFlow()

    // Story 13.3 — upload occupé : notifie l'UI quand un 2e upload est tenté pendant InProgress
    private val _uploadBusyEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val uploadBusyEvent: SharedFlow<Unit> = _uploadBusyEvent.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _storeState = MutableStateFlow<StoreState>(StoreState.Idle)
    val storeState: StateFlow<StoreState> = _storeState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var resetJob: Job? = null
    private var storeJob: Job? = null

    // [Review][Patch] Tracker le job de download pour annulation en cas de relance concurrente.
    private var downloadJob: Job? = null

    init {
        // Story 13.2 — purger les entrées expirées (TTL 30 jours) à chaque ouverture de l'explorateur
        viewModelScope.launch { catalogRepository.purgeExpired() }
    }

    // [Review][Patch] P7 — tracker le job de localisation : sans ça, un vieux `localize` en cours
    // pouvait émettre `Located(fileHashA)` et écraser le state d'un download fileHashB plus récent.
    private var locateJob: Job? = null

    @Volatile private var downloadStartMs: Long = 0L
    private var lastNodeCount: Int = 0

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

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

    fun moveToTrash(fileHash: String) {
        viewModelScope.launch {
            // F1 fix: émettre undoEvent uniquement si le soft-delete DB a réussi
            catalogRepository.moveToTrash(fileHash).onSuccess {
                _undoEvent.emit(fileHash)
            }
        }
    }

    fun undoMoveToTrash(fileHash: String) {
        viewModelScope.launch {
            catalogRepository.restoreFromTrash(fileHash)
        }
    }

    fun initiateDownload(fileHash: String, isPreview: Boolean = false) {
        // [Review][Patch] P6 — preemption : un utilisateur qui tape un fichier B pendant que
        // A est `Locating` doit pouvoir basculer. On annule les deux jobs (locate + download)
        // et on (re)pose l'état à `Locating(fileHash)`. Le même fichier tappé deux fois est
        // idempotent (pas de relance si déjà en cours sur le même hash).
        val current = _downloadState.value
        if (current is DownloadState.Locating && current.fileHash == fileHash && current.isPreview == isPreview) return
        if (current is DownloadState.Downloading && current.fileHash == fileHash && current.isPreview == isPreview) return
        locateJob?.cancel()
        downloadJob?.cancel()
        downloadStartMs = System.currentTimeMillis()
        _downloadState.value = DownloadState.Locating(fileHash, isPreview)
        locateJob = viewModelScope.launch {
            localizeFileBlocksUseCase.invoke(fileHash)
                .onSuccess { map ->
                    _downloadState.value = DownloadState.Located(fileHash, map, isPreview)
                    startDownload(fileHash, map, isPreview)
                }
                .onFailure { e ->
                    _downloadState.value = DownloadState.Error(fileHash, e.message ?: "Localisation échouée", isPreview)
                }
        }
    }

    /**
     * Story 6.2 — chaîne automatique après `Located` : déclenche la course K+2 et met à jour
     * `_downloadState` à chaque progression. `k` est lu depuis le catalogue (persisté à l'upload).
     */
    private fun startDownload(fileHash: String, blockMap: Map<String, ResolvedBlockLocation>, isPreview: Boolean = false) {
        downloadJob = viewModelScope.launch {
            val k = catalogRepository.getEntry(fileHash).getOrNull()?.k ?: ErasureParameters().k
            downloadFileBlocksUseCase.invoke(blockMap, k).collect { state ->
                when (state) {
                    is DownloadProgressState.Progress -> {
                        Log.i(
                            "MobiCloud:DL",
                            "fileHash=${fileHash.take(8)} progress=${state.received}/${state.k} failed=${state.failed}"
                        )
                        lastNodeCount = state.contributions.map { it.nodeId }.toSet().size
                        _downloadState.value = DownloadState.Downloading(
                            fileHash = fileHash,
                            received = state.received,
                            k = state.k,
                            failed = state.failed,
                            contributions = state.contributions,
                            slowNodeIds = state.slowNodeIds,
                            failedFragmentIndices = state.failedFragmentIndices,
                            isPreview = isPreview
                        )
                    }
                    is DownloadProgressState.Completed -> {
                        // Story 6.3 — chaîne immédiate : déchiffrement + décodage EC + écriture
                        // atomique. Voir AssembleDownloadedFileUseCase pour les garanties
                        // (no partial visible, hash final, IV-transport gap, etc.).
                        assembleDownloadedFileUseCase.invoke(fileHash, state.blocks, isPreview)
                            .collect { progress ->
                                when (progress) {
                                    is AssembleDownloadedFileUseCase.AssembleProgress.Decrypting -> {
                                        Log.i(
                                            "MobiCloud:ASM",
                                            "fileHash=${fileHash.take(8)} decrypt=${progress.processed}/${progress.k}"
                                        )
                                        _downloadState.value = DownloadState.Decrypting(
                                            fileHash = fileHash,
                                            processed = progress.processed,
                                            k = progress.k,
                                            isPreview = isPreview
                                        )
                                    }
                                    is AssembleDownloadedFileUseCase.AssembleProgress.Finalized ->
                                        _downloadState.value = when (val r = progress.result) {
                                            is AssembleDownloadedFileUseCase.AssembleResult.Success -> {
                                                val durationMs = System.currentTimeMillis() - downloadStartMs
                                                DownloadState.Assembled(fileHash, r.filePath, durationMs, lastNodeCount, isPreview)
                                            }
                                            is AssembleDownloadedFileUseCase.AssembleResult.Failure ->
                                                DownloadState.Error(
                                                    fileHash,
                                                    r.exception.message ?: "échec reconstruction",
                                                    isPreview = isPreview
                                                )
                                        }
                                }
                            }
                    }
                    is DownloadProgressState.Failed ->
                        _downloadState.value = DownloadState.Error(fileHash, state.reason, isPreview = isPreview)
                }
            }
        }
    }

    fun resetDownloadState() {
        downloadJob?.cancel()
        locateJob?.cancel()
        _downloadState.value = DownloadState.Idle
    }

    fun cancelUpload() {
        // [Review][Patch] P2 — guard double-tap : si déjà Cancelled, le resetJob est en cours ;
        // un 2e appel l'annulerait → état Cancelled permanent. On sort immédiatement.
        if (_storeState.value is StoreState.Cancelled) return
        // [Review][Patch] P1 — guard Idle : si aucun upload actif, ne pas afficher "⊘ Upload annulé"
        // storeJob null ou inactif = pas d'upload en cours.
        if (storeJob?.isActive != true) return
        storeJob?.cancel()
        resetJob?.cancel()
        _storeState.value = StoreState.Cancelled
        scheduleReset(delayMs = 3000L)
    }

    fun storeFile(uri: Uri) {
        if (_storeState.value is StoreState.InProgress) {
            _uploadBusyEvent.tryEmit(Unit)
            return
        }
        resetJob?.cancel()
        _storeState.value = StoreState.InProgress.Encoding  // P7: set before launch to close TOCTOU window
        storeJob = viewModelScope.launch {
            val (fileSizeBytes, originalFileName) = withContext(ioDispatcher) {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) ?: ""
                        Pair(size, name)
                    } else Pair(null, "")
                } ?: Pair(null, "")
            }
            if (fileSizeBytes != null && fileSizeBytes > MAX_FILE_SIZE_BYTES) {
                _storeState.value = StoreState.Error("File too large (max ${MAX_FILE_SIZE_MB} MB)")
                scheduleReset()
                return@launch
            }

            val fileBytes = withContext(ioDispatcher) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: run {
                _storeState.value = StoreState.Error("Could not read the file")
                scheduleReset()
                return@launch
            }

            val fileHash = sha256Hex(fileBytes)

            val tempFile = File(context.cacheDir, "mobicloud_store_${System.currentTimeMillis()}.tmp")
            try {
                withContext(ioDispatcher) { tempFile.writeBytes(fileBytes) }

                val optimalResult = selectOptimalPeersUseCase(
                    fileSizeBytes = fileSizeBytes ?: 0L,
                    baseK = 1,  // TEMP démo soutenance : RS(1,1) au lieu de RS(2,1) — 2 peers suffisent
                    allowDuplicatePeers = false
                ).getOrElse { e ->
                    val userMessage = when (e) {
                        is PeerSelectionException.PeerFlowTimeout ->
                            "Network unavailable: unable to reach nodes in time."
                        is PeerSelectionException.InsufficientCapableNodes ->
                            "Insufficient storage: only ${e.message?.substringAfter("Disponibles : ") ?: "0"} " +
                            "node(s) with enough free space."
                        is PeerSelectionException.InsufficientRedundancyNodes ->
                            "Network too small: at least 3 connected devices needed to guarantee redundancy."
                        is PeerSelectionException.InvalidBaseK ->
                            "Invalid K parameter (${e.message})."
                        else -> "Node selection failed: ${e.message ?: "unknown error"}"
                    }
                    _storeState.value = StoreState.Error(userMessage)
                    scheduleReset()
                    return@launch
                }
                val params = optimalResult.params
                val selectedPeers = optimalResult.selectedPeers

                val fragments = encodeErasureFragmentsUseCase(tempFile, params)
                    .getOrElse { e ->
                        _storeState.value = StoreState.Error("Encoding failed: ${e.message ?: "unknown error"}")
                        scheduleReset()
                        return@launch
                    }

                _storeState.value = StoreState.InProgress.Encrypting

                // Story 6.3 — `getIdentity()` retourne la clé Keystore SIGN/VERIFY (incompatible
                // avec ECDH sur API < 31). Le wrap ECIES utilise une seconde paire dédiée
                // (`getEncryptionIdentity()`), software-managée, stockée chiffrée. Voir
                // Story 6.3 Contrainte #1 pour la justification.
                val encryptionIdentity = securityRepository.getEncryptionIdentity()
                    .getOrElse { e ->
                        _storeState.value = StoreState.Error("Encryption identity unavailable: ${e.message ?: "unknown error"}")
                        scheduleReset()
                        return@launch
                    }

                val bundle = fragmentCipherUseCase.encrypt(fragments, encryptionIdentity.publicKeyBytes)
                    .getOrElse { e ->
                        _storeState.value = StoreState.Error("Encryption failed: ${e.message ?: "unknown error"}")
                        scheduleReset()
                        return@launch
                    }

                val total = bundle.encryptedFragments.size
                val dataBlockCount = params.k
                _storeState.value = StoreState.InProgress.Distributing(total = total, dataBlockCount = dataBlockCount)

                distributeEncryptedBlocksUseCase.distribute(
                    encryptedBundle = bundle,
                    fileHash = fileHash,
                    params = params,
                    selectedPeers = selectedPeers,
                    originalFileName = originalFileName
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
                        _storeState.value = StoreState.Error("Distribution failed: ${e.message ?: "unknown error"}")
                        scheduleReset()
                    }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_storeState.value is StoreState.InProgress) {
                    _storeState.value = StoreState.Error("Unexpected error: ${e.message ?: "unknown error"}")
                    scheduleReset()
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    // Story 13.5 — actions dossiers

    fun navigateIntoFolder(name: String) { _currentFolder.value = name }
    fun navigateToRoot() { _currentFolder.value = null }

    fun moveFileToFolder(fileHash: String, folderPath: String?) {
        viewModelScope.launch { catalogRepository.moveFileToFolder(fileHash, folderPath) }
    }

    fun renameFolder(oldPath: String, newPath: String) {
        viewModelScope.launch { catalogRepository.renameFolder(oldPath, newPath) }
    }

    fun deleteFolder(folderPath: String) {
        viewModelScope.launch {
            if (_currentFolder.value == folderPath) _currentFolder.value = null
            catalogRepository.deleteFolder(folderPath)
        }
    }

    private fun scheduleReset(delayMs: Long = 5000L) {
        resetJob = viewModelScope.launch {
            delay(delayMs)  // > Snackbar Short duration to avoid cancelling in-flight snackbar
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
