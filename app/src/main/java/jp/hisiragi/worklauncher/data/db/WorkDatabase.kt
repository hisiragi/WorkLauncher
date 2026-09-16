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
        HomeWidgetEntity::class,
    ],
    version = 3,
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
    abstract fun homeWidgetDao(): HomeWidgetDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptFile TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS home_widgets (
                        appWidgetId INTEGER NOT NULL PRIMARY KEY,
                        stackId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        stackOrder INTEGER NOT NULL,
                        heightDp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_home_widgets_stackId ON home_widgets (stackId)"
                )
            }
        }

        fun build(context: Context): WorkDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WorkDatabase::class.java,
                "worklauncher.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
