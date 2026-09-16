package jp.hisiragi.worklauncher.service.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import jp.hisiragi.worklauncher.domain.LlmModelCatalog
import jp.hisiragi.worklauncher.domain.ModelModality
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs a model file the user installed themselves. The native runtime holds one
 * model in memory and tolerates a single generation at a time, so calls are
 * serialised and the model is loaded lazily on first use.
 */
class OnDeviceLlmEngine(
    private val context: Context,
    private val modelPath: String,
) : LlmEngine {

    override val label: String = File(modelPath).name

    private val mutex = Mutex()
    private var inference: LlmInference? = null

    private fun ensureLoaded(): LlmInference = inference ?: run {
        val file = File(modelPath)
        if (!file.exists()) {
            throw LlmUnavailableException("Model file not found: $modelPath")
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_CONTEXT_TOKENS)
            .build()
        val created = runCatching { LlmInference.createFromOptions(context, options) }
            .getOrElse { throw LlmUnavailableException(it.message ?: "Could not load the model") }
        inference = created
        created
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        run(prompt, audio = null)

    /**
     * Passes a recording straight to the model. Only models built with the audio
     * modality accept this; [acceptsAudio] says which.
     */
    suspend fun generateWithAudio(prompt: String, audio: ByteArray): String =
        run(prompt, audio)

    private suspend fun run(prompt: String, audio: ByteArray?): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val engine = ensureLoaded()
                // A fresh session per call keeps one feature's context out of
                // another's; the loaded model itself is reused.
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .apply {
                        if (audio != null) {
                            setGraphOptions(
                                GraphOptions.builder().setEnableAudioModality(true).build()
                            )
                        }
                    }
                    .build()
                LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
                    session.addQueryChunk(prompt)
                    if (audio != null) {
                        runCatching { session.addAudio(audio) }.getOrElse {
                            throw LlmUnavailableException(
                                it.message ?: "This model does not accept audio"
                            )
                        }
                    }
                    runCatching { session.generateResponse() }
                        .getOrElse { throw LlmUnavailableException(it.message ?: "Generation failed") }
                }
            }
        }

    /** True when the installed file is a build that takes audio input. */
    val acceptsAudio: Boolean
        get() = LlmModelCatalog.byFileName(File(modelPath).name)?.modality == ModelModality.AUDIO

    override fun close() {
        runCatching { inference?.close() }
        inference = null
    }

    private companion object {
        const val MAX_CONTEXT_TOKENS = 2048
        const val TOP_K = 40
        const val TEMPERATURE = 0.4f
    }
}
