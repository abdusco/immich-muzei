package dev.abdus.apps.immich.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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

    val selectedTagIds = state.config.selectedTagIds

    // Partition into picked and available, and sort each section by name
    val (pickedTags, availableTags) = remember(filteredTags, selectedTagIds) {
        val (picked, available) = filteredTags.partition { it.id in selectedTagIds }
        Pair(picked.sortedBy { it.name }, available.sortedBy { it.name })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                item { SearchField(filterText, { filterText = it }, "Search tags") }

                state.errorMessage?.let { item { ErrorText(it) } }

                if (selectedTagIds.isEmpty()) {
                    item { Hint("No tags selected, so photos aren't filtered by tag.") }
                }

                if (pickedTags.isNotEmpty()) {
                    item { SectionHeader("Selected (${pickedTags.size})") }
                    tagItems(pickedTags, selected = true, onTagClick)
                }

                item { SectionHeader("All tags (${availableTags.size})") }
                if (availableTags.isEmpty()) {
                    item { Hint(if (filterText.isBlank()) "No tags" else "No matching tags") }
                } else {
                    tagItems(availableTags, selected = false, onTagClick)
                }
            }
        }
    }
}

private fun LazyListScope.tagItems(
    tags: List<ImmichTagUiModel>,
    selected: Boolean,
    onTagClick: (String) -> Unit
) {
    itemsIndexed(
        items = tags,
        key = { _, tag -> tag.id },
        contentType = { _, _ -> "tag_item" }
    ) { index, tag ->
        ListItem(
            modifier = Modifier
                .animateItem()
                .groupItem(index, tags.size)
                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onTagClick(tag.id) }),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
            headlineContent = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Checkbox(checked = selected, onCheckedChange = null) }
        )
    }
}
