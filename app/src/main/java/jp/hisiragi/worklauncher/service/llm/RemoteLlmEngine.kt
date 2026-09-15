package jp.hisiragi.worklauncher.service.llm

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to an OpenAI-compatible `/v1/chat/completions` endpoint, which is what
 * Ollama and llama.cpp's server both speak. The user runs the server; nothing
 * here reaches the public internet unless they point it there.
 */
class RemoteLlmEngine(
    private val baseUrl: String,
    private val model: String,
) : LlmEngine {

    override val label: String = model.ifBlank { baseUrl }

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val endpoint = runCatching { URL(chatCompletionsUrl()) }
                .getOrElse { throw LlmUnavailableException("Invalid endpoint: $baseUrl") }

            val payload = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put("max_tokens", maxTokens)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", prompt)
                    ),
                )
            }

            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
            }

            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use(BufferedReader::readText)
                    .orEmpty()

                if (status !in 200..299) {
                    throw LlmUnavailableException("Server returned $status: ${body.take(200)}")
                }
                parseContent(body)
            } catch (e: LlmUnavailableException) {
                throw e
            } catch (e: Exception) {
                throw LlmUnavailableException(e.message ?: "Could not reach $baseUrl")
            } finally {
                connection.disconnect()
            }
        }

    private fun chatCompletionsUrl(): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/v1/chat/completions"
    }

    private fun parseContent(body: String): String {
        val json = runCatching { JSONObject(body) }
            .getOrElse { throw LlmUnavailableException("Unexpected response from the server") }
        val choices = json.optJSONArray("choices")
            ?: throw LlmUnavailableException("Response had no choices")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw LlmUnavailableException("Response had no message")
        return message.optString("content").ifBlank {
            throw LlmUnavailableException("The model returned nothing")
        }
    }

    /** Nothing to release: each call opens and closes its own connection. */
    override fun close() = Unit

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 120_000
    }
}
