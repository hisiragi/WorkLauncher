package jp.hisiragi.worklauncher.service.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

/**
 * A text-in, text-out model. Both backends are slow and single-threaded from the
 * caller's point of view, so every call suspends and runs off the main thread.
 */
interface LlmEngine : AutoCloseable {

    /** Human-readable name of whatever is actually answering. */
    val label: String

    /**
     * Emits the answer in pieces as the model produces them. Each value is a
     * delta, not the running total, so callers append rather than replace.
     */
    fun generateStream(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): Flow<String>

    /** The whole answer at once, for callers with nothing to show in between. */
    suspend fun generate(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String =
        generateStream(prompt, maxTokens).toList().joinToString("")

    companion object {
        const val DEFAULT_MAX_TOKENS = 512
    }
}

/** Thrown with a message meant to be shown to the user as-is. */
class LlmUnavailableException(message: String) : Exception(message)
