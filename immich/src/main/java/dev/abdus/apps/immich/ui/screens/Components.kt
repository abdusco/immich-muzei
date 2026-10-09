package dev.abdus.apps.immich.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// Shared layout: content sits in rounded surfaceContainer groups inset 16dp from the screen
// edges, with section headers and hints aligned to the text inside the groups (32dp).

private val ScreenInset = 16.dp
private val ContentInset = 32.dp

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = ContentInset, end = ContentInset, top = 24.dp, bottom = 8.dp)
    )
}

// A rounded block of related rows.
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenInset),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = content
    )
}

/**
 * Makes a single lazy-list item look like part of a [SettingsGroup]: only the first and last
 * items of a group get rounded corners, and items are separated by a 1dp gap.
 */
internal fun Modifier.groupItem(index: Int, count: Int): Modifier = composed {
    val corner = MaterialTheme.shapes.extraLarge
    val none = CornerSize(0.dp)
    val shape = corner.copy(
        topStart = if (index == 0) corner.topStart else none,
        topEnd = if (index == 0) corner.topEnd else none,
        bottomStart = if (index == count - 1) corner.bottomStart else none,
        bottomEnd = if (index == count - 1) corner.bottomEnd else none,
    )
    this
        .padding(horizontal = ScreenInset)
        .padding(top = if (index > 0) 1.dp else 0.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        shape = MaterialTheme.shapes.large,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenInset, vertical = 8.dp)
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = ContentInset, vertical = 8.dp)
    )
}

@Composable
internal fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = ContentInset, vertical = 8.dp)
    )
}
