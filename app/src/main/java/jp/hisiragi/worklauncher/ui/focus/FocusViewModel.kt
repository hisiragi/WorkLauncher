package jp.hisiragi.worklauncher.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.db.FocusSessionEntity
import jp.hisiragi.worklauncher.data.db.TaskEntity
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.domain.FocusKind
import jp.hisiragi.worklauncher.service.FocusState
import jp.hisiragi.worklauncher.util.TimeUtils
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val timer: FocusState = FocusState(),
    val openTasks: List<TaskEntity> = emptyList(),
    val todaySessions: List<FocusSessionEntity> = emptyList(),
    val completedToday: Int = 0,
    val focusMinutesToday: Int = 0,
)

class FocusViewModel(private val container: AppContainer) : ViewModel() {

    /** Emits whenever the calendar date changes, so "today" never goes stale. */
    private val currentDay: Flow<LocalDate> = flow {
        while (true) {
            emit(TimeUtils.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todaySessions = currentDay.flatMapLatest { day ->
        container.focusSessionDao.observeSince(TimeUtils.startOfDayMillis(day))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayFocusSeconds = currentDay.flatMapLatest { day ->
        container.focusSessionDao.observeFocusSeconds(
            TimeUtils.startOfDayMillis(day),
            TimeUtils.endOfDayMillis(day),
        )
    }

    val uiState: StateFlow<FocusUiState> = combine(
        container.settingsRepository.settings,
        container.focusController.state,
        container.taskRepository.openTasks,
        todaySessions,
        todayFocusSeconds,
    ) { settings, timer, tasks, sessions, focusSeconds ->
        FocusUiState(
            settings = settings,
            timer = timer,
            openTasks = tasks.take(20),
            todaySessions = sessions.filter { it.kind == FocusKind.FOCUS.name },
            completedToday = sessions.count { it.kind == FocusKind.FOCUS.name && it.completed },
            focusMinutesToday = focusSeconds / 60,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun start(kind: FocusKind = uiState.value.timer.kind, task: TaskEntity? = null) {
        container.focusController.start(
            kind = kind,
            taskId = task?.id,
            taskTitle = task?.title.orEmpty(),
        )
    }

    fun pause() = container.focusController.pause()

    fun resume() = container.focusController.resume()

    fun stop() = container.focusController.stop()

    fun skip() = container.focusController.skip()

    fun addMinutes(minutes: Int) = container.focusController.addMinutes(minutes)

    fun setFocusMinutes(minutes: Int) {
        viewModelScope.launch { container.settingsRepository.setPomodoroFocusMinutes(minutes) }
    }
}
