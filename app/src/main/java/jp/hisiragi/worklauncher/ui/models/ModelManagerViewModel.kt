package jp.hisiragi.worklauncher.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.domain.CatalogModel
import jp.hisiragi.worklauncher.domain.LlmBackend
import jp.hisiragi.worklauncher.domain.LlmModelCatalog
import jp.hisiragi.worklauncher.domain.ModelDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelRow(
    val model: CatalogModel,
    val installed: Boolean,
    val active: Boolean,
)

data class ModelManagerUiState(
    val rows: List<ModelRow> = emptyList(),
    val download: ModelDownloadState = ModelDownloadState.Idle,
    val freeBytes: Long = 0,
)

class ModelManagerViewModel(private val container: AppContainer) : ViewModel() {

    private val downloader = container.modelDownloader

    /** Bumped after any install or delete so the installed flags re-read disk. */
    private val diskRevision = MutableStateFlow(0)

    val uiState: StateFlow<ModelManagerUiState> = combine(
        container.settingsRepository.settings.map { it.llmModelPath },
        downloader.state,
        diskRevision,
    ) { activePath, download, _ ->
        ModelManagerUiState(
            rows = LlmModelCatalog.models.map { model ->
                ModelRow(
                    model = model,
                    installed = downloader.isInstalled(model),
                    active = activePath == downloader.fileFor(model).path,
                )
            },
            download = download,
            freeBytes = downloader.freeBytes(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelManagerUiState())

    fun hasRoomFor(model: CatalogModel): Boolean = downloader.hasRoomFor(model)

    fun download(model: CatalogModel) = downloader.start(model)

    fun cancelDownload() = downloader.cancel()

    fun delete(model: CatalogModel) {
        viewModelScope.launch {
            val wasActive = uiState.value.rows.firstOrNull { it.model.id == model.id }?.active
            downloader.delete(model)
            if (wasActive == true) {
                container.settingsRepository.setLlmModelPath("")
            }
            diskRevision.value++
        }
    }

    /** Points the engine at this model and switches the backend to on-device. */
    fun activate(model: CatalogModel) {
        viewModelScope.launch {
            container.settingsRepository.setLlmModelPath(downloader.fileFor(model).path)
            container.settingsRepository.setLlmBackend(LlmBackend.ON_DEVICE)
            diskRevision.value++
        }
    }

    fun refresh() {
        diskRevision.value++
    }
}
