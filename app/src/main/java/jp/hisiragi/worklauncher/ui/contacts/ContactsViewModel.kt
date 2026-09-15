package jp.hisiragi.worklauncher.ui.contacts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.db.QuickContactEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(val contacts: List<QuickContactEntity> = emptyList())

class ContactsViewModel(private val container: AppContainer) : ViewModel() {

    val uiState: StateFlow<ContactsUiState> = container.quickContactRepository.contacts
        .map { ContactsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    /** Imports a contact chosen from the system picker. */
    fun addFromUri(uri: Uri) {
        viewModelScope.launch {
            container.quickContactRepository.fromPickedUri(uri)?.let {
                container.quickContactRepository.add(it.copy(sortOrder = nextSortOrder()))
            }
        }
    }

    fun add(contact: QuickContactEntity) {
        viewModelScope.launch {
            container.quickContactRepository.add(contact.copy(sortOrder = nextSortOrder()))
        }
    }

    fun update(contact: QuickContactEntity) {
        viewModelScope.launch { container.quickContactRepository.update(contact) }
    }

    fun delete(contact: QuickContactEntity) {
        viewModelScope.launch { container.quickContactRepository.delete(contact) }
    }

    private fun nextSortOrder(): Int =
        (uiState.value.contacts.maxOfOrNull { it.sortOrder } ?: 0) + 1
}
