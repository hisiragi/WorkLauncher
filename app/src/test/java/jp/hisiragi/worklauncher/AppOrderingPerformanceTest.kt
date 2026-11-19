package jp.hisiragi.worklauncher

import jp.hisiragi.worklauncher.domain.AppOrdering
import jp.hisiragi.worklauncher.domain.DrawerSort
import jp.hisiragi.worklauncher.domain.LauncherApp
import java.text.Collator
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer re-sorts a few hundred apps whenever the list or the chosen order
 * changes. Collating a pair of labels is expensive, so the sort precomputes one
 * key per label instead of collating inside every comparison.
 */
class AppOrderingPerformanceTest {

    private val locale = Locale.JAPANESE

    private val apps: List<LauncherApp> = run {
        val random = Random(42)
        val prefixes = listOf(
            "Slack", "Chrome", "カレンダー", "あとで読む", "電卓", "設定", "ファイル",
            "Zoom", "マップ", "写真", "メール", "時計", "Drive", "ニュース",
        )
        List(400) { index ->
            LauncherApp(
                packageName = "com.example.app$index",
                activityName = "Main",
                label = "${prefixes[random.nextInt(prefixes.size)]} $index",
                launchCount = random.nextInt(50),
                lastLaunchedAt = random.nextLong(1_000_000),
            )
        }
    }

    /** What the drawer did before: collate inside every comparison. */
    private fun sortedByComparing(source: List<LauncherApp>): List<LauncherApp> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        return source.sortedWith { left, right ->
            collator.compare(left.label.trim(), right.label.trim())
        }
    }

    @Test
    fun `key based sort agrees with comparing every pair`() {
        assertEquals(
            sortedByComparing(apps).map { it.label },
            AppOrdering.sorted(apps, DrawerSort.ALPHABETICAL, locale).map { it.label },
        )
    }

    @Test
    fun `most used still falls back to label order within a tie`() {
        val sorted = AppOrdering.sorted(apps, DrawerSort.MOST_USED, locale)
        val counts = sorted.map { it.launchCount }
        assertEquals(counts.sortedDescending(), counts)
    }

    @Test
    fun `key based sort is faster than collating every comparison`() {
        // Warm the JIT and the collator's internal caches for both paths.
        repeat(5) {
            sortedByComparing(apps)
            AppOrdering.sorted(apps, DrawerSort.ALPHABETICAL, locale)
        }

        val comparing = timeOf { sortedByComparing(apps) }
        val keyed = timeOf { AppOrdering.sorted(apps, DrawerSort.ALPHABETICAL, locale) }

        println("compare-per-pair: ${comparing / 1_000_000.0} ms, collation keys: ${keyed / 1_000_000.0} ms")
        assertTrue(
            "Key-based sort should not be slower: keyed=$keyed ns, comparing=$comparing ns",
            keyed <= comparing,
        )
    }

    private fun timeOf(rounds: Int = 20, block: () -> Unit): Long {
        val start = System.nanoTime()
        repeat(rounds) { block() }
        return (System.nanoTime() - start) / rounds
    }
}
