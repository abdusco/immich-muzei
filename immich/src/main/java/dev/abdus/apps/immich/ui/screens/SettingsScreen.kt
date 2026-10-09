package dev.abdus.apps.immich.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.abdus.apps.immich.data.ImmichAlbumUiModel
import dev.abdus.apps.immich.ui.AlbumPickerActivity
import dev.abdus.apps.immich.ui.ConfigActivity
import dev.abdus.apps.immich.ui.ImmichImageLoaderProvider
import dev.abdus.apps.immich.ui.SettingsUiState
import dev.abdus.apps.immich.ui.SettingsViewModel
import dev.abdus.apps.immich.ui.TagPickerActivity

// "Taken since" presets: label to days back from today. null means no date filter.
private val DATE_PRESETS: List<Pair<String, Int?>> = listOf(
    "Any time" to null,
    "Today" to 0,
    "Last week" to 7,
    "Last 2 weeks" to 14,
    "Last month" to 30,
    "Last 2 months" to 60,
    "Last 6 months" to 180,
)

private fun dateLabel(daysBack: Int?): String =
    DATE_PRESETS.firstOrNull { it.second == daysBack }?.first ?: "Last $daysBack days"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imageLoader = remember(context) { ImmichImageLoaderProvider.get(context) }

    // Re-check whether Immich is the active Muzei source whenever the screen resumes
    // (including after returning from Muzei)
    var isImmichActive by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isImmichActive = viewModel.isImmichActiveSource()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isImmichActive = viewModel.isImmichActiveSource()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openConfig = { context.startActivity(Intent(context, ConfigActivity::class.java)) }

    if (!state.config.isConfigured) {
        ImmichEmptyState(onConnect = openConfig)
        return
    }

    ImmichContent(
        state = state,
        isImmichActive = isImmichActive,
        imageLoader = imageLoader,
        onChangeAlbum = { context.startActivity(Intent(context, AlbumPickerActivity::class.java)) },
        onChangeTags = { context.startActivity(Intent(context, TagPickerActivity::class.java)) },
        onEditConfig = openConfig,
        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
        onCreatedAfterChanged = viewModel::updateFilterDaysBack,
        onLaunchChooseProvider = viewModel::launchChooseMuzeiSource
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmichContent(
    state: SettingsUiState,
    isImmichActive: Boolean,
    imageLoader: ImageLoader,
    onChangeAlbum: () -> Unit,
    onChangeTags: () -> Unit,
    onEditConfig: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onCreatedAfterChanged: (Int?) -> Unit,
    onLaunchChooseProvider: (Context) -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDateDialog by remember { mutableStateOf(false) }

    val selectedAlbums = state.albums.filter { it.id in state.config.selectedAlbumIds }
    val selectedTags = state.tags.filter { it.id in state.config.selectedTagIds }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Immich") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp
            )
        ) {
            if (!isImmichActive) {
                item {
                    NoticeCard(
                        title = "Immich is not your active wallpaper source",
                        body = "Switch your Muzei source to Immich to see photos from your library.",
                        action = "Change source",
                        onAction = { onLaunchChooseProvider(context) },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                }
            }

            item { SectionHeader("Photos") }
            item {
                SettingsGroup {
                    Column(Modifier.clickable(onClick = onChangeAlbum)) {
                        NavigationItem(
                            icon = Icons.Outlined.PhotoLibrary,
                            title = "Albums",
                            summary = summarize(selectedAlbums.map { it.title }, empty = "All albums")
                        )
                        if (selectedAlbums.isNotEmpty()) {
                            AlbumThumbnails(selectedAlbums, imageLoader)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    NavigationItem(
                        icon = Icons.AutoMirrored.Outlined.Label,
                        title = "Tags",
                        summary = summarize(selectedTags.map { it.name }, empty = "Any tag"),
                        modifier = Modifier.clickable(onClick = onChangeTags)
                    )
                }
            }

            item { SectionHeader("Filters") }
            item {
                SettingsGroup {
                    ListItem(
                        modifier = Modifier.toggleable(
                            value = state.config.favoritesOnly,
                            role = Role.Switch,
                            onValueChange = { onToggleFavoritesOnly() }
                        ),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                        headlineContent = { Text("Favorites only") },
                        supportingContent = { Text("Show only favorited photos") },
                        trailingContent = { Switch(checked = state.config.favoritesOnly, onCheckedChange = null) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    NavigationItem(
                        icon = Icons.Outlined.CalendarMonth,
                        title = "Taken since",
                        summary = dateLabel(state.config.filterPresetDaysBack),
                        modifier = Modifier.clickable { showDateDialog = true }
                    )
                }
            }

            item { SectionHeader("Server") }
            item {
                SettingsGroup {
                    NavigationItem(
                        icon = Icons.Outlined.Dns,
                        title = "Connection",
                        summary = state.config.serverUrl.orEmpty(),
                        modifier = Modifier.clickable(onClick = onEditConfig)
                    )
                }
            }
        }
    }

    if (showDateDialog) {
        DatePresetDialog(
            selected = state.config.filterPresetDaysBack,
            onSelect = {
                showDateDialog = false
                onCreatedAfterChanged(it)
            },
            onDismiss = { showDateDialog = false }
        )
    }
}

private fun summarize(names: List<String>, empty: String): String = when {
    names.isEmpty() -> empty
    names.size <= 3 -> names.joinToString(", ")
    else -> names.take(3).joinToString(", ") + ", +${names.size - 3} more"
}

@Composable
private fun AlbumThumbnails(albums: List<ImmichAlbumUiModel>, imageLoader: ImageLoader) {
    LazyRow(
        modifier = Modifier.padding(bottom = 16.dp),
        // Line up the first thumbnail with the row text (16dp padding + 24dp icon + 16dp gap)
        contentPadding = PaddingValues(start = 56.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            val thumbModifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.medium)
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    imageLoader = imageLoader,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = thumbModifier
                )
            } else {
                Box(
                    modifier = thumbModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = album.title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationItem(
    icon: ImageVector,
    title: String,
    summary: String,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
    )
}

@Composable
private fun DatePresetDialog(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
        title = { Text("Taken since") },
        text = {
            Column(Modifier.selectableGroup()) {
                DATE_PRESETS.forEach { (label, days) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = days == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(days) }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RadioButton(selected = days == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NoticeCard(
    title: String,
    body: String,
    containerColor: Color,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (action == null) 16.dp else 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                    Text(action)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmichEmptyState(onConnect: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Immich") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Connect to Immich",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add your server URL and API key to start showing photos.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onConnect) { Text("Connect server") }
        }
    }
}
