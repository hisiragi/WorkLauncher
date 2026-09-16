package jp.hisiragi.worklauncher.service.llm

import jp.hisiragi.worklauncher.service.CapturedNotification

/** Turns the current notification shade into a short "what needs you" list. */
class NotificationDigest(private val llm: LlmManager) {

    suspend fun summarize(notifications: List<CapturedNotification>): String? {
        // Ongoing notifications are players and progress bars, never a to-do.
        val candidates = notifications
            .filterNot { it.ongoing }
            .sortedByDescending { it.postedAt }
            .take(MAX_NOTIFICATIONS)
        if (candidates.isEmpty()) return null
        return llm.generate(prompt(candidates), maxTokens = 400)
    }

    private fun prompt(notifications: List<CapturedNotification>): String = buildString {
        appendLine("You are triaging a working professional's phone notifications.")
        appendLine("List only the ones that plausibly need a reply or an action today.")
        appendLine("Use at most five short bullet points, each starting with \"- \".")
        appendLine("Put the most urgent first. If nothing needs action, say so in one line.")
        appendLine("Do not invent anything that is not in the list.")
        appendLine()
        appendLine("Notifications:")
        notifications.forEach { notification ->
            val app = notification.packageName.substringAfterLast('.')
            appendLine("- [$app] ${notification.title}: ${notification.text.take(MAX_TEXT_CHARS)}")
        }
    }

    private companion object {
        const val MAX_NOTIFICATIONS = 30
        const val MAX_TEXT_CHARS = 200
    }
}
