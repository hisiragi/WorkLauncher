package jp.hisiragi.worklauncher.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a receipt photo down to roughly [maxPx] on its long edge. Receipts are
 * full-resolution camera shots, so decoding one at native size would be several
 * times the size of a screen-sized bitmap.
 */
@Composable
fun rememberReceiptThumbnail(file: File, maxPx: Int): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, file.path, file.lastModified(), maxPx) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeSampled(file, maxPx) }.getOrNull()
        }
    }

private fun decodeSampled(file: File, maxPx: Int): ImageBitmap? {
    if (!file.exists() || file.length() == 0L) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longEdge = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longEdge / (sample * 2) >= maxPx) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}
