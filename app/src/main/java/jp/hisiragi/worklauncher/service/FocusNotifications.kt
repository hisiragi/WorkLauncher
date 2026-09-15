package jp.hisiragi.worklauncher.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.hisiragi.worklauncher.MainActivity
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.domain.FocusKind
import jp.hisiragi.worklauncher.util.TimeUtils

object FocusNotifications {

    const val ONGOING_CHANNEL_ID = "focus_timer"
    const val ALERT_CHANNEL_ID = "focus_alerts"
    const val ONGOING_NOTIFICATION_ID = 1001
    private const val ALERT_NOTIFICATION_ID = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                ONGOING_CHANNEL_ID,
                context.getString(R.string.channel_focus_timer),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_focus_timer_desc)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.channel_focus_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_focus_alerts_desc)
            }
        )
    }

    fun buildOngoing(context: Context, state: FocusState): Notification {
        val title = when (state.kind) {
            FocusKind.FOCUS -> context.getString(R.string.focus_kind_focus)
            FocusKind.SHORT_BREAK -> context.getString(R.string.focus_kind_short_break)
            FocusKind.LONG_BREAK -> context.getString(R.string.focus_kind_long_break)
        }
        val subtitle = state.taskTitle.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.focus_notification_subtitle, state.cycle + 1)

        val builder = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_focus)
            .setContentTitle("$title · ${TimeUtils.formatCountdown(state.remainingSeconds)}")
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(state.totalSeconds, state.totalSeconds - state.remainingSeconds, false)
            .setContentIntent(contentIntent(context))

        if (state.running) {
            builder.addAction(
                0,
                context.getString(R.string.action_pause),
                broadcast(context, FocusController.ACTION_PAUSE, 1),
            )
        } else {
            builder.addAction(
                0,
                context.getString(R.string.action_resume),
                broadcast(context, FocusController.ACTION_RESUME, 2),
            )
        }
        builder.addAction(
            0,
            context.getString(R.string.action_stop),
            broadcast(context, FocusController.ACTION_STOP, 3),
        )
        return builder.build()
    }

    fun notifyIntervalFinished(
        context: Context,
        finishedKind: FocusKind,
        nextKind: FocusKind,
        nextMinutes: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val title = when (finishedKind) {
            FocusKind.FOCUS -> context.getString(R.string.focus_done_focus)
            else -> context.getString(R.string.focus_done_break)
        }
        val nextLabel = when (nextKind) {
            FocusKind.FOCUS -> context.getString(R.string.focus_kind_focus)
            FocusKind.SHORT_BREAK -> context.getString(R.string.focus_kind_short_break)
            FocusKind.LONG_BREAK -> context.getString(R.string.focus_kind_long_break)
        }
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_focus)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.focus_next_up, nextLabel, nextMinutes))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
        }
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            FocusController.intent(context, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
