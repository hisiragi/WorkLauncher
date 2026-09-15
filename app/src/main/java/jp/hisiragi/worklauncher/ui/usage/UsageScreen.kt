package jp.hisiragi.worklauncher.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.components.PermissionBanner
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.ui.components.StatTile
import jp.hisiragi.worklauncher.util.Launch
import jp.hisiragi.worklauncher.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    onBack: () -> Unit,
    viewModel: UsageViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.usage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!state.permissionGranted) {
                PermissionBanner(
                    message = stringResource(R.string.usage_permission_rationale),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onClick = { Launch.usageAccessSettings(context) },
                    modifier = Modifier.padding(16.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UsageRange.entries.forEach { range ->
                            FilterChip(
                                selected = state.range == range,
                                onClick = { viewModel.setRange(range) },
                                label = { Text(rangeLabel(range)) },
                            )
                        }
                    }
                }

                item {
                    SectionCard(title = stringResource(R.string.usage_split_title)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatTile(
                                value = TimeUtils.formatDurationFromMillis(state.split.totalMillis),
                                label = stringResource(R.string.usage_total),
                            )
                            StatTile(
                                value = TimeUtils.formatDurationFromMillis(state.split.workMillis),
                                label = stringResource(R.string.category_work),
                                accent = MaterialTheme.colorScheme.primary,
                            )
                            StatTile(
                                value = TimeUtils.formatDurationFromMillis(state.split.personalMillis),
                                label = stringResource(R.string.category_personal),
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        SplitBar(state.split.workMillis, state.split.personalMillis, state.split.otherMillis)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.usage_categorise_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.usage.isEmpty()) {
                    item { EmptyState(stringResource(R.string.usage_empty)) }
                } else {
                    items(state.usage, key = { it.packageName }) { usage ->
                        val maxMillis = state.usage.first().foregroundMillis.coerceAtLeast(1)
                        UsageRow(
                            label = usage.label,
                            millis = usage.foregroundMillis,
                            fraction = usage.foregroundMillis.toFloat() / maxMillis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitBar(workMillis: Long, personalMillis: Long, otherMillis: Long) {
    val total = (workMillis + personalMillis + otherMillis).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Segment(workMillis.toFloat() / total, MaterialTheme.colorScheme.primary)
        Segment(personalMillis.toFloat() / total, MaterialTheme.colorScheme.tertiary)
        Segment(otherMillis.toFloat() / total, MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(fraction: Float, color: Color) {
    if (fraction <= 0f) return
    Box(
        modifier = Modifier
            .weight(fraction)
            .fillMaxSize()
            .background(color)
    )
}

@Composable
private fun UsageRow(label: String, millis: Long, fraction: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = TimeUtils.formatDurationFromMillis(millis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun rangeLabel(range: UsageRange): String = stringResource(
    when (range) {
        UsageRange.TODAY -> R.string.usage_range_today
        UsageRange.WEEK -> R.string.usage_range_week
    }
)
