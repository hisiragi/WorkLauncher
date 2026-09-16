package jp.hisiragi.worklauncher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.domain.DrawerSort
import jp.hisiragi.worklauncher.domain.LlmAvailability
import jp.hisiragi.worklauncher.domain.LlmBackend
import jp.hisiragi.worklauncher.domain.LauncherApp
import jp.hisiragi.worklauncher.domain.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val hiddenApps: List<LauncherApp> = emptyList(),
    val distractionApps: List<LauncherApp> = emptyList(),
    val dockApps: List<LauncherApp> = emptyList(),
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.settingsRepository

    val uiState: StateFlow<SettingsUiState> = combine(
        repo.settings,
        container.appRepository.apps,
    ) { settings, apps ->
        SettingsUiState(
            settings = settings,
            hiddenApps = apps.filter { it.hidden },
            distractionApps = apps.filter { it.distraction },
            dockApps = apps.filter { it.favorite }.sortedBy { it.dockOrder },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) = launch { repo.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { repo.setDynamicColor(enabled) }
    fun setGridColumns(columns: Int) = launch { repo.setGridColumns(columns) }
    fun setShowAppLabels(show: Boolean) = launch { repo.setShowAppLabels(show) }
    fun setDrawerSort(sort: DrawerSort) = launch { repo.setDrawerSort(sort) }
    fun setUse24HourClock(use: Boolean) = launch { repo.setUse24HourClock(use) }
    fun setWorkStartMinute(minute: Int) = launch { repo.setWorkStartMinute(minute) }
    fun setWorkEndMinute(minute: Int) = launch { repo.setWorkEndMinute(minute) }
    fun setStandardWorkMinutes(minutes: Int) = launch { repo.setStandardWorkMinutes(minutes) }
    fun setDefaultBreakMinutes(minutes: Int) = launch { repo.setDefaultBreakMinutes(minutes) }
    fun setPomodoroFocusMinutes(minutes: Int) = launch { repo.setPomodoroFocusMinutes(minutes) }
    fun setPomodoroShortBreak(minutes: Int) = launch { repo.setPomodoroShortBreakMinutes(minutes) }
    fun setPomodoroLongBreak(minutes: Int) = launch { repo.setPomodoroLongBreakMinutes(minutes) }
    fun setPomodoroCycles(cycles: Int) = launch { repo.setPomodoroCycles(cycles) }
    fun setFocusGates(enabled: Boolean) = launch { repo.setFocusGatesDistractions(enabled) }
    fun setFocusVibrates(enabled: Boolean) = launch { repo.setFocusVibrates(enabled) }
    fun setShowWorkSummaryCard(show: Boolean) = launch { repo.setShowWorkSummaryCard(show) }
    fun setShowAgendaCard(show: Boolean) = launch { repo.setShowAgendaCard(show) }
    fun setShowTasksCard(show: Boolean) = launch { repo.setShowTasksCard(show) }
    fun setSearchEngine(url: String) = launch { repo.setSearchEngineUrl(url) }
    fun setCurrencySymbol(symbol: String) = launch { repo.setCurrencySymbol(symbol) }
    fun setLlmBackend(backend: LlmBackend) = launch { repo.setLlmBackend(backend) }
    fun setLlmModelPath(path: String) = launch { repo.setLlmModelPath(path) }
    fun setLlmEndpoint(url: String) = launch { repo.setLlmEndpoint(url) }
    fun setLlmRemoteModel(model: String) = launch { repo.setLlmRemoteModel(model) }

    val llmAvailability: StateFlow<LlmAvailability> = container.llmManager.availability

    fun toggleWorkDay(dayIndex: Int) = launch {
        val current = uiState.value.settings.workDayMask
        repo.setWorkDayMask(current xor (1 shl dayIndex))
    }

    fun unhide(app: LauncherApp) = launch { container.appRepository.setHidden(app, false) }

    fun clearDistraction(app: LauncherApp) = launch {
        container.appRepository.setDistraction(app, false)
    }

    fun removeFromDock(app: LauncherApp) = launch {
        container.appRepository.setFavorite(app, favorite = false, dockOrder = 0)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
