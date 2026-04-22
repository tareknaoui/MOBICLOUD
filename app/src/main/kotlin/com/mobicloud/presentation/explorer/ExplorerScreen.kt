package com.mobicloud.presentation.explorer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobicloud.presentation.explorer.components.CatalogEntryCard
import com.mobicloud.presentation.explorer.components.ErasureProgressIndicator
import kotlinx.serialization.Serializable

@Serializable
object ExplorerRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: ExplorerViewModel = hiltViewModel()
) {
    val entries by viewModel.catalogEntries.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val storeState by viewModel.storeState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Clé stable : ne change que sur Success/Error, pas à chaque ACK de Distributing
    val terminalState = remember(storeState) {
        storeState.takeIf { it is StoreState.Success || it is StoreState.Error }
    }
    LaunchedEffect(terminalState) {
        when (terminalState) {
            is StoreState.Success -> snackbarHostState.showSnackbar(
                "Fichier stocké avec succès sur ${terminalState.nodeCount} nœuds"
            )
            is StoreState.Error -> snackbarHostState.showSnackbar("Erreur : ${terminalState.message}")
            else -> Unit
        }
    }

    val terminalDownloadState = remember(downloadState) {
        downloadState.takeIf { it is DownloadState.Located || it is DownloadState.Error }
    }
    LaunchedEffect(terminalDownloadState) {
        when (val s = terminalDownloadState) {
            is DownloadState.Located -> snackbarHostState.showSnackbar(
                "${s.blockMap.size} blocs localisés pour ${s.fileHash.take(8)}..."
            )
            is DownloadState.Error -> snackbarHostState.showSnackbar("Erreur : ${s.message}")
            else -> Unit
        }
    }

    val storeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.storeFile(it) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { storeLauncher.launch("*/*") }) {
                Icon(Icons.Default.Upload, contentDescription = "Stocker un fichier")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val inProgressState = storeState as? StoreState.InProgress
            if (inProgressState != null) {
                ErasureProgressIndicator(
                    state = inProgressState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshCatalog() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "> CATALOGUE VIDE — aucun fichier stocké dans le cluster_",
                            color = Color(0xFFFFB300),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.fileHash }) { entry ->
                            CatalogEntryCard(
                                entry = entry,
                                onDownload = { fileHash -> viewModel.initiateDownload(fileHash) }
                            )
                        }
                    }
                }
            }
        }
    }
}
