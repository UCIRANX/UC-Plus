package com.lumen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One compact row: the protocol selector followed by five 40dp tools
 * (remove no-ping, filter, refresh, and the [trailing] sort and ping buttons).
 */
@Composable
internal fun ServerFilterBar(
    protocol: String, protocols: List<String>, onProtocol: (String) -> Unit,
    groupByCountry: Boolean, onGroupByCountry: (Boolean) -> Unit,
    reachable: Boolean, onReachable: (Boolean) -> Unit, maxPing: Int, onMaxPing: (Int) -> Unit,
    onReset: () -> Unit, refreshing: Boolean, onRefreshAll: () -> Unit,
    noPingCount: Int = 0, onRemoveNoPing: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    // Protocols the user unchecked: hidden from the all-protocols list and skipped by its ping.
    disabledProtocols: Set<String> = emptySet(),
    onProtocolEnabled: (String, Boolean) -> Unit = { _, _ -> },
    // Opens the Psiphon scan popup; the button sits in the Psiphon row of the protocol menu.
    onOpenPsiphonScan: (() -> Unit)? = null
) {
    val s = LocalStrings.current
    var protocolMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    val count = (if (groupByCountry) 1 else 0) + (if (reachable) 1 else 0) + (if (maxPing > 0) 1 else 0)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedButton({ protocolMenu = true }, Modifier.fillMaxWidth().height(40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (protocol.equals("locked", true)) InterfaceText.PROTECTED.inLanguage(s.localeCode)
                    else protocol.ifBlank { s.protocolField }, Modifier.weight(1f), maxLines = 1,
                    style = MaterialTheme.typography.labelLarge, fontSize = 13.sp,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
            }
            LumenMenu(protocolMenu, { protocolMenu = false }) {
                Text(s.protocolHint, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DropdownMenuItem(text = { Text(s.allProtocols) },
                    trailingIcon = { if (protocol.isEmpty()) Icon(Icons.Default.Check, null) },
                    onClick = { onProtocol(""); protocolMenu = false })
                protocols.forEach { id ->
                    val label = if (id.equals("locked", true)) InterfaceText.PROTECTED.inLanguage(s.localeCode) else id.uppercase()
                    DropdownMenuItem(
                        text = {
                            // The name chooses the protocol section; the box only decides whether it is listed.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label,
                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                                    fontWeight = if (protocol == id) FontWeight.Bold else FontWeight.Normal)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (id.equals("psiphon", ignoreCase = true) && onOpenPsiphonScan != null) {
                                    val scanText = com.lumen.ui.screens.psiphonScanTexts(s.localeCode)
                                    TextButton(onClick = { protocolMenu = false; onOpenPsiphonScan() },
                                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                                        Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(scanText.scanButton, fontSize = 12.sp)
                                    }
                                }
                                Checkbox(checked = id !in disabledProtocols,
                                    onCheckedChange = { onProtocolEnabled(id, it) })
                            }
                        },
                        onClick = { onProtocol(id); protocolMenu = false })
                }
            }
        }
        // Removes only unfavourited servers whose ping test ran and returned no ping (pingMs == 0).
        FilledTonalIconButton(onClick = onRemoveNoPing, enabled = noPingCount > 0 && !refreshing,
            modifier = Modifier.size(40.dp)) {
            BadgedBox(badge = { if (noPingCount > 0) Badge { Text(noPingCount.toString()) } }) {
                Icon(Icons.Default.DeleteSweep, null, Modifier.size(20.dp))
            }
        }
        Box {
            FilledTonalIconButton({ filterMenu = true }, Modifier.size(40.dp)) {
                BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
                    Icon(Icons.Default.FilterList, s.filtersLabel, Modifier.size(20.dp))
                }
            }
            LumenMenu(filterMenu, { filterMenu = false }) {
                DropdownMenuItem(text = { Text(s.groupByCountry) }, onClick = { onGroupByCountry(!groupByCountry) },
                    trailingIcon = { if (groupByCountry) Icon(Icons.Default.Check, null) })
                DropdownMenuItem(text = { Text(s.onlyReachable) }, onClick = { onReachable(!reachable) },
                    trailingIcon = { if (reachable) Icon(Icons.Default.Check, null) })
                HorizontalDivider()
                listOf(0, 100, 200, 500).forEach { value ->
                    DropdownMenuItem(text = { Text("${s.maxPingLabel}: ${if (value == 0) s.offLabel else "$value ms"}") },
                        onClick = { onMaxPing(value) }, trailingIcon = { if (maxPing == value) Icon(Icons.Default.Check, null) })
                }
                DropdownMenuItem(text = { Text(s.resetFilters) }, onClick = { onReset(); filterMenu = false })
            }
        }
        FilledTonalIconButton(onClick = onRefreshAll, enabled = !refreshing, modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(contentColor = Color(0xFF2E7D32))) {
            Icon(Icons.Default.Refresh, s.refreshSubscription, Modifier.size(20.dp))
        }
        trailing()
    }
}
