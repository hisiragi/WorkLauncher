package jp.hisiragi.worklauncher.ui.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.domain.AppCategory
import jp.hisiragi.worklauncher.domain.LauncherApp
import jp.hisiragi.worklauncher.ui.components.AppIconImage
import jp.hisiragi.worklauncher.util.Launch

/** Long-press menu for a single app in the drawer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: LauncherApp,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleDistraction: () -> Unit,
    onSetCategory: (AppCategory) -> Unit,
    onRename: (String?) -> Unit,
    /** Offered only where the dock is on screen. */
    onEditDock: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renaming by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconImage(app = app, size = 44.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = app.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppCategory.entries.forEach { category ->
                    FilterChip(
                        selected = app.category == category,
                        onClick = { onSetCategory(category) },
                        label = { Text(categoryLabel(category)) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (app.favorite) R.string.app_action_unpin else R.string.app_action_pin
                        )
                    )
                },
                supportingContent = if (!app.favorite) null else {
                    { Text(stringResource(R.string.app_action_pinned_hint)) }
                },
                leadingContent = {
                    Icon(
                        imageVector = if (app.favorite) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (app.favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                modifier = Modifier.clickableRow { onToggleFavorite() },
            )
            if (app.favorite && onEditDock != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_action_edit_dock)) },
                    leadingContent = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                    modifier = Modifier.clickableRow(onEditDock),
                )
            }
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (app.distraction) {
                                R.string.app_action_unmark_distraction
                            } else {
                                R.string.app_action_mark_distraction
                            }
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.app_action_distraction_hint)) },
                leadingContent = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) },
                modifier = Modifier.clickableRow { onToggleDistraction() },
            )
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (app.hidden) R.string.app_action_unhide else R.string.app_action_hide
                        )
                    )
                },
                leadingContent = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                modifier = Modifier.clickableRow { onToggleHidden() },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_action_rename)) },
                leadingContent = { Icon(Icons.Filled.Category, contentDescription = null) },
                modifier = Modifier.clickableRow { renaming = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_action_app_info)) },
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                modifier = Modifier.clickableRow { Launch.appInfo(context, app.packageName) },
            )
            if (!app.isSystemApp) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_action_uninstall)) },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    modifier = Modifier.clickableRow { Launch.uninstall(context, app.packageName) },
                )
            }
        }
    }

    if (renaming) {
        RenameDialog(
            initial = app.label,
            onDismiss = { renaming = false },
            onConfirm = {
                onRename(it)
                renaming = false
            },
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_action_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.app_rename_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.action_reset)) }
        },
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
