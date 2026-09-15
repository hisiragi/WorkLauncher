package jp.hisiragi.worklauncher.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A unit of work the user tracks on the launcher. */
@Entity(tableName = "tasks", indices = [Index("isDone"), Index("dueAt")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** 0 = low, 1 = normal, 2 = high, 3 = urgent. */
    val priority: Int = 1,
    val dueAt: Long? = null,
    val project: String? = null,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    /** Minutes the user estimated for this task, used by the focus planner. */
    val estimateMinutes: Int = 0,
    val focusedMinutes: Int = 0,
)

@Entity(tableName = "notes", indices = [Index("pinned"), Index("updatedAt")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    /** Index into [jp.hisiragi.worklauncher.ui.theme.NoteColors]. */
    val colorIndex: Int = 0,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One working day of the time clock. Keyed by epoch day so a day is unique. */
@Entity(tableName = "time_cards", indices = [Index(value = ["epochDay"], unique = true)])
data class TimeCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val clockInAt: Long? = null,
    val clockOutAt: Long? = null,
    val breakMinutes: Int = 0,
    /** Start of an in-progress break, null when not on a break. */
    val breakStartedAt: Long? = null,
    /** "OFFICE", "REMOTE", "CLIENT", "TRIP". */
    val workPlace: String = "OFFICE",
    val memo: String = "",
)

@Entity(tableName = "focus_sessions", indices = [Index("startedAt")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val plannedMinutes: Int,
    val actualSeconds: Int = 0,
    /** "FOCUS", "SHORT_BREAK", "LONG_BREAK". */
    val kind: String = "FOCUS",
    val taskId: Long? = null,
    val label: String = "",
    val completed: Boolean = false,
)

@Entity(tableName = "quick_contacts")
data class QuickContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val company: String? = null,
    val photoUri: String? = null,
    val sortOrder: Int = 0,
)

/** Launcher-owned metadata layered on top of what the package manager reports. */
@Entity(tableName = "app_meta", primaryKeys = ["componentKey"], indices = [Index("category")])
data class AppMetaEntity(
    /** "packageName/activityName". */
    val componentKey: String,
    val customLabel: String? = null,
    /** "WORK", "PERSONAL", "UTILITY", "UNSORTED". */
    val category: String = "UNSORTED",
    val hidden: Boolean = false,
    val favorite: Boolean = false,
    val dockOrder: Int = 0,
    /** Apps flagged here are gated behind a confirmation while focus mode runs. */
    val distraction: Boolean = false,
    val launchCount: Int = 0,
    val lastLaunchedAt: Long = 0,
)

@Entity(tableName = "expenses", indices = [Index("epochDay")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** Stored in the smallest currency unit the user types, i.e. whole yen. */
    val amount: Long,
    /** "TRANSPORT", "MEAL", "SUPPLIES", "ACCOMMODATION", "ENTERTAINMENT", "OTHER". */
    val category: String = "OTHER",
    val memo: String = "",
    val project: String? = null,
    val reimbursed: Boolean = false,
    /** File name of the receipt photo inside the app's receipts directory. */
    val receiptFile: String? = null,
)

/**
 * A third-party app widget placed on the home screen. Widgets sharing a
 * [stackId] occupy one slot and are swiped between.
 */
@Entity(tableName = "home_widgets", indices = [Index("stackId")])
data class HomeWidgetEntity(
    /** The id allocated by AppWidgetHost; also the row's identity. */
    @PrimaryKey val appWidgetId: Int,
    val stackId: String,
    /** Position within the stack. */
    val position: Int = 0,
    /** Position of the stack itself among the home screen's slots. */
    val stackOrder: Int = 0,
    val heightDp: Int = 180,
)
