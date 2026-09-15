package jp.hisiragi.worklauncher.service.llm

import android.content.Context
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.data.settings.SettingsRepository
import jp.hisiragi.worklauncher.domain.LlmAvailability
import jp.hisiragi.worklauncher.domain.LlmBackend
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun engineFor(current: LlmConfig): LlmEngine {
        engine?.takeIf { engineKey == current.key }?.let { return it }
        engine?.close()
        val created = when (current.backend) {
            LlmBackend.ON_DEVICE -> OnDeviceLlmEngine(context, current.modelPath)
            LlmBackend.REMOTE -> RemoteLlmEngine(current.endpoint, current.remoteModel)
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
    ) {
        constructor(settings: LauncherSettings) : this(
            backend = settings.llmBackend,
            modelPath = settings.llmModelPath,
            endpoint = settings.llmEndpoint,
            remoteModel = settings.llmRemoteModel,
        )

        val key: String get() = "$backend|$modelPath|$endpoint|$remoteModel"

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
        }
    }
}
