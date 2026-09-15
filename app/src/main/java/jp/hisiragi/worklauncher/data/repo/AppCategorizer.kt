package jp.hisiragi.worklauncher.data.repo

import android.content.pm.ApplicationInfo
import jp.hisiragi.worklauncher.domain.AppCategory

/**
 * Guesses which bucket an app belongs in so the drawer is usable before the
 * user has sorted anything by hand. A category the user picked always wins;
 * this only fills in [AppCategory.UNSORTED].
 */
object AppCategorizer {

    /**
     * The store category the developer declared. Absent for most sideloaded and
     * pre-installed apps, which is why [fromPackageName] exists.
     */
    fun fromSystemCategory(category: Int): AppCategory? = when (category) {
        ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_MAPS -> AppCategory.WORK
        ApplicationInfo.CATEGORY_GAME,
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE,
        ApplicationInfo.CATEGORY_SOCIAL,
        ApplicationInfo.CATEGORY_NEWS,
        -> AppCategory.PERSONAL
        else -> null
    }

    /** Matches on the vendor prefix so `com.slack.foo` lands with `com.Slack`. */
    fun fromPackageName(packageName: String): AppCategory? {
        val lower = packageName.lowercase()
        return when {
            WORK_TOKENS.any { lower.contains(it) } -> AppCategory.WORK
            PERSONAL_TOKENS.any { lower.contains(it) } -> AppCategory.PERSONAL
            UTILITY_TOKENS.any { lower.contains(it) } -> AppCategory.UTILITY
            else -> null
        }
    }

    fun categorize(packageName: String, systemCategory: Int, isSystemApp: Boolean): AppCategory =
        fromPackageName(packageName)
            ?: fromSystemCategory(systemCategory)
            ?: if (isSystemApp) AppCategory.UTILITY else AppCategory.UNSORTED

    private val WORK_TOKENS = listOf(
        "slack", "microsoft.teams", "zoom", "webex", "gmail", "outlook", "office",
        "google.android.apps.docs", "google.android.calendar", "google.android.gm",
        "google.android.apps.meetings", "notion", "atlassian", "jira", "confluence",
        "trello", "asana", "salesforce", "zoho", "dropbox", "box.android",
        "evernote", "onedrive", "sharepoint", "chatwork", "cybozu", "sansan",
        "freee", "moneyforward", "linkedin", "figma", "github", "gitlab",
    )

    private val PERSONAL_TOKENS = listOf(
        "instagram", "twitter", "com.x.", "tiktok", "youtube", "netflix", "spotify",
        "facebook", "snapchat", "reddit", "pinterest", "twitch", "discord",
        "naver.line", "kakao", "wechat", "whatsapp", "telegram", "nicovideo",
        "amazon.avod", "disney", "hulu", "abema", "game", "niantic", "supercell",
        "mihoyo", "hoyoverse", "roblox", "minecraft",
    )

    private val UTILITY_TOKENS = listOf(
        "com.android.settings", "com.android.documentsui", "com.android.dialer",
        "com.android.contacts", "com.android.calculator", "com.android.deskclock",
        "filemanager", "files", "camera", "calculator", "clock", "antivirus",
    )
}
