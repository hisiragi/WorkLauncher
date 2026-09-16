package jp.hisiragi.worklauncher.service.llm

import jp.hisiragi.worklauncher.data.db.NoteEntity

class NoteSummarizer(private val llm: LlmManager) {

    /** Condenses one note; null when no model is configured or it failed. */
    suspend fun summarize(note: NoteEntity): String? {
        if (note.body.isBlank()) return null
        return llm.generate(prompt(note), maxTokens = 300)?.trim()
    }

    private fun prompt(note: NoteEntity): String = buildString {
        appendLine("Summarise the note below for a working professional.")
        appendLine("Lead with the single most important point, then at most four bullets")
        appendLine("starting with \"- \". Pull out any action items and dates.")
        appendLine("Answer in the language the note is written in. Add nothing that is not in it.")
        appendLine()
        if (note.title.isNotBlank()) appendLine("Title: ${note.title}")
        appendLine("Body:")
        append(note.body.take(MAX_BODY_CHARS))
    }

    private companion object {
        const val MAX_BODY_CHARS = 4000
    }
}
