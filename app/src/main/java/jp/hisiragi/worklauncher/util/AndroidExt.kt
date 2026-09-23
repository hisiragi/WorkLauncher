package jp.hisiragi.worklauncher.util

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import jp.hisiragi.worklauncher.R

/** Converts any launcher icon into something Compose can draw. */
fun Drawable.toImageBitmap(sizePx: Int = 128): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap.asImageBitmap()
    }
    val width = if (intrinsicWidth > 0) intrinsicWidth else sizePx
    val height = if (intrinsicHeight > 0) intrinsicHeight else sizePx
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

/** Starts [intent], reporting to the user instead of crashing when nothing handles it. */
fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    Toast.makeText(this, getString(R.string.error_no_handler), Toast.LENGTH_SHORT).show()
    false
} catch (e: SecurityException) {
    Toast.makeText(this, getString(R.string.error_no_handler), Toast.LENGTH_SHORT).show()
    false
}

object Launch {

    fun app(context: Context, packageName: String, activityName: String) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(packageName, activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (!context.startActivitySafely(intent)) {
            context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                context.startActivitySafely(it)
            }
        }
    }

    fun appInfo(context: Context, packageName: String) {
        context.startActivitySafely(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    fun uninstall(context: Context, packageName: String) {
        context.startActivitySafely(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
        )
    }

    fun webSearch(context: Context, engineUrl: String, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val url = jp.hisiragi.worklauncher.domain.SearchEngineHelper.buildSearchUrl(engineUrl, trimmed)
        context.startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun dial(context: Context, number: String) {
        context.startActivitySafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    fun sms(context: Context, number: String) {
        context.startActivitySafely(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    }

    fun email(context: Context, address: String) {
        context.startActivitySafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    }

    fun systemSettings(context: Context, action: String) {
        context.startActivitySafely(Intent(action))
    }

    fun homeSettings(context: Context) {
        context.startActivitySafely(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    fun usageAccessSettings(context: Context) {
        context.startActivitySafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun notificationPolicySettings(context: Context) {
        context.startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    fun url(context: Context, url: String) {
        context.startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun notificationListenerSettings(context: Context) {
        context.startActivitySafely(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        )
    }
}

object LauncherStatus {

    /** True when WorkLauncher is the activity the system resolves HOME to. */
    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Usage access is a special permission granted from Settings, not at runtime. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
