package com.mobicloud.presentation.explorer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.presentation.explorer.components.AssembledBottomSheet
import com.mobicloud.presentation.explorer.components.CatalogEntryCard
import com.mobicloud.presentation.explorer.components.DownloadProgressIndicator
import com.mobicloud.presentation.explorer.components.ErasureProgressIndicator
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
object ExplorerRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    onNavigateToTrash: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ExplorerViewModel = hiltViewModel()
) {
    val entries by viewModel.catalogEntries.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val storeState by viewModel.storeState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val terminalState = remember(storeState) {
        storeState.takeIf { it is StoreState.Success || it is StoreState.Error }
    }
    LaunchedEffect(terminalState) {
        when (terminalState) {
            is StoreState.Success -> snackbarHostState.showSnackbar(
                "Saved across ${terminalState.nodeCount} member${if (terminalState.nodeCount > 1) "s" else ""}"
            )
            is StoreState.Error -> {
                android.util.Log.d("MobiCloud:Explorer", "[STORE-ERROR] ${terminalState.message}")
                snackbarHostState.showSnackbar("An error occurred. Please try again.")
            }
            else -> Unit
        }
    }

    val terminalDownloadState = remember(downloadState) {
        downloadState.takeIf { it is DownloadState.Error }
    }
    LaunchedEffect(terminalDownloadState) {
        val s = terminalDownloadState as? DownloadState.Error ?: return@LaunchedEffect
        android.util.Log.d("MobiCloud:Explorer", "[DOWNLOAD-ERROR] ${s.message}")
        val friendlyMessage = if (s.message.contains("blocs valides") || s.message.contains("nœuds actifs"))
            "Not enough members online to retrieve this file"
        else
            "An error occurred. Please try again."
        snackbarHostState.showSnackbar(friendlyMessage)
    }

    val assembledState = downloadState as? DownloadState.Assembled
    if (assembledState != null) {
        AssembledBottomSheet(
            state = assembledState,
            onOpen = { filePath ->
                val file = File(filePath)
                android.util.Log.i("MobiCloud:Open", "[DIAG] tentative ouverture path=$filePath exists=${file.exists()} size=${if (file.exists()) file.length() else -1} ext='${file.extension}'")
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    android.util.Log.i("MobiCloud:Open", "[DIAG] uri=$uri")
                    val mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
                    android.util.Log.i("MobiCloud:Open", "[DIAG] mime=$mimeType")
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                        viewModel.resetDownloadState()
                    } catch (e: ActivityNotFoundException) {
                        android.util.Log.w("MobiCloud:Open", "[DIAG] ActivityNotFound mime=$mimeType", e)
                        scope.launch { snackbarHostState.showSnackbar("No app installed to open this file type.") }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MobiCloud:Open", "[DIAG] FileProvider échoué path=$filePath", e)
                    scope.launch { snackbarHostState.showSnackbar("Could not open the file. Please try again.") }
                }
            },
            onDismiss = { viewModel.resetDownloadState() }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.uploadBusyEvent.collect {
            snackbarHostState.showSnackbar("Upload in progress, please wait")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { fileHash ->
            val result = snackbarHostState.showSnackbar(
                message = "Moved to trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoMoveToTrash(fileHash)
            }
        }
    }

    val storeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.storeFile(it) }
    }

    // Capture last non-null progress states so exit animations can still display them
    val inProgressState = storeState as? StoreState.InProgress
    var capturedUpload by remember { mutableStateOf(inProgressState) }
    if (inProgressState != null) capturedUpload = inProgressState

    val inProgressDownload = downloadState.takeIf { it is DownloadState.Downloading || it is DownloadState.Decrypting }
    var capturedDownload by remember { mutableStateOf(inProgressDownload) }
    if (inProgressDownload != null) capturedDownload = inProgressDownload

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF4F6F8), // Clean cool slate gray/off-white background
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1E)
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToTrash) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Trash",
                            tint = Color(0xFF8E8E93)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White // Sleek white top bar
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { storeLauncher.launch("*/*") },
                containerColor = Color(0xFF0A84FF),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp) // Premium rounded corner FAB instead of generic pill
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Store a file")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = inProgressState != null,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                capturedUpload?.let { state ->
                    ErasureProgressIndicator(
                        state = state,
                        onCancel = { viewModel.cancelUpload() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = storeState is StoreState.Cancelled,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Text(
                    text = "Upload cancelled",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFF3B30),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            AnimatedVisibility(
                visible = inProgressDownload != null,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                capturedDownload?.let { state ->
                    DownloadProgressIndicator(
                        state = state,
                        onCancel = { viewModel.resetDownloadState() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshCatalog() },
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = entries.isEmpty(),
                    label = "emptyVsList"
                ) { isEmpty ->
                    if (isEmpty) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(horizontal = 32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(Color(0xFFE8ECEF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = Color(0xFF90A4AE),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "No shared files",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1C1C1E)
                                    )
                                    Text(
                                        text = "Tap the cloud icon in the bottom right to upload and secure your first file.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF757575),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(entries, key = { it.fileHash }) { entry ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            viewModel.moveToTrash(entry.fileHash)
                                            true
                                        } else false
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    modifier = Modifier.animateItem(fadeInSpec = tween(300), fadeOutSpec = tween(200)),
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    color = Color(0xFFFFEBEE), // Soft premium rose background
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(16.dp))
                                                .padding(end = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFFF3B30),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                ) {
                                    CatalogEntryCard(
                                        entry = entry,
                                        onDownload = { fileHash -> viewModel.initiateDownload(fileHash) }
                                    )
                                }
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}
