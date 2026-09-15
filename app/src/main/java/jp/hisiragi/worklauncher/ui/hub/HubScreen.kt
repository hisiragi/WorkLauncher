package jp.hisiragi.worklauncher.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.ui.navigation.Route

private data class HubTile(
    val route: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
)

/** One place listing every work tool the launcher ships with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val tiles = listOf(
        HubTile(Route.TASKS, R.string.hub_tasks, R.string.hub_tasks_sub, Icons.Filled.CheckCircle),
        HubTile(Route.FOCUS, R.string.hub_focus, R.string.hub_focus_sub, Icons.Filled.Timer),
        HubTile(Route.TIME_CARD, R.string.hub_timecard, R.string.hub_timecard_sub, Icons.Filled.Schedule),
        HubTile(Route.AGENDA, R.string.hub_agenda, R.string.hub_agenda_sub, Icons.Filled.CalendarMonth),
        HubTile(Route.NOTES, R.string.hub_notes, R.string.hub_notes_sub, Icons.Filled.EditNote),
        HubTile(Route.CONTACTS, R.string.hub_contacts, R.string.hub_contacts_sub, Icons.Filled.Contacts),
        HubTile(Route.EXPENSES, R.string.hub_expenses, R.string.hub_expenses_sub, Icons.AutoMirrored.Filled.ReceiptLong),
        HubTile(Route.USAGE, R.string.hub_usage, R.string.hub_usage_sub, Icons.Filled.Insights),
        HubTile(Route.DRAWER, R.string.hub_apps, R.string.hub_apps_sub, Icons.Filled.Apps),
        HubTile(Route.SETTINGS, R.string.hub_settings, R.string.hub_settings_sub, Icons.Filled.Settings),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hub_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tiles, key = { it.route }) { tile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(tile.route) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(
                            imageVector = tile.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(tile.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(tile.subtitleRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
