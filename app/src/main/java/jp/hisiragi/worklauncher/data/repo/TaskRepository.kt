package jp.hisiragi.worklauncher.data.repo

import jp.hisiragi.worklauncher.data.db.TaskDao
import jp.hisiragi.worklauncher.data.db.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {

    val tasks: Flow<List<TaskEntity>> = dao.observeAll()
    val openTasks: Flow<List<TaskEntity>> = dao.observeOpen()
    val projects: Flow<List<String>> = dao.observeProjects()

    suspend fun find(id: Long): TaskEntity? = dao.findById(id)

    suspend fun add(task: TaskEntity): Long = dao.insert(task)

    suspend fun update(task: TaskEntity) = dao.update(task)

    suspend fun delete(task: TaskEntity) = dao.delete(task)

    suspend fun setDone(id: Long, done: Boolean) =
        dao.setDone(id, done, if (done) System.currentTimeMillis() else null)

    suspend fun addFocusedMinutes(taskId: Long, minutes: Int) {
        if (minutes > 0) dao.addFocusedMinutes(taskId, minutes)
    }

    suspend fun clearCompleted() = dao.clearCompleted()
}
