package jp.hisiragi.worklauncher.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.data.db.NoteEntity
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.theme.NoteColors
import jp.hisiragi.worklauncher.ui.theme.noteTint
import jp.hisiragi.worklauncher.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    viewModel: NotesViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val llmAvailability by viewModel.llmAvailability.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = NoteEntity() }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.notes_add))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.notes_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            )
            Spacer(Modifier.height(8.dp))

            if (state.notes.isEmpty()) {
                EmptyState(stringResource(R.string.notes_empty))
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { editing = note },
                            onTogglePin = { viewModel.togglePinned(note) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { note ->
        NoteEditorSheet(
            note = note,
            canSummarize = llmAvailability.isReady,
            summary = summary,
            onSummarize = viewModel::summarize,
            onDismiss = {
                editing = null
                viewModel.clearSummary()
            },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = if (note.id != 0L) {
                {
                    viewModel.delete(note)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun NoteCard(note: NoteEntity, onClick: () -> Unit, onTogglePin: () -> Unit) {
    val tint = noteTint(note.colorIndex)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint.container),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { stringResource(R.string.notes_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    color = tint.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.notes_pin),
                        modifier = Modifier.size(16.dp),
                        tint = if (note.pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            tint.content.copy(alpha = 0.35f)
                        },
                    )
                }
            }
            if (note.body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint.content.copy(alpha = 0.82f),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = TimeUtils.formatDateShort(
                    TimeUtils.toLocalDateTime(note.updatedAt).toLocalDate()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = tint.content.copy(alpha = 0.6f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorSheet(
    note: NoteEntity,
    canSummarize: Boolean,
    summary: SummaryState,
    onSummarize: (NoteEntity) -> Unit,
    onDismiss: () -> Unit,
    onSave: (NoteEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    var colorIndex by remember { mutableIntStateOf(note.colorIndex) }
    var pinned by remember { mutableStateOf(note.pinned) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (note.id == 0L) R.string.notes_new else R.string.notes_edit
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { pinned = !pinned }) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.notes_pin),
                        tint = if (pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.notes_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.notes_field_body)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
            )

            if (canSummarize) {
                SummarySection(
                    summary = summary,
                    enabled = body.isNotBlank(),
                    onSummarize = {
                        onSummarize(note.copy(title = title.trim(), body = body.trim()))
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NoteColors.indices.forEach { index ->
                    Spacer(
                        modifier = Modifier
                            .size(if (colorIndex == index) 34.dp else 28.dp)
                            .clip(CircleShape)
                            .background(noteTint(index).container)
                            .clickable { colorIndex = index }
                    )
                }
            }

            TextButton(
                onClick = {
                    onSave(
                        note.copy(
                            title = title.trim(),
                            body = body.trim(),
                            colorIndex = colorIndex,
                            pinned = pinned,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SummarySection(
    summary: SummaryState,
    enabled: Boolean,
    onSummarize: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onSummarize,
            enabled = enabled && summary !is SummaryState.Running,
        ) {
            if (summary is SummaryState.Running) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(stringResource(R.string.notes_summarize))
        }

        when (summary) {
            is SummaryState.Ready -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.notes_summary),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = summary.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SummaryState.Failed -> Text(
                text = stringResource(R.string.notes_summarize_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )

            else -> Unit
        }
    }
}
