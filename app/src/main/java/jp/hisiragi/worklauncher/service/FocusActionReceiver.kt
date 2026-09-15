package jp.hisiragi.worklauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.hisiragi.worklauncher.WorkLauncherApp

/** Handles the pause/resume/stop buttons on the focus notification. */
class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as? WorkLauncherApp ?: return
        app.container.focusController.handleAction(action)
    }
}
