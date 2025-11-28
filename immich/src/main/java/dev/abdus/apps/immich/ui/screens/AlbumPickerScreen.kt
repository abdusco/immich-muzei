package dev.abdus.apps.immich.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.abdus.apps.immich.data.AlbumSortBy
import dev.abdus.apps.immich.data.ImmichAlbumUiModel
import dev.abdus.apps.immich.ui.AlbumPickerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerScreen(
    state: AlbumPickerUiState,
    imageLoader: coil3.ImageLoader,
    onAlbumClick: (String) -> Unit,
    onSortByChange: (AlbumSortBy) -> Unit,
    onToggleReversed: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var filterText by remember { mutableStateOf("") }

    val filteredAlbums = remember(state.albums, filterText) {
        if (filterText.isBlank()) {
            state.albums
        } else {
            state.albums.filter { album ->
                album.title.contains(filterText, ignoreCase = true)
            }
        }
    }

    val selectedAlbumIds = remember(state.config.selectedAlbumIds) {
        state.config.selectedAlbumIds
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Album") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        androidx.compose.material3.OutlinedTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
                            label = { Text("Filter albums") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        AlbumSortControls(
                            sortBy = state.sortBy,
                            sortReversed = state.sortReversed,
                            onSortByChange = onSortByChange,
                            onToggleReversed = onToggleReversed
                        )
                    }
                    item {
                        state.errorMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    item {
                        val selectedCount = selectedAlbumIds.size
                        Text(
                            text = when (selectedCount) {
                                0 -> "No albums selected (all albums will be used)"
                                1 -> "1 album selected"
                                else -> "$selectedCount albums selected"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(
                        items = filteredAlbums,
                        key = { album -> album.id },
                        contentType = { "album_item" }
                    ) { album ->
                        val isSelected = album.id in selectedAlbumIds
                        AlbumPickerRow(
                            album = album,
                            imageLoader = imageLoader,
                            selected = isSelected,
                            onClick = {
                                onAlbumClick(album.id)
                            }
                        )
                    }
                }
            }
        }
    }

@Composable
private fun AlbumPickerRow(
    album: ImmichAlbumUiModel,
    imageLoader: coil3.ImageLoader,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Checkbox(
                checked = selected,
                onCheckedChange = null
            )

            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.assetCount} photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumSortControls(
    sortBy: AlbumSortBy,
    sortReversed: Boolean,
    onSortByChange: (AlbumSortBy) -> Unit,
    onToggleReversed: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort by:", style = MaterialTheme.typography.bodyMedium)

            var expanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { expanded = true }) {
                    Text(when (sortBy) {
                        AlbumSortBy.NAME -> "Name"
                        AlbumSortBy.ASSET_COUNT -> "Count"
                        AlbumSortBy.UPDATED_AT -> "Updated"
                        AlbumSortBy.MOST_RECENT_PHOTO -> "Most Recent Photo"
                    })
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Name") },
                        onClick = {
                            onSortByChange(AlbumSortBy.NAME)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Asset Count") },
                        onClick = {
                            onSortByChange(AlbumSortBy.ASSET_COUNT)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Updated At") },
                        onClick = {
                            onSortByChange(AlbumSortBy.UPDATED_AT)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Most Recent Photo") },
                        onClick = {
                            onSortByChange(AlbumSortBy.MOST_RECENT_PHOTO)
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(onClick = onToggleReversed) {
            Text(if (sortReversed) "↓" else "↑")
        }
    }
}

