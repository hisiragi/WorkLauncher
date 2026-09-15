package jp.hisiragi.worklauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.data.db.TaskEntity
import jp.hisiragi.worklauncher.domain.AgendaEvent
import jp.hisiragi.worklauncher.domain.LauncherApp
import jp.hisiragi.worklauncher.domain.Priority
import jp.hisiragi.worklauncher.ui.components.AppIconImage
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.ui.components.StatTile
import jp.hisiragi.worklauncher.ui.components.combinedClickableCompat
import jp.hisiragi.worklauncher.ui.components.rememberBatteryStatus
import jp.hisiragi.worklauncher.ui.theme.PriorityColors
import jp.hisiragi.worklauncher.util.TimeUtils

@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenTimeCard: () -> Unit,
    onOpenHub: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gatedApp by viewModel.gatedApp.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshAgenda() }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ClockHeader(state) }

            item { SearchRow(onClick = onOpenSearch) }

            item {
                QuickActionRow(
                    state = state,
                    onClockIn = { viewModel.clockIn() },
                    onClockOut = viewModel::clockOut,
                    onToggleBreak = viewModel::toggleBreak,
                    onFocus = {
                        if (state.focus.active) onOpenFocus() else viewModel.startFocus()
                    },
                    onOpenHub = onOpenHub,
                )
            }

            if (state.focus.active) {
                item { FocusStrip(state, onOpenFocus) }
            }

            if (state.settings.showWorkSummaryCard) {
                item { WorkSummaryCard(state, onOpenTimeCard) }
            }

            if (state.settings.showAgendaCard) {
                item { AgendaCard(state, onOpenAgenda, viewModel::refreshAgenda) }
            }

            if (state.settings.showTasksCard) {
                item {
                    TasksCard(
                        state = state,
                        onOpenTasks = onOpenTasks,
                        onToggle = viewModel::toggleTaskDone,
                    )
                }
            }

            if (state.suggestedApps.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.home_frequent_apps),
                        icon = Icons.Filled.Apps,
                        actionLabel = stringResource(R.string.home_all_apps),
                        onActionClick = onOpenDrawer,
                    ) {
                        AppStrip(
                            apps = state.suggestedApps,
                            onClick = { viewModel.launchApp(context, it) },
                        )
                    }
                }
            }
        }

        Dock(
            apps = state.dockApps,
            onAppClick = { viewModel.launchApp(context, it) },
            onDrawerClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        )
    }

    gatedApp?.let { app ->
        FocusGateDialog(
            app = app,
            onDismiss = viewModel::dismissGate,
            onConfirm = { viewModel.launchGatedApp(context) },
        )
    }
}

@Composable
private fun ClockHeader(state: HomeUiState) {
    val battery = rememberBatteryStatus()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = TimeUtils.formatTime(state.nowMillis, state.settings.use24HourClock),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = TimeUtils.formatDate(TimeUtils.toLocalDateTime(state.nowMillis).toLocalDate()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            if (battery.known) {
                val batteryTint = if (battery.charging) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    imageVector = if (battery.charging) {
                        Icons.Filled.BatteryChargingFull
                    } else {
                        Icons.Filled.BatteryStd
                    },
                    contentDescription = stringResource(
                        if (battery.charging) R.string.home_battery_charging else R.string.home_battery
                    ),
                    modifier = Modifier.size(14.dp),
                    tint = batteryTint,
                )
                Text(
                    text = "${battery.level}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = batteryTint,
                )
            }
            Spacer(Modifier.weight(1f))
            WorkStatusChip(state)
        }
    }
}

@Composable
private fun WorkStatusChip(state: HomeUiState) {
    val (label, color) = when {
        state.onBreak -> stringResource(R.string.status_on_break) to MaterialTheme.colorScheme.tertiary
        state.clockedIn -> stringResource(R.string.status_working) to MaterialTheme.colorScheme.secondary
        else -> stringResource(R.string.status_off_duty) to MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
            disabledContainerColor = color.copy(alpha = 0.12f),
        ),
    )
}

@Composable
private fun SearchRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.home_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionRow(
    state: HomeUiState,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onToggleBreak: () -> Unit,
    onFocus: () -> Unit,
    onOpenHub: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (state.clockedIn) {
            Button(
                onClick = onClockOut,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_clock_out), maxLines = 1)
            }
            val breakLabel = stringResource(
                if (state.onBreak) R.string.action_break_end else R.string.action_break_start
            )
            if (state.onBreak) {
                Button(
                    onClick = onToggleBreak,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.FreeBreakfast,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(breakLabel, maxLines = 1)
                }
            } else {
                OutlinedButton(
                    onClick = onToggleBreak,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.FreeBreakfast,
                        contentDescription = breakLabel,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            Button(
                onClick = onClockIn,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_clock_in), maxLines = 1)
            }
        }

        OutlinedButton(
            onClick = onFocus,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = stringResource(R.string.action_focus),
                modifier = Modifier.size(18.dp),
            )
        }
        OutlinedButton(
            onClick = onOpenHub,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Filled.Work,
                contentDescription = stringResource(R.string.action_open_hub),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FocusStrip(state: HomeUiState, onOpenFocus: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenFocus),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = TimeUtils.formatCountdown(state.focus.remainingSeconds),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = state.focus.taskTitle.ifBlank {
                        stringResource(R.string.focus_cycle_label, state.focus.cycle + 1)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.focus.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
private fun WorkSummaryCard(state: HomeUiState, onOpenTimeCard: () -> Unit) {
    SectionCard(
        title = stringResource(R.string.home_today_summary),
        icon = Icons.Filled.Work,
        actionLabel = stringResource(R.string.home_details),
        onActionClick = onOpenTimeCard,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatTile(
                value = TimeUtils.formatDuration(state.workedMinutes),
                label = stringResource(R.string.stat_worked),
            )
            StatTile(
                value = state.timeCard?.clockInAt
                    ?.let { TimeUtils.formatTime(it, state.settings.use24HourClock) }
                    ?: "--:--",
                label = stringResource(R.string.stat_clock_in),
            )
            StatTile(
                value = TimeUtils.formatDuration(state.focusMinutesToday),
                label = stringResource(R.string.stat_focus),
                accent = MaterialTheme.colorScheme.tertiary,
            )
            StatTile(
                value = state.openTaskCount.toString(),
                label = stringResource(R.string.stat_open_tasks),
                accent = if (state.overdueCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
        }
        if (state.onBreak) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FreeBreakfast,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.home_on_break_for,
                            TimeUtils.formatDuration(state.currentBreakMinutes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        val remaining = state.settings.standardWorkMinutes - state.workedMinutes
        if (state.clockedIn && remaining > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_remaining_today, TimeUtils.formatDuration(remaining)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgendaCard(
    state: HomeUiState,
    onOpenAgenda: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_agenda),
        icon = Icons.Filled.CalendarMonth,
        actionLabel = stringResource(R.string.home_details),
        onActionClick = onOpenAgenda,
    ) {
        when {
            !state.calendarPermissionGranted -> {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.agenda_permission_needed))
                }
            }
            state.todayEvents.isEmpty() -> EmptyState(stringResource(R.string.agenda_empty_today))
            else -> Column {
                state.todayEvents.take(3).forEach { event ->
                    AgendaRow(event, state)
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(event: AgendaEvent, state: HomeUiState) {
    val isNow = event.startAt <= state.nowMillis && event.endAt >= state.nowMillis
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (event.calendarColor != 0) {
                        Color(event.calendarColor)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (event.allDay) {
                    stringResource(R.string.agenda_all_day)
                } else {
                    "${TimeUtils.formatTime(event.startAt, state.settings.use24HourClock)}" +
                        " – ${TimeUtils.formatTime(event.endAt, state.settings.use24HourClock)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isNow) {
            Text(
                text = stringResource(R.string.agenda_now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TasksCard(
    state: HomeUiState,
    onOpenTasks: () -> Unit,
    onToggle: (TaskEntity) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_tasks),
        icon = Icons.Filled.CheckCircle,
        actionLabel = stringResource(R.string.home_details),
        onActionClick = onOpenTasks,
    ) {
        if (state.todayTasks.isEmpty()) {
            EmptyState(stringResource(R.string.tasks_empty_today))
        } else {
            Column {
                state.todayTasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(task) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (task.isDone) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = PriorityColors[Priority.fromLevel(task.priority).level],
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        task.dueAt?.let { due ->
                            Text(
                                text = TimeUtils.formatTime(due, state.settings.use24HourClock),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (due < state.nowMillis) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppStrip(apps: List<LauncherApp>, onClick: (LauncherApp) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        apps.take(6).forEach { app ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClick(app) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIconImage(app = app, size = 40.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Dock(
    apps: List<LauncherApp>,
    onAppClick: (LauncherApp) -> Unit,
    onDrawerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            apps.take(5).forEach { app ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickableCompat(onClick = { onAppClick(app) }),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIconImage(app = app, size = 40.dp)
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onDrawerClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = stringResource(R.string.home_all_apps),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
fun FocusGateDialog(app: LauncherApp, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Timer, contentDescription = null) },
        title = { Text(stringResource(R.string.focus_gate_title)) },
        text = { Text(stringResource(R.string.focus_gate_message, app.label)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.focus_gate_open_anyway)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.focus_gate_stay)) }
        },
    )
}
