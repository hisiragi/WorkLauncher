package jp.hisiragi.worklauncher.ui.assistant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.domain.ChatMessage
import jp.hisiragi.worklauncher.domain.LlmAvailability
import jp.hisiragi.worklauncher.service.NotificationCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val thinking: Boolean = false,
    val digest: String? = null,
    val digestRunning: Boolean = false,
    val notificationCount: Int = 0,
    val hasNotificationAccess: Boolean = false,
)

class AssistantViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
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
        if (trimmed.isEmpty() || _uiState.value.thinking) return

        val history = _uiState.value.messages + ChatMessage(fromUser = true, text = trimmed)
        _uiState.value = _uiState.value.copy(messages = history, thinking = true)

        viewModelScope.launch {
            val reply = container.llmManager.generate(promptFor(history))
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
        appendLine("You are a concise assistant inside a launcher app for working professionals.")
        appendLine("Answer in the language the user writes in. Keep answers short.")
        appendLine()
        history.takeLast(MAX_TURNS).forEach { message ->
            appendLine(if (message.fromUser) "User: ${message.text}" else "Assistant: ${message.text}")
        }
        append("Assistant:")
    }

    private companion object {
        const val MAX_TURNS = 12
    }
}
