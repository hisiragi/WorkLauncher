package jp.hisiragi.worklauncher.domain


/** Priority buckets shown on the task board. */
enum class Priority(val level: Int) {
    LOW(0), NORMAL(1), HIGH(2), URGENT(3);

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NORMAL
    }
}

/** How an app is bucketed in the drawer. */
enum class AppCategory {
    WORK, PERSONAL, UTILITY, UNSORTED;

    companion object {
        fun fromKey(key: String): AppCategory = entries.firstOrNull { it.name == key } ?: UNSORTED
    }
}

enum class DrawerSort { ALPHABETICAL, MOST_USED, RECENT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class WorkPlace {
    OFFICE, REMOTE, CLIENT, TRIP;

    companion object {
        fun fromKey(key: String): WorkPlace = entries.firstOrNull { it.name == key } ?: OFFICE
    }
}

enum class ExpenseCategory {
    TRANSPORT, MEAL, SUPPLIES, ACCOMMODATION, ENTERTAINMENT, OTHER;

    companion object {
        fun fromKey(key: String): ExpenseCategory = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

enum class FocusKind {
    FOCUS, SHORT_BREAK, LONG_BREAK;

    companion object {
        fun fromKey(key: String): FocusKind = entries.firstOrNull { it.name == key } ?: FOCUS
    }
}

/** A launchable app plus the launcher's own metadata about it. */
data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val category: AppCategory = AppCategory.UNSORTED,
    val hidden: Boolean = false,
    val favorite: Boolean = false,
    val dockOrder: Int = 0,
    val distraction: Boolean = false,
    val launchCount: Int = 0,
    val lastLaunchedAt: Long = 0,
    val isSystemApp: Boolean = false,
) {
    val componentKey: String get() = "$packageName/$activityName"

    /** First character used by the drawer's alphabet index. */
    val sortKey: String get() = label.trim().uppercase()
}

/** A calendar event read from the system calendar provider. */
data class AgendaEvent(
    val id: Long,
    val title: String,
    val location: String?,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean,
    val calendarColor: Int,
    val calendarName: String?,
    val organizer: String?,
)

/** Per-app foreground time for a day, from UsageStatsManager. */
data class AppUsage(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
    val launchCount: Int,
    val category: AppCategory,
)

/** Aggregated hours for one working day. */
data class WorkDaySummary(
    val epochDay: Long,
    val clockInAt: Long?,
    val clockOutAt: Long?,
    val breakMinutes: Int,
    val workedMinutes: Int,
    val overtimeMinutes: Int,
    val workPlace: WorkPlace,
    val memo: String,
    val onBreak: Boolean,
) {
    val isOpen: Boolean get() = clockInAt != null && clockOutAt == null
}
