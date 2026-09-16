package jp.hisiragi.worklauncher.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.core.WidgetHostController
import jp.hisiragi.worklauncher.data.db.HomeWidgetEntity
import jp.hisiragi.worklauncher.data.db.TaskEntity
import jp.hisiragi.worklauncher.data.db.TimeCardEntity
import jp.hisiragi.worklauncher.data.repo.TimeCardRepository
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.domain.AgendaEvent
import jp.hisiragi.worklauncher.domain.LauncherApp
import jp.hisiragi.worklauncher.domain.LlmAvailability
import jp.hisiragi.worklauncher.domain.WorkPlace
import jp.hisiragi.worklauncher.service.FocusState
import jp.hisiragi.worklauncher.util.TimeUtils
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val nowMillis: Long = System.currentTimeMillis(),
    val timeCard: TimeCardEntity? = null,
    val workedMinutes: Int = 0,
    val todayTasks: List<TaskEntity> = emptyList(),
    val overdueCount: Int = 0,
    val openTaskCount: Int = 0,
    val todayEvents: List<AgendaEvent> = emptyList(),
    val calendarPermissionGranted: Boolean = false,
    val focus: FocusState = FocusState(),
    val focusMinutesToday: Int = 0,
    val dockApps: List<LauncherApp> = emptyList(),
    val suggestedApps: List<LauncherApp> = emptyList(),
) {
    val clockedIn: Boolean get() = timeCard?.clockInAt != null && timeCard.clockOutAt == null
    val onBreak: Boolean get() = timeCard?.breakStartedAt != null

    /** Minutes elapsed in the break currently in progress. */
    val currentBreakMinutes: Int
        get() = timeCard?.breakStartedAt
            ?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0L).toInt() }
            ?: 0

    /** The meeting the user should be looking at right now, or the next one. */
    val currentOrNextEvent: AgendaEvent?
        get() = todayEvents.firstOrNull { it.endAt >= nowMillis }
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val agenda = MutableStateFlow<List<AgendaEvent>>(emptyList())
    private val calendarPermission = MutableStateFlow(false)

    /** Drives the clock and the "worked so far" counter. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    /**
     * The launcher process lives for days, so every "today" query has to be
     * re-issued when the date rolls over rather than pinned at construction.
     * This polls once a minute — the clock [ticker] runs at 1 Hz and is far too
     * eager for something that changes at most once a day.
     */
    private val currentDay: Flow<LocalDate> = flow {
        while (true) {
            emit(TimeUtils.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayCard = currentDay.flatMapLatest { day ->
        container.timeCardRepository.observeRange(day, day)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val focusSecondsToday = currentDay.flatMapLatest { day ->
        container.focusSessionDao.observeFocusSeconds(
            TimeUtils.startOfDayMillis(day),
            TimeUtils.endOfDayMillis(day),
        )
    }

    val gatedApp: StateFlow<LauncherApp?> = container.appLauncher.gatedApp

    private val workSlice = combine(
        container.settingsRepository.settings,
        todayCard,
        container.taskRepository.openTasks,
        agenda,
        ticker,
    ) { settings, cards, tasks, events, now ->
        WorkSlice(settings, cards.firstOrNull(), tasks, events, now)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        workSlice,
        container.focusController.state,
        container.appRepository.apps,
        focusSecondsToday,
        calendarPermission,
    ) { slice, focus, apps, focusSeconds, hasCalendar ->
        val todayEnd = TimeUtils.endOfDayMillis(
            TimeUtils.toLocalDateTime(slice.now).toLocalDate()
        )
        val visible = apps.filterNot { it.hidden }
        HomeUiState(
            settings = slice.settings,
            nowMillis = slice.now,
            timeCard = slice.card,
            workedMinutes = slice.card?.let { TimeCardRepository.workedMinutes(it, slice.now) } ?: 0,
            todayTasks = slice.tasks
                .filter { it.dueAt == null || it.dueAt <= todayEnd }
                .take(4),
            overdueCount = slice.tasks.count { it.dueAt != null && it.dueAt < slice.now },
            openTaskCount = slice.tasks.size,
            todayEvents = slice.events,
            calendarPermissionGranted = hasCalendar,
            focus = focus,
            focusMinutesToday = focusSeconds / 60,
            dockApps = visible.filter { it.favorite }.sortedBy { it.dockOrder }.take(8),
            suggestedApps = visible
                .filter { it.launchCount > 0 }
                .sortedByDescending { it.launchCount }
                .take(8),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Reload the agenda on the first frame and again whenever the date changes.
        viewModelScope.launch { currentDay.collect { refreshAgenda() } }
    }

    fun refreshAgenda() {
        viewModelScope.launch {
            calendarPermission.value = container.calendarRepository.hasPermission()
            agenda.value = container.calendarRepository.eventsToday()
        }
    }

    fun clockIn(workPlace: WorkPlace = WorkPlace.OFFICE) {
        viewModelScope.launch { container.timeCardRepository.clockIn(workPlace = workPlace) }
    }

    fun clockOut() {
        viewModelScope.launch { container.timeCardRepository.clockOut() }
    }

    fun toggleBreak() {
        viewModelScope.launch {
            if (uiState.value.onBreak) {
                container.timeCardRepository.endBreak()
            } else {
                container.timeCardRepository.startBreak()
            }
        }
    }

    fun toggleTaskDone(task: TaskEntity) {
        viewModelScope.launch { container.taskRepository.setDone(task.id, !task.isDone) }
    }

    fun quickAddTask(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { container.taskRepository.add(TaskEntity(title = trimmed)) }
    }

    fun startFocus() {
        val state = uiState.value
        val task = state.todayTasks.firstOrNull { !it.isDone }
        container.focusController.start(
            kind = state.focus.kind,
            taskId = task?.id,
            taskTitle = task?.title.orEmpty(),
        )
    }

    val llmAvailability: StateFlow<LlmAvailability> = container.llmManager.availability

    val widgetHost: WidgetHostController get() = container.widgetHostController

    /** Widgets grouped into their stacks, in the order the stacks are shown. */
    val widgetStacks: StateFlow<List<List<HomeWidgetEntity>>> =
        container.widgetHostController.widgets
            .map { widgets ->
                widgets.groupBy { it.stackId }
                    .values
                    .sortedBy { stack -> stack.minOf { it.stackOrder } }
                    .map { stack -> stack.sortedBy { it.position } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addWidget(appWidgetId: Int, heightDp: Int, stackId: String?) {
        viewModelScope.launch {
            container.widgetHostController.add(appWidgetId, stackId, heightDp)
        }
    }

    fun removeWidget(widget: HomeWidgetEntity) {
        viewModelScope.launch { container.widgetHostController.remove(widget.appWidgetId) }
    }

    fun launchApp(context: Context, app: LauncherApp) = container.appLauncher.launch(context, app)

    fun launchGatedApp(context: Context) {
        gatedApp.value?.let { container.appLauncher.launch(context, it, ignoreFocusGate = true) }
    }

    fun dismissGate() = container.appLauncher.dismissGate()

    private data class WorkSlice(
        val settings: LauncherSettings,
        val card: TimeCardEntity?,
        val tasks: List<TaskEntity>,
        val events: List<AgendaEvent>,
        val now: Long,
    )
}
