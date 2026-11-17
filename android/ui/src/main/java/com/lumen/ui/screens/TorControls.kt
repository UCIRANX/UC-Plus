package com.lumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One country in the Tor list: its code, its name in the app language and its bridge count. */
data class TorCountryRow(val code: String, val name: String, val count: Int, val favorite: Boolean)

private fun flagOf(code: String): String =
    code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

/**
 * Bridge type selector shown above the Tor server list. The chips share one row, so the labels
 * are small and nothing has to be scrolled sideways.
 */
@Composable
fun TorControlsCard(
    bridgeType: String,
    types: List<Pair<String, String>>,
    onBridgeType: (String) -> Unit,
) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(s.torTypeLabel, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            types.forEach { (id, label) ->
                TorTypeChip(
                    label = label,
                    selected = bridgeType == id,
                    onClick = { onBridgeType(id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TorTypeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .height(26.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = foreground,
            fontSize = 9.5.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/**
 * The Tor country list, drawn in place of the server rows. The first row means "any country".
 * Each country row selects that country when its name is tapped, and its arrow opens the servers
 * of that country just below it. The last row shows every server.
 */
fun LazyListScope.torCountryList(
    rows: List<TorCountryRow>,
    selected: String,
    totalCount: Int,
    anyLabel: String,
    showServersLabel: String,
    emptyText: String,
    expandedCode: String,
    serversOf: (String) -> List<NodeUiModel>,
    onSelect: (String) -> Unit,
    onToggleExpand: (String) -> Unit,
    onSelectServer: (NodeUiModel) -> Unit,
    onFavorite: (String) -> Unit,
    onShowServers: () -> Unit,
) {
    item(key = "tor_any_country", contentType = "tor_country") {
        TorCountryItem(
            flag = "",
            name = anyLabel,
            count = totalCount,
            selected = selected.isEmpty(),
            favorite = null,
            expanded = null,
            onClick = { onSelect("") },
            onToggleExpand = {},
            onFavorite = {},
        )
    }
    if (rows.isEmpty()) {
        item(key = "tor_no_countries", contentType = "tor_empty") {
            Text(
                emptyText,
                Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    rows.forEach { row ->
        val isExpanded = expandedCode.equals(row.code, ignoreCase = true)
        item(key = "tor_country:${row.code}", contentType = "tor_country") {
            TorCountryItem(
                flag = flagOf(row.code),
                name = row.name,
                count = row.count,
                selected = selected.equals(row.code, ignoreCase = true),
                favorite = row.favorite,
                expanded = isExpanded,
                onClick = { onSelect(row.code) },
                onToggleExpand = { onToggleExpand(row.code) },
                onFavorite = { onFavorite(row.code) },
            )
        }
        if (isExpanded) {
            serversOf(row.code).forEach { node ->
                item(key = "tor_server:${node.id}", contentType = "tor_server") {
                    TorServerItem(node = node, onClick = { onSelectServer(node) })
                }
            }
        }
    }
    item(key = "tor_show_servers", contentType = "tor_button") {
        OutlinedButton(
            onClick = onShowServers,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(showServersLabel)
        }
    }
}

@Composable
private fun TorCountryItem(
    flag: String,
    name: String,
    count: Int,
    selected: Boolean,
    favorite: Boolean?,
    expanded: Boolean?,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onFavorite: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (flag.isEmpty()) name else "$flag  $name",
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$count",
            Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (favorite != null) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded != null) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One server of an expanded country. Tapping it chooses that single server. */
@Composable
private fun TorServerItem(node: NodeUiModel, onClick: () -> Unit) {
    val background = if (node.isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            node.name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (node.isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}
