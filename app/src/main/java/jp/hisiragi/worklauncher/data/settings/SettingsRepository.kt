package jp.hisiragi.worklauncher.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jp.hisiragi.worklauncher.domain.DrawerSort
import jp.hisiragi.worklauncher.domain.LlmBackend
import jp.hisiragi.worklauncher.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "worklauncher_settings")

/** Everything the user can tune, persisted in DataStore. */
data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val gridColumns: Int = 4,
    val showAppLabels: Boolean = true,
    val drawerSort: DrawerSort = DrawerSort.ALPHABETICAL,
    val use24HourClock: Boolean = true,
    /** Minutes from midnight. */
    val workStartMinute: Int = 9 * 60,
    val workEndMinute: Int = 18 * 60,
    /** Bit 0 = Monday … bit 6 = Sunday. Defaults to Mon-Fri. */
    val workDayMask: Int = 0b0011111,
    val standardWorkMinutes: Int = 480,
    val defaultBreakMinutes: Int = 60,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroShortBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val pomodoroCyclesBeforeLongBreak: Int = 4,
    val focusGatesDistractions: Boolean = true,
    val focusVibrates: Boolean = true,
    val searchEngineUrl: String = DEFAULT_SEARCH_ENGINE,
    val showWorkSummaryCard: Boolean = true,
    val showAgendaCard: Boolean = true,
    val showTasksCard: Boolean = true,
    val currencySymbol: String = "¥",
    /** Off until the user installs a model or points at an endpoint. */
    val llmBackend: LlmBackend = LlmBackend.NONE,
    /** Absolute path of an on-device .task/.bin model file. */
    val llmModelPath: String = "",
    /** Base URL of an OpenAI-compatible server, e.g. http://192.168.1.10:11434 */
    val llmEndpoint: String = "",
    val llmRemoteModel: String = "",
    val llmSummarizeNotifications: Boolean = false,
    /** Base URL of a hosted OpenAI-compatible provider. */
    val llmApiEndpoint: String = DEFAULT_API_ENDPOINT,
    val llmApiModel: String = "",
    val llmApiKey: String = "",
) {
    companion object {
        const val DEFAULT_SEARCH_ENGINE = "https://www.google.com/search?q="
        const val DEFAULT_API_ENDPOINT = "https://api.openai.com/v1"
    }
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            gridColumns = prefs[Keys.GRID_COLUMNS] ?: 4,
            showAppLabels = prefs[Keys.SHOW_APP_LABELS] ?: true,
            drawerSort = prefs[Keys.DRAWER_SORT]?.let { runCatching { DrawerSort.valueOf(it) }.getOrNull() }
                ?: DrawerSort.ALPHABETICAL,
            use24HourClock = prefs[Keys.CLOCK_24H] ?: true,
            workStartMinute = prefs[Keys.WORK_START] ?: (9 * 60),
            workEndMinute = prefs[Keys.WORK_END] ?: (18 * 60),
            workDayMask = prefs[Keys.WORK_DAYS] ?: 0b0011111,
            standardWorkMinutes = prefs[Keys.STANDARD_WORK_MINUTES] ?: 480,
            defaultBreakMinutes = prefs[Keys.DEFAULT_BREAK_MINUTES] ?: 60,
            pomodoroFocusMinutes = prefs[Keys.POMODORO_FOCUS] ?: 25,
            pomodoroShortBreakMinutes = prefs[Keys.POMODORO_SHORT] ?: 5,
            pomodoroLongBreakMinutes = prefs[Keys.POMODORO_LONG] ?: 15,
            pomodoroCyclesBeforeLongBreak = prefs[Keys.POMODORO_CYCLES] ?: 4,
            focusGatesDistractions = prefs[Keys.FOCUS_GATES] ?: true,
            focusVibrates = prefs[Keys.FOCUS_VIBRATES] ?: true,
            searchEngineUrl = prefs[Keys.SEARCH_ENGINE] ?: LauncherSettings.DEFAULT_SEARCH_ENGINE,
            showWorkSummaryCard = prefs[Keys.CARD_WORK] ?: true,
            showAgendaCard = prefs[Keys.CARD_AGENDA] ?: true,
            showTasksCard = prefs[Keys.CARD_TASKS] ?: true,
            currencySymbol = prefs[Keys.CURRENCY] ?: "¥",
            llmBackend = prefs[Keys.LLM_BACKEND]
                ?.let { runCatching { LlmBackend.valueOf(it) }.getOrNull() }
                ?: LlmBackend.NONE,
            llmModelPath = prefs[Keys.LLM_MODEL_PATH].orEmpty(),
            llmEndpoint = prefs[Keys.LLM_ENDPOINT].orEmpty(),
            llmRemoteModel = prefs[Keys.LLM_REMOTE_MODEL].orEmpty(),
            llmSummarizeNotifications = prefs[Keys.LLM_NOTIFICATION_DIGEST] ?: false,
            llmApiEndpoint = prefs[Keys.LLM_API_ENDPOINT] ?: LauncherSettings.DEFAULT_API_ENDPOINT,
            llmApiModel = prefs[Keys.LLM_API_MODEL].orEmpty(),
            llmApiKey = prefs[Keys.LLM_API_KEY].orEmpty(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME_MODE, mode.name)
    suspend fun setDynamicColor(enabled: Boolean) = put(Keys.DYNAMIC_COLOR, enabled)
    suspend fun setGridColumns(columns: Int) = put(Keys.GRID_COLUMNS, columns.coerceIn(3, 7))
    suspend fun setShowAppLabels(show: Boolean) = put(Keys.SHOW_APP_LABELS, show)
    suspend fun setDrawerSort(sort: DrawerSort) = put(Keys.DRAWER_SORT, sort.name)
    suspend fun setUse24HourClock(use: Boolean) = put(Keys.CLOCK_24H, use)
    suspend fun setWorkStartMinute(minute: Int) = put(Keys.WORK_START, minute.coerceIn(0, 24 * 60))
    suspend fun setWorkEndMinute(minute: Int) = put(Keys.WORK_END, minute.coerceIn(0, 24 * 60))
    suspend fun setWorkDayMask(mask: Int) = put(Keys.WORK_DAYS, mask)
    suspend fun setStandardWorkMinutes(minutes: Int) = put(Keys.STANDARD_WORK_MINUTES, minutes.coerceIn(0, 24 * 60))
    suspend fun setDefaultBreakMinutes(minutes: Int) = put(Keys.DEFAULT_BREAK_MINUTES, minutes.coerceIn(0, 8 * 60))
    suspend fun setPomodoroFocusMinutes(minutes: Int) = put(Keys.POMODORO_FOCUS, minutes.coerceIn(1, 180))
    suspend fun setPomodoroShortBreakMinutes(minutes: Int) = put(Keys.POMODORO_SHORT, minutes.coerceIn(1, 60))
    suspend fun setPomodoroLongBreakMinutes(minutes: Int) = put(Keys.POMODORO_LONG, minutes.coerceIn(1, 120))
    suspend fun setPomodoroCycles(cycles: Int) = put(Keys.POMODORO_CYCLES, cycles.coerceIn(2, 12))
    suspend fun setFocusGatesDistractions(enabled: Boolean) = put(Keys.FOCUS_GATES, enabled)
    suspend fun setFocusVibrates(enabled: Boolean) = put(Keys.FOCUS_VIBRATES, enabled)
    suspend fun setSearchEngineUrl(url: String) = put(Keys.SEARCH_ENGINE, url)
    suspend fun setShowWorkSummaryCard(show: Boolean) = put(Keys.CARD_WORK, show)
    suspend fun setShowAgendaCard(show: Boolean) = put(Keys.CARD_AGENDA, show)
    suspend fun setShowTasksCard(show: Boolean) = put(Keys.CARD_TASKS, show)
    suspend fun setCurrencySymbol(symbol: String) = put(Keys.CURRENCY, symbol)
    suspend fun setLlmBackend(backend: LlmBackend) = put(Keys.LLM_BACKEND, backend.name)
    suspend fun setLlmModelPath(path: String) = put(Keys.LLM_MODEL_PATH, path.trim())
    suspend fun setLlmEndpoint(url: String) = put(Keys.LLM_ENDPOINT, url.trim())
    suspend fun setLlmRemoteModel(model: String) = put(Keys.LLM_REMOTE_MODEL, model.trim())
    suspend fun setLlmSummarizeNotifications(enabled: Boolean) =
        put(Keys.LLM_NOTIFICATION_DIGEST, enabled)
    suspend fun setLlmApiEndpoint(url: String) = put(Keys.LLM_API_ENDPOINT, url.trim())
    suspend fun setLlmApiModel(model: String) = put(Keys.LLM_API_MODEL, model.trim())
    suspend fun setLlmApiKey(key: String) = put(Keys.LLM_API_KEY, key.trim())

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SHOW_APP_LABELS = booleanPreferencesKey("show_app_labels")
        val DRAWER_SORT = stringPreferencesKey("drawer_sort")
        val CLOCK_24H = booleanPreferencesKey("clock_24h")
        val WORK_START = intPreferencesKey("work_start")
        val WORK_END = intPreferencesKey("work_end")
        val WORK_DAYS = intPreferencesKey("work_days")
        val STANDARD_WORK_MINUTES = intPreferencesKey("standard_work_minutes")
        val DEFAULT_BREAK_MINUTES = intPreferencesKey("default_break_minutes")
        val POMODORO_FOCUS = intPreferencesKey("pomodoro_focus")
        val POMODORO_SHORT = intPreferencesKey("pomodoro_short")
        val POMODORO_LONG = intPreferencesKey("pomodoro_long")
        val POMODORO_CYCLES = intPreferencesKey("pomodoro_cycles")
        val FOCUS_GATES = booleanPreferencesKey("focus_gates")
        val FOCUS_VIBRATES = booleanPreferencesKey("focus_vibrates")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val CARD_WORK = booleanPreferencesKey("card_work")
        val CARD_AGENDA = booleanPreferencesKey("card_agenda")
        val CARD_TASKS = booleanPreferencesKey("card_tasks")
        val CURRENCY = stringPreferencesKey("currency")
        val LLM_BACKEND = stringPreferencesKey("llm_backend")
        val LLM_MODEL_PATH = stringPreferencesKey("llm_model_path")
        val LLM_ENDPOINT = stringPreferencesKey("llm_endpoint")
        val LLM_REMOTE_MODEL = stringPreferencesKey("llm_remote_model")
        val LLM_NOTIFICATION_DIGEST = booleanPreferencesKey("llm_notification_digest")
        val LLM_API_ENDPOINT = stringPreferencesKey("llm_api_endpoint")
        val LLM_API_MODEL = stringPreferencesKey("llm_api_model")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
    }
}
