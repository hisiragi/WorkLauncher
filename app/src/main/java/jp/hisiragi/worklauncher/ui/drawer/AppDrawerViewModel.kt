package jp.hisiragi.worklauncher.ui.drawer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.domain.AppCategory
import jp.hisiragi.worklauncher.domain.AppOrdering
import jp.hisiragi.worklauncher.domain.DrawerSort
import jp.hisiragi.worklauncher.domain.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppDrawerUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val query: String = "",
    val categoryFilter: AppCategory? = null,
    val showHidden: Boolean = false,
    val apps: List<LauncherApp> = emptyList(),
    val totalCount: Int = 0,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
}

class AppDrawerViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<AppCategory?>(null)
    private val showHidden = MutableStateFlow(false)

    private val _selectedApp = MutableStateFlow<LauncherApp?>(null)
    val selectedApp: StateFlow<LauncherApp?> = _selectedApp.asStateFlow()

    val gatedApp: StateFlow<LauncherApp?> = container.appLauncher.gatedApp

    private val drawerSort: StateFlow<DrawerSort> = container.settingsRepository.settings
        .map { it.drawerSort }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerSort.ALPHABETICAL)

    /**
     * Ordering does not depend on the query, so the list is sorted only when the
     * apps or the chosen order change. The filtering below preserves that order,
     * which keeps typing off the sort path entirely.
     */
    private val sortedApps = combine(
        container.appRepository.apps,
        drawerSort,
    ) { apps, sort ->
        AppOrdering.sorted(apps, sort)
    }.flowOn(Dispatchers.Default)

    @OptIn(FlowPreview::class)
    private val debouncedQuery = query.debounce { if (it.isBlank()) 0L else QUERY_DEBOUNCE_MS }

    private val filters = combine(debouncedQuery, categoryFilter, showHidden) { q, category, hidden ->
        Triple(q, category, hidden)
    }

    val uiState: StateFlow<AppDrawerUiState> = combine(
        sortedApps,
        container.settingsRepository.settings,
        filters,
    ) { apps, settings, (q, category, includeHidden) ->
        var total = 0
        val filtered = apps.filter { app ->
            val visible = includeHidden || !app.hidden
            if (visible) total++
            visible &&
                (category == null || app.category == category) &&
                (q.isBlank() || app.label.contains(q, ignoreCase = true))
        }
        AppDrawerUiState(
            settings = settings,
            query = q,
            categoryFilter = category,
            showHidden = includeHidden,
            apps = filtered,
            totalCount = total,
        )
    }
        // Filtering a few hundred apps is not free, and the default would run it
        // on the main thread on every emission.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDrawerUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategoryFilter(category: AppCategory?) {
        categoryFilter.value = category
    }

    fun toggleShowHidden() {
        showHidden.value = !showHidden.value
    }

    fun setSort(sort: DrawerSort) {
        viewModelScope.launch { container.settingsRepository.setDrawerSort(sort) }
    }

    fun select(app: LauncherApp?) {
        _selectedApp.value = app
    }

    fun launch(context: Context, app: LauncherApp) {
        container.appLauncher.launch(context, app)
    }

    fun launchGatedApp(context: Context) {
        gatedApp.value?.let { container.appLauncher.launch(context, it, ignoreFocusGate = true) }
    }

    fun dismissGate() = container.appLauncher.dismissGate()

    fun toggleFavorite(app: LauncherApp) {
        viewModelScope.launch {
            container.appRepository.setFavorite(
                app = app,
                favorite = !app.favorite,
                dockOrder = if (app.favorite) 0 else nextDockOrder(),
            )
        }
    }

    fun toggleHidden(app: LauncherApp) {
        viewModelScope.launch { container.appRepository.setHidden(app, !app.hidden) }
    }

    fun toggleDistraction(app: LauncherApp) {
        viewModelScope.launch { container.appRepository.setDistraction(app, !app.distraction) }
    }

    fun setCategory(app: LauncherApp, category: AppCategory) {
        viewModelScope.launch { container.appRepository.setCategory(app, category) }
    }

    fun rename(app: LauncherApp, label: String?) {
        viewModelScope.launch { container.appRepository.setCustomLabel(app, label) }
    }

    fun webSearch(context: Context) {
        val q = query.value.trim()
        if (q.isEmpty()) return
        jp.hisiragi.worklauncher.util.Launch.webSearch(
            context,
            uiState.value.settings.searchEngineUrl,
            q,
        )
    }

    private fun nextDockOrder(): Int =
        (container.appRepository.apps.value.filter { it.favorite }.maxOfOrNull { it.dockOrder } ?: 0) + 1

    private companion object {
        /** Long enough to skip the intermediate states of a fast typist. */
        const val QUERY_DEBOUNCE_MS = 120L
    }
}
