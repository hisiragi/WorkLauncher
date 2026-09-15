package jp.hisiragi.worklauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.domain.DrawerSort
import jp.hisiragi.worklauncher.domain.LlmAvailability
import jp.hisiragi.worklauncher.domain.LlmBackend
import jp.hisiragi.worklauncher.domain.LauncherApp
import jp.hisiragi.worklauncher.domain.ThemeMode
import jp.hisiragi.worklauncher.ui.components.LabeledRow
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.util.Launch
import jp.hisiragi.worklauncher.util.LauncherStatus
import jp.hisiragi.worklauncher.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = state.settings
    val llmAvailability by viewModel.llmAvailability.collectAsStateWithLifecycle()
    var editingWorkStart by remember { mutableStateOf(false) }
    var editingWorkEnd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                SectionCard(title = stringResource(R.string.settings_launcher)) {
                    val isDefault = LauncherStatus.isDefaultHome(context)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_default_home)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (isDefault) {
                                        R.string.settings_default_home_yes
                                    } else {
                                        R.string.settings_default_home_no
                                    }
                                )
                            )
                        },
                        modifier = Modifier.clickable { Launch.homeSettings(context) },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_usage_access)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (LauncherStatus.hasUsageAccess(context)) {
                                        R.string.settings_granted
                                    } else {
                                        R.string.settings_not_granted
                                    }
                                )
                            )
                        },
                        modifier = Modifier.clickable { Launch.usageAccessSettings(context) },
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_appearance)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(themeLabel(mode)) },
                            )
                        }
                    }
                    SwitchRow(
                        label = stringResource(R.string.settings_dynamic_color),
                        checked = settings.dynamicColor,
                        onChange = viewModel::setDynamicColor,
                    )
                    SwitchRow(
                        label = stringResource(R.string.settings_show_labels),
                        checked = settings.showAppLabels,
                        onChange = viewModel::setShowAppLabels,
                    )
                    SwitchRow(
                        label = stringResource(R.string.settings_24h_clock),
                        checked = settings.use24HourClock,
                        onChange = viewModel::setUse24HourClock,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_grid_columns),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (3..7).forEach { columns ->
                            FilterChip(
                                selected = settings.gridColumns == columns,
                                onClick = { viewModel.setGridColumns(columns) },
                                label = { Text(columns.toString()) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.drawer_sort),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DrawerSort.entries.forEach { sort ->
                            FilterChip(
                                selected = settings.drawerSort == sort,
                                onClick = { viewModel.setDrawerSort(sort) },
                                label = { Text(sortLabel(sort)) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_home_cards)) {
                    SwitchRow(
                        label = stringResource(R.string.home_today_summary),
                        checked = settings.showWorkSummaryCard,
                        onChange = viewModel::setShowWorkSummaryCard,
                    )
                    SwitchRow(
                        label = stringResource(R.string.home_agenda),
                        checked = settings.showAgendaCard,
                        onChange = viewModel::setShowAgendaCard,
                    )
                    SwitchRow(
                        label = stringResource(R.string.home_tasks),
                        checked = settings.showTasksCard,
                        onChange = viewModel::setShowTasksCard,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_work_hours)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_work_start)) },
                        trailingContent = {
                            Text(
                                TimeUtils.formatMinuteOfDay(
                                    settings.workStartMinute,
                                    settings.use24HourClock,
                                )
                            )
                        },
                        modifier = Modifier.clickable { editingWorkStart = true },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_work_end)) },
                        trailingContent = {
                            Text(
                                TimeUtils.formatMinuteOfDay(
                                    settings.workEndMinute,
                                    settings.use24HourClock,
                                )
                            )
                        },
                        modifier = Modifier.clickable { editingWorkEnd = true },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_work_days),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TimeUtils.dayOfWeekLabels().forEachIndexed { index, label ->
                            FilterChip(
                                selected = (settings.workDayMask shr index) and 1 == 1,
                                onClick = { viewModel.toggleWorkDay(index) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_standard_day),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(360, 420, 450, 480, 540).forEach { minutes ->
                            FilterChip(
                                selected = settings.standardWorkMinutes == minutes,
                                onClick = { viewModel.setStandardWorkMinutes(minutes) },
                                label = { Text(TimeUtils.formatDuration(minutes)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_default_break),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0, 30, 45, 60, 90).forEach { minutes ->
                            FilterChip(
                                selected = settings.defaultBreakMinutes == minutes,
                                onClick = { viewModel.setDefaultBreakMinutes(minutes) },
                                label = { Text(TimeUtils.formatDuration(minutes)) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_focus)) {
                    MinutePickerRow(
                        label = stringResource(R.string.settings_focus_length),
                        options = listOf(15, 25, 30, 45, 50, 60, 90),
                        selected = settings.pomodoroFocusMinutes,
                        onSelect = viewModel::setPomodoroFocusMinutes,
                    )
                    MinutePickerRow(
                        label = stringResource(R.string.settings_short_break),
                        options = listOf(3, 5, 10, 15),
                        selected = settings.pomodoroShortBreakMinutes,
                        onSelect = viewModel::setPomodoroShortBreak,
                    )
                    MinutePickerRow(
                        label = stringResource(R.string.settings_long_break),
                        options = listOf(10, 15, 20, 30),
                        selected = settings.pomodoroLongBreakMinutes,
                        onSelect = viewModel::setPomodoroLongBreak,
                    )
                    MinutePickerRow(
                        label = stringResource(R.string.settings_cycles),
                        options = listOf(2, 3, 4, 5, 6),
                        selected = settings.pomodoroCyclesBeforeLongBreak,
                        onSelect = viewModel::setPomodoroCycles,
                        suffixMinutes = false,
                    )
                    SwitchRow(
                        label = stringResource(R.string.settings_focus_gate),
                        checked = settings.focusGatesDistractions,
                        onChange = viewModel::setFocusGates,
                    )
                    SwitchRow(
                        label = stringResource(R.string.settings_focus_vibrate),
                        checked = settings.focusVibrates,
                        onChange = viewModel::setFocusVibrates,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_llm_section)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LlmBackend.entries.forEach { backend ->
                            FilterChip(
                                selected = settings.llmBackend == backend,
                                onClick = { viewModel.setLlmBackend(backend) },
                                label = { Text(llmBackendLabel(backend)) },
                            )
                        }
                    }

                    when (settings.llmBackend) {
                        LlmBackend.NONE -> Unit

                        LlmBackend.ON_DEVICE -> {
                            Spacer(Modifier.height(8.dp))
                            TextFieldRow(
                                label = stringResource(R.string.settings_llm_model_path),
                                placeholder = stringResource(R.string.settings_llm_model_path_hint),
                                value = settings.llmModelPath,
                                onValueChange = viewModel::setLlmModelPath,
                            )
                        }

                        LlmBackend.REMOTE -> {
                            Spacer(Modifier.height(8.dp))
                            TextFieldRow(
                                label = stringResource(R.string.settings_llm_endpoint),
                                placeholder = stringResource(R.string.settings_llm_endpoint_hint),
                                value = settings.llmEndpoint,
                                onValueChange = viewModel::setLlmEndpoint,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextFieldRow(
                                label = stringResource(R.string.settings_llm_remote_model),
                                placeholder = stringResource(R.string.settings_llm_remote_model_hint),
                                value = settings.llmRemoteModel,
                                onValueChange = viewModel::setLlmRemoteModel,
                            )
                        }
                    }

                    if (settings.llmBackend != LlmBackend.NONE) {
                        Spacer(Modifier.height(8.dp))
                        LabeledRow(
                            label = stringResource(R.string.settings_llm_status),
                            value = when (val current = llmAvailability) {
                                is LlmAvailability.Ready -> current.label
                                is LlmAvailability.Unavailable -> current.reason
                                else -> "—"
                            },
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_search_engine)) {
                    val engines = listOf(
                        stringResource(R.string.engine_google) to "https://www.google.com/search?q=",
                        stringResource(R.string.engine_bing) to "https://www.bing.com/search?q=",
                        stringResource(R.string.engine_duckduckgo) to "https://duckduckgo.com/?q=",
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        engines.forEach { (name, url) ->
                            FilterChip(
                                selected = settings.searchEngineUrl == url,
                                onClick = { viewModel.setSearchEngine(url) },
                                label = { Text(name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_currency),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("¥", "$", "€", "£").forEach { symbol ->
                            FilterChip(
                                selected = settings.currencySymbol == symbol,
                                onClick = { viewModel.setCurrencySymbol(symbol) },
                                label = { Text(symbol) },
                            )
                        }
                    }
                }
            }

            if (state.dockApps.isNotEmpty()) {
                item {
                    AppListCard(
                        title = stringResource(R.string.settings_dock_apps),
                        apps = state.dockApps,
                        actionDescription = stringResource(R.string.settings_remove_from_dock),
                        onRemove = viewModel::removeFromDock,
                    )
                }
            }

            if (state.hiddenApps.isNotEmpty()) {
                item {
                    AppListCard(
                        title = stringResource(R.string.settings_hidden_apps),
                        apps = state.hiddenApps,
                        actionDescription = stringResource(R.string.settings_unhide),
                        onRemove = viewModel::unhide,
                    )
                }
            }

            if (state.distractionApps.isNotEmpty()) {
                item {
                    AppListCard(
                        title = stringResource(R.string.settings_distraction_apps),
                        apps = state.distractionApps,
                        actionDescription = stringResource(R.string.settings_clear_distraction),
                        onRemove = viewModel::clearDistraction,
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.settings_about)) {
                    Text(
                        text = stringResource(R.string.settings_about_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (editingWorkStart) {
        TimeDialog(
            initialMinuteOfDay = settings.workStartMinute,
            use24h = settings.use24HourClock,
            onDismiss = { editingWorkStart = false },
            onConfirm = {
                viewModel.setWorkStartMinute(it)
                editingWorkStart = false
            },
        )
    }
    if (editingWorkEnd) {
        TimeDialog(
            initialMinuteOfDay = settings.workEndMinute,
            use24h = settings.use24HourClock,
            onDismiss = { editingWorkEnd = false },
            onConfirm = {
                viewModel.setWorkEndMinute(it)
                editingWorkEnd = false
            },
        )
    }
}

/**
 * Commits on every keystroke. These settings write to DataStore, which is cheap
 * and last-write-wins, so there is nothing to save explicitly.
 */
@Composable
private fun TextFieldRow(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun llmBackendLabel(backend: LlmBackend): String = stringResource(
    when (backend) {
        LlmBackend.NONE -> R.string.settings_llm_backend_none
        LlmBackend.ON_DEVICE -> R.string.settings_llm_backend_on_device
        LlmBackend.REMOTE -> R.string.settings_llm_backend_remote
    }
)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MinutePickerRow(
    label: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    suffixMinutes: Boolean = true,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = {
                        Text(
                            if (suffixMinutes) {
                                stringResource(R.string.focus_minutes, value)
                            } else {
                                value.toString()
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AppListCard(
    title: String,
    apps: List<LauncherApp>,
    actionDescription: String,
    onRemove: (LauncherApp) -> Unit,
) {
    SectionCard(title = title) {
        Column {
            apps.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(app) }) {
                        Icon(Icons.Filled.Close, contentDescription = actionDescription)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initialMinuteOfDay: Int,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = (initialMinuteOfDay / 60) % 24,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = use24h,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour * 60 + timeState.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = { TimePicker(state = timeState) },
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun sortLabel(sort: DrawerSort): String = stringResource(
    when (sort) {
        DrawerSort.ALPHABETICAL -> R.string.drawer_sort_alpha
        DrawerSort.MOST_USED -> R.string.drawer_sort_usage
        DrawerSort.RECENT -> R.string.drawer_sort_recent
    }
)
