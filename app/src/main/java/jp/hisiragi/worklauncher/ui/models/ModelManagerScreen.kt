package jp.hisiragi.worklauncher.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.domain.CatalogModel
import jp.hisiragi.worklauncher.domain.ModelDownloadState
import jp.hisiragi.worklauncher.domain.ModelModality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onBack: () -> Unit,
    viewModel: ModelManagerViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<CatalogModel?>(null) }
    var noRoomFor by remember { mutableStateOf<CatalogModel?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(
                        R.string.models_free_space,
                        formatGb(state.freeBytes),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(state.rows, key = { it.model.id }) { row ->
                ModelCard(
                    row = row,
                    download = state.download,
                    onDownload = {
                        if (viewModel.hasRoomFor(row.model)) {
                            confirming = row.model
                        } else {
                            noRoomFor = row.model
                        }
                    },
                    onCancel = viewModel::cancelDownload,
                    onActivate = { viewModel.activate(row.model) },
                    onDelete = { viewModel.delete(row.model) },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.models_custom_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    confirming?.let { model ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(model.displayName) },
            text = {
                Text(
                    stringResource(
                        R.string.models_confirm_download,
                        formatGb(model.sizeBytes),
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.download(model)
                        confirming = null
                    },
                ) { Text(stringResource(R.string.models_download)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    noRoomFor?.let { model ->
        AlertDialog(
            onDismissRequest = { noRoomFor = null },
            title = { Text(stringResource(R.string.models_no_room_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.models_no_room_body,
                        formatGb(model.sizeBytes),
                        formatGb(state.freeBytes),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { noRoomFor = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun ModelCard(
    row: ModelRow,
    download: ModelDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    val running = download as? ModelDownloadState.Running
    val downloadingThis = running?.modelId == row.model.id
    val failed = (download as? ModelDownloadState.Failed)?.takeIf { it.modelId == row.model.id }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (row.active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.model.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (row.model.modality == ModelModality.AUDIO) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = stringResource(R.string.models_audio_capable),
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    Text(
                        text = "${formatGb(row.model.sizeBytes)} · ${row.model.repo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (row.active) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.models_in_use),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (downloadingThis && running != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { running.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatGb(running.bytesDownloaded)} / ${formatGb(running.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            failed?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    downloadingThis -> {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.models_pause))
                        }
                    }

                    row.installed && !row.active -> {
                        Button(onClick = onActivate) {
                            Text(stringResource(R.string.models_use))
                        }
                    }

                    !row.installed -> {
                        Button(onClick = onDownload, enabled = running == null) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.models_download))
                        }
                    }
                }

                if (row.installed && !downloadingThis) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

private fun formatGb(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
