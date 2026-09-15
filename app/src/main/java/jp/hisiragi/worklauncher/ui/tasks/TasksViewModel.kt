package jp.hisiragi.worklauncher.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.db.TaskEntity
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter { TODAY, WEEK, ALL, DONE }

data class TasksUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val filter: TaskFilter = TaskFilter.TODAY,
    val projectFilter: String? = null,
    val tasks: List<TaskEntity> = emptyList(),
    val projects: List<String> = emptyList(),
    val openCount: Int = 0,
    val overdueCount: Int = 0,
    val doneTodayCount: Int = 0,
)

class TasksViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.TODAY)
    private val projectFilter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TasksUiState> = combine(
        container.taskRepository.tasks,
        container.taskRepository.projects,
        container.settingsRepository.settings,
        filter,
        projectFilter,
    ) { tasks, projects, settings, currentFilter, project ->
        val now = System.currentTimeMillis()
        val todayEnd = TimeUtils.endOfDayMillis(TimeUtils.today())
        val weekEnd = TimeUtils.endOfDayMillis(TimeUtils.endOfWeek())
        val todayStart = TimeUtils.startOfDayMillis(TimeUtils.today())

        val scoped = tasks.filter { project == null || it.project == project }
        val filtered = when (currentFilter) {
            TaskFilter.TODAY -> scoped.filter { !it.isDone && (it.dueAt == null || it.dueAt <= todayEnd) }
            TaskFilter.WEEK -> scoped.filter { !it.isDone && (it.dueAt == null || it.dueAt <= weekEnd) }
            TaskFilter.ALL -> scoped.filter { !it.isDone }
            TaskFilter.DONE -> scoped.filter { it.isDone }
        }

        TasksUiState(
            settings = settings,
            filter = currentFilter,
            projectFilter = project,
            tasks = filtered,
            projects = projects.sorted(),
            openCount = tasks.count { !it.isDone },
            overdueCount = tasks.count { !it.isDone && it.dueAt != null && it.dueAt < now },
            doneTodayCount = tasks.count { it.isDone && (it.completedAt ?: 0) >= todayStart },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    fun setProjectFilter(project: String?) {
        projectFilter.value = project
    }

    fun add(task: TaskEntity) {
        viewModelScope.launch { container.taskRepository.add(task) }
    }

    fun update(task: TaskEntity) {
        viewModelScope.launch { container.taskRepository.update(task) }
    }

    fun toggleDone(task: TaskEntity) {
        viewModelScope.launch { container.taskRepository.setDone(task.id, !task.isDone) }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch { container.taskRepository.delete(task) }
    }

    fun clearCompleted() {
        viewModelScope.launch { container.taskRepository.clearCompleted() }
    }

    /** Starts a focus interval bound to [task] so the time lands on the right row. */
    fun focusOn(task: TaskEntity) {
        container.focusController.start(taskId = task.id, taskTitle = task.title)
    }
}
