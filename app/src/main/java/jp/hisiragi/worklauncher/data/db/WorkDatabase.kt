package jp.hisiragi.worklauncher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class,
        NoteEntity::class,
        TimeCardEntity::class,
        FocusSessionEntity::class,
        QuickContactEntity::class,
        AppMetaEntity::class,
        ExpenseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WorkDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun timeCardDao(): TimeCardDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun quickContactDao(): QuickContactDao
    abstract fun appMetaDao(): AppMetaDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        fun build(context: Context): WorkDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WorkDatabase::class.java,
                "worklauncher.db",
            ).build()
    }
}
