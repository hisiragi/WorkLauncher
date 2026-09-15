package jp.hisiragi.worklauncher.ui.expense

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.data.db.ExpenseEntity
import jp.hisiragi.worklauncher.domain.ExpenseCategory
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.components.rememberReceiptThumbnail
import jp.hisiragi.worklauncher.ui.components.LabeledRow
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.ui.components.StatTile
import jp.hisiragi.worklauncher.util.TimeUtils
import java.io.File
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
    var viewingReceipt by remember { mutableStateOf<String?>(null) }

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
                        receiptFile = expense.receiptFile?.let(viewModel::receiptFile),
                        onToggleReimbursed = { viewModel.toggleReimbursed(expense) },
                        onDelete = { viewModel.delete(expense) },
                        onOpenReceipt = { viewingReceipt = expense.receiptFile },
                    )
                }
            }
        }
    }

    if (adding) {
        AddExpenseDialog(
            viewModel = viewModel,
            onDismiss = { adding = false },
            onSave = { amount, category, memo, project, receipt ->
                viewModel.add(amount, category, memo, project, receipt)
                adding = false
            },
        )
    }

    viewingReceipt?.let { name ->
        ReceiptViewerDialog(
            file = viewModel.receiptFile(name),
            onDismiss = { viewingReceipt = null },
        )
    }
}

@Composable
private fun ReceiptViewerDialog(file: File, onDismiss: () -> Unit) {
    val bitmap by rememberReceiptThumbnail(file, maxPx = 1600)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.expense_receipt),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            } ?: Text(stringResource(R.string.expense_receipt_missing))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
    currency: String,
    receiptFile: File?,
    onToggleReimbursed: () -> Unit,
    onDelete: () -> Unit,
    onOpenReceipt: () -> Unit,
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
            if (receiptFile != null) {
                ReceiptThumbnail(
                    file = receiptFile,
                    size = 40.dp,
                    onClick = onOpenReceipt,
                )
                Spacer(Modifier.width(10.dp))
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
private fun ReceiptThumbnail(
    file: File,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by rememberReceiptThumbnail(file, maxPx = 256)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(R.string.expense_receipt),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Icon(
            imageVector = Icons.Filled.Receipt,
            contentDescription = stringResource(R.string.expense_receipt),
            modifier = Modifier.size(size / 2),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddExpenseDialog(
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit,
    onSave: (Long, ExpenseCategory, String, String?, String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var project by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.TRANSPORT) }
    var receiptName by remember { mutableStateOf<String?>(null) }
    var pendingCapture by remember { mutableStateOf<String?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val name = pendingCapture
        pendingCapture = null
        if (saved && name != null) {
            viewModel.discardUnusedReceipt(receiptName)
            receiptName = name
        } else {
            viewModel.discardUnusedReceipt(name)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importReceipt(uri) { imported ->
                if (imported != null) {
                    viewModel.discardUnusedReceipt(receiptName)
                    receiptName = imported
                }
            }
        }
    }

    fun discardAndDismiss() {
        viewModel.discardUnusedReceipt(receiptName)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::discardAndDismiss,
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    receiptName?.let { name ->
                        ReceiptThumbnail(
                            file = viewModel.receiptFile(name),
                            size = 56.dp,
                            onClick = {},
                        )
                    }
                    TextButton(
                        onClick = {
                            val (name, uri) = viewModel.newReceiptTarget()
                            pendingCapture = name
                            captureLauncher.launch(uri)
                        },
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.expense_receipt_capture))
                    }
                    TextButton(
                        onClick = {
                            pickLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    ) { Text(stringResource(R.string.expense_receipt_pick)) }
                    if (receiptName != null) {
                        IconButton(
                            onClick = {
                                viewModel.discardUnusedReceipt(receiptName)
                                receiptName = null
                            },
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.expense_receipt_remove),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
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
                        receiptName,
                    )
                },
                enabled = (amount.toLongOrNull() ?: 0L) > 0,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = ::discardAndDismiss) { Text(stringResource(R.string.action_cancel)) }
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
