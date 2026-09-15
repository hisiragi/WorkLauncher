package jp.hisiragi.worklauncher

import jp.hisiragi.worklauncher.data.db.TimeCardEntity
import jp.hisiragi.worklauncher.data.repo.TimeCardRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeCardCalculationTest {

    private val nineAm = 1_757_900_000_000L
    private fun minutes(n: Int) = n * 60_000L

    @Test
    fun `a closed day subtracts the recorded break`() {
        val card = TimeCardEntity(
            epochDay = 0,
            clockInAt = nineAm,
            clockOutAt = nineAm + minutes(540), // nine hours later
            breakMinutes = 60,
        )
        assertEquals(480, TimeCardRepository.workedMinutes(card))
    }

    @Test
    fun `an open day is measured up to now`() {
        val card = TimeCardEntity(epochDay = 0, clockInAt = nineAm)
        assertEquals(125, TimeCardRepository.workedMinutes(card, now = nineAm + minutes(125)))
    }

    @Test
    fun `a running break is deducted while it is still open`() {
        val card = TimeCardEntity(
            epochDay = 0,
            clockInAt = nineAm,
            breakStartedAt = nineAm + minutes(180),
        )
        // Four hours elapsed, the last one of them on a break.
        assertEquals(180, TimeCardRepository.workedMinutes(card, now = nineAm + minutes(240)))
    }

    @Test
    fun `a day with no clock-in has no worked time`() {
        assertEquals(0, TimeCardRepository.workedMinutes(TimeCardEntity(epochDay = 0)))
    }

    @Test
    fun `a break longer than the day never yields negative time`() {
        val card = TimeCardEntity(
            epochDay = 0,
            clockInAt = nineAm,
            clockOutAt = nineAm + minutes(60),
            breakMinutes = 120,
        )
        assertEquals(0, TimeCardRepository.workedMinutes(card))
    }
}
