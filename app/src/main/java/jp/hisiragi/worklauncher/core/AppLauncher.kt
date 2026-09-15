package jp.hisiragi.worklauncher.core

import android.content.Context
import jp.hisiragi.worklauncher.data.repo.AppRepository
import jp.hisiragi.worklauncher.domain.LauncherApp
import jp.hisiragi.worklauncher.service.FocusController
import jp.hisiragi.worklauncher.util.Launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Opens apps and keeps the launch counters current. While a focus interval is
 * running, apps the user flagged as distracting are held back behind a
 * confirmation instead of opening straight away.
 */
class AppLauncher(
    private val appRepository: AppRepository,
    private val focusController: FocusController,
    private val scope: CoroutineScope,
) {
    private val _gatedApp = MutableStateFlow<LauncherApp?>(null)

    /** The app the user tried to open during focus mode, if any. */
    val gatedApp: StateFlow<LauncherApp?> = _gatedApp.asStateFlow()

    fun launch(context: Context, app: LauncherApp, ignoreFocusGate: Boolean = false) {
        scope.launch {
            if (!ignoreFocusGate && app.distraction && focusController.shouldGateDistractions()) {
                _gatedApp.value = app
                return@launch
            }
            _gatedApp.value = null
            appRepository.recordLaunch(app)
            Launch.app(context, app.packageName, app.activityName)
        }
    }

    fun dismissGate() {
        _gatedApp.value = null
    }
}
