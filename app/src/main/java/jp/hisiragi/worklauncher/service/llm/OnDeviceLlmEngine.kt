package jp.hisiragi.worklauncher.service.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import jp.hisiragi.worklauncher.domain.LlmModelCatalog
import jp.hisiragi.worklauncher.domain.ModelModality
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    override fun generateStream(prompt: String, maxTokens: Int): Flow<String> =
        stream(prompt, audio = null)

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        run(prompt, audio = null)

    /**
     * Passes a recording straight to the model. Only models built with the audio
     * modality accept this; [acceptsAudio] says which.
     */
    suspend fun generateWithAudio(prompt: String, audio: ByteArray): String =
        run(prompt, audio)

    fun streamWithAudio(prompt: String, audio: ByteArray): Flow<String> = stream(prompt, audio)

    /**
     * The native runtime reports partial results on its own thread, so this
     * bridges the callback into a flow and holds the mutex until it says done.
     */
    private fun stream(prompt: String, audio: ByteArray?): Flow<String> = flow {
        mutex.withLock {
            val engine = ensureLoaded()
            val session = newSession(engine, audio)
            try {
                session.addQueryChunk(prompt)
                audio?.let { session.addAudioOrThrow(it) }

                val chunks = Channel<String>(Channel.UNLIMITED)
                val future = runCatching {
                    session.generateResponseAsync { partial, done ->
                        // Partials are deltas already; the last one can be empty.
                        if (partial.isNotEmpty()) chunks.trySend(partial)
                        if (done) chunks.close()
                    }
                }.getOrElse {
                    chunks.close()
                    throw LlmUnavailableException(it.message ?: "Generation failed")
                }

                try {
                    for (chunk in chunks) emit(chunk)
                } finally {
                    // Cancelling the collector must stop the model, not leave it
                    // running against a session we are about to close.
                    if (!future.isDone) runCatching { session.cancelGenerateResponseAsync() }
                }
            } finally {
                runCatching { session.close() }
            }
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun run(prompt: String, audio: ByteArray?): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val engine = ensureLoaded()
                newSession(engine, audio).use { session ->
                    session.addQueryChunk(prompt)
                    audio?.let { session.addAudioOrThrow(it) }
                    runCatching { session.generateResponse() }
                        .getOrElse { throw LlmUnavailableException(it.message ?: "Generation failed") }
                }
            }
        }

    /** A fresh session per call keeps one feature's context out of another's. */
    private fun newSession(engine: LlmInference, audio: ByteArray?): LlmInferenceSession {
        val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTemperature(TEMPERATURE)
            .apply {
                if (audio != null) {
                    setGraphOptions(GraphOptions.builder().setEnableAudioModality(true).build())
                }
            }
            .build()
        return LlmInferenceSession.createFromOptions(engine, options)
    }

    private fun LlmInferenceSession.addAudioOrThrow(audio: ByteArray) {
        runCatching { addAudio(audio) }.getOrElse {
            throw LlmUnavailableException(it.message ?: "This model does not accept audio")
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
