package jp.hisiragi.worklauncher.service.llm

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to an OpenAI-compatible `/v1/chat/completions` endpoint. Ollama and
 * llama.cpp's server speak it for a model the user runs themselves, and so do
 * the hosted providers once [apiKey] is set.
 */
class RemoteLlmEngine(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String = "",
) : LlmEngine {

    override val label: String = model.ifBlank { baseUrl }

    override fun generateStream(prompt: String, maxTokens: Int): Flow<String> = flow {
        val connection = openConnection(prompt, maxTokens, stream = true)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw errorFor(connection, status)

            connection.inputStream.bufferedReader().use { reader ->
                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    val delta = deltaFromEventLine(line) ?: continue
                    if (delta.isNotEmpty()) emit(delta)
                }
            }
        } catch (e: LlmUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw LlmUnavailableException(e.message ?: "Could not reach $baseUrl")
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Server-sent events arrive as `data: {...}` lines with a `[DONE]` sentinel.
     * Returns null for keep-alives, blank lines and anything unparseable.
     */
    private fun deltaFromEventLine(line: String): String? {
        if (!line.startsWith(DATA_PREFIX)) return null
        val payload = line.removePrefix(DATA_PREFIX).trim()
        if (payload.isEmpty() || payload == DONE) return null
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        return json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content")
            .orEmpty()
    }

    private fun openConnection(prompt: String, maxTokens: Int, stream: Boolean): HttpURLConnection {
        val endpoint = runCatching { URL(chatCompletionsUrl()) }
            .getOrElse { throw LlmUnavailableException("Invalid endpoint: $baseUrl") }

        val payload = JSONObject().apply {
            put("model", model)
            put("stream", stream)
            put("max_tokens", maxTokens)
            put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
            )
        }

        return (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            outputStream.use { it.write(payload.toString().toByteArray()) }
        }
    }

    private fun errorFor(connection: HttpURLConnection, status: Int): LlmUnavailableException {
        val body = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        // The key is the usual culprit, and the raw body rarely says so plainly.
        val hint = when (status) {
            401, 403 -> if (apiKey.isBlank()) "an API key is required" else "the API key was rejected"
            429 -> "rate limited"
            else -> body.take(200)
        }
        return LlmUnavailableException("Server returned $status: $hint")
    }

    /** Nothing to release: each call opens and closes its own connection. */
    override fun close() = Unit

    private fun chatCompletionsUrl(): String {
        val trimmed = baseUrl.trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 120_000
        const val DATA_PREFIX = "data:"
        const val DONE = "[DONE]"
    }
}
