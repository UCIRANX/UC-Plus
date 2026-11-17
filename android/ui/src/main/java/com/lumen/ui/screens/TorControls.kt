package com.lumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.lumen.ui.components.CountryFlagHelper
import com.lumen.ui.theme.LumenMono
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One country in the Tor list: its code, its name in the app language and its bridge count. */
data class TorCountryRow(val code: String, val name: String, val count: Int, val favorite: Boolean, val pingMs: Int? = null)

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

/** What a server under a country can do. These are the same actions the server list offers. */
data class TorServerActions(
    val onSelect: (NodeUiModel) -> Unit,
    val onPing: (NodeUiModel) -> Unit,
    val onCopy: (NodeUiModel) -> Unit,
    val onExportQr: (NodeUiModel) -> Unit,
    val onDelete: (NodeUiModel) -> Unit,
)

/**
 * The Tor country list, drawn like the other servers: flag badge, country name, then a subtitle
 * "Tor - bridge type - ping - servers". The heart keeps its place; the arrow sits after it and opens
 * the servers of that country. [selectedCountry] is null when no Tor choice is the active config, and
 * "" for "any country". [includeAny] is false in the all-protocols list.
 */
fun LazyListScope.torCountryList(
    rows: List<TorCountryRow>,
    selectedCountry: String?,
    totalCount: Int,
    anyLabel: String,
    bridgeLabel: String,
    emptyText: String,
    includeAny: Boolean,
    expandedCode: String,
    serversOf: (String) -> List<NodeUiModel>,
    actions: TorServerActions,
    onSelect: (String) -> Unit,
    onToggleExpand: (String) -> Unit,
    onFavorite: (String) -> Unit,
) {
    if (includeAny) item(key = "tor_any_country", contentType = "tor_country") {
        TorCountryItem(
            flag = "🌐",
            name = anyLabel,
            subtitle = "Tor - $bridgeLabel",
            count = totalCount,
            ping = null,
            selected = selectedCountry == "",
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
                flag = CountryFlagHelper.getServerFlagEmoji(row.code),
                name = row.name,
                subtitle = "Tor - $bridgeLabel",
                count = row.count,
                ping = row.pingMs,
                selected = selectedCountry.equals(row.code, ignoreCase = true),
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
                    TorServerItem(
                        node = node,
                        onClick = { actions.onSelect(node) },
                        onPing = { actions.onPing(node) },
                        onCopy = { actions.onCopy(node) },
                        onExportQr = { actions.onExportQr(node) },
                        onDelete = { actions.onDelete(node) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TorCountryItem(
    flag: String,
    name: String,
    subtitle: String,
    count: Int,
    ping: Int?,
    selected: Boolean,
    favorite: Boolean?,
    expanded: Boolean?,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onFavorite: () -> Unit,
) {
    val art = com.lumen.ui.theme.expressivePalette()
    val bg = if (selected) art.stage else MaterialTheme.colorScheme.surfaceContainerLow
    val textPrimary = if (selected) art.onStage else MaterialTheme.colorScheme.onSurface
    val textMuted = textPrimary.copy(alpha = .75f)
    val pingText = when {
        ping != null && ping > 0 -> "$ping ms"
        ping != null -> "--"
        else -> "--"
    }
    val pingTint = if (ping != null && ping > 0) pingColor(ping, bg) else textMuted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp))
                .background(if (selected) art.lime else MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(flag, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildAnnotatedString {
                    append("$subtitle - ")
                    withStyle(SpanStyle(color = pingTint, fontFamily = LumenMono)) { append(pingText) }
                    append(" - $count")
                },
                style = MaterialTheme.typography.bodySmall,
                color = textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favorite != null) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (favorite) MaterialTheme.colorScheme.error else textMuted,
                )
            }
        }
        if (expanded != null) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = textMuted,
                )
            }
        }
    }
}

/**
 * One server of an expanded country. A tap selects it; a long press opens the same menu as the
 * server list: select, ping, copy link, export QR code and delete. Favourites cannot be deleted,
 * as in the list. Heart toggling is not offered here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TorServerItem(
    node: NodeUiModel,
    onClick: () -> Unit,
    onPing: () -> Unit,
    onCopy: () -> Unit,
    onExportQr: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    var menu by remember(node.id) { mutableStateOf(false) }
    val background = if (node.isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val pingMs = node.pingMs
    val pingText = when {
        pingMs != null && pingMs > 0 -> "$pingMs ms"
        pingMs != null -> s.pingUnreachable
        else -> "--"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
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
        Text(
            pingText,
            Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (node.isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        Box {
            if (menu) LumenMenu(expanded = true, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(s.selectItem) }, onClick = { menu = false; onClick() })
                DropdownMenuItem(text = { Text(s.ping) }, onClick = { menu = false; onPing() })
                DropdownMenuItem(text = { Text(s.copyLink) }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(text = { Text(s.exportQrCode) }, onClick = { menu = false; onExportQr() })
                if (!node.isFavorite) DropdownMenuItem(text = { Text(s.delete) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
