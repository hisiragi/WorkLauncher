package jp.hisiragi.worklauncher.service.llm

import android.content.Context
import jp.hisiragi.worklauncher.domain.CatalogModel
import jp.hisiragi.worklauncher.domain.ModelDownloadState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fetches model files into app-private storage. These are gigabytes over a
 * connection that will drop, so a partial file is kept and resumed with a Range
 * request rather than restarted.
 */
class ModelDownloader(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private var job: Job? = null

    val directory: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun fileFor(model: CatalogModel): File = File(directory, model.fileName)

    private fun partFor(model: CatalogModel): File = File(directory, model.fileName + ".part")

    fun isInstalled(model: CatalogModel): Boolean {
        val file = fileFor(model)
        // A truncated file would fail deep inside the native loader with an
        // unhelpful error, so treat only a full-length file as installed.
        return file.exists() && file.length() == model.sizeBytes
    }

    fun installedModels(): List<File> =
        directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()

    fun freeBytes(): Long = runCatching { directory.usableSpace }.getOrDefault(0L)

    /** True when there is room for the whole file plus a little headroom. */
    fun hasRoomFor(model: CatalogModel): Boolean =
        freeBytes() > model.sizeBytes - partFor(model).length() + HEADROOM_BYTES

    fun start(model: CatalogModel) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                download(model)
                _state.value = ModelDownloadState.Idle
            } catch (e: CancellationException) {
                _state.value = ModelDownloadState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = ModelDownloadState.Failed(
                    model.id,
                    e.message ?: "Download failed",
                )
            }
        }
    }

    /** Leaves the partial file in place so the next start resumes it. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    fun delete(model: CatalogModel) {
        cancelIfDownloading(model)
        runCatching { fileFor(model).delete() }
        runCatching { partFor(model).delete() }
    }

    private fun cancelIfDownloading(model: CatalogModel) {
        val running = _state.value
        if (running is ModelDownloadState.Running && running.modelId == model.id) cancel()
    }

    private suspend fun download(model: CatalogModel) = withContext(Dispatchers.IO) {
        val target = fileFor(model)
        if (target.exists() && target.length() == model.sizeBytes) return@withContext

        val part = partFor(model)
        var downloaded = if (part.exists()) part.length() else 0L
        if (downloaded > model.sizeBytes) {
            // The remote file changed under us; start over rather than resume.
            part.delete()
            downloaded = 0L
        }

        _state.value = ModelDownloadState.Running(model.id, downloaded, model.sizeBytes)

        val connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Server returned $status")
            }
            // A server that ignored the Range header restarts the body at zero.
            val resuming = status == HttpURLConnection.HTTP_PARTIAL
            if (!resuming) downloaded = 0L

            connection.inputStream.use { input ->
                java.io.FileOutputStream(part, resuming).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var sinceReport = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        sinceReport += read
                        if (sinceReport >= REPORT_EVERY_BYTES) {
                            sinceReport = 0L
                            _state.value =
                                ModelDownloadState.Running(model.id, downloaded, model.sizeBytes)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        if (part.length() != model.sizeBytes) {
            throw IllegalStateException("Incomplete download, will resume next time")
        }
        if (!part.renameTo(target)) {
            throw IllegalStateException("Could not move the downloaded file into place")
        }
    }

    private companion object {
        const val DIR_NAME = "models"
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
        const val BUFFER_BYTES = 1 shl 16
        const val REPORT_EVERY_BYTES = 4L * 1024 * 1024
        const val HEADROOM_BYTES = 256L * 1024 * 1024
    }
}
