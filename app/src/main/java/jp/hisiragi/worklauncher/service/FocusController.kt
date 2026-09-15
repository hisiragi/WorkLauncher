package jp.hisiragi.worklauncher.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import jp.hisiragi.worklauncher.data.db.FocusSessionEntity
import jp.hisiragi.worklauncher.data.db.FocusSessionDao
import jp.hisiragi.worklauncher.data.repo.TaskRepository
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.data.settings.SettingsRepository
import jp.hisiragi.worklauncher.domain.FocusKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** Snapshot of the Pomodoro timer, shared by the UI and the foreground service. */
data class FocusState(
    val kind: FocusKind = FocusKind.FOCUS,
    val running: Boolean = false,
    val paused: Boolean = false,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    /** Completed focus intervals since the last long break. */
    val cycle: Int = 0,
    val taskId: Long? = null,
    val taskTitle: String = "",
) {
    val idle: Boolean get() = !running && !paused
    val active: Boolean get() = running || paused
    val progress: Float
        get() = if (totalSeconds <= 0) 0f else 1f - (remainingSeconds.toFloat() / totalSeconds)
}

/**
 * Owns the Pomodoro state machine. A single instance lives in the app container
 * so the timer survives navigation and keeps running behind other apps via
 * [FocusTimerService].
 */
class FocusController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val focusSessionDao: FocusSessionDao,
    private val taskRepository: TaskRepository,
    settingsRepository: SettingsRepository,
) {
    private val settingsFlow: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(scope, SharingStarted.Eagerly, LauncherSettings())

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var endAtMillis: Long = 0
    private var currentSessionId: Long? = null
    private var sessionStartedAt: Long = 0

    /** Starts a new interval, replacing whatever was running. */
    fun start(
        kind: FocusKind = FocusKind.FOCUS,
        minutes: Int? = null,
        taskId: Long? = null,
        taskTitle: String = "",
    ) {
        scope.launch {
            val settings = settingsFlow.value
            val duration = minutes ?: defaultMinutes(kind, settings)
            tickJob?.cancel()
            finishOpenSession(completed = false)

            val totalSeconds = duration * 60
            sessionStartedAt = System.currentTimeMillis()
            endAtMillis = sessionStartedAt + totalSeconds * 1000L
            _state.value = _state.value.copy(
                kind = kind,
                running = true,
                paused = false,
                totalSeconds = totalSeconds,
                remainingSeconds = totalSeconds,
                taskId = taskId,
                taskTitle = taskTitle,
            )
            currentSessionId = focusSessionDao.insert(
                FocusSessionEntity(
                    startedAt = sessionStartedAt,
                    plannedMinutes = duration,
                    kind = kind.name,
                    taskId = taskId,
                    label = taskTitle,
                )
            )
            FocusTimerService.start(context)
            launchTicker()
        }
    }

    fun pause() {
        if (!_state.value.running) return
        tickJob?.cancel()
        tickJob = null
        _state.value = _state.value.copy(running = false, paused = true)
    }

    fun resume() {
        val current = _state.value
        if (!current.paused) return
        endAtMillis = System.currentTimeMillis() + current.remainingSeconds * 1000L
        _state.value = current.copy(running = true, paused = false)
        FocusTimerService.start(context)
        launchTicker()
    }

    /** Stops early. The elapsed time is still credited to the linked task. */
    fun stop() {
        tickJob?.cancel()
        tickJob = null
        scope.launch {
            finishOpenSession(completed = false)
            _state.value = FocusState(
                totalSeconds = settingsFlow.value.pomodoroFocusMinutes * 60,
                remainingSeconds = settingsFlow.value.pomodoroFocusMinutes * 60,
            )
            FocusTimerService.stop(context)
        }
    }

    /** Ends the current interval immediately and moves to the next one. */
    fun skip() {
        tickJob?.cancel()
        tickJob = null
        scope.launch { complete() }
    }

    fun addMinutes(minutes: Int) {
        val current = _state.value
        if (!current.active) return
        val added = minutes * 60
        endAtMillis += added * 1000L
        _state.value = current.copy(
            totalSeconds = current.totalSeconds + added,
            remainingSeconds = current.remainingSeconds + added,
        )
    }

    private fun launchTicker() {
        tickJob = scope.launch {
            while (_state.value.running) {
                val remaining = ceil((endAtMillis - System.currentTimeMillis()) / 1000.0).toInt()
                if (remaining <= 0) {
                    _state.value = _state.value.copy(remainingSeconds = 0)
                    complete()
                    return@launch
                }
                _state.value = _state.value.copy(remainingSeconds = remaining)
                delay(250)
            }
        }
    }

    private suspend fun complete() {
        val settings = settingsFlow.value
        val finished = _state.value
        finishOpenSession(completed = true)

        if (settings.focusVibrates) vibrate()

        val nextKind: FocusKind
        val nextCycle: Int
        if (finished.kind == FocusKind.FOCUS) {
            nextCycle = finished.cycle + 1
            nextKind = if (nextCycle % settings.pomodoroCyclesBeforeLongBreak == 0) {
                FocusKind.LONG_BREAK
            } else {
                FocusKind.SHORT_BREAK
            }
        } else {
            nextCycle = finished.cycle
            nextKind = FocusKind.FOCUS
        }

        val nextMinutes = defaultMinutes(nextKind, settings)
        // The next interval is queued but not auto-started: the user decides
        // when the break actually begins.
        _state.value = FocusState(
            kind = nextKind,
            running = false,
            paused = false,
            totalSeconds = nextMinutes * 60,
            remainingSeconds = nextMinutes * 60,
            cycle = nextCycle,
            taskId = finished.taskId,
            taskTitle = finished.taskTitle,
        )
        FocusNotifications.notifyIntervalFinished(context, finished.kind, nextKind, nextMinutes)
        FocusTimerService.stop(context)
    }

    /** Writes the elapsed time of the in-flight session, if there is one. */
    private suspend fun finishOpenSession(completed: Boolean) {
        val sessionId = currentSessionId ?: return
        currentSessionId = null
        val state = _state.value
        val elapsedSeconds = (state.totalSeconds - state.remainingSeconds).coerceAtLeast(0)
        val stored = FocusSessionEntity(
            id = sessionId,
            startedAt = sessionStartedAt,
            endedAt = System.currentTimeMillis(),
            plannedMinutes = state.totalSeconds / 60,
            actualSeconds = elapsedSeconds,
            kind = state.kind.name,
            taskId = state.taskId,
            label = state.taskTitle,
            completed = completed,
        )
        focusSessionDao.update(stored)
        if (state.kind == FocusKind.FOCUS) {
            state.taskId?.let { taskRepository.addFocusedMinutes(it, elapsedSeconds / 60) }
        }
    }

    private fun defaultMinutes(kind: FocusKind, settings: LauncherSettings): Int = when (kind) {
        FocusKind.FOCUS -> settings.pomodoroFocusMinutes
        FocusKind.SHORT_BREAK -> settings.pomodoroShortBreakMinutes
        FocusKind.LONG_BREAK -> settings.pomodoroLongBreakMinutes
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 220, 140, 220), -1)
            )
        }
    }

    /** True while focus mode should gate the apps the user flagged as distracting. */
    suspend fun shouldGateDistractions(): Boolean =
        _state.value.run { running && kind == FocusKind.FOCUS } &&
            settingsFlow.first().focusGatesDistractions

    internal fun handleAction(action: String) {
        when (action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
            ACTION_SKIP -> skip()
        }
    }

    companion object {
        const val ACTION_PAUSE = "jp.hisiragi.worklauncher.action.FOCUS_PAUSE"
        const val ACTION_RESUME = "jp.hisiragi.worklauncher.action.FOCUS_RESUME"
        const val ACTION_STOP = "jp.hisiragi.worklauncher.action.FOCUS_STOP"
        const val ACTION_SKIP = "jp.hisiragi.worklauncher.action.FOCUS_SKIP"

        fun intent(context: Context, action: String): Intent =
            Intent(context, FocusActionReceiver::class.java).setAction(action)
    }
}
