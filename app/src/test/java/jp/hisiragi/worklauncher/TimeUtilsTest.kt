package jp.hisiragi.worklauncher

import jp.hisiragi.worklauncher.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeUtilsTest {

    @Test
    fun `formatDuration renders hours and minutes`() {
        assertEquals("7h 30m", TimeUtils.formatDuration(450))
        assertEquals("8h", TimeUtils.formatDuration(480))
        assertEquals("45m", TimeUtils.formatDuration(45))
        assertEquals("0m", TimeUtils.formatDuration(0))
    }

    @Test
    fun `formatDuration keeps the sign for a deficit`() {
        assertEquals("-1h 30m", TimeUtils.formatDuration(-90))
        assertEquals("-20m", TimeUtils.formatDuration(-20))
    }

    @Test
    fun `formatCountdown is always two-digit and never negative`() {
        assertEquals("25:00", TimeUtils.formatCountdown(1500))
        assertEquals("00:09", TimeUtils.formatCountdown(9))
        assertEquals("00:00", TimeUtils.formatCountdown(-5))
        assertEquals("90:00", TimeUtils.formatCountdown(5400))
    }

    @Test
    fun `isWorkDay reads the mask with Monday as bit zero`() {
        val monToFri = 0b0011111
        assertTrue(TimeUtils.isWorkDay(LocalDate.of(2026, 9, 14), monToFri)) // Monday
        assertTrue(TimeUtils.isWorkDay(LocalDate.of(2026, 9, 18), monToFri)) // Friday
        assertFalse(TimeUtils.isWorkDay(LocalDate.of(2026, 9, 19), monToFri)) // Saturday
        assertFalse(TimeUtils.isWorkDay(LocalDate.of(2026, 9, 20), monToFri)) // Sunday
    }

    @Test
    fun `week bounds run Monday to Sunday`() {
        val wednesday = LocalDate.of(2026, 9, 16)
        assertEquals(LocalDate.of(2026, 9, 14), TimeUtils.startOfWeek(wednesday))
        assertEquals(LocalDate.of(2026, 9, 20), TimeUtils.endOfWeek(wednesday))
    }

    @Test
    fun `month bounds cover the whole month`() {
        val mid = LocalDate.of(2026, 2, 10)
        assertEquals(LocalDate.of(2026, 2, 1), TimeUtils.startOfMonth(mid))
        assertEquals(LocalDate.of(2026, 2, 28), TimeUtils.endOfMonth(mid))
    }
}
