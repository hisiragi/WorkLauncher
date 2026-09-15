package jp.hisiragi.worklauncher.data.repo

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import jp.hisiragi.worklauncher.domain.AgendaEvent
import jp.hisiragi.worklauncher.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Reads meetings out of the system calendar provider. Read-only by design. */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun eventsBetween(from: LocalDate, to: LocalDate): List<AgendaEvent> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()

            val startMillis = TimeUtils.startOfDayMillis(from)
            val endMillis = TimeUtils.endOfDayMillis(to)

            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
                ContentUris.appendId(this, startMillis)
                ContentUris.appendId(this, endMillis)
            }.build()

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_COLOR,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.ORGANIZER,
                CalendarContract.Instances.STATUS,
            )

            val events = mutableListOf<AgendaEvent>()
            runCatching {
                context.contentResolver.query(
                    uri,
                    projection,
                    "${CalendarContract.Instances.STATUS} IS NULL OR " +
                        "${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED}",
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )
            }.getOrNull()?.use { cursor ->
                while (cursor.moveToNext()) {
                    events += AgendaEvent(
                        id = cursor.getLong(0),
                        title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "(no title)",
                        location = cursor.getString(2)?.takeIf { it.isNotBlank() },
                        startAt = cursor.getLong(3),
                        endAt = cursor.getLong(4),
                        allDay = cursor.getInt(5) == 1,
                        calendarColor = cursor.getInt(6),
                        calendarName = cursor.getString(7),
                        organizer = cursor.getString(8)?.takeIf { it.isNotBlank() },
                    )
                }
            }
            events.sortedBy { it.startAt }
        }

    suspend fun eventsToday(): List<AgendaEvent> {
        val today = TimeUtils.today()
        return eventsBetween(today, today)
    }
}
