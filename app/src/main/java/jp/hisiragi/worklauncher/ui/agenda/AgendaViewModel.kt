package jp.hisiragi.worklauncher.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.domain.AgendaEvent
import jp.hisiragi.worklauncher.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AgendaUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val permissionGranted: Boolean = false,
    val loading: Boolean = false,
    val daysAhead: Int = 7,
    val eventsByDay: List<Pair<LocalDate, List<AgendaEvent>>> = emptyList(),
    val meetingMinutesToday: Int = 0,
)

class AgendaViewModel(private val container: AppContainer) : ViewModel() {

    private val events = MutableStateFlow<List<AgendaEvent>>(emptyList())
    private val permission = MutableStateFlow(false)
    private val loading = MutableStateFlow(false)
    private val daysAhead = MutableStateFlow(7)

    val uiState: StateFlow<AgendaUiState> = combine(
        container.settingsRepository.settings,
        events,
        permission,
        loading,
        daysAhead,
    ) { settings, eventList, hasPermission, isLoading, days ->
        val today = TimeUtils.today()
        AgendaUiState(
            settings = settings,
            permissionGranted = hasPermission,
            loading = isLoading,
            daysAhead = days,
            eventsByDay = eventList
                .groupBy { TimeUtils.toLocalDateTime(it.startAt).toLocalDate() }
                .toSortedMap()
                .map { (date, dayEvents) -> date to dayEvents.sortedBy { it.startAt } },
            meetingMinutesToday = eventList
                .filter {
                    !it.allDay && TimeUtils.toLocalDateTime(it.startAt).toLocalDate() == today
                }
                .sumOf { ((it.endAt - it.startAt) / 60_000L).toInt() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

    init {
        refresh()
    }

    fun setDaysAhead(days: Int) {
        daysAhead.value = days
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            permission.value = container.calendarRepository.hasPermission()
            if (!permission.value) {
                events.value = emptyList()
                return@launch
            }
            loading.value = true
            val today = TimeUtils.today()
            events.value = container.calendarRepository.eventsBetween(
                today,
                today.plusDays(daysAhead.value.toLong()),
            )
            loading.value = false
        }
    }
}
