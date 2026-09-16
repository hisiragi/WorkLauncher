package jp.hisiragi.worklauncher.service.llm

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
)

/**
 * Backs the assistant's search skill with DuckDuckGo's HTML endpoint, which
 * needs no API key. Results are third-party text: they are quoted into the
 * prompt as reference material, never treated as instructions.
 */
class WebSearch {

    suspend fun search(query: String, limit: Int = MAX_RESULTS): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("$ENDPOINT?q=$encoded")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                val html = connection.inputStream.bufferedReader()
                    .use(BufferedReader::readText)
                parse(html).take(limit)
            } catch (e: Exception) {
                emptyList()
            } finally {
                connection.disconnect()
            }
        }

    private fun parse(html: String): List<SearchResult> =
        RESULT_BLOCK.findAll(html).mapNotNull { match ->
            val href = match.groupValues[1]
            val title = match.groupValues[2].stripTags()
            if (title.isBlank()) return@mapNotNull null
            SearchResult(
                title = title,
                snippet = SNIPPET.find(html, match.range.last)
                    ?.groupValues?.get(1)?.stripTags().orEmpty(),
                url = href.resolveRedirect(),
            )
        }.toList()

    /** The lite endpoint wraps targets in its own redirector. */
    private fun String.resolveRedirect(): String {
        val marker = "uddg="
        val start = indexOf(marker)
        if (start < 0) return this
        val raw = substring(start + marker.length).substringBefore('&')
        return runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(this)
    }

    private fun String.stripTags(): String =
        replace(TAG, "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .trim()

    private companion object {
        const val ENDPOINT = "https://html.duckduckgo.com/html/"
        const val USER_AGENT = "Mozilla/5.0 (Android) WorkLauncher"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_RESULTS = 5

        val RESULT_BLOCK =
            Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val SNIPPET =
            Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val TAG = Regex("<[^>]*>")
    }
}
