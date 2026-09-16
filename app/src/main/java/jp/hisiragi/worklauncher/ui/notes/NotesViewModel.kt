package jp.hisiragi.worklauncher.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.db.NoteEntity
import jp.hisiragi.worklauncher.domain.LlmAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of summarising the note open in the editor. */
sealed interface SummaryState {
    data object None : SummaryState
    data object Running : SummaryState
    data object Failed : SummaryState
    data class Ready(val text: String) : SummaryState
}

data class NotesUiState(
    val query: String = "",
    val notes: List<NoteEntity> = emptyList(),
    val totalCount: Int = 0,
)

class NotesViewModel(private val container: AppContainer) : ViewModel() {

    val llmAvailability: StateFlow<LlmAvailability> = container.llmManager.availability

    /** Summary of the note currently open in the editor, if one was produced. */
    private val _summary = MutableStateFlow<SummaryState>(SummaryState.None)
    val summary: StateFlow<SummaryState> = _summary.asStateFlow()

    fun summarize(note: NoteEntity) {
        if (_summary.value is SummaryState.Running) return
        _summary.value = SummaryState.Running
        viewModelScope.launch {
            val text = container.noteSummarizer.summarize(note)
            _summary.value = if (text.isNullOrBlank()) SummaryState.Failed else SummaryState.Ready(text)
        }
    }

    fun clearSummary() {
        _summary.value = SummaryState.None
    }


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
