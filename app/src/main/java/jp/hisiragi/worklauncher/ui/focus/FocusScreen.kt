package jp.hisiragi.worklauncher.ui.focus

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.domain.FocusKind
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.ui.components.StatTile
import jp.hisiragi.worklauncher.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    onBack: () -> Unit,
    viewModel: FocusViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.timer.kind == kind,
                        onClick = { if (state.timer.idle) viewModel.start(kind) },
                        enabled = state.timer.idle,
                        label = { Text(focusKindLabel(kind)) },
                    )
                }
            }

            TimerDial(
                progress = state.timer.progress,
                label = TimeUtils.formatCountdown(state.timer.remainingSeconds),
                caption = state.timer.taskTitle.ifBlank {
                    stringResource(R.string.focus_cycle_label, state.timer.cycle + 1)
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    state.timer.running -> {
                        FilledTonalButton(onClick = viewModel::pause) {
                            Icon(Icons.Filled.Pause, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_pause))
                        }
                    }
                    state.timer.paused -> {
                        Button(onClick = viewModel::resume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_resume))
                        }
                    }
                    else -> {
                        Button(onClick = { viewModel.start() }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_start))
                        }
                    }
                }
                if (state.timer.active) {
                    FilledTonalButton(onClick = viewModel::stop) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_stop))
                    }
                    FilledTonalButton(onClick = viewModel::skip) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.focus_skip),
                        )
                    }
                }
            }

            if (state.timer.active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10).forEach { minutes ->
                        AssistChip(
                            onClick = { viewModel.addMinutes(minutes) },
                            label = { Text(stringResource(R.string.focus_add_minutes, minutes)) },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 25, 45, 60, 90).forEach { minutes ->
                        FilterChip(
                            selected = state.settings.pomodoroFocusMinutes == minutes,
                            onClick = { viewModel.setFocusMinutes(minutes) },
                            label = { Text(stringResource(R.string.focus_minutes, minutes)) },
                        )
                    }
                }
            }

            SectionCard(title = stringResource(R.string.focus_today)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatTile(
                        value = state.completedToday.toString(),
                        label = stringResource(R.string.focus_completed_sessions),
                    )
                    StatTile(
                        value = TimeUtils.formatDuration(state.focusMinutesToday),
                        label = stringResource(R.string.focus_total_time),
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                    StatTile(
                        value = state.todaySessions.size.toString(),
                        label = stringResource(R.string.focus_started_sessions),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (state.openTasks.isNotEmpty() && state.timer.idle) {
                SectionCard(title = stringResource(R.string.focus_pick_task)) {
                    Column {
                        state.openTasks.take(6).forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.start(FocusKind.FOCUS, task) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (task.focusedMinutes > 0) {
                                    Text(
                                        text = TimeUtils.formatDuration(task.focusedMinutes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Circular countdown ring. */
@Composable
private fun TimerDial(progress: Float, label: String, caption: String) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.displayLarge)
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}

@Composable
fun focusKindLabel(kind: FocusKind): String = stringResource(
    when (kind) {
        FocusKind.FOCUS -> R.string.focus_kind_focus
        FocusKind.SHORT_BREAK -> R.string.focus_kind_short_break
        FocusKind.LONG_BREAK -> R.string.focus_kind_long_break
    }
)
