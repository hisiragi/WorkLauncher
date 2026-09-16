package jp.hisiragi.worklauncher.service.llm

import android.content.Context
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.data.settings.SettingsRepository
import jp.hisiragi.worklauncher.domain.LlmAvailability
import jp.hisiragi.worklauncher.domain.LlmBackend
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the one engine the app talks to, rebuilding it when the user changes
 * backends. Every LLM-backed feature checks [availability] first and stays
 * hidden while it is [LlmAvailability.Disabled].
 */
class LlmManager(
    private val context: Context,
    settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var engine: LlmEngine? = null
    private var engineKey: String? = null

    /** Set when a call fails, so settings can show why rather than just failing. */
    private val lastError = MutableStateFlow<String?>(null)

    private val config: StateFlow<LlmConfig> = settingsRepository.settings
        .map(::LlmConfig)
        .stateIn(scope, SharingStarted.Eagerly, LlmConfig(LauncherSettings()))

    val availability: StateFlow<LlmAvailability> = config
        .map { it.availability() }
        .stateIn(scope, SharingStarted.Eagerly, LlmAvailability.Disabled)

    init {
        // A backend change invalidates the loaded model; drop it eagerly rather
        // than keeping a multi-gigabyte model resident for a backend nobody uses.
        scope.launch {
            config.collect { current ->
                mutex.withLock {
                    if (current.key != engineKey) {
                        engine?.close()
                        engine = null
                        engineKey = null
                        lastError.value = null
                    }
                }
            }
        }
    }

    val error: StateFlow<String?> get() = lastError

    /**
     * Runs [prompt], or returns null when no backend is configured. A failure
     * surfaces through [error] and returns null rather than throwing into the UI.
     */
    suspend fun generate(prompt: String, maxTokens: Int = LlmEngine.DEFAULT_MAX_TOKENS): String? {
        val current = config.value
        if (current.availability() !is LlmAvailability.Ready) return null
        return try {
            val active = mutex.withLock { engineFor(current) }
            active.generate(prompt, maxTokens).also { lastError.value = null }
        } catch (e: LlmUnavailableException) {
            lastError.value = e.message
            null
        }
    }

    /**
     * Streams an answer in pieces. Emits nothing when no backend is configured;
     * a failure surfaces through [error] and ends the flow rather than throwing
     * into the UI.
     */
    fun generateStream(
        prompt: String,
        maxTokens: Int = LlmEngine.DEFAULT_MAX_TOKENS,
    ): Flow<String> = flow {
        val current = config.value
        if (current.availability() !is LlmAvailability.Ready) return@flow
        val active = mutex.withLock { engineFor(current) }
        try {
            active.generateStream(prompt, maxTokens).collect { emit(it) }
            lastError.value = null
        } catch (e: LlmUnavailableException) {
            lastError.value = e.message
        }
    }

    /** True when the active engine takes a recording without transcribing it. */
    suspend fun acceptsAudio(): Boolean {
        val current = config.value
        if (current.availability() !is LlmAvailability.Ready) return false
        if (current.backend != LlmBackend.ON_DEVICE) return false
        return runCatching {
            mutex.withLock { engineFor(current) as? OnDeviceLlmEngine }?.acceptsAudio
        }.getOrNull() ?: false
    }

    /** Sends a recording to the model, or null when it cannot take one. */
    suspend fun generateWithAudio(prompt: String, audio: ByteArray): String? {
        val current = config.value
        if (current.availability() !is LlmAvailability.Ready) return null
        return try {
            val engine = mutex.withLock { engineFor(current) } as? OnDeviceLlmEngine
                ?: return null
            engine.generateWithAudio(prompt, audio).also { lastError.value = null }
        } catch (e: LlmUnavailableException) {
            lastError.value = e.message
            null
        }
    }

    private fun engineFor(current: LlmConfig): LlmEngine {
        engine?.takeIf { engineKey == current.key }?.let { return it }
        engine?.close()
        val created = when (current.backend) {
            LlmBackend.ON_DEVICE -> OnDeviceLlmEngine(context, current.modelPath)
            LlmBackend.REMOTE -> RemoteLlmEngine(current.endpoint, current.remoteModel)
            LlmBackend.API -> RemoteLlmEngine(
                baseUrl = current.apiEndpoint,
                model = current.apiModel,
                apiKey = current.apiKey,
            )
            LlmBackend.NONE -> throw LlmUnavailableException("No backend configured")
        }
        engine = created
        engineKey = current.key
        return created
    }

    private data class LlmConfig(
        val backend: LlmBackend,
        val modelPath: String,
        val endpoint: String,
        val remoteModel: String,
        val apiEndpoint: String,
        val apiModel: String,
        val apiKey: String,
    ) {
        constructor(settings: LauncherSettings) : this(
            backend = settings.llmBackend,
            modelPath = settings.llmModelPath,
            endpoint = settings.llmEndpoint,
            remoteModel = settings.llmRemoteModel,
            apiEndpoint = settings.llmApiEndpoint,
            apiModel = settings.llmApiModel,
            apiKey = settings.llmApiKey,
        )

        val key: String
            get() = "$backend|$modelPath|$endpoint|$remoteModel|$apiEndpoint|$apiModel|$apiKey"

        fun availability(): LlmAvailability = when (backend) {
            LlmBackend.NONE -> LlmAvailability.Disabled
            LlmBackend.ON_DEVICE -> when {
                modelPath.isBlank() -> LlmAvailability.Unavailable("No model file selected")
                !File(modelPath).exists() -> LlmAvailability.Unavailable("Model file is missing")
                else -> LlmAvailability.Ready(backend, File(modelPath).name)
            }
            LlmBackend.REMOTE -> when {
                endpoint.isBlank() -> LlmAvailability.Unavailable("No endpoint set")
                remoteModel.isBlank() -> LlmAvailability.Unavailable("No model name set")
                else -> LlmAvailability.Ready(backend, remoteModel)
            }
            LlmBackend.API -> when {
                apiEndpoint.isBlank() -> LlmAvailability.Unavailable("No endpoint set")
                apiKey.isBlank() -> LlmAvailability.Unavailable("No API key set")
                apiModel.isBlank() -> LlmAvailability.Unavailable("No model name set")
                else -> LlmAvailability.Ready(backend, apiModel)
            }
        }
    }
}
