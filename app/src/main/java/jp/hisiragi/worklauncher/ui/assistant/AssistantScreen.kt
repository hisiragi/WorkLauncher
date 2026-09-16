package jp.hisiragi.worklauncher.ui.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.domain.ChatMessage
import jp.hisiragi.worklauncher.domain.LlmAvailability
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.components.SectionCard
import jp.hisiragi.worklauncher.util.Launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AssistantViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshVoiceSupport()
        if (granted) viewModel.startVoice()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshNotificationAccess(context)
        viewModel.refreshVoiceSupport()
    }

    LaunchedEffect(availability) { viewModel.refreshVoiceSupport() }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assistant_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setSearchEnabled(!state.searchEnabled) }) {
                        Icon(
                            imageVector = if (state.searchEnabled) {
                                Icons.Filled.TravelExplore
                            } else {
                                Icons.Filled.SearchOff
                            },
                            contentDescription = stringResource(R.string.assistant_toggle_search),
                            tint = if (state.searchEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearChat) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.assistant_clear),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when (val current = availability) {
                is LlmAvailability.Disabled -> {
                    NotConfigured(
                        message = stringResource(R.string.assistant_not_configured),
                        onOpenSettings = onOpenSettings,
                    )
                    return@Column
                }

                is LlmAvailability.Unavailable -> {
                    NotConfigured(
                        message = current.reason,
                        onOpenSettings = onOpenSettings,
                    )
                    return@Column
                }

                else -> Unit
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    NotificationDigestCard(
                        state = state,
                        onSummarize = viewModel::summarizeNotifications,
                        onGrantAccess = {
                            Launch.notificationListenerSettings(context)
                        },
                    )
                }

                if (state.messages.isEmpty()) {
                    item { EmptyState(stringResource(R.string.assistant_empty)) }
                }

                items(state.messages) { message -> MessageBubble(message) }

                if (state.recording) {
                    item {
                        Text(
                            text = stringResource(
                                if (state.modelTakesAudio) {
                                    R.string.assistant_recording_direct
                                } else {
                                    R.string.assistant_recording_transcribe
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (state.thinking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.assistant_thinking),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                error?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.assistant_hint)) },
                    maxLines = 4,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        when {
                            state.recording -> viewModel.stopVoice()
                            state.voiceAvailable -> viewModel.startVoice()
                            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = !state.thinking,
                ) {
                    Icon(
                        imageVector = if (state.recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = stringResource(
                            if (state.recording) R.string.assistant_stop_voice
                            else R.string.assistant_voice
                        ),
                        tint = if (state.recording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.send(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank() && !state.thinking,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.assistant_send),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotConfigured(message: String, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.assistant_open_settings))
        }
    }
}

@Composable
private fun NotificationDigestCard(
    state: AssistantUiState,
    onSummarize: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.assistant_digest_title),
        icon = Icons.Filled.Notifications,
    ) {
        if (!state.hasNotificationAccess) {
            Text(
                text = stringResource(R.string.assistant_digest_needs_access),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onGrantAccess) {
                Text(stringResource(R.string.assistant_digest_grant))
            }
            return@SectionCard
        }

        Text(
            text = stringResource(R.string.assistant_digest_count, state.notificationCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.digest?.let { summary ->
            Spacer(Modifier.height(8.dp))
            Text(text = summary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSummarize,
            enabled = !state.digestRunning && state.notificationCount > 0,
        ) {
            if (state.digestRunning) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.assistant_digest_run))
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val context = LocalContext.current
    val alignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val container = if (message.fromUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (message.fromUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = container,
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = stringResource(
                        if (message.fromUser) R.string.assistant_you else R.string.assistant_model
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = content.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                )
                if (message.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.assistant_sources),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = content.copy(alpha = 0.7f),
                    )
                    message.sources.forEachIndexed { index, source ->
                        Text(
                            text = "${index + 1}. ${source.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                Launch.url(context, source.url)
                            },
                        )
                    }
                }
            }
        }
    }
}
