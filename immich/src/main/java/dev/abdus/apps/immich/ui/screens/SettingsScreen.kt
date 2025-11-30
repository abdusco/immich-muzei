package dev.abdus.apps.immich.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.abdus.apps.immich.data.ImmichAlbumUiModel
import dev.abdus.apps.immich.data.ImmichTagUiModel
import dev.abdus.apps.immich.data.ImmichUiState
import dev.abdus.apps.immich.ui.AlbumPickerActivity
import dev.abdus.apps.immich.ui.ConfigActivity
import dev.abdus.apps.immich.ui.ImmichImageLoader
import dev.abdus.apps.immich.ui.SettingsViewModel
import dev.abdus.apps.immich.ui.TagPickerActivity

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageLoader = remember(context) { ImmichImageLoader.create(context) }

    // Check if Immich is the active Muzei source
    val isImmichActive = remember { mutableStateOf(true) }

    // Re-check whenever this composable becomes active (including after returning from Muzei)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isImmichActive.value = viewModel.isImmichActiveSource()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Initial check
        isImmichActive.value = viewModel.isImmichActiveSource()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ImmichContent(
        state = state,
        imageLoader = imageLoader,
        isImmichActive = isImmichActive.value,
        onChangeAlbum = {
            context.startActivity(Intent(context, AlbumPickerActivity::class.java))
        },
        onChangeTags = {
            context.startActivity(Intent(context, TagPickerActivity::class.java))
        },
        onEditConfig = {
            context.startActivity(Intent(context, ConfigActivity::class.java))
        },
        onClearPhotos = viewModel::clearPhotos,
        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
        onCreatedAfterChanged = { daysBack: Int? ->
            viewModel.updateFilterDaysBack(daysBack)
        },
        onLaunchChooseProvider = { viewModel.launchChooseMuzeiSource(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmichContent(
    state: ImmichUiState,
    imageLoader: ImageLoader,
    onChangeAlbum: () -> Unit,
    onChangeTags: () -> Unit,
    onEditConfig: () -> Unit,
    onClearPhotos: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onCreatedAfterChanged: (Int?) -> Unit,
    isImmichActive: Boolean,
    onLaunchChooseProvider: (Context) -> Unit
) {
    when {
        !state.config.isConfigured -> ImmichEmptyState()
        else -> {
            var menuExpanded by remember { mutableStateOf(false) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Immich") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        actions = {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        menuExpanded = false
                                        onEditConfig()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Clear photos") },
                                    onClick = {
                                        menuExpanded = false
                                        onClearPhotos()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) }
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                when {
                    state.isLoading -> ImmichLoading()
                    else -> SelectedItemsView(
                        state = state,
                        imageLoader = imageLoader,
                        onChangeAlbum = onChangeAlbum,
                        onChangeTags = onChangeTags,
                        onToggleFavoritesOnly = onToggleFavoritesOnly,
                        paddingValues = paddingValues,
                        onCreatedAfterChanged = onCreatedAfterChanged,
                        isImmichActive = isImmichActive,
                        onLaunchChooseProvider = onLaunchChooseProvider
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmichEmptyState() {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connect to Immich",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Configure your server settings to get started",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { context.startActivity(Intent(context, ConfigActivity::class.java)) }) {
            Text("Configure Settings")
        }
    }
}

@Composable
private fun ImmichLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SelectedItemsView(
    state: ImmichUiState,
    imageLoader: ImageLoader,
    onChangeAlbum: () -> Unit,
    onChangeTags: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    paddingValues: PaddingValues,
    isImmichActive: Boolean,
    onCreatedAfterChanged: (Int?) -> Unit,
    onLaunchChooseProvider: (Context) -> Unit
) {
    // compute days-back to pre-select slider: if createdAfter present, use stored preset index
    val createdAfterDaysBack: Int? = state.config.filterPresetDaysBack

    val selectedAlbums = state.albums.filter { album -> album.id in state.config.selectedAlbumIds }
    val selectedTags = state.tags.filter { tag -> state.config.selectedTagIds.contains(tag.id) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            if (!isImmichActive) {
                SourceWarningBanner(context, onLaunchChooseProvider)
            }
        }

        item {
            state.errorMessage?.let {
                ErrorBanner(it)
            }
        }

        item {
            SelectedAlbums(selectedAlbums, onChangeAlbum, imageLoader)
        }

        item {
            SelectedTags(selectedTags, onChangeTags)
        }

        item {
            FavoritesOnly(state, onToggleFavoritesOnly)
        }

        item {
            DateFilters(
                createdAfterDaysBack = createdAfterDaysBack,
                onCreatedAfterChanged = onCreatedAfterChanged
            )
        }
    }
}

@Composable
fun DateFilters(
    // days-back value (e.g. 7 for last week). null means filter disabled.
    createdAfterDaysBack: Int?,
    onCreatedAfterChanged: (Int?) -> Unit
) {
    // Preset options and mapping to days back from today
    val presets = listOf("Today", "Last week", "2 weeks", "Last month", "2 months", "6 months")
    val daysBack = listOf(0, 7, 14, 30, 60, 180)

    // Try to infer initial index from createdAfterDaysBack if provided, otherwise default to Last week (index 1)
    val initialIndex = remember(createdAfterDaysBack) {
        try {
            if (createdAfterDaysBack != null) {
                val idx = daysBack.indexOf(createdAfterDaysBack)
                if (idx >= 0) idx else 1
            } else {
                1 // default: Last week
            }
        } catch (_: Exception) {
            1
        }
    }

    var sliderPosition by remember { mutableStateOf(initialIndex.toFloat()) }
    var pendingIndex by remember { mutableStateOf(initialIndex) }
    var enabled by remember { mutableStateOf(createdAfterDaysBack != null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Taken since", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Show photos that were created after this date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Toggle for enabling/disabling the slider
                Switch(checked = enabled, onCheckedChange = { checked ->
                    enabled = checked
                    if (!checked) {
                        // when disabling, clear the filter immediately
                        onCreatedAfterChanged(null)
                        sliderPosition = 1f
                        pendingIndex = 1
                    } else {
                        // when enabling, immediately apply the current pending selection
                        val days = daysBack.getOrElse(pendingIndex) { 7 }
                        onCreatedAfterChanged(days)
                    }
                })
            }

            Spacer(modifier = Modifier.width(4.dp))

            Slider(
                value = sliderPosition,
                onValueChange = { value ->
                    // determine nearest index and snap the slider to that exact position immediately
                    sliderPosition = value

                },
                onValueChangeFinished = {
                    if (enabled) {
                        pendingIndex = sliderPosition.toInt().coerceIn(0, presets.lastIndex)
                        val days = daysBack.getOrElse(pendingIndex) { 7 }
                        onCreatedAfterChanged(days)
                    }
                },
                // Make slider non-interactive when filter is disabled
                enabled = enabled,
                valueRange = 0f..(presets.lastIndex).toFloat(),
                steps = presets.size - 2 // discrete steps between endpoints

            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                presets.forEachIndexed { idx, label ->
                    val isSelected = idx == pendingIndex
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Left,
                        )
                    }
                }
            }

            // Removed Apply / Clear buttons: changes are applied immediately when enabled
        }
    }
}


@Composable
private fun FavoritesOnly(state: ImmichUiState, onToggleFavoritesOnly: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Favorites only", style = MaterialTheme.typography.titleMedium)
                Text(text = "Show only favorited photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.config.favoritesOnly, onCheckedChange = { onToggleFavoritesOnly() })
        }
    }
}

@Composable
private fun SelectedTags(selectedTags: List<ImmichTagUiModel>, onAddTags: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Filter by tags",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        if (selectedTags.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "No tags selected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onAddTags) { Text("Add Tags") }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (selectedTags.size) {
                            1 -> "1 tag selected"
                            else -> "${selectedTags.size} tags selected"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Show selected tags as chips (up to 3), with a "+N more" chip if there are more
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedTags.take(3)) { tag ->
                            SuggestionChip(
                                enabled = false,
                                onClick = {},
                                label = {
                                    Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            )
                        }
                        if (selectedTags.size > 3) {
                            item {
                                SuggestionChip(
                                    enabled = false,
                                    onClick = {},
                                    label = { Text("+${selectedTags.size - 3} more") }
                                )
                            }
                        }
                    }

                    Button(onClick = onAddTags, modifier = Modifier.fillMaxWidth()) {
                        Text("Change Tags")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedAlbums(selectedAlbums: List<ImmichAlbumUiModel>, onChangeAlbum: () -> Unit, imageLoader: ImageLoader) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Albums",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        if (selectedAlbums.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "No albums selected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "All albums will be used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onChangeAlbum) { Text("Add Albums") }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = when (selectedAlbums.size) {
                            1 -> "1 album selected"
                            else -> "${selectedAlbums.size} albums selected"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )


                    // Show textual summary of titles (up to 3)
                    val previewTitles = selectedAlbums.take(3).joinToString(", ") { it.title }
                    Text(
                        text = if (selectedAlbums.size <= 3) previewTitles else "$previewTitles, +${selectedAlbums.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Thumbnails preview (scrollable horizontally)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(selectedAlbums, key = { it.id }) { album ->
                            Card(modifier = Modifier.size(96.dp), shape = RoundedCornerShape(8.dp)) {
                                if (album.coverUrl != null) {
                                    AsyncImage(
                                        model = album.coverUrl,
                                        imageLoader = imageLoader,
                                        contentDescription = album.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(onClick = onChangeAlbum, modifier = Modifier.fillMaxWidth()) {
                        Text("Change Albums")
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(errorMessage: String) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(text = errorMessage, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SourceWarningBanner(context: Context, onLaunchChooseProvider: (Context) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Immich is not your active wallpaper source",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "Change your Muzei source to Immich to see photos from your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Button(onClick = { onLaunchChooseProvider(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Change Source")
            }
        }
    }
}
