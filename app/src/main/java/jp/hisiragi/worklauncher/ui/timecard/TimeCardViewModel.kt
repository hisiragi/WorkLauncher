package jp.hisiragi.worklauncher.ui.timecard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.db.TimeCardEntity
import jp.hisiragi.worklauncher.data.repo.TimeCardRepository
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.domain.WorkPlace
import jp.hisiragi.worklauncher.util.CsvExporter
import jp.hisiragi.worklauncher.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class TimeCardRange { WEEK, MONTH }

data class TimeCardDayRow(
    val date: LocalDate,
    val card: TimeCardEntity?,
    val workedMinutes: Int,
    val overtimeMinutes: Int,
    val isToday: Boolean,
    val isWorkDay: Boolean,
)

data class TimeCardUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val range: TimeCardRange = TimeCardRange.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    val rows: List<TimeCardDayRow> = emptyList(),
    val today: TimeCardEntity? = null,
    val todayWorkedMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val daysWorked: Int = 0,
) {
    val clockedIn: Boolean get() = today?.clockInAt != null && today.clockOutAt == null
    val onBreak: Boolean get() = today?.breakStartedAt != null
}

class TimeCardViewModel(private val container: AppContainer) : ViewModel() {

    private val range = MutableStateFlow(TimeCardRange.WEEK)
    private val anchor = MutableStateFlow(TimeUtils.today())

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val cards = combine(range, anchor) { r, a -> bounds(r, a) }
        .flatMapLatest { (from, to) -> container.timeCardRepository.observeRange(from, to) }

    val uiState: StateFlow<TimeCardUiState> = combine(
        container.settingsRepository.settings,
        cards,
        range,
        anchor,
        ticker,
    ) { settings, cardList, currentRange, currentAnchor, now ->
        val (from, to) = bounds(currentRange, currentAnchor)
        val byDay = cardList.associateBy { it.epochDay }
        val today = TimeUtils.today()

        val rows = generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .map { date ->
                val card = byDay[date.toEpochDay()]
                val worked = card?.let { TimeCardRepository.workedMinutes(it, now) } ?: 0
                TimeCardDayRow(
                    date = date,
                    card = card,
                    workedMinutes = worked,
                    overtimeMinutes = (worked - settings.standardWorkMinutes).coerceAtLeast(0),
                    isToday = date == today,
                    isWorkDay = TimeUtils.isWorkDay(date, settings.workDayMask),
                )
            }
            .toList()

        val todayCard = byDay[today.toEpochDay()]
        TimeCardUiState(
            settings = settings,
            range = currentRange,
            anchor = currentAnchor,
            rows = rows.reversed(),
            today = todayCard,
            todayWorkedMinutes = todayCard?.let { TimeCardRepository.workedMinutes(it, now) } ?: 0,
            totalMinutes = rows.sumOf { it.workedMinutes },
            overtimeMinutes = rows.sumOf { it.overtimeMinutes },
            daysWorked = rows.count { it.workedMinutes > 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeCardUiState())

    private fun bounds(range: TimeCardRange, anchor: LocalDate): Pair<LocalDate, LocalDate> =
        when (range) {
            TimeCardRange.WEEK -> TimeUtils.startOfWeek(anchor) to TimeUtils.endOfWeek(anchor)
            TimeCardRange.MONTH -> TimeUtils.startOfMonth(anchor) to TimeUtils.endOfMonth(anchor)
        }

    fun setRange(value: TimeCardRange) {
        range.value = value
    }

    fun shiftPeriod(delta: Long) {
        anchor.value = when (range.value) {
            TimeCardRange.WEEK -> anchor.value.plusWeeks(delta)
            TimeCardRange.MONTH -> anchor.value.plusMonths(delta)
        }
    }

    fun resetPeriod() {
        anchor.value = TimeUtils.today()
    }

    fun clockIn(workPlace: WorkPlace) {
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

    fun setWorkPlace(date: LocalDate, workPlace: WorkPlace) {
        viewModelScope.launch { container.timeCardRepository.setWorkPlace(date, workPlace) }
    }

    fun setBreakMinutes(date: LocalDate, minutes: Int) {
        viewModelScope.launch { container.timeCardRepository.setBreakMinutes(date, minutes) }
    }

    fun setMemo(date: LocalDate, memo: String) {
        viewModelScope.launch { container.timeCardRepository.setMemo(date, memo) }
    }

    fun setTimes(date: LocalDate, clockInAt: Long?, clockOutAt: Long?) {
        viewModelScope.launch { container.timeCardRepository.setTimes(date, clockInAt, clockOutAt) }
    }

    fun deleteDay(date: LocalDate) {
        viewModelScope.launch { container.timeCardRepository.deleteDay(date) }
    }

    /** Exports the visible period as a CSV through the system share sheet. */
    fun exportCsv(context: Context, header: List<String>, subject: String) {
        viewModelScope.launch {
            val state = uiState.value
            val (from, to) = bounds(state.range, state.anchor)
            val cards = container.timeCardRepository.listRange(from, to).associateBy { it.epochDay }
            val rows = generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .map { date ->
                    val card = cards[date.toEpochDay()]
                    val worked = card?.let { TimeCardRepository.workedMinutes(it) } ?: 0
                    listOf(
                        TimeUtils.formatIsoDate(date),
                        card?.clockInAt?.let { TimeUtils.formatTime(it, true) }.orEmpty(),
                        card?.clockOutAt?.let { TimeUtils.formatTime(it, true) }.orEmpty(),
                        (card?.breakMinutes ?: 0).toString(),
                        String.format(java.util.Locale.US, "%.2f", worked / 60.0),
                        (worked - state.settings.standardWorkMinutes)
                            .coerceAtLeast(0)
                            .let { String.format(java.util.Locale.US, "%.2f", it / 60.0) },
                        card?.workPlace.orEmpty(),
                        card?.memo.orEmpty(),
                    )
                }
                .toList()
            val label = when (state.range) {
                TimeCardRange.WEEK -> "week-${TimeUtils.formatIsoDate(from)}"
                TimeCardRange.MONTH -> YearMonth.from(from).toString()
            }
            CsvExporter.share(
                context = context,
                fileName = "worklauncher-timecard-$label.csv",
                header = header,
                rows = rows,
                subject = subject,
            )
        }
    }
}
