package jp.hisiragi.worklauncher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.WidgetHostController
import jp.hisiragi.worklauncher.util.toImageBitmap

/**
 * Picks a provider and walks the bind → configure handshake. The allocated id is
 * released on any abandoned step so it does not leak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    controller: WidgetHostController,
    onDismiss: () -> Unit,
    onPicked: (appWidgetId: Int, heightDp: Int) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val providers = remember { controller.installedProviders() }

    // Carried across the bind and configure round trips.
    var pending by remember { mutableStateOf<Pending?>(null) }

    fun finish(appWidgetId: Int, info: AppWidgetProviderInfo) {
        onPicked(appWidgetId, controller.defaultHeightDp(info))
        pending = null
        onDismiss()
    }

    fun abandon() {
        pending?.let { controller.releaseId(it.appWidgetId) }
        pending = null
    }

    val configureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val current = pending
        if (current == null) return@rememberLauncherForActivityResult
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            finish(current.appWidgetId, current.info)
        } else {
            abandon()
        }
    }

    fun configureOrFinish(appWidgetId: Int, info: AppWidgetProviderInfo) {
        val intent = controller.configureIntent(appWidgetId, info)
        if (intent == null) {
            finish(appWidgetId, info)
            return
        }
        pending = Pending(appWidgetId, info)
        // Some configure activities refuse to launch from a non-system host;
        // keeping the widget unconfigured beats dropping it on the floor.
        runCatching { configureLauncher.launch(intent) }
            .onFailure { finish(appWidgetId, info) }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val current = pending ?: return@rememberLauncherForActivityResult
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            configureOrFinish(current.appWidgetId, current.info)
        } else {
            abandon()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            abandon()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.widget_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (providers.isEmpty()) {
            Text(
                text = stringResource(R.string.widget_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            return@ModalBottomSheet
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(providers, key = { it.provider.flattenToShortString() }) { info ->
                ProviderRow(
                    info = info,
                    onClick = {
                        val appWidgetId = controller.allocateId()
                        if (controller.bindIfAllowed(appWidgetId, info)) {
                            configureOrFinish(appWidgetId, info)
                        } else {
                            pending = Pending(appWidgetId, info)
                            runCatching {
                                bindLauncher.launch(
                                    controller.bindPermissionIntent(appWidgetId, info)
                                )
                            }.onFailure { abandon() }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(info: AppWidgetProviderInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val label = remember(info) {
        info.loadLabel(context.packageManager).orEmpty().ifBlank {
            info.provider.shortClassName
        }
    }
    val preview = remember(info) {
        runCatching {
            (info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0))?.toImageBitmap(96)
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        preview?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = info.provider.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class Pending(val appWidgetId: Int, val info: AppWidgetProviderInfo)
