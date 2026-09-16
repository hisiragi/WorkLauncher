package jp.hisiragi.worklauncher.service.llm

/**
 * A text-in, text-out model. Both backends are slow and single-threaded from the
 * caller's point of view, so every call suspends and runs off the main thread.
 */
interface LlmEngine : AutoCloseable {

    /** Human-readable name of whatever is actually answering. */
    val label: String

    suspend fun generate(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String

    companion object {
        const val DEFAULT_MAX_TOKENS = 512
    }
}

/** Thrown with a message meant to be shown to the user as-is. */
class LlmUnavailableException(message: String) : Exception(message)
