package dev.abdus.apps.immich.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.abdus.apps.immich.data.ImmichAlbumUiModel
import dev.abdus.apps.immich.data.ImmichUiState
import dev.abdus.apps.immich.ui.AlbumPickerActivity
import dev.abdus.apps.immich.ui.ConfigActivity
import dev.abdus.apps.immich.ui.ImmichImageLoader
import dev.abdus.apps.immich.ui.ImmichSettingsViewModel
import dev.abdus.apps.immich.ui.TagPickerActivity

@Composable
fun ImmichSettingsScreen(
    viewModel: ImmichSettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageLoader = remember(context) { ImmichImageLoader.create(context) }

    // Check if Immich is the active Muzei source
    val isImmichActive = remember { mutableStateOf(true) }

    // Re-check whenever this composable becomes active (including after returning from Muzei)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
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
            context.startActivity(android.content.Intent(context, AlbumPickerActivity::class.java))
        },
        onRemoveAlbum = viewModel::toggleAlbum,
        onRemoveTag = viewModel::toggleTag,
        onAddTags = {
            context.startActivity(android.content.Intent(context, TagPickerActivity::class.java))
        },
        onEditConfig = {
            context.startActivity(android.content.Intent(context, ConfigActivity::class.java))
        },
        onClearPhotos = viewModel::clearPhotos,
        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmichContent(
    state: ImmichUiState,
    imageLoader: coil3.ImageLoader,
    onChangeAlbum: () -> Unit,
    onRemoveAlbum: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddTags: () -> Unit,
    onEditConfig: () -> Unit,
    onClearPhotos: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    isImmichActive: Boolean
) {
    when {
        !state.config.isConfigured -> {
            // Show empty state with button to launch ConfigActivity
            ImmichEmptyState()
        }
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
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Clear photos") },
                                    onClick = {
                                        menuExpanded = false
                                        onClearPhotos()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    }
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
                        onRemoveAlbum = onRemoveAlbum,
                        onRemoveTag = onRemoveTag,
                        onAddTags = onAddTags,
                        onToggleFavoritesOnly = onToggleFavoritesOnly,
                        paddingValues = paddingValues,
                        isImmichActive = isImmichActive
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
        Button(
            onClick = {
                context.startActivity(android.content.Intent(context, ConfigActivity::class.java))
            }
        ) {
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
    imageLoader: coil3.ImageLoader,
    onChangeAlbum: () -> Unit,
    onRemoveAlbum: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddTags: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    paddingValues: PaddingValues,
    isImmichActive: Boolean
) {
    val selectedAlbums = state.albums.filter { album ->
        album.id in state.config.selectedAlbumIds
    }

    val selectedTags = state.tags.filter { tag ->
        state.config.selectedTagIds.contains(tag.id)
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Muzei Source Warning Banner (show at top if not active)
        if (!isImmichActive) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
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
                        Button(
                            onClick = {
                                try {
                                    // Open Muzei's source chooser
                                    val intent = android.content.Intent().apply {
                                        action = "com.google.android.apps.muzei.ACTION_CHOOSE_PROVIDER"
                                        setPackage("net.nurik.roman.muzei")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback: try opening Muzei app
                                    try {
                                        val intent = context.packageManager.getLaunchIntentForPackage("net.nurik.roman.muzei")
                                        if (intent != null) {
                                            context.startActivity(intent)
                                        }
                                    } catch (e2: Exception) {
                                        android.util.Log.e("ImmichSettings", "Could not open Muzei", e2)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Change Source")
                        }
                    }
                }
            }
        }

        item {
            state.errorMessage?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Favorites Only Toggle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleFavoritesOnly() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Favorites only",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Show only favorited photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = state.config.favoritesOnly,
                        onCheckedChange = { onToggleFavoritesOnly() }
                    )
                }
            }
        }

        // Albums Section (multiple, optional)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
                if (selectedAlbums.isNotEmpty()) {
                    Text(
                        text = when (selectedAlbums.size) {
                            1 -> "1 album"
                            else -> "${selectedAlbums.size} albums"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, end = 4.dp)
                    )
                }
            }
        }

        if (selectedAlbums.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No albums selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "All albums will be used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onChangeAlbum) {
                            Text("Add Albums")
                        }
                    }
                }
            }
        } else {
            items(
                items = selectedAlbums,
                key = { album -> "album-${album.id}" }
            ) { album ->
                SelectedAlbumRow(
                    album = album,
                    imageLoader = imageLoader,
                    onRemove = { onRemoveAlbum(album.id) }
                )
            }
            item {
                Button(
                    onClick = onChangeAlbum,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add More Albums")
                }
            }
        }

        // Tags Section
        item {
            Text(
                text = "Filter by tags",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            )
        }

        if (selectedTags.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No tags selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onAddTags) {
                            Text("Add Tags")
                        }
                    }
                }
            }
        } else {
            items(
                items = selectedTags,
                key = { tag -> "tag-${tag.id}" }
            ) { tag ->
                SelectedTagRow(
                    tag = tag,
                    onRemove = { onRemoveTag(tag.id) }
                )
            }
            item {
                Button(
                    onClick = onAddTags,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add More Tags")
                }
            }
        }
    }
}

@Composable
private fun SelectedTagRow(
    tag: dev.abdus.apps.immich.data.ImmichTagUiModel,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}

@Composable
private fun SelectedAlbumRow(
    album: ImmichAlbumUiModel,
    imageLoader: coil3.ImageLoader,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            album.coverUrl?.let { url ->
                Card(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    AsyncImage(
                        model = url,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${album.assetCount} photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}



