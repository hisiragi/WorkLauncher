package jp.hisiragi.worklauncher.domain

import java.text.Collator
import java.text.CollationKey
import java.util.Locale

/**
 * Orders app labels the way the user's language does. Comparing raw strings puts
 * every Latin name ahead of every Japanese one and orders kana by code point, so
 * the drawer went through the alphabet before reaching あ.
 */
object AppOrdering {

    /**
     * Collators are not thread-safe and building one is not free, so a fresh
     * instance is made per sort rather than shared.
     */
    private fun collator(locale: Locale): Collator =
        Collator.getInstance(locale).apply {
            // Treat case and accents as ties so "chrome" and "Chrome" stay adjacent.
            strength = Collator.SECONDARY
        }

    /** Kept for comparing bare labels; [sorted] is the path the drawer uses. */
    fun labelComparator(locale: Locale = Locale.getDefault()): Comparator<String> {
        val collator = collator(locale)
        return Comparator { left, right -> collator.compare(left.trim(), right.trim()) }
    }

    /**
     * Collating a pair costs far more than comparing two numbers, and a sort asks
     * for ~n log n comparisons. Each label's key is computed once up front so the
     * sort itself compares keys, which is a plain byte comparison.
     */
    fun sorted(
        apps: List<LauncherApp>,
        sort: DrawerSort = DrawerSort.ALPHABETICAL,
        locale: Locale = Locale.getDefault(),
    ): List<LauncherApp> {
        if (apps.size < 2) return apps
        val collator = collator(locale)
        val keyed = apps.map { Keyed(it, collator.getCollationKey(it.label.trim())) }
        val comparator: Comparator<Keyed> = when (sort) {
            DrawerSort.ALPHABETICAL -> compareBy { it.key }
            DrawerSort.MOST_USED ->
                compareByDescending<Keyed> { it.app.launchCount }.thenBy { it.key }
            DrawerSort.RECENT ->
                compareByDescending<Keyed> { it.app.lastLaunchedAt }.thenBy { it.key }
        }
        return keyed.sortedWith(comparator).map { it.app }
    }

    private class Keyed(val app: LauncherApp, val key: CollationKey)
}
