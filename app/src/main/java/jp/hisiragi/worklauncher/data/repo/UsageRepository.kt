package jp.hisiragi.worklauncher.data.repo

import android.app.usage.UsageStatsManager
import android.content.Context
import jp.hisiragi.worklauncher.domain.AppCategory
import jp.hisiragi.worklauncher.domain.AppUsage
import jp.hisiragi.worklauncher.domain.LauncherApp
import jp.hisiragi.worklauncher.util.LauncherStatus
import jp.hisiragi.worklauncher.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Screen-time figures pulled from [UsageStatsManager]. Needs usage access. */
class UsageRepository(
    private val context: Context,
    private val appRepository: AppRepository,
) {
    fun hasPermission(): Boolean = LauncherStatus.hasUsageAccess(context)

    suspend fun usageForDay(date: LocalDate): List<AppUsage> =
        usageBetween(date, date)

    suspend fun usageBetween(from: LocalDate, to: LocalDate): List<AppUsage> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@withContext emptyList()

            val start = TimeUtils.startOfDayMillis(from)
            val end = TimeUtils.endOfDayMillis(to)

            val stats = runCatching {
                manager.queryAndAggregateUsageStats(start, end)
            }.getOrElse { return@withContext emptyList() }

            val appsByPackage = appRepository.apps.value.associateBy { it.packageName }

            stats.values
                .filter { it.totalTimeInForeground > 0 }
                .mapNotNull { stat ->
                    // Only surface things the user can actually open; background
                    // system packages would otherwise dominate the list.
                    val app: LauncherApp = appsByPackage[stat.packageName] ?: return@mapNotNull null
                    AppUsage(
                        packageName = stat.packageName,
                        label = app.label,
                        foregroundMillis = stat.totalTimeInForeground,
                        launchCount = app.launchCount,
                        category = app.category,
                    )
                }
                .sortedByDescending { it.foregroundMillis }
        }

    /** Total foreground time split into work vs. everything else. */
    fun split(usage: List<AppUsage>): UsageSplit {
        val work = usage.filter { it.category == AppCategory.WORK }.sumOf { it.foregroundMillis }
        val personal = usage.filter { it.category == AppCategory.PERSONAL }.sumOf { it.foregroundMillis }
        val other = usage.filter {
            it.category != AppCategory.WORK && it.category != AppCategory.PERSONAL
        }.sumOf { it.foregroundMillis }
        return UsageSplit(work, personal, other)
    }

    data class UsageSplit(
        val workMillis: Long,
        val personalMillis: Long,
        val otherMillis: Long,
    ) {
        val totalMillis: Long get() = workMillis + personalMillis + otherMillis
    }
}
