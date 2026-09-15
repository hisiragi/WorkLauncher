package jp.hisiragi.worklauncher.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object TimeUtils {

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): LocalDate = LocalDate.now(zone)

    fun todayEpochDay(): Long = today().toEpochDay()

    fun epochDayOf(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDayMillis(date: LocalDate): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun startOfWeek(date: LocalDate = today()): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun endOfWeek(date: LocalDate = today()): LocalDate =
        date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    fun startOfMonth(date: LocalDate = today()): LocalDate = date.withDayOfMonth(1)

    fun endOfMonth(date: LocalDate = today()): LocalDate =
        date.with(TemporalAdjusters.lastDayOfMonth())

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun formatTime(millis: Long, use24h: Boolean): String =
        formatTime(toLocalDateTime(millis).toLocalTime(), use24h)

    fun formatTime(time: LocalTime, use24h: Boolean): String {
        val pattern = if (use24h) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun formatMinuteOfDay(minuteOfDay: Int, use24h: Boolean): String =
        formatTime(LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60), use24h)

    fun formatDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun formatDateShort(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.getDefault()))

    fun formatIsoDate(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** "7h 30m" style duration used across the work summary screens. */
    fun formatDuration(totalMinutes: Int): String {
        val sign = if (totalMinutes < 0) "-" else ""
        val abs = kotlin.math.abs(totalMinutes)
        val hours = abs / 60
        val minutes = abs % 60
        return when {
            hours > 0 && minutes > 0 -> "$sign${hours}h ${minutes}m"
            hours > 0 -> "$sign${hours}h"
            else -> "$sign${minutes}m"
        }
    }

    fun formatDurationFromMillis(millis: Long): String =
        formatDuration((millis / 60_000L).toInt())

    /** "25:00" style countdown for the focus timer. */
    fun formatCountdown(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val minutes = safe / 60
        val seconds = safe % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /** Bit 0 = Monday … bit 6 = Sunday, matching [LauncherSettings.workDayMask]. */
    fun isWorkDay(date: LocalDate, mask: Int): Boolean {
        val bit = date.dayOfWeek.value - 1
        return (mask shr bit) and 1 == 1
    }

    fun dayOfWeekLabels(): List<String> = DayOfWeek.entries.map { day ->
        day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
    }
}
