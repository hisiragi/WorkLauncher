package jp.hisiragi.worklauncher.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.repo.UsageRepository
import jp.hisiragi.worklauncher.domain.AppUsage
import jp.hisiragi.worklauncher.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UsageRange { TODAY, WEEK }

data class UsageUiState(
    val permissionGranted: Boolean = false,
    val loading: Boolean = false,
    val range: UsageRange = UsageRange.TODAY,
    val usage: List<AppUsage> = emptyList(),
    val split: UsageRepository.UsageSplit = UsageRepository.UsageSplit(0, 0, 0),
)

class UsageViewModel(private val container: AppContainer) : ViewModel() {

    private val usage = MutableStateFlow<List<AppUsage>>(emptyList())
    private val permission = MutableStateFlow(false)
    private val loading = MutableStateFlow(false)
    private val range = MutableStateFlow(UsageRange.TODAY)

    val uiState: StateFlow<UsageUiState> = combine(
        usage,
        permission,
        loading,
        range,
    ) { usageList, hasPermission, isLoading, currentRange ->
        UsageUiState(
            permissionGranted = hasPermission,
            loading = isLoading,
            range = currentRange,
            usage = usageList,
            split = container.usageRepository.split(usageList),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageUiState())

    init {
        refresh()
    }

    fun setRange(value: UsageRange) {
        range.value = value
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            permission.value = container.usageRepository.hasPermission()
            if (!permission.value) {
                usage.value = emptyList()
                return@launch
            }
            loading.value = true
            val today = TimeUtils.today()
            usage.value = when (range.value) {
                UsageRange.TODAY -> container.usageRepository.usageForDay(today)
                UsageRange.WEEK -> container.usageRepository.usageBetween(
                    TimeUtils.startOfWeek(today),
                    today,
                )
            }
            loading.value = false
        }
    }
}
