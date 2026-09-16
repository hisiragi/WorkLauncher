package jp.hisiragi.worklauncher.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.hisiragi.worklauncher.ui.agenda.AgendaScreen
import jp.hisiragi.worklauncher.ui.assistant.AssistantScreen
import jp.hisiragi.worklauncher.ui.models.ModelManagerScreen
import jp.hisiragi.worklauncher.ui.contacts.ContactsScreen
import jp.hisiragi.worklauncher.ui.drawer.AppDrawerScreen
import jp.hisiragi.worklauncher.ui.expense.ExpenseScreen
import jp.hisiragi.worklauncher.ui.focus.FocusScreen
import jp.hisiragi.worklauncher.ui.home.HomeScreen
import jp.hisiragi.worklauncher.ui.hub.HubScreen
import jp.hisiragi.worklauncher.ui.notes.NotesScreen
import jp.hisiragi.worklauncher.ui.settings.SettingsScreen
import jp.hisiragi.worklauncher.ui.tasks.TasksScreen
import jp.hisiragi.worklauncher.ui.timecard.TimeCardScreen
import jp.hisiragi.worklauncher.ui.usage.UsageScreen

object Route {
    const val HOME = "home"
    const val DRAWER = "drawer"
    const val SEARCH = "search"
    const val HUB = "hub"
    const val TASKS = "tasks"
    const val NOTES = "notes"
    const val FOCUS = "focus"
    const val TIME_CARD = "timecard"
    const val AGENDA = "agenda"
    const val CONTACTS = "contacts"
    const val USAGE = "usage"
    const val EXPENSES = "expenses"
    const val ASSISTANT = "assistant"
    const val MODELS = "models"
    const val SETTINGS = "settings"
}

@Composable
fun WorkLauncherNavHost(
    navController: NavHostController = rememberNavController(),
    /** Incremented by [jp.hisiragi.worklauncher.MainActivity] each time HOME is pressed. */
    homePressToken: Int = 0,
    /** Route the activity wants opened, e.g. from a notification tap. */
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {},
) {
    LaunchedEffect(homePressToken) {
        if (homePressToken > 0) {
            navController.popBackStack(Route.HOME, inclusive = false)
        }
    }

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            onPendingRouteHandled()
        }
    }

    val navigate: (String) -> Unit = { route ->
        navController.navigate(route) { launchSingleTop = true }
    }
    val back: () -> Unit = {
        if (!navController.popBackStack()) navController.navigate(Route.HOME)
    }

    NavHost(
        navController = navController,
        startDestination = Route.HOME,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
        exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) },
    ) {
        composable(Route.HOME) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                // A decisive swipe up opens the drawer, the way a
                                // stock launcher behaves.
                                if (dragged < -120f) navigate(Route.DRAWER)
                            },
                        ) { _, dragAmount -> dragged += dragAmount }
                    }
            ) {
                HomeScreen(
                    onOpenDrawer = { navigate(Route.DRAWER) },
                    onOpenSearch = { navigate(Route.SEARCH) },
                    onOpenTasks = { navigate(Route.TASKS) },
                    onOpenAgenda = { navigate(Route.AGENDA) },
                    onOpenFocus = { navigate(Route.FOCUS) },
                    onOpenTimeCard = { navigate(Route.TIME_CARD) },
                    onOpenHub = { navigate(Route.HUB) },
                )
            }
        }

        composable(Route.DRAWER) {
            AppDrawerScreen(onBack = back, startWithKeyboard = false)
        }

        composable(Route.SEARCH) {
            AppDrawerScreen(onBack = back, startWithKeyboard = true)
        }

        composable(Route.HUB) {
            HubScreen(onBack = back, onNavigate = navigate)
        }

        composable(Route.TASKS) { TasksScreen(onBack = back) }
        composable(Route.NOTES) { NotesScreen(onBack = back) }
        composable(Route.FOCUS) { FocusScreen(onBack = back) }
        composable(Route.TIME_CARD) { TimeCardScreen(onBack = back) }
        composable(Route.AGENDA) { AgendaScreen(onBack = back) }
        composable(Route.CONTACTS) { ContactsScreen(onBack = back) }
        composable(Route.USAGE) { UsageScreen(onBack = back) }
        composable(Route.EXPENSES) { ExpenseScreen(onBack = back) }
        composable(Route.ASSISTANT) {
            AssistantScreen(
                onBack = back,
                onOpenSettings = { navController.navigate(Route.SETTINGS) },
            )
        }
        composable(Route.MODELS) { ModelManagerScreen(onBack = back) }
        composable(Route.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onOpenModels = { navController.navigate(Route.MODELS) },
            )
        }
    }
}
