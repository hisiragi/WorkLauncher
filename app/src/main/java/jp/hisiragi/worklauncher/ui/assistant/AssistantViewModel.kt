package jp.hisiragi.worklauncher.ui.assistant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.domain.ChatMessage
import jp.hisiragi.worklauncher.domain.ChatSource
import jp.hisiragi.worklauncher.domain.LlmAvailability
import jp.hisiragi.worklauncher.service.NotificationCollector
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val thinking: Boolean = false,
    val searchEnabled: Boolean = true,
    val streaming: Boolean = false,
    val recording: Boolean = false,
    val voiceAvailable: Boolean = false,
    val modelTakesAudio: Boolean = false,
    val digest: String? = null,
    val digestRunning: Boolean = false,
    val notificationCount: Int = 0,
    val hasNotificationAccess: Boolean = false,
)

class AssistantViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())

    /** The in-flight answer, so a new question or Stop can cancel it. */
    private var generation: Job? = null
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    val availability: StateFlow<LlmAvailability> = container.llmManager.availability
    val error: StateFlow<String?> = container.llmManager.error

    init {
        viewModelScope.launch {
            NotificationCollector.notifications.collect { notifications ->
                _uiState.value = _uiState.value.copy(notificationCount = notifications.size)
            }
        }
    }

    fun refreshNotificationAccess(context: Context) {
        _uiState.value = _uiState.value.copy(
            hasNotificationAccess = NotificationCollector.hasAccess(context)
        )
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.thinking || _uiState.value.streaming) return

        val history = _uiState.value.messages + ChatMessage(fromUser = true, text = trimmed)
        val useSearch = _uiState.value.searchEnabled
        _uiState.value = _uiState.value.copy(messages = history, thinking = true)

        generation?.cancel()
        generation = viewModelScope.launch {
            val plan = container.searchSkill.plan(promptFor(history), useSearch)

            // The empty reply is appended first so the bubble is on screen and
            // grows as deltas arrive, rather than appearing fully formed at the end.
            val placeholder = ChatMessage(
                fromUser = false,
                text = "",
                searchQuery = plan.query,
                sources = plan.sources.map { ChatSource(it.title, it.url) },
            )
            _uiState.value = _uiState.value.copy(
                messages = history + placeholder,
                thinking = false,
                streaming = true,
            )

            val answer = StringBuilder()
            container.llmManager.generateStream(plan.prompt, plan.maxTokens).collect { delta ->
                answer.append(delta)
                replaceLastAnswer(placeholder.copy(text = answer.toString()))
            }

            replaceLastAnswer(placeholder.copy(text = answer.toString().trim()))
            _uiState.value = _uiState.value.copy(streaming = false)
        }
    }

    /** Swaps the reply bubble in place while it fills in. */
    private fun replaceLastAnswer(message: ChatMessage) {
        val messages = _uiState.value.messages
        if (messages.isEmpty() || messages.last().fromUser) return
        _uiState.value = _uiState.value.copy(
            messages = messages.dropLast(1) + message,
        )
    }

    /** Stops an answer mid-flow, keeping whatever has arrived. */
    fun stopGenerating() {
        generation?.cancel()
        generation = null
        _uiState.value = _uiState.value.copy(thinking = false, streaming = false)
    }

    fun setSearchEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(searchEnabled = enabled)
    }

    fun refreshVoiceSupport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                voiceAvailable = container.voiceInput.hasMicPermission(),
                modelTakesAudio = container.llmManager.acceptsAudio(),
            )
        }
    }

    /**
     * An audio-capable model hears the recording itself; anything else needs the
     * system recognizer to turn it into text first.
     */
    fun startVoice() {
        if (_uiState.value.recording || _uiState.value.thinking) return
        _uiState.value = _uiState.value.copy(recording = true)

        viewModelScope.launch {
            if (_uiState.value.modelTakesAudio) {
                val audio = container.voiceInput.record { !_uiState.value.recording }
                _uiState.value = _uiState.value.copy(recording = false)
                if (audio != null) sendAudio(audio)
            } else {
                val text = container.voiceInput.transcribe(Locale.getDefault().toLanguageTag())
                _uiState.value = _uiState.value.copy(recording = false)
                if (!text.isNullOrBlank()) send(text)
            }
        }
    }

    /** Ends an in-progress recording; the pass above picks the audio up. */
    fun stopVoice() {
        _uiState.value = _uiState.value.copy(recording = false)
    }

    private fun sendAudio(audio: ByteArray) {
        val history = _uiState.value.messages +
            ChatMessage(fromUser = true, text = VOICE_PLACEHOLDER)
        _uiState.value = _uiState.value.copy(messages = history, thinking = true)

        viewModelScope.launch {
            val reply = container.llmManager.generateWithAudio(AUDIO_PROMPT, audio)
            _uiState.value = _uiState.value.copy(
                messages = if (reply != null) {
                    history + ChatMessage(fromUser = false, text = reply.trim())
                } else {
                    history
                },
                thinking = false,
            )
        }
    }

    fun clearChat() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }

    fun summarizeNotifications() {
        if (_uiState.value.digestRunning) return
        _uiState.value = _uiState.value.copy(digestRunning = true)
        viewModelScope.launch {
            val summary = container.notificationDigest
                .summarize(NotificationCollector.notifications.value)
            _uiState.value = _uiState.value.copy(digest = summary, digestRunning = false)
        }
    }

    /**
     * The engines are stateless between calls, so the running conversation is
     * replayed as a single prompt rather than kept in a native session.
     */
    private fun promptFor(history: List<ChatMessage>): String = buildString {
        history.takeLast(MAX_TURNS).forEach { message ->
            appendLine(if (message.fromUser) "User: ${message.text}" else "Assistant: ${message.text}")
        }
        append("Assistant:")
    }

    private companion object {
        const val MAX_TURNS = 12
        const val VOICE_PLACEHOLDER = "\uD83C\uDFA4"
        const val AUDIO_PROMPT =
            "Listen to the recording and answer the speaker concisely, in their language."
    }
}
