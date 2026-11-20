package jp.hisiragi.worklauncher.screenshot

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import jp.hisiragi.worklauncher.WorkLauncherApp
import jp.hisiragi.worklauncher.data.db.TaskEntity
import jp.hisiragi.worklauncher.domain.ThemeMode
import jp.hisiragi.worklauncher.ui.drawer.AppDrawerScreen
import jp.hisiragi.worklauncher.ui.home.HomeScreen
import jp.hisiragi.worklauncher.ui.theme.WorkLauncherTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the launcher on the JVM so the screens can be looked at without a
 * device. Robolectric gives the composables a real Context and the app's own
 * dependency graph — Room, the repositories, the view models — so what is
 * captured is the screen as the app builds it, not a mock-up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: WorkLauncherApp get() = ApplicationProvider.getApplicationContext()

    /** An empty install shows every card in its empty state, which says little. */
    @Before
    fun seed() = runBlocking {
        installApps(
            "com.slack" to "Slack",
            "com.google.android.calendar" to "カレンダー",
            "com.android.chrome" to "Chrome",
            "com.microsoft.teams" to "Teams",
            "com.android.calculator2" to "電卓",
        )
        val container = app.container
        container.appRepository.refresh()
        container.appRepository.apps.value.take(4).forEachIndexed { index, launcherApp ->
            container.appRepository.setFavorite(launcherApp, favorite = true, dockOrder = index)
        }

        val now = System.currentTimeMillis()
        container.taskRepository.add(
            TaskEntity(title = "見積書を送る", priority = 3, dueAt = now + 2 * 3_600_000)
        )
        container.taskRepository.add(
            TaskEntity(title = "レビューのコメント返し", priority = 2, dueAt = now + 5 * 3_600_000)
        )
        container.taskRepository.add(
            TaskEntity(title = "週報をまとめる", priority = 1, dueAt = now + 8 * 3_600_000)
        )

        container.timeCardRepository.clockIn()
        container.timeCardRepository.startBreak()
    }

    private fun installApps(vararg apps: Pair<String, String>) {
        val shadow = shadowOf(app.packageManager)
        val launcherFilter = IntentFilter(Intent.ACTION_MAIN)
            .apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        apps.forEach { (packageName, label) ->
            val component = ComponentName(packageName, "$packageName.MainActivity")
            val activityInfo = shadow.addActivityIfNotPresent(component)
            activityInfo.nonLocalizedLabel = label
            activityInfo.applicationInfo?.nonLocalizedLabel = label
            shadow.addIntentFilterForActivity(component, launcherFilter)
        }
    }

    @Test
    fun home_light() = capture(ThemeMode.LIGHT, "home-light") {
        HomeScreen(
            onOpenDrawer = {},
            onOpenSearch = {},
            onOpenTasks = {},
            onOpenAgenda = {},
            onOpenFocus = {},
            onOpenTimeCard = {},
            onOpenHub = {},
            onOpenAssistant = {},
        )
    }

    @Test
    fun home_dark() = capture(ThemeMode.DARK, "home-dark") {
        HomeScreen(
            onOpenDrawer = {},
            onOpenSearch = {},
            onOpenTasks = {},
            onOpenAgenda = {},
            onOpenFocus = {},
            onOpenTimeCard = {},
            onOpenHub = {},
            onOpenAssistant = {},
        )
    }

    @Test
    fun drawer_light() = capture(ThemeMode.LIGHT, "drawer-light") {
        AppDrawerScreen(onBack = {})
    }

    @Test
    fun drawer_dark() = capture(ThemeMode.DARK, "drawer-dark") {
        AppDrawerScreen(onBack = {})
    }

    private fun capture(mode: ThemeMode, name: String, content: @Composable () -> Unit) {
        compose.setContent {
            WorkLauncherTheme(themeMode = mode, dynamicColor = false) {
                // MainActivity paints this scrim over the wallpaper; without it the
                // screens would composite onto white and the dark theme would look
                // washed out here but not on a device.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f))
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }
}
