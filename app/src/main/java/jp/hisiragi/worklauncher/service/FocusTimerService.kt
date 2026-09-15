package jp.hisiragi.worklauncher.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import jp.hisiragi.worklauncher.WorkLauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers

/**
 * Keeps the focus timer alive and visible while the user is in another app.
 * The service holds no timer state of its own — it mirrors [FocusController].
 */
class FocusTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FocusNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as WorkLauncherApp).container.focusController
        startForegroundCompat(FocusNotifications.buildOngoing(this, controller.state.value))

        if (collectJob == null) {
            collectJob = scope.launch {
                controller.state
                    // The controller ticks four times a second but the countdown
                    // only shows whole seconds, so redraw on real changes alone.
                    .distinctUntilChanged { old, new ->
                        old.remainingSeconds == new.remainingSeconds &&
                            old.running == new.running &&
                            old.kind == new.kind
                    }
                    .collect { state ->
                        if (state.idle) {
                            stopSelfSafely()
                        } else {
                            postOngoing(state)
                        }
                    }
            }
        }
        return START_STICKY
    }

    private fun postOngoing(state: FocusState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // The service still runs; only the visible countdown is withheld.
            return
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(
                FocusNotifications.ONGOING_NOTIFICATION_ID,
                FocusNotifications.buildOngoing(this, state),
            )
        }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FocusNotifications.ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FocusNotifications.ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        collectJob = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FocusTimerService::class.java)) }
        }
    }
}
