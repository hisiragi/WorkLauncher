package jp.hisiragi.worklauncher.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.data.db.TaskEntity
import jp.hisiragi.worklauncher.domain.Priority
import jp.hisiragi.worklauncher.ui.components.ColorDot
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.components.StatTile
import jp.hisiragi.worklauncher.ui.theme.PriorityColors
import jp.hisiragi.worklauncher.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    viewModel: TasksViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.filter == TaskFilter.DONE) {
                        IconButton(onClick = viewModel::clearCompleted) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.tasks_clear_completed),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tasks_add))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatTile(
                    value = state.openCount.toString(),
                    label = stringResource(R.string.stat_open_tasks),
                )
                StatTile(
                    value = state.overdueCount.toString(),
                    label = stringResource(R.string.stat_overdue),
                    accent = MaterialTheme.colorScheme.error,
                )
                StatTile(
                    value = state.doneTodayCount.toString(),
                    label = stringResource(R.string.stat_done_today),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filterLabel(filter)) },
                    )
                }
            }

            if (state.projects.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.projectFilter == null,
                        onClick = { viewModel.setProjectFilter(null) },
                        label = { Text(stringResource(R.string.tasks_all_projects)) },
                    )
                    state.projects.forEach { project ->
                        FilterChip(
                            selected = state.projectFilter == project,
                            onClick = {
                                viewModel.setProjectFilter(
                                    if (state.projectFilter == project) null else project
                                )
                            },
                            label = { Text(project) },
                        )
                    }
                }
            }

            if (state.tasks.isEmpty()) {
                EmptyState(stringResource(R.string.tasks_empty))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            use24h = state.settings.use24HourClock,
                            onToggle = { viewModel.toggleDone(task) },
                            onClick = { editing = task },
                            onFocus = { viewModel.focusOn(task) },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        TaskEditorSheet(
            task = null,
            projects = state.projects,
            use24h = state.settings.use24HourClock,
            onDismiss = { creating = false },
            onSave = {
                viewModel.add(it)
                creating = false
            },
            onDelete = null,
        )
    }

    editing?.let { task ->
        TaskEditorSheet(
            task = task,
            projects = state.projects,
            use24h = state.settings.use24HourClock,
            onDismiss = { editing = null },
            onSave = {
                viewModel.update(it)
                editing = null
            },
            onDelete = {
                viewModel.delete(task)
                editing = null
            },
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    use24h: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    val overdue = !task.isDone && task.dueAt != null && task.dueAt < System.currentTimeMillis()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (task.isDone) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = stringResource(R.string.tasks_toggle_done),
                    tint = PriorityColors[Priority.fromLevel(task.priority).level],
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    task.project?.takeIf { it.isNotBlank() }?.let { project ->
                        ColorDot(color = MaterialTheme.colorScheme.secondary, size = 6.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = project,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    task.dueAt?.let { due ->
                        Text(
                            text = TimeUtils.formatDateShort(
                                TimeUtils.toLocalDateTime(due).toLocalDate()
                            ) + " " + TimeUtils.formatTime(due, use24h),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (task.focusedMinutes > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = TimeUtils.formatDuration(task.focusedMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            if (!task.isDone) {
                IconButton(onClick = onFocus) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = stringResource(R.string.tasks_start_focus),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun filterLabel(filter: TaskFilter): String = stringResource(
    when (filter) {
        TaskFilter.TODAY -> R.string.tasks_filter_today
        TaskFilter.WEEK -> R.string.tasks_filter_week
        TaskFilter.ALL -> R.string.tasks_filter_all
        TaskFilter.DONE -> R.string.tasks_filter_done
    }
)
