package jp.hisiragi.worklauncher

import jp.hisiragi.worklauncher.domain.AppOrdering
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher ships Japanese first, so ordering has to handle kana and kanji,
 * not just ASCII.
 */
class DrawerSortTest {

    private val labels = listOf(
        "Slack", "chrome", "カレンダー", "あとで読む", "Zoom", "電卓", "2048",
    )

    @Test
    fun `raw string ordering puts every latin name before every japanese one`() {
        // What the drawer used to do, kept to document why the collator exists.
        assertEquals(
            listOf("2048", "chrome", "Slack", "Zoom", "あとで読む", "カレンダー", "電卓"),
            labels.sortedBy { it.trim().uppercase() },
        )
    }

    @Test
    fun `collator interleaves kana ahead of kanji and keeps latin together`() {
        val sorted = labels.sortedWith(AppOrdering.labelComparator(Locale.JAPANESE))
        // Kana sorts by reading, ahead of kanji, rather than by code point block.
        assertTrue(
            "あとで読む should precede カレンダー, got $sorted",
            sorted.indexOf("あとで読む") < sorted.indexOf("カレンダー"),
        )
        assertTrue(
            "カレンダー should precede 電卓, got $sorted",
            sorted.indexOf("カレンダー") < sorted.indexOf("電卓"),
        )
    }

    @Test
    fun `case does not split otherwise adjacent names`() {
        val sorted = listOf("chrome", "Calendar", "Chrome")
            .sortedWith(AppOrdering.labelComparator(Locale.ENGLISH))
        assertEquals(listOf("Calendar", "chrome", "Chrome"), sorted)
    }

    @Test
    fun `leading and trailing space does not change where a name lands`() {
        val sorted = listOf("  Zoom", "Apple", " Banana ")
            .sortedWith(AppOrdering.labelComparator(Locale.ENGLISH))
        assertEquals(listOf("Apple", " Banana ", "  Zoom"), sorted)
    }
}
