package jp.hisiragi.worklauncher.core

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import jp.hisiragi.worklauncher.util.toImageBitmap
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes app icons on demand and keeps a bounded number of them.
 *
 * Loading every installed app's icon up front meant holding a few hundred
 * drawables for a grid that shows a couple of dozen at a time, which is most of
 * what the drawer cost in memory. Decoded icons are also written to disk: asking
 * the package manager for an icon and rasterising the drawable is the expensive
 * part, and without this every cold start pays it again for every app.
 */
class AppIconLoader(private val context: Context, maxEntries: Int = MAX_ENTRIES) {

    private val packageManager = context.packageManager
    private val memory = LruCache<String, ImageBitmap>(maxEntries)

    private val diskDir: File
        get() = File(context.cacheDir, DIR_NAME).apply { mkdirs() }

    fun cached(componentKey: String): ImageBitmap? = memory.get(componentKey)

    suspend fun load(packageName: String, activityName: String, sizePx: Int = ICON_PX): ImageBitmap? {
        val key = "$packageName/$activityName"
        memory.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            val file = fileFor(packageName, activityName)
            readFrom(file) ?: decode(packageName, activityName, sizePx)?.also { writeTo(file, it) }
        }

        if (bitmap != null) memory.put(key, bitmap)
        return bitmap
    }

    private fun decode(packageName: String, activityName: String, sizePx: Int): ImageBitmap? =
        runCatching {
            packageManager
                .getActivityIcon(ComponentName(packageName, activityName))
                .toImageBitmap(sizePx)
        }.recoverCatching {
            // Activity-level icons can be missing even when the app has one.
            packageManager.getApplicationIcon(packageName).toImageBitmap(sizePx)
        }.getOrNull()

    private fun readFrom(file: File): ImageBitmap? = runCatching {
        if (!file.exists()) return@runCatching null
        BitmapFactory.decodeFile(file.path)?.asImageBitmap()
    }.getOrNull()

    private fun writeTo(file: File, bitmap: ImageBitmap) {
        runCatching {
            file.outputStream().use { out ->
                bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    /**
     * Activity names are too long and too punctuated for a file name, so only
     * the package stays readable — which is what lets [invalidate] find the
     * files belonging to one app without consulting the in-memory cache.
     */
    private fun fileFor(packageName: String, activityName: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(activityName.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(diskDir, "$packageName-$digest.png")
    }

    /**
     * Dropped when packages change so a reinstalled app does not keep its old
     * icon. A named package only invalidates its own entries.
     */
    fun invalidate(packageName: String?) {
        if (packageName == null) {
            memory.evictAll()
            runCatching { diskDir.listFiles()?.forEach { it.delete() } }
            return
        }
        memory.snapshot().keys
            .filter { it.substringBefore('/') == packageName }
            .forEach { memory.remove(it) }
        runCatching {
            diskDir.listFiles { file -> file.name.startsWith("$packageName-") }
                ?.forEach { it.delete() }
        }
    }

    private companion object {
        const val DIR_NAME = "icon-cache"
        const val MAX_ENTRIES = 192
        const val ICON_PX = 128
    }
}
