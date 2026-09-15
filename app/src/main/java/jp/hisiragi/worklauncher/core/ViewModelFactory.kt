package jp.hisiragi.worklauncher.core

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.hisiragi.worklauncher.WorkLauncherApp
import jp.hisiragi.worklauncher.ui.agenda.AgendaViewModel
import jp.hisiragi.worklauncher.ui.assistant.AssistantViewModel
import jp.hisiragi.worklauncher.ui.contacts.ContactsViewModel
import jp.hisiragi.worklauncher.ui.drawer.AppDrawerViewModel
import jp.hisiragi.worklauncher.ui.expense.ExpenseViewModel
import jp.hisiragi.worklauncher.ui.focus.FocusViewModel
import jp.hisiragi.worklauncher.ui.home.HomeViewModel
import jp.hisiragi.worklauncher.ui.notes.NotesViewModel
import jp.hisiragi.worklauncher.ui.settings.SettingsViewModel
import jp.hisiragi.worklauncher.ui.tasks.TasksViewModel
import jp.hisiragi.worklauncher.ui.timecard.TimeCardViewModel
import jp.hisiragi.worklauncher.ui.usage.UsageViewModel

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as WorkLauncherApp).container

/** Single factory for every screen's ViewModel. */
val AppViewModelFactory = viewModelFactory {
    initializer { HomeViewModel(container()) }
    initializer { AppDrawerViewModel(container()) }
    initializer { TasksViewModel(container()) }
    initializer { NotesViewModel(container()) }
    initializer { FocusViewModel(container()) }
    initializer { TimeCardViewModel(container()) }
    initializer { AgendaViewModel(container()) }
    initializer { ContactsViewModel(container()) }
    initializer { UsageViewModel(container()) }
    initializer { ExpenseViewModel(container()) }
    initializer { AssistantViewModel(container()) }
    initializer { SettingsViewModel(container()) }
}
