package jp.hisiragi.worklauncher.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.hisiragi.worklauncher.R

/** The four time-clock actions that need confirming before they are recorded. */
enum class ClockAction {
    CLOCK_IN,
    CLOCK_OUT,
    BREAK_START,
    BREAK_END,
}

/**
 * The clock buttons sit on the home screen, where a stray tap is easy and the
 * result is a wrong time card entry, so each one is confirmed first.
 */
@Composable
fun ClockConfirmDialog(
    action: ClockAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (titleRes, confirmRes) = when (action) {
        ClockAction.CLOCK_IN -> R.string.confirm_clock_in to R.string.action_clock_in
        ClockAction.CLOCK_OUT -> R.string.confirm_clock_out to R.string.action_clock_out
        ClockAction.BREAK_START -> R.string.confirm_break_start to R.string.action_break_start
        ClockAction.BREAK_END -> R.string.confirm_break_end to R.string.action_break_end
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Text(
                text = stringResource(R.string.confirm_clock_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(confirmRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
