package jp.hisiragi.worklauncher.ui.agenda

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.domain.AgendaEvent
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.components.PermissionBanner
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.ui.components.StatTile
import jp.hisiragi.worklauncher.util.startActivitySafely
import jp.hisiragi.worklauncher.util.TimeUtils
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    onBack: () -> Unit,
    viewModel: AgendaViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agenda_title)) },
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
                    message = stringResource(R.string.agenda_permission_rationale),
                    actionLabel = stringResource(R.string.action_grant),
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                    modifier = Modifier.padding(16.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    SectionCard(title = stringResource(R.string.agenda_meeting_load)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatTile(
                                value = TimeUtils.formatDuration(state.meetingMinutesToday),
                                label = stringResource(R.string.agenda_meetings_today),
                            )
                            StatTile(
                                value = state.eventsByDay
                                    .firstOrNull { it.first == TimeUtils.today() }
                                    ?.second?.size?.toString() ?: "0",
                                label = stringResource(R.string.agenda_count_today),
                                accent = MaterialTheme.colorScheme.secondary,
                            )
                            StatTile(
                                value = state.eventsByDay.sumOf { it.second.size }.toString(),
                                label = stringResource(R.string.agenda_count_upcoming),
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = state.daysAhead == days,
                                onClick = { viewModel.setDaysAhead(days) },
                                label = { Text(stringResource(R.string.agenda_days, days)) },
                            )
                        }
                    }
                }

                if (state.eventsByDay.isEmpty()) {
                    item { EmptyState(stringResource(R.string.agenda_empty_range)) }
                }

                state.eventsByDay.forEach { (date, events) ->
                    item(key = "header-${date.toEpochDay()}") { DayHeader(date) }
                    items(
                        count = events.size,
                        key = { index -> "${date.toEpochDay()}-${events[index].id}-$index" },
                    ) { index ->
                        EventRow(
                            event = events[index],
                            use24h = state.settings.use24HourClock,
                            onOpenLocation = { openLocation(context, it) },
                        )
                    }
                }
            }
        }
    }
}

/** Opens a meeting link directly, or hands a physical address to a map app. */
private fun openLocation(context: Context, location: String) {
    val uri = if (location.startsWith("http://") || location.startsWith("https://")) {
        Uri.parse(location)
    } else {
        Uri.parse("geo:0,0?q=" + Uri.encode(location))
    }
    context.startActivitySafely(Intent(Intent.ACTION_VIEW, uri))
}

@Composable
private fun DayHeader(date: LocalDate) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.width(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = TimeUtils.formatDate(date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (date == TimeUtils.today()) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EventRow(event: AgendaEvent, use24h: Boolean, onOpenLocation: (String) -> Unit) {
    val now = System.currentTimeMillis()
    val isNow = event.startAt <= now && event.endAt >= now
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = event.location != null) {
                // Meeting links usually live in the location field.
                event.location?.let(onOpenLocation)
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (event.calendarColor != 0) {
                        Color(event.calendarColor)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (event.allDay) {
                    stringResource(R.string.agenda_all_day)
                } else {
                    TimeUtils.formatTime(event.startAt, use24h) + " – " +
                        TimeUtils.formatTime(event.endAt, use24h)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            event.location?.let { location ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.width(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (isNow) {
            Text(
                text = stringResource(R.string.agenda_now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
