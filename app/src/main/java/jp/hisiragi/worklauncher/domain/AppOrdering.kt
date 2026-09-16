package jp.hisiragi.worklauncher.domain

import java.text.Collator
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
    fun labelComparator(locale: Locale = Locale.getDefault()): Comparator<String> {
        val collator = Collator.getInstance(locale).apply {
            // Treat case and accents as ties so "chrome" and "Chrome" stay adjacent.
            strength = Collator.SECONDARY
        }
        return Comparator { left, right -> collator.compare(left.trim(), right.trim()) }
    }

    fun byLabel(locale: Locale = Locale.getDefault()): Comparator<LauncherApp> {
        val labels = labelComparator(locale)
        return Comparator { left, right -> labels.compare(left.label, right.label) }
    }

    fun forSort(sort: DrawerSort, locale: Locale = Locale.getDefault()): Comparator<LauncherApp> {
        val byLabel = byLabel(locale)
        return when (sort) {
            DrawerSort.ALPHABETICAL -> byLabel
            DrawerSort.MOST_USED ->
                compareByDescending<LauncherApp> { it.launchCount }.then(byLabel)
            DrawerSort.RECENT ->
                compareByDescending<LauncherApp> { it.lastLaunchedAt }.then(byLabel)
        }
    }
}
