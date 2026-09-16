package jp.hisiragi.worklauncher.core

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import jp.hisiragi.worklauncher.data.db.HomeWidgetDao
import jp.hisiragi.worklauncher.data.db.HomeWidgetEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Owns the launcher's [AppWidgetHost]. Hosting widgets means holding ids that
 * outlive the process, so every id handed out by [allocateId] must either end up
 * in the database or be released with [releaseId] — a leaked id keeps the
 * provider updating a view nothing displays.
 */
class WidgetHostController(
    private val context: Context,
    private val dao: HomeWidgetDao,
) {
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)

    private var listening = false

    val widgets: Flow<List<HomeWidgetEntity>> = dao.observeAll()

    fun startListening() {
        if (listening) return
        // Throws if the host was never given a chance to bind; a launcher that
        // cannot listen should still show the rest of the home screen.
        runCatching { host.startListening() }.onSuccess { listening = true }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    fun installedProviders(): List<AppWidgetProviderInfo> =
        runCatching { appWidgetManager.installedProviders }
            .getOrElse { emptyList() }
            .sortedBy { it.loadLabel(context.packageManager).orEmpty().lowercase() }

    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { appWidgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun releaseId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** True when the widget is already bound and no consent prompt is needed. */
    fun bindIfAllowed(appWidgetId: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)
        }.getOrDefault(false)

    /** The consent intent to launch when [bindIfAllowed] returns false. */
    fun bindPermissionIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        }

    /** Null when the provider has no configuration screen. */
    fun configureIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent? =
        info.configure?.let { component ->
            Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                this.component = component
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        }

    fun createView(hostContext: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = providerInfo(appWidgetId) ?: return null
        return runCatching { host.createView(hostContext, appWidgetId, info) }.getOrNull()
    }

    /** Tells the provider how much room it got, so it can pick a layout. */
    fun resize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        runCatching {
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            view.updateAppWidgetOptions(options)
        }
    }

    /** Adds to an existing stack, or starts a new one when [stackId] is null. */
    suspend fun add(appWidgetId: Int, stackId: String?, heightDp: Int = DEFAULT_HEIGHT_DP) {
        val targetStack = stackId ?: UUID.randomUUID().toString()
        dao.upsert(
            HomeWidgetEntity(
                appWidgetId = appWidgetId,
                stackId = targetStack,
                position = dao.nextPosition(targetStack),
                stackOrder = if (stackId == null) {
                    dao.nextStackOrder()
                } else {
                    dao.listStack(targetStack).firstOrNull()?.stackOrder ?: dao.nextStackOrder()
                },
                heightDp = heightDp,
            )
        )
    }

    suspend fun remove(appWidgetId: Int) {
        dao.delete(appWidgetId)
        releaseId(appWidgetId)
    }

    /** Default widget height, honouring what the provider asks for. */
    fun defaultHeightDp(info: AppWidgetProviderInfo): Int {
        val minHeightPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            maxOf(info.minHeight, info.maxResizeHeight.takeIf { it > 0 } ?: info.minHeight)
        } else {
            info.minHeight
        }
        val density = context.resources.displayMetrics.density
        val dp = (minHeightPx / density).toInt()
        return dp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
    }

    fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    companion object {
        /** Stable across launches so the system keeps our widget bindings. */
        private const val HOST_ID = 0x5748
        const val DEFAULT_HEIGHT_DP = 180
        const val MIN_HEIGHT_DP = 80
        const val MAX_HEIGHT_DP = 420
    }
}
