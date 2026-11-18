package com.lumen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.ui.components.AddServerSheet
import com.lumen.ui.components.BrandTopBar
import com.lumen.ui.components.ConnectionState
import com.lumen.ui.components.CountryFlagHelper
import com.lumen.ui.components.LumenDialog
import com.lumen.ui.components.SubscriptionDetailsDialog
import com.lumen.ui.theme.LocalLumenColors
import com.lumen.ui.theme.TileBlueDark
import com.lumen.ui.theme.TileBlueDarkOn
import com.lumen.ui.theme.TileBlueLight
import com.lumen.ui.theme.TileBlueLightOn

enum class ServerSort { PING, NAME, NAME_DESC, COUNTRY, PROTOCOL }

const val SERVERS_SORT_PREF = "servers_last_sort"

fun serverSortOptions(s: LumenStrings): List<Pair<ServerSort, String>> = listOf(
    ServerSort.PING to s.sortByPing,
    ServerSort.NAME to s.sortByName,
    ServerSort.NAME_DESC to s.sortByNameDesc,
    ServerSort.COUNTRY to s.sortByCountry,
    ServerSort.PROTOCOL to s.sortByProtocol
)

fun serverSortLabel(sort: ServerSort, s: LumenStrings): String =
    serverSortOptions(s).firstOrNull { it.first == sort }?.second ?: s.sortByPing

fun serverSortOf(key: String): ServerSort =
    runCatching { ServerSort.valueOf(key) }.getOrDefault(ServerSort.PING)

fun serverSortComparator(sort: ServerSort): Comparator<NodeUiModel> = when (sort) {
    ServerSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    ServerSort.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
    ServerSort.PING -> compareBy<NodeUiModel> { it.pingMs?.takeIf { ping -> ping > 0 } ?: Int.MAX_VALUE }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }
    ServerSort.COUNTRY -> compareBy<NodeUiModel> { countryCodeOf(it).ifEmpty { "zzz" } }
        .thenBy { it.name.lowercase() }
    ServerSort.PROTOCOL -> compareBy<NodeUiModel> { protocolKey(it) }
        .thenBy { it.name.lowercase() }
}

/** Display order of protocol groups: Amnezia, then OpenVPN, then SSTP, then everything else. */
private val PROTOCOL_DISPLAY_ORDER = listOf(setOf("awg", "amneziawg", "amnezia"), setOf("openvpn"), setOf("sstp"))

internal fun protocolRank(key: String): Int =
    PROTOCOL_DISPLAY_ORDER.indexOfFirst { key in it }.let { if (it < 0) PROTOCOL_DISPLAY_ORDER.size else it }

internal fun protocolKey(node: NodeUiModel): String =
    node.displayProtocol.substringBefore('/').trim().lowercase()
        .ifEmpty { node.protocol.trim().lowercase() }

internal fun countryCodeOf(node: NodeUiModel): String =
    node.countryCode.trim().ifEmpty { CountryFlagHelper.detectCountryStrict(node.name, node.server) }
        .uppercase()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerListScreen(
    nodes: List<NodeUiModel>,
    subscriptions: List<SubscriptionUiModel>,
    serverGroups: List<ServerGroupUiModel> = emptyList(),
    refreshingIds: Set<String>,
    isPinging: Boolean,
    pingingNodeIds: Set<String> = emptySet(),
    autoPingOnOpen: Boolean = false,
    testingNodeId: String?,
    serverTestResults: Map<String, String>,
    connectionState: ConnectionState,
    onToggleConnection: () -> Unit,
    onSelectNode: (NodeUiModel) -> Unit,
    onEditNode: (NodeUiModel) -> Unit,
    onDeleteNode: (NodeUiModel) -> Unit,
    onDeleteAllNodes: () -> Unit = {},
    onAddNode: () -> Unit,
    onPingAll: () -> Unit,
    onStopPing: () -> Unit = {},
    onPingNodes: (List<NodeUiModel>) -> Unit = {},
    onPingNode: (NodeUiModel) -> Unit,
    onCopyNodeLink: (NodeUiModel) -> Unit = {},
    onExportQrCode: (NodeUiModel) -> Unit = {},
    onExportLumen: (NodeUiModel) -> Unit = {},
    onImportClipboard: () -> Unit,
    onUpdateSubscription: (SubscriptionUiModel, String, String) -> Boolean = { _, _, _ -> false },
    onRefreshSubscription: (SubscriptionUiModel) -> Unit,
    onRefreshAllSubscriptions: () -> Unit = {},
    onRemoveNoPingServers: () -> Unit = {},
    onToggleFavorite: (NodeUiModel) -> Unit = {},
    onDeleteSubscription: (SubscriptionUiModel) -> Unit,
    onImportFile: () -> Unit = {},
    onImportQr: () -> Unit = {},
    onPingGroup: (String?) -> Unit = {},
    onCreateGroup: (String) -> Unit = {},
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onDeleteGroup: (String) -> Unit = {},
    onAssignNodesToGroup: (List<NodeUiModel>, String?) -> Unit = { _, _ -> },
    onExportNodesText: (Set<String>) -> String = { "" },
    onExportSubscriptionText: (String?) -> String = { "" },
    onShareText: (String) -> Unit = {},
    onCopyText: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
    persistPreferences: Boolean = true,
    compactGrid: Boolean = false,
    selectedGroupFilter: String? = null,
    onSelectedGroupFilter: (String) -> Unit = {},
    torControls: @Composable (String) -> Unit = {},
    // Tor only: the country list shown instead of the rows. It gets a callback that shows every server.
    torRows: List<TorCountryRow> = emptyList(),
    torSelectedCountry: String? = null,
    torExpandedCode: String = "",
    torServersOf: (String) -> List<NodeUiModel> = { emptyList() },
    torAnyLabel: String = "",
    onTorSelectCountry: (String) -> Unit = {},
    onTorToggleExpand: (String) -> Unit = {},
    onTorFavorite: (String) -> Unit = {},
    disabledProtocols: Set<String> = emptySet(),
    onProtocolEnabledChange: (String, Boolean) -> Unit = { _, _ -> }
) {
    val lumenColors = LocalLumenColors.current
    val art = com.lumen.ui.theme.expressivePalette()
    val strings = LocalStrings.current
    val scanQr = rememberQrScanRequest(onImportQr)

    var searchQuery by remember { mutableStateOf("") }
    var localFilter by remember { mutableStateOf("all") }
    // Only the "all servers" view exists: groups and per-subscription views are gone.
    val selectedFilter = "all"
    val chooseFilter: (String) -> Unit = { localFilter = it; onSelectedGroupFilter(it) }
    var sortKey by if (persistPreferences) rememberUiPreference(SERVERS_SORT_PREF, ServerSort.PING.name)
        else remember { mutableStateOf(ServerSort.PING.name) }
    val currentSort = serverSortOf(sortKey)
    var showSortMenu by remember { mutableStateOf(false) }
    var editingSubscription by remember { mutableStateOf<SubscriptionUiModel?>(null) }
    var deletingSubscription by remember { mutableStateOf<SubscriptionUiModel?>(null) }
    var detailsSubscription by remember { mutableStateOf<SubscriptionUiModel?>(null) }
    var deletingNode by remember { mutableStateOf<NodeUiModel?>(null) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var renamingGroup by remember { mutableStateOf<ServerGroupUiModel?>(null) }
    var deletingGroup by remember { mutableStateOf<ServerGroupUiModel?>(null) }
    var movingNodes by remember { mutableStateOf<List<NodeUiModel>?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var pendingSelectedId by remember { mutableStateOf<String?>(null) }
    val selectServer: (NodeUiModel) -> Unit = { node -> pendingSelectedId = node.id; onSelectNode(node) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showSubscriptionMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showPingModeDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var protocol by remember { mutableStateOf("") }
    var reachablePref by if (persistPreferences) rememberUiPreference("servers_filter_reachable", "0") else remember { mutableStateOf("0") }
    val onlyReachable = reachablePref == "1"
    var countryPref by if (persistPreferences) rememberUiPreference("servers_group_by_country", "0") else remember { mutableStateOf("0") }
    var maxPingPref by if (persistPreferences) rememberUiPreference("servers_filter_max_ping", "0") else remember { mutableStateOf("0") }
    val maxPing = maxPingPref.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val groupByCountry = countryPref == "1"
    var autoPingStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onOpen() }
    LaunchedEffect(autoPingOnOpen, nodes.isNotEmpty()) {
        if (autoPingOnOpen && nodes.isNotEmpty() && !autoPingStarted) {
            autoPingStarted = true
            // Respect the active protocol filter: ping only that group, or everything when no filter is set.
            if (protocol.isBlank()) onPingNodes(nodes.filter { protocolKey(it) !in disabledProtocols || it.isFavorite })
            else onPingNodes(nodes.filter { protocolKey(it) == protocol })
        }
    }
    BackHandler(enabled = selectedIds.isNotEmpty()) { selectedIds = emptySet() }

    // Tor servers are listed by country. Each country is an entry that sorts with the other rows,
    // and its servers appear under it when its arrow is open.
    val torOn = protocol.equals("tor", ignoreCase = true) || (protocol.isBlank() && "tor" !in disabledProtocols)
    val displayNodes: List<NodeUiModel> = remember(nodes, torRows, torSelectedCountry, torExpandedCode, protocol, disabledProtocols) {
        if (!torOn) nodes else nodes.filterNot { protocolKey(it) == "tor" } +
            torRows.map { torCountryNode(it, torSelectedCountry, torExpandedCode) }
    }
    val query = ServerListQuery(selectedFilter, searchQuery, protocol, onlyReachable, currentSort, maxPing, groupByCountry,
        disabledProtocols)
    val prepared by rememberPreparedServerList(displayNodes, query)
    val preparing = prepared.source !== displayNodes || prepared.query != query
    val manualNodesCount = prepared.manualCount
    // Keep rows during a ping refresh; never display another group's rows after switching groups.
    val filteredNodes = if (prepared.query.group == selectedFilter) prepared.visible else emptyList()
    // All-protocols ping skips unchecked protocols, favourites included. Inside a protocol's own
    // section its servers are pinged, even when that protocol is unchecked.
    // A Tor country entry pings all of its servers.
    fun pingTargets(list: List<NodeUiModel>): List<NodeUiModel> {
        val expanded = list.flatMap { node -> if (node.torCountry != null) torServersOf(node.countryCode) else listOf(node) }
        return if (protocol.isBlank()) expanded.filter { protocolKey(it) !in disabledProtocols || it.isFavorite } else expanded
    }
    val torActions = TorServerActions(
        onSelect = selectServer,
        onPing = onPingNode,
        onCopy = onCopyNodeLink,
        onExportQr = onExportQrCode,
        onDelete = { node -> if (!node.isFavorite) deletingNode = node },
    )
    LaunchedEffect(prepared.source, pendingSelectedId) {
        if (pendingSelectedId != null && (prepared.source.any { it.id == pendingSelectedId && it.isSelected } ||
                (prepared.source === nodes && nodes.none { it.id == pendingSelectedId }))) pendingSelectedId = null
    }

    val activeSub = subscriptionForServerFilter(selectedFilter, subscriptions)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top Bar
        val lastSubscriptionUpdate = subscriptions.maxOfOrNull { it.lastUpdated }?.takeIf { it > 0L }
        val lastUpdateText = lastSubscriptionUpdate?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
        } ?: "—"
        BrandTopBar(
            title = "UC Plus",
            subtitle = "Telegram: @UCIRANIR",
            onClick = { uriHandler.openUri("https://t.me/uciranir") },
            actions = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        lastUpdateText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (selectedIds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${strings.selected}: ${selectedIds.size}", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { onPingNodes(nodes.filter { it.id in selectedIds }) }) { Text(strings.ping) }
                    TextButton(onClick = {
                        onExportNodesText(selectedIds).takeIf { it.isNotBlank() }?.let(onShareText)
                    }) { Text(strings.exportSelected) }
                    TextButton(onClick = { selectedIds = emptySet() }) { Text(strings.cancel) }
                }
            }
            // Sort and ping sit in the same 40dp row as the protocol selector and the other tools.
            // Ping opens a popup under its button: "Continue" pings only servers never tested,
            // "From start" pings every visible server (both respect the active protocol filter).
            val listToolbar: @Composable () -> Unit = {
                Box {
                    FilledTonalIconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Sort, serverSortLabel(currentSort, strings), Modifier.size(20.dp))
                    }
                    LumenMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        serverSortOptions(strings).forEach { (sort, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                sortKey = sort.name
                                showSortMenu = false
                            })
                        }
                    }
                }
                Box {
                    if (isPinging) {
                        FilledTonalIconButton(onClick = onStopPing, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Stop, strings.cancel, Modifier.size(20.dp))
                        }
                    } else {
                        FilledTonalIconButton(onClick = { showPingModeDialog = true },
                            enabled = filteredNodes.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.NetworkPing, strings.ping, Modifier.size(20.dp))
                        }
                    }
                    LumenMenu(expanded = showPingModeDialog && !isPinging, onDismissRequest = { showPingModeDialog = false }) {
                        DropdownMenuItem(text = { Text(strings.pingFromStart) }, onClick = {
                            showPingModeDialog = false
                            onPingNodes(pingTargets(filteredNodes))
                        })
                        DropdownMenuItem(text = { Text(strings.pingContinue) }, onClick = {
                            showPingModeDialog = false
                            onPingNodes(pingTargets(filteredNodes).filter { it.pingMs == null })
                        })
                    }
                }
            }

            ServerFilterBar(
                protocol, (prepared.protocols + listOfNotNull("tor".takeIf { nodes.any { protocolKey(it) == "tor" } }))
                    .distinct().sortedWith(compareBy<String> { protocolRank(it) }.thenBy { it }), { protocol = it },
                groupByCountry, { countryPref = if (it) "1" else "0" },
                onlyReachable, { reachablePref = if (it) "1" else "0" }, maxPing, { maxPingPref = it.toString() },
                onReset = { protocol = ""; reachablePref = "0"; countryPref = "0"; maxPingPref = "0" },
                refreshing = refreshingIds.isNotEmpty(),
                onRefreshAll = onRefreshAllSubscriptions,
                noPingCount = nodes.count { it.pingMs == 0 && !it.isFavorite },
                onRemoveNoPing = onRemoveNoPingServers,
                trailing = listToolbar,
                disabledProtocols = disabledProtocols,
                onProtocolEnabled = onProtocolEnabledChange)

            // Tor-only bridge type selector appears under the filter bar.
            torControls(protocol)

            // 5. Server List (for Tor: the country list until "show servers" is chosen)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (torOn && protocol.equals("tor", ignoreCase = true)) item(key = "tor_any_country", contentType = "tor_country") {
                    TorCountryRowView(
                        flag = "🌐", name = torAnyLabel, subtitle = "Tor", count = torRows.sumOf { it.count },
                        ping = null, showPing = false, selected = torSelectedCountry == "",
                        favorite = null, expanded = null,
                        onClick = { onTorSelectCountry("") }, onToggleExpand = {}, onFavorite = {},
                    )
                }
                run {
                item(key = "server_overview", contentType = "overview") {
            // 3. Subscription Provider Banner (if present and filter matches)
            if (activeSub != null && selectedFilter != "manual") {
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val bannerBg = art.blue
                val bannerText = art.onBlue
                val btnBg = art.onLavender.copy(alpha = .1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomEnd = 12.dp, bottomStart = 30.dp))
                        .background(bannerBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    com.lumen.ui.components.ExpressiveMotif(com.lumen.ui.components.Motif.Fan, bannerText.copy(alpha = .08f),
                        Modifier.align(Alignment.BottomEnd).size(140.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(activeSub.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = bannerText)
                                Text(strings.serverCountLabel(activeSub.nodeCount),
                                    style = MaterialTheme.typography.bodySmall, color = bannerText.copy(alpha = .7f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(com.lumen.ui.components.expressiveShape(com.lumen.ui.components.Motif.Clover))
                                    .background(btnBg)
                                    .clickable { onRefreshSubscription(activeSub) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (refreshingIds.contains(activeSub.id)) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = bannerText
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = strings.refreshSubscription,
                                        tint = bannerText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { showSubscriptionMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = activeSub.name, tint = bannerText)
                                }
                                LumenMenu(expanded = showSubscriptionMenu, onDismissRequest = { showSubscriptionMenu = false }) {
                                    DropdownMenuItem(text = { Text(strings.subscriptionProperties) }, onClick = {
                                        showSubscriptionMenu = false; detailsSubscription = activeSub
                                    })
                                    DropdownMenuItem(text = { Text(strings.edit) }, onClick = {
                                        showSubscriptionMenu = false; editingSubscription = activeSub
                                    })
                                    DropdownMenuItem(text = { Text(strings.exportAll) }, onClick = {
                                        showSubscriptionMenu = false
                                        onExportSubscriptionText(activeSub.id).takeIf { it.isNotBlank() }?.let(onShareText)
                                    })
                                    DropdownMenuItem(text = { Text(strings.delete) }, onClick = {
                                        showSubscriptionMenu = false; deletingSubscription = activeSub
                                    })
                                }
                            }
                            }
                        }

                        activeSub.trafficRatio?.let { trafficRatio ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val ratio = trafficRatio.coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val trafficText = activeSub.trafficSummary ?: "—"
                        val daysText = activeSub.expiryDaysLeft?.let { "$it ${strings.daysRemaining}" }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = trafficText,
                                fontSize = 11.sp,
                                color = bannerText.copy(alpha = 0.8f)
                            )
                            if (daysText != null) Text(
                                text = daysText,
                                fontSize = 11.sp,
                                color = bannerText.copy(alpha = 0.8f)
                            )
                        }
                        if (com.lumen.ui.components.hasProviderCard(activeSub)) {
                            com.lumen.ui.components.SubscriptionProviderCard(activeSub, onOpenUrl,
                                contentColor = bannerText)
                        }
                    }
                }
            }


                }
                if (preparing && filteredNodes.isEmpty()) item(key = "preparing_servers", contentType = "progress") {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (filteredNodes.isEmpty() && !preparing) {
                    item(key = "empty_servers", contentType = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(if (nodes.isEmpty()) strings.noServers else strings.nothingFound,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                if (nodes.isEmpty()) onRefreshAllSubscriptions() else {
                                    searchQuery = ""
                                    chooseFilter("all")
                                    protocol = ""
                                    reachablePref = "0"
                                    countryPref = "0"
                                    maxPingPref = "0"
                                }
                            }) { Text(if (nodes.isEmpty()) strings.refreshSubscription else strings.resetFilters) }
                        }
                    }
                }
                val sections: List<Pair<String, List<NodeUiModel>>> = if (groupByCountry) {
                    filteredNodes.groupBy { countryCodeOf(it).ifBlank { "—" } }.map { it.key to it.value }
                } else listOf("" to filteredNodes)
                sections.forEach { (country, sectionNodes) ->
                    if (country.isNotBlank()) item(key = "country:$country", contentType = "country") {
                        Text("${CountryFlagHelper.getFlagEmoji(country)}  $country · ${sectionNodes.size}",
                            Modifier.padding(horizontal = 8.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    serverRunItems(
                        sectionNodes, compactGrid,
                        gridRow = { row ->
                            Row(Modifier.fillMaxWidth().animateItem(fadeInSpec = null, fadeOutSpec = null),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { node ->
                                    CompactServerCard(if (pendingSelectedId != null) node.copy(isSelected = node.id == pendingSelectedId) else node,
                                        node.id in selectedIds, node.id in pingingNodeIds || testingNodeId == node.id,
                                        onClick = {
                                            if (selectedIds.isEmpty()) selectServer(node)
                                            else selectedIds = if (node.id in selectedIds) selectedIds - node.id else selectedIds + node.id
                                        }, onLongClick = { selectedIds = selectedIds + node.id },
                                        onEdit = { onEditNode(node) }, onPing = { onPingNode(node) },
                                        onCopy = { onCopyNodeLink(node) }, onExportLumen = { onExportLumen(node) },
                                        onDelete = { deletingNode = node }, modifier = Modifier.weight(1f),
                                        onFavorite = { onToggleFavorite(node) })
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        },
                        listRow = { node ->
                            ServerListRow(if (pendingSelectedId != null) node.copy(isSelected = node.id == pendingSelectedId) else node, node.id in selectedIds,
                                node.id in pingingNodeIds || testingNodeId == node.id,
                                serverTestResults[node.id],
                                onClick = { clicked ->
                                    if (selectedIds.isEmpty()) selectServer(clicked)
                                    else selectedIds = if (clicked.id in selectedIds) selectedIds - clicked.id else selectedIds + clicked.id
                                },
                                onLongClick = { selectedIds = selectedIds + it.id },
                                onEditNode = onEditNode, onPingNode = onPingNode,
                                onCopyNodeLink = onCopyNodeLink, onExportQrCode = onExportQrCode, onExportLumen = onExportLumen,
                                onMove = { movingNodes = listOf(it) }, onDeleteNode = { deletingNode = it },
                                onToggleFavorite = onToggleFavorite,
                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                        },
                        torRow = { node ->
                            val info = node.torCountry
                            if (info != null) TorCountryRowView(
                                flag = CountryFlagHelper.getServerFlagEmoji(node.countryCode),
                                name = node.name,
                                subtitle = "Tor",
                                count = info.count,
                                ping = node.pingMs,
                                showPing = true,
                                selected = info.selected,
                                favorite = node.isFavorite,
                                expanded = info.expanded,
                                onClick = { onTorSelectCountry(node.countryCode) },
                                onToggleExpand = { onTorToggleExpand(node.countryCode) },
                                onFavorite = { onTorFavorite(node.countryCode) },
                            )
                        },
                        torChild = { child ->
                            TorServerItem(
                                node = child,
                                onClick = { torActions.onSelect(child) },
                                onPing = { torActions.onPing(child) },
                                onCopy = { torActions.onCopy(child) },
                                onExportQr = { torActions.onExportQr(child) },
                                onDelete = { torActions.onDelete(child) },
                            )
                        },
                        childrenOf = torServersOf,
                    )
                }
                item(key = "server_footer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                }
            }
        }
    }

    editingSubscription?.let { original ->
        var name by remember(original.id) { mutableStateOf(original.name) }
        var url by remember(original.id) { mutableStateOf(original.url) }
        LumenDialog(
            title = strings.edit,
            onDismissRequest = { editingSubscription = null },
            confirmText = strings.saveAction,
            onConfirm = {
                if (url.isNotBlank()) {
                    val saved = onUpdateSubscription(original, name.trim(), url.trim())
                    if (saved) { editingSubscription = null }
                }
            },
            dismissText = strings.cancel,
            onDismiss = { editingSubscription = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveTextField(shape = MaterialTheme.shapes.large, value = name, onValueChange = { name = it },
                    label = { Text(strings.nameOptional) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExpressiveTextField(shape = MaterialTheme.shapes.large, value = url, onValueChange = { url = it },
                    label = { Text(strings.url) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    detailsSubscription?.let { sub ->
        SubscriptionDetailsDialog(sub, onDismiss = { detailsSubscription = null },
            onCopyUrl = onCopyText, onOpenUrl = onOpenUrl)
    }
    deletingSubscription?.let { sub ->
        LumenDialog(title = strings.deleteSubscription, message = sub.name,
            onDismissRequest = { deletingSubscription = null }, confirmText = strings.delete,
            onConfirm = { onDeleteSubscription(sub); deletingSubscription = null },
            dismissText = strings.cancel, onDismiss = { deletingSubscription = null }, destructive = true)
    }
    deletingNode?.let { node ->
        LumenDialog(title = strings.delete, message = node.name,
            onDismissRequest = { deletingNode = null }, confirmText = strings.delete,
            onConfirm = { onDeleteNode(node); deletingNode = null },
            dismissText = strings.cancel, onDismiss = { deletingNode = null }, destructive = true)
    }
    if (showCreateGroup || renamingGroup != null) {
        val original = renamingGroup
        var name by remember(original?.id) { mutableStateOf(original?.name.orEmpty()) }
        LumenDialog(title = if (original == null) strings.newGroup else strings.renameGroup,
            onDismissRequest = { showCreateGroup = false; renamingGroup = null },
            confirmText = if (original == null) strings.createAction else strings.saveAction,
            onConfirm = {
                if (name.isNotBlank()) {
                    if (original == null) onCreateGroup(name.trim()) else onRenameGroup(original.id, name.trim())
                    showCreateGroup = false; renamingGroup = null
                }
            }, dismissText = strings.cancel,
            onDismiss = { showCreateGroup = false; renamingGroup = null }) {
            ExpressiveTextField(shape = MaterialTheme.shapes.large, value = name, onValueChange = { name = it },
                label = { Text(strings.nodeName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
    deletingGroup?.let { group ->
        LumenDialog(title = strings.deleteGroup, message = strings.deleteGroupConfirm,
            onDismissRequest = { deletingGroup = null }, confirmText = strings.delete,
            onConfirm = {
                onDeleteGroup(group.id)
                if (selectedFilter == "group:${group.id}") chooseFilter("all")
                deletingGroup = null
            }, dismissText = strings.cancel, onDismiss = { deletingGroup = null }, destructive = true)
    }
    if (showDeleteAll) {
        LumenDialog(title = strings.deleteAllServers, message = strings.deleteAllServersConfirm,
            onDismissRequest = { showDeleteAll = false }, confirmText = strings.delete,
            onConfirm = { onDeleteAllNodes(); showDeleteAll = false },
            dismissText = strings.cancel, onDismiss = { showDeleteAll = false }, destructive = true)
    }
    movingNodes?.let { nodesToMove ->
        LumenDialog(title = strings.moveToGroup, onDismissRequest = { movingNodes = null },
            dismissText = strings.cancel, onDismiss = { movingNodes = null }) {
            Column {
                TextButton(onClick = { onAssignNodesToGroup(nodesToMove, null); movingNodes = null; selectedIds = emptySet() }) {
                    Text(strings.groupDefault)
                }
                serverGroups.forEach { group ->
                    TextButton(onClick = { onAssignNodesToGroup(nodesToMove, group.id); movingNodes = null; selectedIds = emptySet() }) {
                        Text(group.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    LumenFilterChip(label, isSelected, onClick)
}

/** Aggregate filters deliberately have no provider owner. */
internal fun subscriptionForServerFilter(filter: String, subscriptions: List<SubscriptionUiModel>): SubscriptionUiModel? =
    filter.takeIf { it.startsWith("subscription:") }?.removePrefix("subscription:")
        ?.let { id -> subscriptions.firstOrNull { it.id == id } }


/** Per-row inputs keep unrelated latency/selection updates skippable. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ServerListRow(
    node: NodeUiModel, selectionChecked: Boolean, pinging: Boolean, testResult: String?,
    onClick: (NodeUiModel) -> Unit, onLongClick: (NodeUiModel) -> Unit,
    onEditNode: (NodeUiModel) -> Unit, onPingNode: (NodeUiModel) -> Unit,
    onCopyNodeLink: (NodeUiModel) -> Unit, onExportQrCode: (NodeUiModel) -> Unit,
    onExportLumen: (NodeUiModel) -> Unit,
    onMove: (NodeUiModel) -> Unit, onDeleteNode: (NodeUiModel) -> Unit,
    onToggleFavorite: (NodeUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val art = com.lumen.ui.theme.expressivePalette()

                    var showNodeMenu by remember(node.id) { mutableStateOf(false) }
                    val isSelected = node.isSelected
                    val highlighted = isSelected || selectionChecked
                    val cardBg = androidx.compose.animation.animateColorAsState(
                        targetValue = if (selectionChecked) art.blue else if (isSelected) art.stage else MaterialTheme.colorScheme.surfaceContainerLow,
                        animationSpec = androidx.compose.animation.core.spring(), label = "server_color"
                    )
                    val rowCorner = androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (highlighted) 22.dp else 14.dp,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        ), label = "server_shape"
                    )
                    val textPrimary = if (selectionChecked) art.onBlue else if (isSelected) art.onStage else MaterialTheme.colorScheme.onSurface
                    val textMuted = textPrimary.copy(alpha = .75f)

                    val flagEmoji = remember(node.countryCode) {
                        CountryFlagHelper.getServerFlagEmoji(node.countryCode)
                    }

                        // Latency
                        val pingMs = node.pingMs
                        val unreachable = pingMs != null && pingMs <= 0 && !pinging
                        val pingText = when {
                            pinging -> "..."
                            pingMs != null && pingMs > 0 -> "$pingMs ms"
                            pingMs != null -> strings.pingUnreachable
                            else -> "--"
                        }


                    Row(
                        modifier = modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                shape = RoundedCornerShape(topStart = rowCorner.value, topEnd = 12.dp,
                                    bottomEnd = rowCorner.value, bottomStart = 12.dp)
                                clip = true
                            }.drawBehind { drawRect(cardBg.value) }
                            .combinedClickable(
                                onClick = {
                                    onClick(node)
                                },
                                onLongClick = { showNodeMenu = true }
                            )
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flag squircle badge
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp))
                                .background(if (highlighted) art.lime else MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = flagEmoji,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                fontSize = 15.sp,
                                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                                letterSpacing = if (strings.localeCode == "fa") 0.sp else (-0.5).sp,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(node.localizedProtocol(strings),
                                    style = MaterialTheme.typography.bodySmall, color = textMuted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(8.dp))
                                Text(pingText, fontFamily = com.lumen.ui.theme.LumenMono,
                                    style = MaterialTheme.typography.labelSmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                    color = if (pinging || pingMs == null) textMuted else pingColor(pingMs, cardBg.value),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        if (isSelected || selectionChecked) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = if (selectionChecked) Icons.Default.CheckBox else Icons.Default.CheckCircle,
                                contentDescription = strings.selected,
                                tint = if (art.stage.luminance() > .5f) art.onStage else art.lime,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onToggleFavorite(node) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (node.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (node.isFavorite) MaterialTheme.colorScheme.error else textMuted
                            )
                        }
                        Box {
                            if (showNodeMenu) LumenMenu(expanded = true, onDismissRequest = { showNodeMenu = false }) {
                                DropdownMenuItem(text = { Text(strings.selectItem) }, onClick = {
                                    showNodeMenu = false; onLongClick(node)
                                })
                                DropdownMenuItem(text = { Text(strings.ping) }, onClick = {
                                    showNodeMenu = false; onPingNode(node)
                                })
                                if (!node.isProtected) DropdownMenuItem(text = { Text(strings.copyLink) }, onClick = {
                                    showNodeMenu = false; onCopyNodeLink(node)
                                })
                                if (!node.isProtected) DropdownMenuItem(text = { Text(strings.exportQrCode) }, onClick = {
                                    showNodeMenu = false; onExportQrCode(node)
                                })
                                if (!node.isFavorite) DropdownMenuItem(text = { Text(strings.delete) }, onClick = {
                                    showNodeMenu = false; onDeleteNode(node)
                                })
                            }
                        }
                    }

}

/** A Tor country as a list entry. Its id is not a real server; taps and pings are routed by [torCountry]. */
private fun torCountryNode(row: TorCountryRow, selected: String?, expandedCode: String): NodeUiModel = NodeUiModel(
    id = "tor-country:${row.code}",
    name = row.name,
    protocol = "tor",
    server = "",
    port = 0,
    pingMs = row.pingMs,
    countryCode = row.code,
    isFavorite = row.favorite,
    displayProtocol = "Tor",
    torCountry = TorCountryInfo(
        count = row.count,
        selected = selected.equals(row.code, ignoreCase = true),
        expanded = expandedCode.equals(row.code, ignoreCase = true),
    ),
)

/**
 * Lays out one section. Plain servers go in rows (three per row in grid mode). A Tor country is one
 * row, and when it is open its servers follow it.
 */
private fun LazyListScope.serverRunItems(
    nodes: List<NodeUiModel>,
    compactGrid: Boolean,
    gridRow: @Composable LazyItemScope.(List<NodeUiModel>) -> Unit,
    listRow: @Composable LazyItemScope.(NodeUiModel) -> Unit,
    torRow: @Composable (NodeUiModel) -> Unit,
    torChild: @Composable (NodeUiModel) -> Unit,
    childrenOf: (String) -> List<NodeUiModel>,
) {
    var run = mutableListOf<NodeUiModel>()
    fun flush() {
        if (run.isEmpty()) return
        if (compactGrid) {
            items(run.chunked(3), key = { row -> "grid:" + row.joinToString { it.id } }, contentType = { "server_grid" }) { row ->
                gridRow(row)
            }
        } else {
            items(run, key = { it.id }, contentType = { "server" }) { node -> listRow(node) }
        }
        run = mutableListOf()
    }
    for (node in nodes) {
        val info = node.torCountry
        if (info == null) {
            run.add(node)
            continue
        }
        flush()
        item(key = node.id, contentType = "tor_country") { torRow(node) }
        if (info.expanded) childrenOf(node.countryCode).forEach { child ->
            item(key = "tor_server:${child.id}", contentType = "tor_server") { torChild(child) }
        }
    }
    flush()
}
