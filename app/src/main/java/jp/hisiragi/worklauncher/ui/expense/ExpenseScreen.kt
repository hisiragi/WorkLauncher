package jp.hisiragi.worklauncher.ui.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.data.db.ExpenseEntity
import jp.hisiragi.worklauncher.domain.ExpenseCategory
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.components.LabeledRow
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.ui.components.StatTile
import jp.hisiragi.worklauncher.util.TimeUtils
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    onBack: () -> Unit,
    viewModel: ExpenseViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }

    val csvHeader = listOf(
        stringResource(R.string.csv_date),
        stringResource(R.string.csv_amount),
        stringResource(R.string.csv_category),
        stringResource(R.string.csv_project),
        stringResource(R.string.csv_memo),
        stringResource(R.string.csv_reimbursed),
    )
    val csvSubject = stringResource(R.string.expense_export_subject)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportCsv(context, csvHeader, csvSubject) }) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = stringResource(R.string.expense_export),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.expense_add))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.shiftMonth(-1) }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(R.string.expense_previous_month),
                        )
                    }
                    Text(
                        text = state.month.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.shiftMonth(1) }) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.expense_next_month),
                        )
                    }
                    TextButton(onClick = viewModel::resetMonth) {
                        Text(stringResource(R.string.expense_this_month))
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.expense_summary)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatTile(
                            value = formatMoney(state.monthTotal, state.settings.currencySymbol),
                            label = stringResource(R.string.expense_month_total),
                        )
                        StatTile(
                            value = formatMoney(state.unreimbursedTotal, state.settings.currencySymbol),
                            label = stringResource(R.string.expense_unreimbursed),
                            accent = MaterialTheme.colorScheme.error,
                        )
                        StatTile(
                            value = state.expenses.size.toString(),
                            label = stringResource(R.string.expense_count),
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (state.byCategory.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        state.byCategory.forEach { (category, amount) ->
                            LabeledRow(
                                label = expenseCategoryLabel(category),
                                value = formatMoney(amount, state.settings.currencySymbol),
                            )
                        }
                    }
                }
            }

            if (state.expenses.isEmpty()) {
                item { EmptyState(stringResource(R.string.expense_empty)) }
            } else {
                items(state.expenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        currency = state.settings.currencySymbol,
                        onToggleReimbursed = { viewModel.toggleReimbursed(expense) },
                        onDelete = { viewModel.delete(expense) },
                    )
                }
            }
        }
    }

    if (adding) {
        AddExpenseDialog(
            onDismiss = { adding = false },
            onSave = { amount, category, memo, project ->
                viewModel.add(amount, category, memo, project)
                adding = false
            },
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
    currency: String,
    onToggleReimbursed: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleReimbursed) {
                Icon(
                    imageVector = if (expense.reimbursed) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = stringResource(R.string.expense_toggle_reimbursed),
                    tint = if (expense.reimbursed) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.memo.ifBlank {
                        expenseCategoryLabel(ExpenseCategory.fromKey(expense.category))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        TimeUtils.formatDateShort(LocalDate.ofEpochDay(expense.epochDay)),
                        expenseCategoryLabel(ExpenseCategory.fromKey(expense.category)),
                        expense.project,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatMoney(expense.amount, currency),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (Long, ExpenseCategory, String, String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var project by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.TRANSPORT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expense_add)) },
        text = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value -> amount = value.filter { it.isDigit() }.take(9) },
                    label = { Text(stringResource(R.string.expense_field_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpenseCategory.entries.forEach { entry ->
                        FilterChip(
                            selected = category == entry,
                            onClick = { category = entry },
                            label = { Text(expenseCategoryLabel(entry)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text(stringResource(R.string.expense_field_memo)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text(stringResource(R.string.expense_field_project)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        amount.toLongOrNull() ?: 0L,
                        category,
                        memo,
                        project.takeIf { it.isNotBlank() },
                    )
                },
                enabled = (amount.toLongOrNull() ?: 0L) > 0,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun formatMoney(amount: Long, currency: String): String =
    currency + NumberFormat.getIntegerInstance(Locale.getDefault()).format(amount)

@Composable
fun expenseCategoryLabel(category: ExpenseCategory): String = stringResource(
    when (category) {
        ExpenseCategory.TRANSPORT -> R.string.expense_category_transport
        ExpenseCategory.MEAL -> R.string.expense_category_meal
        ExpenseCategory.SUPPLIES -> R.string.expense_category_supplies
        ExpenseCategory.ACCOMMODATION -> R.string.expense_category_accommodation
        ExpenseCategory.ENTERTAINMENT -> R.string.expense_category_entertainment
        ExpenseCategory.OTHER -> R.string.expense_category_other
    }
)
