package jp.hisiragi.worklauncher.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT * FROM tasks
        ORDER BY isDone ASC,
                 CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END ASC,
                 dueAt ASC,
                 priority DESC,
                 createdAt DESC
        """
    )
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY priority DESC, dueAt ASC")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT DISTINCT project FROM tasks WHERE project IS NOT NULL AND project != ''")
    fun observeProjects(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE tasks SET isDone = :done, completedAt = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?)

    @Query("UPDATE tasks SET focusedMinutes = focusedMinutes + :minutes WHERE id = :id")
    suspend fun addFocusedMinutes(id: Long, minutes: Int)

    @Query("DELETE FROM tasks WHERE isDone = 1")
    suspend fun clearCompleted()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface TimeCardDao {
    @Query("SELECT * FROM time_cards WHERE epochDay = :epochDay")
    suspend fun findByDay(epochDay: Long): TimeCardEntity?

    @Query("SELECT * FROM time_cards WHERE epochDay = :epochDay")
    fun observeByDay(epochDay: Long): Flow<TimeCardEntity?>

    @Query("SELECT * FROM time_cards WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TimeCardEntity>>

    @Query("SELECT * FROM time_cards WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun listRange(from: Long, to: Long): List<TimeCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: TimeCardEntity): Long

    @Update
    suspend fun update(card: TimeCardEntity)

    @Delete
    suspend fun delete(card: TimeCardEntity)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :from ORDER BY startedAt DESC")
    fun observeSince(from: Long): Flow<List<FocusSessionEntity>>

    @Query(
        "SELECT COUNT(*) FROM focus_sessions " +
            "WHERE kind = 'FOCUS' AND completed = 1 AND startedAt BETWEEN :from AND :to"
    )
    fun observeCompletedCount(from: Long, to: Long): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(actualSeconds), 0) FROM focus_sessions " +
            "WHERE kind = 'FOCUS' AND startedAt BETWEEN :from AND :to"
    )
    fun observeFocusSeconds(from: Long, to: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)
}

@Dao
interface QuickContactDao {
    @Query("SELECT * FROM quick_contacts ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<QuickContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: QuickContactEntity): Long

    @Update
    suspend fun update(contact: QuickContactEntity)

    @Delete
    suspend fun delete(contact: QuickContactEntity)
}

@Dao
interface AppMetaDao {
    @Query("SELECT * FROM app_meta")
    fun observeAll(): Flow<List<AppMetaEntity>>

    @Query("SELECT * FROM app_meta WHERE componentKey = :key")
    suspend fun find(key: String): AppMetaEntity?

    @Upsert
    suspend fun upsert(meta: AppMetaEntity)

    @Query("DELETE FROM app_meta WHERE componentKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun listRange(from: Long, to: Long): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)
}
