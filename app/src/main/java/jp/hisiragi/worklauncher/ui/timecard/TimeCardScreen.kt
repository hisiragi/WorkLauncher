package jp.hisiragi.worklauncher.ui.timecard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.domain.WorkPlace
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.components.LabeledRow
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.ui.components.StatTile
import jp.hisiragi.worklauncher.util.TimeUtils
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCardScreen(
    onBack: () -> Unit,
    viewModel: TimeCardViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingRow by remember { mutableStateOf<TimeCardDayRow?>(null) }

    val csvHeader = listOf(
        stringResource(R.string.csv_date),
        stringResource(R.string.csv_clock_in),
        stringResource(R.string.csv_clock_out),
        stringResource(R.string.csv_break_minutes),
        stringResource(R.string.csv_worked_hours),
        stringResource(R.string.csv_overtime_hours),
        stringResource(R.string.csv_work_place),
        stringResource(R.string.csv_memo),
    )
    val csvSubject = stringResource(R.string.timecard_export_subject)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timecard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportCsv(context, csvHeader, csvSubject) }) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = stringResource(R.string.timecard_export),
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
            item { TodayCard(state, viewModel) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeCardRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.range == range,
                            onClick = { viewModel.setRange(range) },
                            label = { Text(rangeLabel(range)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.shiftPeriod(-1) }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(R.string.timecard_previous),
                        )
                    }
                    TextButton(onClick = viewModel::resetPeriod) {
                        Text(stringResource(R.string.timecard_this_period))
                    }
                    IconButton(onClick = { viewModel.shiftPeriod(1) }) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.timecard_next),
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.timecard_period_summary)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatTile(
                            value = TimeUtils.formatDuration(state.totalMinutes),
                            label = stringResource(R.string.stat_total),
                        )
                        StatTile(
                            value = TimeUtils.formatDuration(state.overtimeMinutes),
                            label = stringResource(R.string.stat_overtime),
                            accent = MaterialTheme.colorScheme.error,
                        )
                        StatTile(
                            value = state.daysWorked.toString(),
                            label = stringResource(R.string.stat_days),
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                        StatTile(
                            value = if (state.daysWorked > 0) {
                                TimeUtils.formatDuration(state.totalMinutes / state.daysWorked)
                            } else {
                                "--"
                            },
                            label = stringResource(R.string.stat_average),
                            accent = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            if (state.rows.isEmpty()) {
                item { EmptyState(stringResource(R.string.timecard_empty)) }
            } else {
                items(state.rows, key = { it.date.toEpochDay() }) { row ->
                    DayRow(
                        row = row,
                        use24h = state.settings.use24HourClock,
                        onClick = { editingRow = row },
                    )
                }
            }
        }
    }

    editingRow?.let { row ->
        DayEditorSheet(
            row = row,
            use24h = state.settings.use24HourClock,
            onDismiss = { editingRow = null },
            onSetWorkPlace = { viewModel.setWorkPlace(row.date, it) },
            onSetBreakMinutes = { viewModel.setBreakMinutes(row.date, it) },
            onSetMemo = { viewModel.setMemo(row.date, it) },
            onSetTimes = { inAt, outAt -> viewModel.setTimes(row.date, inAt, outAt) },
            onDelete = {
                viewModel.deleteDay(row.date)
                editingRow = null
            },
        )
    }
}

@Composable
private fun TodayCard(state: TimeCardUiState, viewModel: TimeCardViewModel) {
    SectionCard(title = stringResource(R.string.timecard_today)) {
        LabeledRow(
            label = stringResource(R.string.stat_clock_in),
            value = state.today?.clockInAt
                ?.let { TimeUtils.formatTime(it, state.settings.use24HourClock) } ?: "--:--",
        )
        LabeledRow(
            label = stringResource(R.string.stat_clock_out),
            value = state.today?.clockOutAt
                ?.let { TimeUtils.formatTime(it, state.settings.use24HourClock) } ?: "--:--",
        )
        LabeledRow(
            label = stringResource(R.string.stat_break),
            value = TimeUtils.formatDuration(state.today?.breakMinutes ?: 0),
        )
        LabeledRow(
            label = stringResource(R.string.stat_worked),
            value = TimeUtils.formatDuration(state.todayWorkedMinutes),
            valueColor = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.clockedIn) {
                Button(
                    onClick = viewModel::clockOut,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_clock_out))
                }
                OutlinedButton(onClick = viewModel::toggleBreak) {
                    Icon(Icons.Filled.FreeBreakfast, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (state.onBreak) R.string.action_break_end else R.string.action_break_start
                        )
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.clockIn(WorkPlace.OFFICE) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_clock_in))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkPlace.entries.forEach { place ->
                FilterChip(
                    selected = state.today?.workPlace == place.name,
                    onClick = { viewModel.setWorkPlace(TimeUtils.today(), place) },
                    enabled = state.today != null,
                    label = { Text(workPlaceLabel(place)) },
                )
            }
        }
    }
}

@Composable
private fun DayRow(row: TimeCardDayRow, use24h: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isToday) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(76.dp)) {
                Text(
                    text = TimeUtils.formatDateShort(row.date),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (row.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (row.isWorkDay) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (row.card?.clockInAt != null) {
                        TimeUtils.formatTime(row.card.clockInAt, use24h) + " – " +
                            (row.card.clockOutAt?.let { TimeUtils.formatTime(it, use24h) }
                                ?: stringResource(R.string.timecard_open))
                    } else {
                        stringResource(R.string.timecard_no_record)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.card?.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                    Text(
                        text = memo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = TimeUtils.formatDuration(row.workedMinutes),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (row.overtimeMinutes > 0) {
                    Text(
                        text = "+" + TimeUtils.formatDuration(row.overtimeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayEditorSheet(
    row: TimeCardDayRow,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onSetWorkPlace: (WorkPlace) -> Unit,
    onSetBreakMinutes: (Int) -> Unit,
    onSetMemo: (String) -> Unit,
    onSetTimes: (Long?, Long?) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var memo by remember { mutableStateOf(row.card?.memo.orEmpty()) }
    var breakMinutes by remember {
        mutableStateOf((row.card?.breakMinutes ?: 0).toString())
    }

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
            Text(
                text = TimeUtils.formatDate(row.date),
                style = MaterialTheme.typography.titleMedium,
            )

            TimeAdjustRow(
                label = stringResource(R.string.stat_clock_in),
                millis = row.card?.clockInAt,
                date = row.date,
                use24h = use24h,
                onChange = { onSetTimes(it, row.card?.clockOutAt) },
            )
            TimeAdjustRow(
                label = stringResource(R.string.stat_clock_out),
                millis = row.card?.clockOutAt,
                date = row.date,
                use24h = use24h,
                onChange = { onSetTimes(row.card?.clockInAt, it) },
            )

            OutlinedTextField(
                value = breakMinutes,
                onValueChange = { value ->
                    breakMinutes = value.filter { it.isDigit() }.take(3)
                    breakMinutes.toIntOrNull()?.let(onSetBreakMinutes)
                },
                label = { Text(stringResource(R.string.timecard_break_minutes)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkPlace.entries.forEach { place ->
                    FilterChip(
                        selected = row.card?.workPlace == place.name,
                        onClick = { onSetWorkPlace(place) },
                        label = { Text(workPlaceLabel(place)) },
                    )
                }
            }

            OutlinedTextField(
                value = memo,
                onValueChange = {
                    memo = it
                    onSetMemo(it)
                },
                label = { Text(stringResource(R.string.timecard_memo)) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (row.card != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.timecard_delete_day),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeAdjustRow(
    label: String,
    millis: Long?,
    date: LocalDate,
    use24h: Boolean,
    onChange: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(shift(millis, date, -15)) }) { Text("-15m") }
        Text(
            text = millis?.let { TimeUtils.formatTime(it, use24h) } ?: "--:--",
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { onChange(shift(millis, date, 15)) }) { Text("+15m") }
    }
}

/** Nudges a stamp by [deltaMinutes], seeding from 9:00 when the day is empty. */
private fun shift(millis: Long?, date: LocalDate, deltaMinutes: Int): Long {
    val base = millis ?: date.atTime(9, 0).atZone(TimeUtils.zone).toInstant().toEpochMilli()
    return base + deltaMinutes * 60_000L
}

@Composable
fun workPlaceLabel(place: WorkPlace): String = stringResource(
    when (place) {
        WorkPlace.OFFICE -> R.string.workplace_office
        WorkPlace.REMOTE -> R.string.workplace_remote
        WorkPlace.CLIENT -> R.string.workplace_client
        WorkPlace.TRIP -> R.string.workplace_trip
    }
)

@Composable
private fun rangeLabel(range: TimeCardRange): String = stringResource(
    when (range) {
        TimeCardRange.WEEK -> R.string.timecard_range_week
        TimeCardRange.MONTH -> R.string.timecard_range_month
    }
)
