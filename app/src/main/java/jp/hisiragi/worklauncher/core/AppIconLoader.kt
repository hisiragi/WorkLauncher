package jp.hisiragi.worklauncher.core

import android.content.ComponentName
import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import jp.hisiragi.worklauncher.util.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes app icons on demand and keeps a bounded number of them.
 *
 * Loading every installed app's icon up front meant holding a few hundred
 * drawables for a grid that shows a couple of dozen at a time, which is most of
 * what the drawer cost in memory.
 */
class AppIconLoader(private val context: Context, maxEntries: Int = MAX_ENTRIES) {

    private val packageManager = context.packageManager
    private val cache = LruCache<String, ImageBitmap>(maxEntries)

    fun cached(componentKey: String): ImageBitmap? = cache.get(componentKey)

    suspend fun load(packageName: String, activityName: String, sizePx: Int = ICON_PX): ImageBitmap? {
        val key = "$packageName/$activityName"
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                packageManager
                    .getActivityIcon(ComponentName(packageName, activityName))
                    .toImageBitmap(sizePx)
            }.recoverCatching {
                // Activity-level icons can be missing even when the app has one.
                packageManager.getApplicationIcon(packageName).toImageBitmap(sizePx)
            }.getOrNull()
        }

        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /** Dropped when packages change so a reinstalled app does not keep its old icon. */
    fun clear() = cache.evictAll()

    private companion object {
        const val MAX_ENTRIES = 192
        const val ICON_PX = 128
    }
}
