package jp.hisiragi.worklauncher.data.repo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Receipt photos live in app-private storage; only the file name is kept in the
 * database so the row stays valid if the app's data directory moves.
 */
class ReceiptStore(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun fileFor(name: String): File = File(directory, name)

    /** A destination for the camera to write into, and the name to persist. */
    fun newCaptureTarget(): Pair<String, Uri> {
        val name = "receipt_${UUID.randomUUID()}.jpg"
        // The manifest registers this authority as "${applicationId}.fileprovider",
        // and packageName is the applicationId at runtime, suffix included.
        val authority = "${context.packageName}.fileprovider"
        return name to FileProvider.getUriForFile(context, authority, fileFor(name))
    }

    /** Copies a gallery pick into private storage, returning the stored name. */
    suspend fun importFrom(source: Uri): String? = withContext(Dispatchers.IO) {
        val name = "receipt_${UUID.randomUUID()}.jpg"
        runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                fileFor(name).outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            name
        }.getOrNull()
    }

    suspend fun delete(name: String?) = withContext(Dispatchers.IO) {
        if (name.isNullOrBlank()) return@withContext
        runCatching { fileFor(name).delete() }
    }

    /** Drops a capture target the user abandoned, so empty files do not pile up. */
    suspend fun deleteIfEmpty(name: String) = withContext(Dispatchers.IO) {
        val file = fileFor(name)
        if (file.exists() && file.length() == 0L) file.delete()
    }

    private companion object {
        const val DIR_NAME = "receipts"
    }
}
