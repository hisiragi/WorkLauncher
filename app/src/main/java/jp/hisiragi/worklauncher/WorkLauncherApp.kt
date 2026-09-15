package jp.hisiragi.worklauncher

import android.app.Application
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.service.FocusNotifications

class WorkLauncherApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
        FocusNotifications.ensureChannels(this)
    }
}
