package jp.hisiragi.worklauncher.data.repo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.core.content.ContextCompat
import jp.hisiragi.worklauncher.data.db.AppCacheDao
import jp.hisiragi.worklauncher.data.db.AppMetaDao
import jp.hisiragi.worklauncher.data.db.AppMetaEntity
import jp.hisiragi.worklauncher.data.db.CachedActivityEntity
import jp.hisiragi.worklauncher.domain.AppCategory
import jp.hisiragi.worklauncher.domain.AppOrdering
import jp.hisiragi.worklauncher.domain.LauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridges the package manager's launchable activities with the launcher's own
 * per-app metadata (category, dock position, hidden/distraction flags).
 */
class AppRepository(
    private val context: Context,
    private val appMetaDao: AppMetaDao,
    private val appCacheDao: AppCacheDao,
    private val scope: CoroutineScope,
    /** Called with the changed package, or null when the whole list is suspect. */
    private val onPackagesChanged: (String?) -> Unit = {},
) {
    private val packageManager: PackageManager = context.packageManager

    /** Raw activity list, refreshed when packages are installed or removed. */
    private val installed = MutableStateFlow<List<InstalledActivity>>(emptyList())

    val apps: StateFlow<List<LauncherApp>> =
        combine(installed, appMetaDao.observeAll()) { activities, metaList ->
            val metaByKey = metaList.associateBy { it.componentKey }
            activities.map { activity ->
                val meta = metaByKey[activity.componentKey]
                LauncherApp(
                    packageName = activity.packageName,
                    activityName = activity.activityName,
                    label = meta?.customLabel?.takeIf { it.isNotBlank() } ?: activity.label,
                    // A category the user picked wins; otherwise fall back to the guess.
                    category = meta?.category
                        ?.let(AppCategory::fromKey)
                        ?.takeIf { it != AppCategory.UNSORTED }
                        ?: activity.autoCategory,
                    hidden = meta?.hidden ?: false,
                    favorite = meta?.favorite ?: false,
                    dockOrder = meta?.dockOrder ?: 0,
                    distraction = meta?.distraction ?: false,
                    launchCount = meta?.launchCount ?: 0,
                    lastLaunchedAt = meta?.lastLaunchedAt ?: 0,
                    isSystemApp = activity.isSystemApp,
                )
            }.let(AppOrdering::sorted)
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onPackagesChanged(intent?.data?.schemeSpecificPart)
            scope.launch { refresh() }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            seedFromCache()
            refresh()
        }
    }

    /**
     * Shows the last known list straight away. Querying the package manager and
     * loading a label for every app takes long enough to leave the drawer empty
     * on a cold start, and the answer is almost always what it was last time.
     */
    private suspend fun seedFromCache() = withContext(Dispatchers.IO) {
        if (installed.value.isNotEmpty()) return@withContext
        val cached = runCatching { appCacheDao.listAll() }.getOrDefault(emptyList())
        if (cached.isEmpty()) return@withContext
        installed.value = cached.map { entry ->
            InstalledActivity(
                packageName = entry.packageName,
                activityName = entry.activityName,
                label = entry.label,
                isSystemApp = entry.isSystemApp,
                autoCategory = AppCategory.fromKey(entry.autoCategory),
            )
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos: List<ResolveInfo> = runCatching {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrElse { emptyList() }

        val fresh = resolveInfos.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            // The launcher never lists itself: tapping it from the drawer is a no-op.
            if (activityInfo.packageName == context.packageName) return@mapNotNull null
            val appInfo = activityInfo.applicationInfo
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            InstalledActivity(
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                label = runCatching { info.loadLabel(packageManager).toString() }
                    .getOrElse { activityInfo.packageName },
                isSystemApp = isSystemApp,
                autoCategory = AppCategorizer.categorize(
                    packageName = activityInfo.packageName,
                    systemCategory = appInfo.category,
                    isSystemApp = isSystemApp,
                ),
            )
        }.distinctBy { it.componentKey }

        if (fresh == installed.value) return@withContext
        installed.value = fresh
        runCatching {
            appCacheDao.replaceAll(
                fresh.map { activity ->
                    CachedActivityEntity(
                        componentKey = activity.componentKey,
                        packageName = activity.packageName,
                        activityName = activity.activityName,
                        label = activity.label,
                        isSystemApp = activity.isSystemApp,
                        autoCategory = activity.autoCategory.name,
                    )
                }
            )
        }
    }

    suspend fun recordLaunch(app: LauncherApp) {
        val existing = appMetaDao.find(app.componentKey)
        val base = existing ?: AppMetaEntity(componentKey = app.componentKey)
        appMetaDao.upsert(
            base.copy(
                launchCount = base.launchCount + 1,
                lastLaunchedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setFavorite(app: LauncherApp, favorite: Boolean, dockOrder: Int = app.dockOrder) {
        mutate(app.componentKey) { it.copy(favorite = favorite, dockOrder = dockOrder) }
    }

    /**
     * Renumbers the dock from a full ordered list. Rewriting every position
     * rather than nudging one keeps the order dense, so removing and re-adding
     * apps cannot leave several of them sharing a position.
     */
    suspend fun setDockOrder(componentKeys: List<String>) {
        componentKeys.forEachIndexed { index, key ->
            mutate(key) { it.copy(favorite = true, dockOrder = index) }
        }
    }

    suspend fun setHidden(app: LauncherApp, hidden: Boolean) {
        mutate(app.componentKey) { it.copy(hidden = hidden) }
    }

    suspend fun setDistraction(app: LauncherApp, distraction: Boolean) {
        mutate(app.componentKey) { it.copy(distraction = distraction) }
    }

    suspend fun setCategory(app: LauncherApp, category: AppCategory) {
        mutate(app.componentKey) { it.copy(category = category.name) }
    }

    suspend fun setCustomLabel(app: LauncherApp, label: String?) {
        mutate(app.componentKey) { it.copy(customLabel = label?.takeIf { l -> l.isNotBlank() }) }
    }

    private suspend fun mutate(key: String, transform: (AppMetaEntity) -> AppMetaEntity) {
        val existing = appMetaDao.find(key) ?: AppMetaEntity(componentKey = key)
        appMetaDao.upsert(transform(existing))
    }

    /** Compared wholesale to decide whether the cache needs rewriting. */
    private data class InstalledActivity(
        val packageName: String,
        val activityName: String,
        val label: String,
        val isSystemApp: Boolean,
        val autoCategory: AppCategory,
    ) {
        val componentKey: String get() = "$packageName/$activityName"
    }
}
