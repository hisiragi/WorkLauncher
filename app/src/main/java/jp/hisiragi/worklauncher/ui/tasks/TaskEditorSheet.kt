package jp.hisiragi.worklauncher.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.data.db.TaskEntity
import jp.hisiragi.worklauncher.domain.Priority
import jp.hisiragi.worklauncher.ui.theme.PriorityColors
import jp.hisiragi.worklauncher.util.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Create/edit form for a task, shown as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    task: TaskEntity?,
    projects: List<String>,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(task?.title.orEmpty()) }
    var notes by remember { mutableStateOf(task?.notes.orEmpty()) }
    var project by remember { mutableStateOf(task?.project.orEmpty()) }
    var priority by remember { mutableStateOf(Priority.fromLevel(task?.priority ?: 1)) }
    var estimate by remember { mutableStateOf(task?.estimateMinutes?.takeIf { it > 0 }?.toString().orEmpty()) }
    var dueAt by remember { mutableStateOf(task?.dueAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (task == null) R.string.tasks_new else R.string.tasks_edit
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.tasks_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.tasks_field_notes)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Priority.entries.forEach { level ->
                    FilterChip(
                        selected = priority == level,
                        onClick = { priority = level },
                        label = { Text(priorityLabel(level)) },
                    )
                }
            }

            OutlinedTextField(
                value = project,
                onValueChange = { project = it },
                label = { Text(stringResource(R.string.tasks_field_project)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (projects.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    projects.forEach { existing ->
                        AssistChip(onClick = { project = existing }, label = { Text(existing) })
                    }
                }
            }

            OutlinedTextField(
                value = estimate,
                onValueChange = { value -> estimate = value.filter { it.isDigit() }.take(4) },
                label = { Text(stringResource(R.string.tasks_field_estimate)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        dueAt?.let {
                            TimeUtils.formatDateShort(TimeUtils.toLocalDateTime(it).toLocalDate())
                        } ?: stringResource(R.string.tasks_set_due_date)
                    )
                }
                if (dueAt != null) {
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(TimeUtils.formatTime(dueAt!!, use24h))
                    }
                    TextButton(onClick = { dueAt = null }) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }

            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isEmpty()) return@Button
                    val base = task ?: TaskEntity(title = trimmed)
                    onSave(
                        base.copy(
                            title = trimmed,
                            notes = notes.trim(),
                            priority = priority.level,
                            project = project.trim().takeIf { it.isNotEmpty() },
                            dueAt = dueAt,
                            estimateMinutes = estimate.toIntOrNull() ?: 0,
                        )
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showDatePicker) {
        val initial = dueAt ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtils.toLocalDateTime(initial)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { utcMillis ->
                            // The picker reports UTC midnight; rebuild the instant
                            // in the device's zone so the due time stays correct.
                            val date: LocalDate = Instant.ofEpochMilli(utcMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            val time = dueAt
                                ?.let { TimeUtils.toLocalDateTime(it).toLocalTime() }
                                ?: LocalTime.of(9, 0)
                            dueAt = date.atTime(time)
                                .atZone(TimeUtils.zone)
                                .toInstant()
                                .toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val current = dueAt?.let { TimeUtils.toLocalDateTime(it) }
        val timeState = rememberTimePickerState(
            initialHour = current?.hour ?: 9,
            initialMinute = current?.minute ?: 0,
            is24Hour = use24h,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val date = current?.toLocalDate() ?: TimeUtils.today()
                        dueAt = date.atTime(timeState.hour, timeState.minute)
                            .atZone(TimeUtils.zone)
                            .toInstant()
                            .toEpochMilli()
                        showTimePicker = false
                    }
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.LOW -> R.string.priority_low
        Priority.NORMAL -> R.string.priority_normal
        Priority.HIGH -> R.string.priority_high
        Priority.URGENT -> R.string.priority_urgent
    }
)
