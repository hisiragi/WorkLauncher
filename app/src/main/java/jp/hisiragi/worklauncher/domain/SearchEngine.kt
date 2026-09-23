package jp.hisiragi.worklauncher.domain

import androidx.annotation.StringRes
import jp.hisiragi.worklauncher.R

enum class SearchEngineType(
    @StringRes val labelRes: Int,
    val defaultUrl: String,
    val isCustom: Boolean = false,
) {
    GOOGLE(
        labelRes = R.string.engine_google,
        defaultUrl = "https://www.google.com/search?q=",
    ),
    BING(
        labelRes = R.string.engine_bing,
        defaultUrl = "https://www.bing.com/search?q=",
    ),
    YAHOO_JAPAN(
        labelRes = R.string.engine_yahoo_japan,
        defaultUrl = "https://search.yahoo.co.jp/search?p=",
    ),
    BRAVE(
        labelRes = R.string.engine_brave,
        defaultUrl = "https://search.brave.com/search?q=",
    ),
    STARTPAGE(
        labelRes = R.string.engine_startpage,
        defaultUrl = "https://www.startpage.com/sp/search?query=",
    ),
    DUCKDUCKGO(
        labelRes = R.string.engine_duckduckgo,
        defaultUrl = "https://duckduckgo.com/?q=",
    ),
    CUSTOM(
        labelRes = R.string.engine_custom,
        defaultUrl = "",
        isCustom = true,
    );

    companion object {
        fun fromUrl(url: String): SearchEngineType = when (url) {
            GOOGLE.defaultUrl -> GOOGLE
            BING.defaultUrl -> BING
            YAHOO_JAPAN.defaultUrl -> YAHOO_JAPAN
            BRAVE.defaultUrl -> BRAVE
            STARTPAGE.defaultUrl -> STARTPAGE
            DUCKDUCKGO.defaultUrl -> DUCKDUCKGO
            else -> CUSTOM
        }
    }
}

object SearchEngineHelper {
    const val DEFAULT_CUSTOM_URL = "https://duckduckgo.com/?q=%s"

    fun buildSearchUrl(templateUrl: String, query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return templateUrl
        val encoded = try {
            android.net.Uri.encode(trimmed)
        } catch (_: Exception) {
            java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
        return when {
            templateUrl.contains("%s") -> templateUrl.replace("%s", encoded)
            templateUrl.contains("{q}") -> templateUrl.replace("{q}", encoded)
            else -> templateUrl + encoded
        }
    }
}
