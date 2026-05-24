package com.mobicloud.presentation.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.domain.models.CatalogEntry
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
object TrashRoute

private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
private const val TTL_DAYS = 30L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val entries by viewModel.deletedEntries.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var confirmDeleteHash by remember { mutableStateOf<String?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    // Dialogue suppression individuelle
    confirmDeleteHash?.let { hashToDelete ->
        AlertDialog(
            onDismissRequest = { confirmDeleteHash = null },
            title = { Text("Supprimer définitivement ?", color = Color.White) },
            text = { Text("Ce fichier sera supprimé de façon irréversible.", color = Color(0xFFCCCCCC)) },
            confirmButton = {
                TextButton(onClick = {
                    // F2 fix: hash capturé localement, pas de force-unwrap !!
                    viewModel.permanentlyDelete(hashToDelete)
                    confirmDeleteHash = null
                }) {
                    Text("Delete", color = Color(0xFFFF3333))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteHash = null }) {
                    Text("Cancel", color = Color(0xFFFFB300))
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    // Dialogue vider la corbeille
    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Empty trash?", color = Color.White) },
            text = { Text("All files will be permanently deleted. This action cannot be undone.", color = Color(0xFFCCCCCC)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyConfirm = false
                }) {
                    Text("Empty", color = Color(0xFFFF3333))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("Cancel", color = Color(0xFFFFB300))
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Trash", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showEmptyConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Empty trash",
                                tint = Color(0xFFFF3333)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Your trash is empty",
                        color = Color(0xFF888888),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.fileHash }) { entry ->
                    TrashEntryRow(
                        entry = entry,
                        onRestore = {
                            viewModel.restoreEntry(entry.fileHash)
                            scope.launch { snackbarHostState.showSnackbar("File restored") }
                        },
                        onDelete = { confirmDeleteHash = entry.fileHash }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashEntryRow(
    entry: CatalogEntry,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val deletedDate = entry.deletedAt?.let { DATE_FORMAT.format(Date(it)) } ?: "—"
    val daysLeft = entry.deletedAt?.let {
        val elapsed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it)
        (TTL_DAYS - elapsed).coerceAtLeast(0)
    } ?: TTL_DAYS

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.originalFileName.ifBlank { entry.fileHash.take(16) },
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Supprimé le $deletedDate · $daysLeft j restants",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restaurer",
                    tint = Color(0xFFFFB300)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Supprimer définitivement",
                    tint = Color(0xFFFF3333)
                )
            }
        }
    }
}
