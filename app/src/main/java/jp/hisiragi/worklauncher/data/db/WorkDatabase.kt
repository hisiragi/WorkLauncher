package jp.hisiragi.worklauncher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptFile TEXT")
            }
        }

        fun build(context: Context): WorkDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WorkDatabase::class.java,
                "worklauncher.db",
            ).addMigrations(MIGRATION_1_2).build()
    }
}
