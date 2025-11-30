package dev.abdus.apps.immich.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.abdus.apps.immich.data.ImmichTagUiModel
import dev.abdus.apps.immich.ui.TagPickerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerScreen(
    state: TagPickerUiState,
    onTagClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var filterText by remember { mutableStateOf("") }

    val filteredTags = remember(state.tags, filterText) {
        if (filterText.isBlank()) {
            state.tags
        } else {
            state.tags.filter { tag ->
                tag.name.contains(filterText, ignoreCase = true)
            }
        }
    }

    val selectedTagIds = remember(state.config.selectedTagIds) {
        state.config.selectedTagIds
    }

    // Partition into picked and available, and sort each section by name
    val (pickedTags, availableTags) = remember(filteredTags, selectedTagIds) {
        val (picked, available) = filteredTags.partition { it.id in selectedTagIds }
        val sortedPicked = picked.sortedBy { it.name }
        val sortedAvailable = available.sortedBy { it.name }
        Pair(sortedPicked, sortedAvailable)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Tags") },
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
                        OutlinedTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
                            label = { Text("Filter tags") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
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

                    // Picked tags section
                    item {
                        Text(
                            text = "Picked (${pickedTags.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    if (pickedTags.isEmpty()) {
                        item {
                            Text(
                                text = "No picked tags",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(
                            items = pickedTags,
                            key = { tag -> tag.id },
                            contentType = { "tag_item" }
                        ) { tag ->
                            TagPickerRow(
                                tag = tag,
                                selected = true,
                                onClick = { onTagClick(tag.id) }
                            )
                        }
                    }

                    // Available tags section
                    item {
                        Text(
                            text = "Available (${availableTags.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    if (availableTags.isEmpty()) {
                        item {
                            Text(
                                text = "No available tags",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(
                            items = availableTags,
                            key = { tag -> tag.id },
                            contentType = { "tag_item" }
                        ) { tag ->
                            TagPickerRow(
                                tag = tag,
                                selected = false,
                                onClick = { onTagClick(tag.id) }
                            )
                        }
                    }
                 }
        }
    }
}

@Composable
private fun TagPickerRow(
    tag: ImmichTagUiModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    // Use animateItemPlacement to animate moves between picked/available sections.
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .animateItemPlacement(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() }
            )
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

// Compatibility fallback for animateItemPlacement: if the real API isn't present on the
// classpath, this no-op keeps compilation working. Remove when using a Compose version
// that provides androidx.compose.foundation.lazy.animateItemPlacement.
fun Modifier.animateItemPlacement(): Modifier = this
