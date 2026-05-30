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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
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
import com.mobicloud.presentation.explorer.components.FolderItem
import com.mobicloud.presentation.explorer.components.MoveToFolderSheet
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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    // Story 13.5 — état local pour le bottom sheet et les dialogues
    var moveSheetEntry by remember { mutableStateOf<String?>(null) }
    var folderContextTarget by remember { mutableStateOf<String?>(null) }
    var renameDialogTarget by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

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
    
    // Preview: automatically open the file without showing the bottom sheet
    LaunchedEffect(assembledState) {
        if (assembledState != null && assembledState.isPreview) {
            val filePath = assembledState.filePath
            val file = File(filePath)
            android.util.Log.i("MobiCloud:Open", "[PREVIEW] tentative ouverture path=$filePath exists=${file.exists()} size=${if (file.exists()) file.length() else -1} ext='${file.extension}'")
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    viewModel.resetDownloadState()
                } catch (e: ActivityNotFoundException) {
                    android.util.Log.w("MobiCloud:Open", "[PREVIEW] ActivityNotFound mime=$mimeType", e)
                    snackbarHostState.showSnackbar("No app installed to open this file type.")
                    viewModel.resetDownloadState()
                }
            } catch (e: Exception) {
                android.util.Log.e("MobiCloud:Open", "[PREVIEW] FileProvider échoué path=$filePath", e)
                snackbarHostState.showSnackbar("Could not open the file. Please try again.")
                viewModel.resetDownloadState()
            }
        }
    }

    // Download: show the details bottom sheet for user confirmation
    if (assembledState != null && !assembledState.isPreview) {
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

    // Story 13.5 — MoveToFolderSheet
    moveSheetEntry?.let { fileHash ->
        MoveToFolderSheet(
            folders = folders,
            currentFolderPath = entries.find { it.fileHash == fileHash }?.folderPath,
            onMoveToFolder = { folderName -> viewModel.moveFileToFolder(fileHash, folderName) },
            onMoveToRoot = { viewModel.moveFileToFolder(fileHash, null) },
            onDismiss = { moveSheetEntry = null }
        )
    }

    // Story 13.5 — dialogue contextuel dossier (long-press sur FolderItem)
    folderContextTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderContextTarget = null },
            title = { Text(folder, fontWeight = FontWeight.Bold) },
            text = { Text("Que voulez-vous faire avec ce dossier ?") },
            confirmButton = {
                TextButton(onClick = {
                    folderContextTarget = null
                    renameInput = folder
                    renameDialogTarget = folder
                }) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Renommer")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folder)
                    folderContextTarget = null
                }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF3B30))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Supprimer", color = Color(0xFFFF3B30))
                }
            }
        )
    }

    // Story 13.5 — dialogue renommage dossier
    renameDialogTarget?.let { oldName ->
        AlertDialog(
            onDismissRequest = { renameDialogTarget = null; renameInput = "" },
            title = { Text("Renommer le dossier", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Nouveau nom") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank() && renameInput != oldName) {
                            viewModel.renameFolder(oldName, renameInput.trim())
                        }
                        renameDialogTarget = null
                        renameInput = ""
                    },
                    enabled = renameInput.isNotBlank()
                ) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogTarget = null; renameInput = "" }) { Text("Annuler") }
            }
        )
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
            Spacer(modifier = Modifier.height(12.dp))
            
            // Search Bar
            ExplorerSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(10.dp))

            // Story 13.5 — breadcrumb quand on est dans un dossier
            if (currentFolder != null) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.navigateToRoot() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color(0xFF0A84FF))
                    }
                    Text(
                        text = "/ $currentFolder",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C1C1E)
                    )
                }
            }

            // Story 13.5 — row de dossiers à la racine
            if (currentFolder == null && folders.isNotEmpty()) {
                val allEntries = entries
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    items(folders) { folder ->
                        val count = allEntries.count { it.folderPath == folder }
                        FolderItem(
                            name = folder,
                            fileCount = count,
                            onClick = { viewModel.navigateIntoFolder(folder) },
                            onLongClick = { folderContextTarget = folder }
                        )
                    }
                }
            }

            // Filter Chips
            FilterChipRow(
                selectedCategory = selectedCategory,
                onCategorySelected = { viewModel.setSelectedCategory(it) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
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
                                        onDownload = { fileHash -> viewModel.initiateDownload(fileHash, isPreview = false) },
                                        onPreview = { fileHash -> viewModel.initiateDownload(fileHash, isPreview = true) },
                                        onLongClick = { moveSheetEntry = entry.fileHash }
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

@Composable
private fun ExplorerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search files...", color = Color(0xFF8E8E93)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF8E8E93)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF8E8E93))
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF0A84FF),
            unfocusedBorderColor = Color(0xFFE5E5EA),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = Color(0xFF1C1C1E),
            unfocusedTextColor = Color(0xFF1C1C1E)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun FilterChipRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("All", "Images", "Videos", "Audio", "Documents")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(categories) { category ->
            CustomFilterChip(
                text = category,
                isSelected = category == selectedCategory,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun CustomFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) Color(0xFF0A84FF) else Color.White
    val textColor = if (isSelected) Color.White else Color(0xFF48484A)
    val borderColor = if (isSelected) Color(0xFF0A84FF) else Color(0xFFE5E5EA)

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
