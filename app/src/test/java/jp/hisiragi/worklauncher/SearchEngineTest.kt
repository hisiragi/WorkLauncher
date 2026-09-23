package jp.hisiragi.worklauncher

import jp.hisiragi.worklauncher.domain.SearchEngineHelper
import jp.hisiragi.worklauncher.domain.SearchEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `resolves engine type from default URLs`() {
        assertEquals(SearchEngineType.GOOGLE, SearchEngineType.fromUrl("https://www.google.com/search?q="))
        assertEquals(SearchEngineType.BING, SearchEngineType.fromUrl("https://www.bing.com/search?q="))
        assertEquals(SearchEngineType.YAHOO_JAPAN, SearchEngineType.fromUrl("https://search.yahoo.co.jp/search?p="))
        assertEquals(SearchEngineType.BRAVE, SearchEngineType.fromUrl("https://search.brave.com/search?q="))
        assertEquals(SearchEngineType.STARTPAGE, SearchEngineType.fromUrl("https://www.startpage.com/sp/search?query="))
        assertEquals(SearchEngineType.DUCKDUCKGO, SearchEngineType.fromUrl("https://duckduckgo.com/?q="))
    }

    @Test
    fun `resolves unknown or custom URL to CUSTOM type`() {
        assertEquals(SearchEngineType.CUSTOM, SearchEngineType.fromUrl("https://kagi.com/search?q=%s"))
        assertEquals(SearchEngineType.CUSTOM, SearchEngineType.fromUrl("https://example.com/search?q="))
        assertEquals(SearchEngineType.CUSTOM, SearchEngineType.fromUrl(""))
    }

    @Test
    fun `builds search URL with append format`() {
        val googleUrl = SearchEngineHelper.buildSearchUrl(
            SearchEngineType.GOOGLE.defaultUrl,
            "kotlin",
        )
        assertEquals("https://www.google.com/search?q=kotlin", googleUrl)

        val yahooUrl = SearchEngineHelper.buildSearchUrl(
            SearchEngineType.YAHOO_JAPAN.defaultUrl,
            "android launcher",
        )
        assertTrue(yahooUrl.startsWith("https://search.yahoo.co.jp/search?p="))
        assertTrue(yahooUrl.contains("android") && yahooUrl.contains("launcher"))
    }

    @Test
    fun `builds search URL with percent-s placeholder`() {
        val customUrl = SearchEngineHelper.buildSearchUrl(
            "https://kagi.com/search?q=%s",
            "hello world",
        )
        assertTrue(customUrl.startsWith("https://kagi.com/search?q="))
        assertTrue(customUrl.contains("hello") && customUrl.contains("world"))
    }

    @Test
    fun `builds search URL with curly bracket placeholder`() {
        val customUrl = SearchEngineHelper.buildSearchUrl(
            "https://example.com/?query={q}&lang=ja",
            "test query",
        )
        assertTrue(customUrl.startsWith("https://example.com/?query="))
        assertTrue(customUrl.endsWith("&lang=ja"))
        assertTrue(customUrl.contains("test") && customUrl.contains("query"))
    }

    @Test
    fun `empty or blank query returns template url unchanged`() {
        val url = SearchEngineHelper.buildSearchUrl(
            "https://www.google.com/search?q=",
            "   ",
        )
        assertEquals("https://www.google.com/search?q=", url)
    }
}
