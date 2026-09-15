package jp.hisiragi.worklauncher.service.llm

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import jp.hisiragi.worklauncher.domain.ExpenseCategory
import jp.hisiragi.worklauncher.domain.ReceiptDraft
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Reads a receipt photo in two steps: OCR on device, then the LLM turns that
 * text into fields. Splitting it this way means a small text-only model is
 * enough — no vision model required.
 */
class ReceiptReader(private val llm: LlmManager) {

    private val recognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    suspend fun read(file: File): ReceiptDraft? {
        val text = recognize(file) ?: return null
        if (text.isBlank()) return null
        val response = llm.generate(prompt(text), maxTokens = 256) ?: return null
        return parse(response)
    }

    private suspend fun recognize(file: File): String? = withContext(Dispatchers.IO) {
        val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
            ?: return@withContext null
        val image = InputImage.fromBitmap(bitmap, 0)
        runCatching {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it.text) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
        }.getOrNull()
    }

    private fun prompt(ocrText: String): String = buildString {
        appendLine("You are reading a receipt. Below is OCR text from the photo.")
        appendLine("Reply with ONLY a JSON object, no prose and no code fences, with keys:")
        appendLine("""  "amount": total paid as an integer in the smallest currency unit, or null""")
        appendLine("""  "vendor": shop or company name as a string, or null""")
        appendLine("""  "date": the date as "YYYY-MM-DD", or null""")
        appendLine("""  "category": one of TRANSPORT, MEAL, SUPPLIES, ACCOMMODATION, ENTERTAINMENT, OTHER""")
        appendLine("Pick the grand total, not a subtotal or the change given.")
        appendLine()
        appendLine("OCR text:")
        append(ocrText.take(MAX_OCR_CHARS))
    }

    private fun parse(response: String): ReceiptDraft? {
        // Models commonly wrap JSON in prose or fences; take the outermost object.
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = runCatching { JSONObject(response.substring(start, end + 1)) }.getOrNull()
            ?: return null

        return ReceiptDraft(
            amount = json.optLong("amount", 0L).takeIf { it > 0L },
            vendor = json.optString("vendor").trim().takeIf { it.isNotBlank() && it != "null" },
            isoDate = json.optString("date").trim().takeIf { ISO_DATE.matches(it) },
            category = json.optString("category").trim()
                .takeIf { it.isNotBlank() }
                ?.let { key -> ExpenseCategory.entries.firstOrNull { it.name == key.uppercase() } },
        )
    }

    private companion object {
        const val MAX_OCR_CHARS = 2000
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
