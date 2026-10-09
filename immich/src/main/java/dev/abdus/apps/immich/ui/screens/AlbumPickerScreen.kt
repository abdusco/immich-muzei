package dev.abdus.apps.immich.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.abdus.apps.immich.data.AlbumSortBy
import dev.abdus.apps.immich.data.ImmichAlbumUiModel
import dev.abdus.apps.immich.ui.AlbumPickerUiState

private fun AlbumSortBy.label(): String = when (this) {
    AlbumSortBy.NAME -> "Name"
    AlbumSortBy.ASSET_COUNT -> "Photo count"
    AlbumSortBy.UPDATED_AT -> "Last updated"
    AlbumSortBy.MOST_RECENT_PHOTO -> "Most recent photo"
}

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

    val selectedAlbumIds = state.config.selectedAlbumIds

    // Partition filtered albums into picked / available and sort each section separately
    val (pickedAlbums, availableAlbums) = remember(filteredAlbums, selectedAlbumIds, state.sortBy, state.sortReversed) {
        val (picked, available) = filteredAlbums.partition { it.id in selectedAlbumIds }

        fun sortList(list: List<ImmichAlbumUiModel>): List<ImmichAlbumUiModel> {
            val sorted = when (state.sortBy) {
                AlbumSortBy.NAME -> list.sortedBy { it.title }
                AlbumSortBy.ASSET_COUNT -> list.sortedBy { it.assetCount }
                AlbumSortBy.UPDATED_AT -> list.sortedBy { it.updatedAt ?: "" }
                AlbumSortBy.MOST_RECENT_PHOTO -> list.sortedBy { it.lastModifiedAssetTimestamp ?: "" }
            }
            return if (state.sortReversed) sorted.reversed() else sorted
        }

        Pair(sortList(picked), sortList(available))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albums") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AlbumSortMenu(
                        sortBy = state.sortBy,
                        sortReversed = state.sortReversed,
                        onSortByChange = onSortByChange,
                        onToggleReversed = onToggleReversed
                    )
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 16.dp)
            ) {
                item { SearchField(filterText, { filterText = it }, "Search albums") }

                state.errorMessage?.let { item { ErrorText(it) } }

                if (selectedAlbumIds.isEmpty()) {
                    item { Hint("No albums selected, so photos come from all albums.") }
                }

                if (pickedAlbums.isNotEmpty()) {
                    item { SectionHeader("Selected (${pickedAlbums.size})") }
                    albumItems(pickedAlbums, selected = true, imageLoader, onAlbumClick)
                }

                item { SectionHeader("All albums (${availableAlbums.size})") }
                if (availableAlbums.isEmpty()) {
                    item { Hint(if (filterText.isBlank()) "No albums" else "No matching albums") }
                } else {
                    albumItems(availableAlbums, selected = false, imageLoader, onAlbumClick)
                }
            }
        }
    }
}

private fun LazyListScope.albumItems(
    albums: List<ImmichAlbumUiModel>,
    selected: Boolean,
    imageLoader: coil3.ImageLoader,
    onAlbumClick: (String) -> Unit
) {
    itemsIndexed(
        items = albums,
        key = { _, album -> album.id },
        contentType = { _, _ -> "album_item" }
    ) { index, album ->
        AlbumPickerRow(
            album = album,
            imageLoader = imageLoader,
            selected = selected,
            onClick = { onAlbumClick(album.id) },
            modifier = Modifier
                .animateItem()
                .groupItem(index, albums.size)
        )
    }
}

@Composable
private fun AlbumPickerRow(
    album: ImmichAlbumUiModel,
    imageLoader: coil3.ImageLoader,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            val thumbModifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = thumbModifier
                )
            } else {
                Box(
                    modifier = thumbModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        headlineContent = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${album.assetCount} photos") },
        trailingContent = { Checkbox(checked = selected, onCheckedChange = null) }
    )
}

@Composable
private fun AlbumSortMenu(
    sortBy: AlbumSortBy,
    sortReversed: Boolean,
    onSortByChange: (AlbumSortBy) -> Unit,
    onToggleReversed: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AlbumSortBy.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    trailingIcon = {
                        if (option == sortBy) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        onSortByChange(option)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Reverse order") },
                trailingIcon = {
                    Checkbox(checked = sortReversed, onCheckedChange = null)
                },
                onClick = {
                    onToggleReversed()
                    expanded = false
                }
            )
        }
    }
}
