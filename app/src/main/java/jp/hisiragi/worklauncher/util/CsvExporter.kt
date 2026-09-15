package jp.hisiragi.worklauncher.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes a CSV into the cache and hands it to the system share sheet. */
object CsvExporter {

    /** Excel only reads a UTF-8 CSV correctly when it starts with a BOM. */
    private const val BOM = "\uFEFF"

    suspend fun share(
        context: Context,
        fileName: String,
        header: List<String>,
        rows: List<List<String>>,
        subject: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val file = runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            File(dir, fileName).apply {
                writeText(BOM + buildCsv(header, rows))
            }
        }.getOrElse { return@withContext false }

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { return@withContext false }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            context.startActivitySafely(Intent.createChooser(intent, subject))
        }
    }

    private fun buildCsv(header: List<String>, rows: List<List<String>>): String =
        buildString {
            appendLine(header.joinToString(",") { escape(it) })
            rows.forEach { row -> appendLine(row.joinToString(",") { escape(it) }) }
        }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
