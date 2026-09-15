package jp.hisiragi.worklauncher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.ui.navigation.Route
import jp.hisiragi.worklauncher.ui.navigation.WorkLauncherNavHost
import jp.hisiragi.worklauncher.ui.theme.WorkLauncherTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * The launcher's only activity. It is the HOME target, so it never finishes —
 * pressing HOME re-delivers an intent instead of recreating the activity.
 */
class MainActivity : ComponentActivity() {

    /** Bumped on every HOME press so the nav graph can pop back to the home route. */
    private var homePressToken by mutableIntStateOf(0)
    private var pendingRoute by mutableStateOf<String?>(null)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as WorkLauncherApp).container
        val settingsFlow = container.settingsRepository.settings
            .stateIn(container.applicationScope, SharingStarted.Eagerly, LauncherSettings())

        pendingRoute = routeForIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            val settings by settingsFlow.collectAsState()
            WorkLauncherTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                // The window shows the wallpaper, so the launcher only paints a
                // light scrim rather than an opaque background.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.background
                                .copy(alpha = 0.78f)
                        )
                ) {
                    WorkLauncherNavHost(
                        homePressToken = homePressToken,
                        pendingRoute = pendingRoute,
                        onPendingRouteHandled = { pendingRoute = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = routeForIntent(intent)
        if (route != null) {
            pendingRoute = route
        } else if (Intent.ACTION_MAIN == intent.action &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        ) {
            homePressToken++
        }
    }

    private fun routeForIntent(intent: Intent?): String? = when (intent?.action) {
        ACTION_OPEN_FOCUS -> Route.FOCUS
        ACTION_OPEN_TASKS -> Route.TASKS
        ACTION_OPEN_TIME_CARD -> Route.TIME_CARD
        else -> null
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    companion object {
        const val ACTION_OPEN_FOCUS = "jp.hisiragi.worklauncher.action.OPEN_FOCUS"
        const val ACTION_OPEN_TASKS = "jp.hisiragi.worklauncher.action.OPEN_TASKS"
        const val ACTION_OPEN_TIME_CARD = "jp.hisiragi.worklauncher.action.OPEN_TIME_CARD"
    }
}
