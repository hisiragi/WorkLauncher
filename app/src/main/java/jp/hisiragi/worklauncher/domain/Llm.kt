package jp.hisiragi.worklauncher.domain

/** Where inference runs. [NONE] keeps every LLM-backed feature hidden. */
enum class LlmBackend {
    NONE,

    /** A model file the user installed, run in-process by MediaPipe. */
    ON_DEVICE,

    /** An OpenAI-compatible server the user runs themselves (Ollama, llama.cpp). */
    REMOTE;

    companion object {
        fun fromKey(key: String): LlmBackend =
            entries.firstOrNull { it.name == key } ?: NONE
    }
}

/** Why the LLM features are or are not usable right now. */
sealed interface LlmAvailability {
    data object Disabled : LlmAvailability
    data object Loading : LlmAvailability
    data class Ready(val backend: LlmBackend, val label: String) : LlmAvailability
    data class Unavailable(val reason: String) : LlmAvailability

    val isReady: Boolean get() = this is Ready
}

/** A single turn in a chat, kept in memory only. */
data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
)

/** Fields pulled out of a receipt photo, all optional. */
data class ReceiptDraft(
    val amount: Long? = null,
    val vendor: String? = null,
    val isoDate: String? = null,
    val category: ExpenseCategory? = null,
)
