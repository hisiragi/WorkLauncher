package jp.hisiragi.worklauncher.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.db.NoteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val query: String = "",
    val notes: List<NoteEntity> = emptyList(),
    val totalCount: Int = 0,
)

class NotesViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<NotesUiState> = combine(
        container.noteRepository.notes,
        query,
    ) { notes, q ->
        NotesUiState(
            query = q,
            notes = notes.filter {
                q.isBlank() || it.title.contains(q, true) || it.body.contains(q, true)
            },
            totalCount = notes.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun save(note: NoteEntity) {
        viewModelScope.launch { container.noteRepository.save(note) }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch { container.noteRepository.delete(note) }
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch { container.noteRepository.setPinned(note, !note.pinned) }
    }
}
