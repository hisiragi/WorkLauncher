package jp.hisiragi.worklauncher.data.repo

import jp.hisiragi.worklauncher.data.db.TimeCardDao
import jp.hisiragi.worklauncher.data.db.TimeCardEntity
import jp.hisiragi.worklauncher.domain.WorkDaySummary
import jp.hisiragi.worklauncher.domain.WorkPlace
import jp.hisiragi.worklauncher.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The time clock. One row per calendar day; clocking in twice on the same day
 * reopens the existing row rather than creating a second one.
 */
class TimeCardRepository(private val dao: TimeCardDao) {

    fun observeDay(date: LocalDate): Flow<WorkDaySummary?> =
        dao.observeByDay(date.toEpochDay()).map { it?.toSummary(standardMinutes = 0) }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TimeCardEntity>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay())

    suspend fun listRange(from: LocalDate, to: LocalDate): List<TimeCardEntity> =
        dao.listRange(from.toEpochDay(), to.toEpochDay())

    suspend fun clockIn(date: LocalDate = TimeUtils.today(), workPlace: WorkPlace = WorkPlace.OFFICE) {
        val now = System.currentTimeMillis()
        val existing = dao.findByDay(date.toEpochDay())
        if (existing == null) {
            dao.insert(
                TimeCardEntity(
                    epochDay = date.toEpochDay(),
                    clockInAt = now,
                    workPlace = workPlace.name,
                )
            )
        } else {
            // Re-opening a day that was already closed keeps the original start.
            dao.update(
                existing.copy(
                    clockInAt = existing.clockInAt ?: now,
                    clockOutAt = null,
                    workPlace = workPlace.name,
                )
            )
        }
    }

    suspend fun clockOut(date: LocalDate = TimeUtils.today()) {
        val existing = dao.findByDay(date.toEpochDay()) ?: return
        val now = System.currentTimeMillis()
        // Closing the day while a break is running also ends that break.
        val extraBreak = existing.breakStartedAt?.let { minutesBetween(it, now) } ?: 0
        dao.update(
            existing.copy(
                clockOutAt = now,
                breakMinutes = existing.breakMinutes + extraBreak,
                breakStartedAt = null,
            )
        )
    }

    suspend fun startBreak(date: LocalDate = TimeUtils.today()) {
        val existing = dao.findByDay(date.toEpochDay()) ?: return
        if (existing.breakStartedAt != null) return
        dao.update(existing.copy(breakStartedAt = System.currentTimeMillis()))
    }

    suspend fun endBreak(date: LocalDate = TimeUtils.today()) {
        val existing = dao.findByDay(date.toEpochDay()) ?: return
        val startedAt = existing.breakStartedAt ?: return
        dao.update(
            existing.copy(
                breakMinutes = existing.breakMinutes + minutesBetween(startedAt, System.currentTimeMillis()),
                breakStartedAt = null,
            )
        )
    }

    suspend fun setBreakMinutes(date: LocalDate, minutes: Int) {
        val existing = dao.findByDay(date.toEpochDay()) ?: return
        dao.update(existing.copy(breakMinutes = minutes.coerceAtLeast(0)))
    }

    suspend fun setWorkPlace(date: LocalDate, workPlace: WorkPlace) {
        val existing = dao.findByDay(date.toEpochDay()) ?: return
        dao.update(existing.copy(workPlace = workPlace.name))
    }

    suspend fun setMemo(date: LocalDate, memo: String) {
        val existing = dao.findByDay(date.toEpochDay()) ?: return
        dao.update(existing.copy(memo = memo))
    }

    suspend fun setTimes(date: LocalDate, clockInAt: Long?, clockOutAt: Long?) {
        val existing = dao.findByDay(date.toEpochDay())
        if (existing == null) {
            dao.insert(
                TimeCardEntity(epochDay = date.toEpochDay(), clockInAt = clockInAt, clockOutAt = clockOutAt)
            )
        } else {
            dao.update(existing.copy(clockInAt = clockInAt, clockOutAt = clockOutAt))
        }
    }

    suspend fun deleteDay(date: LocalDate) {
        dao.findByDay(date.toEpochDay())?.let { dao.delete(it) }
    }

    companion object {
        private fun minutesBetween(fromMillis: Long, toMillis: Long): Int =
            ((toMillis - fromMillis).coerceAtLeast(0) / 60_000L).toInt()

        /**
         * Worked minutes for a card. An open day is measured up to [now] so the
         * home screen can tick along while the user is still clocked in.
         */
        fun workedMinutes(card: TimeCardEntity, now: Long = System.currentTimeMillis()): Int {
            val start = card.clockInAt ?: return 0
            val end = card.clockOutAt ?: now
            val runningBreak = card.breakStartedAt
                ?.let { minutesBetween(it, if (card.clockOutAt != null) end else now) }
                ?: 0
            val gross = minutesBetween(start, end)
            return (gross - card.breakMinutes - runningBreak).coerceAtLeast(0)
        }

        fun TimeCardEntity.toSummary(
            standardMinutes: Int,
            now: Long = System.currentTimeMillis(),
        ): WorkDaySummary {
            val worked = workedMinutes(this, now)
            return WorkDaySummary(
                epochDay = epochDay,
                clockInAt = clockInAt,
                clockOutAt = clockOutAt,
                breakMinutes = breakMinutes,
                workedMinutes = worked,
                overtimeMinutes = (worked - standardMinutes).coerceAtLeast(0),
                workPlace = WorkPlace.fromKey(workPlace),
                memo = memo,
                onBreak = breakStartedAt != null,
            )
        }
    }
}
