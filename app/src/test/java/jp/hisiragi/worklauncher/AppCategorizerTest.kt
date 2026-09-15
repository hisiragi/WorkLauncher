package jp.hisiragi.worklauncher

import android.content.pm.ApplicationInfo
import jp.hisiragi.worklauncher.data.repo.AppCategorizer
import jp.hisiragi.worklauncher.domain.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCategorizerTest {

    @Test
    fun `store category maps productivity to work and media to personal`() {
        assertEquals(
            AppCategory.WORK,
            AppCategorizer.fromSystemCategory(ApplicationInfo.CATEGORY_PRODUCTIVITY),
        )
        assertEquals(
            AppCategory.PERSONAL,
            AppCategorizer.fromSystemCategory(ApplicationInfo.CATEGORY_GAME),
        )
        assertEquals(
            AppCategory.PERSONAL,
            AppCategorizer.fromSystemCategory(ApplicationInfo.CATEGORY_SOCIAL),
        )
    }

    @Test
    fun `undeclared store category yields no guess`() {
        assertNull(AppCategorizer.fromSystemCategory(ApplicationInfo.CATEGORY_UNDEFINED))
    }

    @Test
    fun `package names are matched case insensitively`() {
        assertEquals(AppCategory.WORK, AppCategorizer.fromPackageName("com.Slack"))
        assertEquals(AppCategory.WORK, AppCategorizer.fromPackageName("us.zoom.videomeetings"))
        assertEquals(AppCategory.PERSONAL, AppCategorizer.fromPackageName("com.instagram.android"))
        assertNull(AppCategorizer.fromPackageName("com.example.unknownapp"))
    }

    @Test
    fun `package name wins over a contradicting store category`() {
        // Slack declares itself as social; it is still a work app here.
        assertEquals(
            AppCategory.WORK,
            AppCategorizer.categorize(
                packageName = "com.Slack",
                systemCategory = ApplicationInfo.CATEGORY_SOCIAL,
                isSystemApp = false,
            ),
        )
    }

    @Test
    fun `unknown system apps fall back to utility and others stay unsorted`() {
        assertEquals(
            AppCategory.UTILITY,
            AppCategorizer.categorize(
                packageName = "com.vendor.somepreload",
                systemCategory = ApplicationInfo.CATEGORY_UNDEFINED,
                isSystemApp = true,
            ),
        )
        assertEquals(
            AppCategory.UNSORTED,
            AppCategorizer.categorize(
                packageName = "com.example.unknownapp",
                systemCategory = ApplicationInfo.CATEGORY_UNDEFINED,
                isSystemApp = false,
            ),
        )
    }
}
