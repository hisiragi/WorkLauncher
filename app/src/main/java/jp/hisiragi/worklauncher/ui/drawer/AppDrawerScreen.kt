package jp.hisiragi.worklauncher.ui.drawer

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.hisiragi.worklauncher.R
import jp.hisiragi.worklauncher.core.AppViewModelFactory
import jp.hisiragi.worklauncher.domain.AppCategory
import jp.hisiragi.worklauncher.domain.DrawerSort
import jp.hisiragi.worklauncher.ui.components.AppGridItem
import jp.hisiragi.worklauncher.ui.components.EmptyState
import jp.hisiragi.worklauncher.ui.home.FocusGateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    startWithKeyboard: Boolean = false,
    viewModel: AppDrawerViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selected by viewModel.selectedApp.collectAsStateWithLifecycle()
    val gated by viewModel.gatedApp.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var sortMenuOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Reordering keeps the grid anchored to whatever item was on top, which
    // lands the user mid-list and reads as the sort not having applied.
    LaunchedEffect(state.settings.drawerSort, state.categoryFilter, state.query) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(startWithKeyboard) {
        if (startWithKeyboard) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.drawer_title_count, state.totalCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.drawer_sort))
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            DrawerSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(sort)) },
                                    leadingIcon = {
                                        if (sort == state.settings.drawerSort) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSort(sort)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (state.showHidden) {
                                                R.string.drawer_hide_hidden
                                            } else {
                                                R.string.drawer_show_hidden
                                            }
                                        )
                                    )
                                },
                                onClick = {
                                    viewModel.toggleShowHidden()
                                    sortMenuOpen = false
                                },
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
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.drawer_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.hasQuery) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.action_clear),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        val match = state.apps.firstOrNull()
                        if (match != null) {
                            keyboard?.hide()
                            viewModel.launch(context, match)
                        } else {
                            viewModel.webSearch(context)
                        }
                    }
                ),
            )

            Spacer(Modifier.height(8.dp))

            CategoryFilterRow(
                selected = state.categoryFilter,
                onSelect = viewModel::setCategoryFilter,
            )

            Spacer(Modifier.height(4.dp))

            if (state.apps.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(stringResource(R.string.drawer_no_matches))
                    if (state.hasQuery) {
                        TextButton(onClick = { viewModel.webSearch(context) }) {
                            Text(stringResource(R.string.drawer_search_web, state.query))
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(state.settings.gridColumns),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.apps, key = { it.componentKey }) { app ->
                        AppGridItem(
                            app = app,
                            showLabel = state.settings.showAppLabels,
                            iconSize = 48.dp,
                            dimmed = app.hidden,
                            onClick = { viewModel.launch(context, app) },
                            onLongClick = { viewModel.select(app) },
                        )
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AppActionsSheet(
            app = app,
            onDismiss = { viewModel.select(null) },
            onToggleFavorite = { viewModel.toggleFavorite(app) },
            onToggleHidden = { viewModel.toggleHidden(app) },
            onToggleDistraction = { viewModel.toggleDistraction(app) },
            onSetCategory = { viewModel.setCategory(app, it) },
            onRename = { viewModel.rename(app, it) },
        )
    }

    gated?.let { app ->
        FocusGateDialog(
            app = app,
            onDismiss = viewModel::dismissGate,
            onConfirm = { viewModel.launchGatedApp(context) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    selected: AppCategory?,
    onSelect: (AppCategory?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.category_all)) },
        )
        AppCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(categoryLabel(category)) },
            )
        }
    }
}

@Composable
fun categoryLabel(category: AppCategory): String = stringResource(
    when (category) {
        AppCategory.WORK -> R.string.category_work
        AppCategory.PERSONAL -> R.string.category_personal
        AppCategory.UTILITY -> R.string.category_utility
        AppCategory.UNSORTED -> R.string.category_unsorted
    }
)

@Composable
private fun sortLabel(sort: DrawerSort): String = stringResource(
    when (sort) {
        DrawerSort.ALPHABETICAL -> R.string.drawer_sort_alpha
        DrawerSort.MOST_USED -> R.string.drawer_sort_usage
        DrawerSort.RECENT -> R.string.drawer_sort_recent
    }
)
