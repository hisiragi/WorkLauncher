package jp.hisiragi.worklauncher.service.llm

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Two ways to get the user's voice into the model. A model built with the audio
 * modality takes the recording itself, which keeps wording and tone the
 * transcriber would flatten; everything else goes through the system recognizer.
 */
class VoiceInput(private val context: Context) {

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Records mono 16 kHz PCM until [shouldStop] returns true, which is what the
     * audio-capable LiteRT builds expect.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(shouldStop: () -> Boolean): ByteArray? = withContext(Dispatchers.IO) {
        if (!hasMicPermission()) return@withContext null

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return@withContext null

        val bufferSize = maxOf(minBuffer, SAMPLE_RATE) // at least a second of slack
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        }.getOrNull() ?: return@withContext null

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext null
        }

        val output = ByteArrayOutputStream()
        try {
            recorder.startRecording()
            val buffer = ByteArray(bufferSize)
            while (!shouldStop() && output.size() < MAX_BYTES) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) output.write(buffer, 0, read)
            }
        } catch (e: Exception) {
            return@withContext null
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        output.toByteArray().takeIf { it.isNotEmpty() }
    }

    /** One-shot transcription through the system recognizer. */
    suspend fun transcribe(languageTag: String): String? {
        if (!hasMicPermission() || !isRecognitionAvailable()) return null
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                var settled = false

                fun finish(result: String?) {
                    if (settled) return
                    settled = true
                    runCatching { recognizer.destroy() }
                    continuation.resume(result)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        finish(
                            results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                        )
                    }

                    override fun onError(error: Int) = finish(null)

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                continuation.invokeOnCancellation { finish(null) }
                runCatching { recognizer.startListening(intent) }.onFailure { finish(null) }
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_BYTES = SAMPLE_RATE * 2 * 60 // one minute of 16-bit mono
    }
}
