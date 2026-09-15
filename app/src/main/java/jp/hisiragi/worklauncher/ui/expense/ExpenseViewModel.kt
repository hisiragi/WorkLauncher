package jp.hisiragi.worklauncher.ui.expense

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.hisiragi.worklauncher.core.AppContainer
import jp.hisiragi.worklauncher.data.db.ExpenseEntity
import jp.hisiragi.worklauncher.data.settings.LauncherSettings
import jp.hisiragi.worklauncher.domain.ExpenseCategory
import jp.hisiragi.worklauncher.util.CsvExporter
import jp.hisiragi.worklauncher.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class ExpenseUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val month: YearMonth = YearMonth.now(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val monthTotal: Long = 0,
    val unreimbursedTotal: Long = 0,
    val byCategory: List<Pair<ExpenseCategory, Long>> = emptyList(),
)

class ExpenseViewModel(private val container: AppContainer) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<ExpenseUiState> = combine(
        container.settingsRepository.settings,
        container.expenseRepository.expenses,
        month,
    ) { settings, all, currentMonth ->
        val from = currentMonth.atDay(1).toEpochDay()
        val to = currentMonth.atEndOfMonth().toEpochDay()
        val scoped = all.filter { it.epochDay in from..to }
        ExpenseUiState(
            settings = settings,
            month = currentMonth,
            expenses = scoped,
            monthTotal = scoped.sumOf { it.amount },
            unreimbursedTotal = scoped.filterNot { it.reimbursed }.sumOf { it.amount },
            byCategory = scoped
                .groupBy { ExpenseCategory.fromKey(it.category) }
                .map { (category, items) -> category to items.sumOf { it.amount } }
                .sortedByDescending { it.second },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseUiState())

    fun shiftMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    fun resetMonth() {
        month.value = YearMonth.now()
    }

    fun add(
        amount: Long,
        category: ExpenseCategory,
        memo: String,
        project: String?,
        date: LocalDate = TimeUtils.today(),
    ) {
        if (amount <= 0) return
        viewModelScope.launch {
            container.expenseRepository.add(
                ExpenseEntity(
                    epochDay = date.toEpochDay(),
                    amount = amount,
                    category = category.name,
                    memo = memo.trim(),
                    project = project?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
        }
    }

    fun toggleReimbursed(expense: ExpenseEntity) {
        viewModelScope.launch {
            container.expenseRepository.setReimbursed(expense, !expense.reimbursed)
        }
    }

    fun delete(expense: ExpenseEntity) {
        viewModelScope.launch { container.expenseRepository.delete(expense) }
    }

    fun exportCsv(context: Context, header: List<String>, subject: String) {
        viewModelScope.launch {
            val current = month.value
            val rows = container.expenseRepository
                .listRange(current.atDay(1).toEpochDay(), current.atEndOfMonth().toEpochDay())
                .map { expense ->
                    listOf(
                        TimeUtils.formatIsoDate(LocalDate.ofEpochDay(expense.epochDay)),
                        expense.amount.toString(),
                        expense.category,
                        expense.project.orEmpty(),
                        expense.memo,
                        if (expense.reimbursed) "1" else "0",
                    )
                }
            CsvExporter.share(
                context = context,
                fileName = "worklauncher-expenses-$current.csv",
                header = header,
                rows = rows,
                subject = subject,
            )
        }
    }
}
