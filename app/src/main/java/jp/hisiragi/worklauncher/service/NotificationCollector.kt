package jp.hisiragi.worklauncher.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification, reduced to the text worth summarising. */
data class CapturedNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val ongoing: Boolean,
)

/**
 * Mirrors the active notification shade while the user has granted listener
 * access. Nothing is written to disk — notification contents stay in memory and
 * disappear with the process.
 */
class NotificationCollector : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected.value = false
        active.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val snapshot = runCatching { activeNotifications }.getOrNull().orEmpty()
        active.value = snapshot.mapNotNull { it.toCaptured() }
    }

    private fun StatusBarNotification.toCaptured(): CapturedNotification? {
        val extras = notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return CapturedNotification(
            key = key,
            packageName = packageName,
            title = title,
            text = text,
            postedAt = postTime,
            ongoing = isOngoing,
        )
    }

    companion object {
        private val active = MutableStateFlow<List<CapturedNotification>>(emptyList())
        private val connected = MutableStateFlow(false)

        /** Empty whenever listener access is not granted. */
        val notifications: StateFlow<List<CapturedNotification>> = active.asStateFlow()
        val isConnected: StateFlow<Boolean> = connected.asStateFlow()

        /** True once the user has ticked this app in notification access settings. */
        fun hasAccess(context: Context): Boolean {
            val enabled = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                )
            }.getOrNull().orEmpty()
            val component = ComponentName(context, NotificationCollector::class.java)
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == component
            }
        }
    }
}
