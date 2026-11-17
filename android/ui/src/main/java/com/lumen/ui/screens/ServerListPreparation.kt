package com.lumen.ui.screens

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ServerListQuery(
    val group: String, val text: String = "", val protocol: String = "",
    val onlyReachable: Boolean = false, val sort: ServerSort = ServerSort.PING,
    val maxPing: Int = 0, val groupByCountry: Boolean = false,
    val disabledProtocols: Set<String> = emptySet()
)

internal data class PreparedServerList(
    val source: List<NodeUiModel>, val query: ServerListQuery,
    val visible: List<NodeUiModel>, val protocols: List<String> = emptyList(),
    val subscribedCount: Int = 0, val manualCount: Int = 0
)

/** CPU work lives off Main. Cancelling the producer prevents an older query publishing later. */
@Composable
internal fun rememberPreparedServerList(nodes: List<NodeUiModel>, query: ServerListQuery): State<PreparedServerList> =
    produceState(PreparedServerList(emptyList(), query, emptyList()), nodes, query) {
        val previous = value
        value = withContext(Dispatchers.Default) {
            if (previous.query == query && selectionOnlyUpdate(previous.source, nodes)) {
                val byId = nodes.associateBy { it.id }
                previous.copy(source = nodes, visible = previous.visible.mapNotNull { byId[it.id] })
            } else prepareServerList(nodes, query)
        }
    }

internal fun selectionOnlyUpdate(previous: List<NodeUiModel>, next: List<NodeUiModel>): Boolean =
    previous.size == next.size && previous.indices.all { i ->
        val a = previous[i]; val b = next[i]
        a === b || a.copy(isSelected = b.isSelected) == b
    }

internal fun prepareServerList(nodes: List<NodeUiModel>, query: ServerListQuery): PreparedServerList {
    val protocols = sortedSetOf<String>()
    var subscribed = 0
    var manual = 0
    nodes.forEach { node ->
        if (node.subscriptionId != null) subscribed++
        if (nodeInGroup(node, GROUP_MANUAL)) manual++
        protocolKey(node).takeIf { it.isNotBlank() }?.let(protocols::add)
    }
    val search = query.text
    val searchEmpty = search.isBlank()
    val candidates = nodesForServerFilter(nodes, query.group).filter { node ->
        (searchEmpty || node.name.contains(search, true) || node.server.contains(search, true) || node.protocol.contains(search, true)) &&
            (query.protocol.isBlank() || protocolKey(node) == query.protocol) &&
            // All-protocols view hides unchecked protocols, but favourites always stay visible.
            (query.protocol.isNotBlank() || protocolKey(node) !in query.disabledProtocols || node.isFavorite) &&
            (!query.onlyReachable || (node.pingMs ?: 0) > 0) &&
            (query.maxPing <= 0 || (node.pingMs?.let { it in 1..query.maxPing } == true))
    }
    // Country detection can parse names/endpoints. Resolve it once per node, never per comparison.
    val withinGroup = if (query.sort == ServerSort.PROTOCOL) {
        val keys = candidates.associate { it.id to protocolKey(it) }
        compareBy<NodeUiModel> { protocolRank(keys[it.id].orEmpty()) }
            .thenBy { keys[it.id] }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    } else if (query.sort == ServerSort.PING) {
        serverSortComparator(ServerSort.PING)
    } else {
        // Name and country orders keep the protocol groups in their fixed display order.
        compareBy<NodeUiModel> { protocolRank(protocolKey(it)) }
            .then(serverSortComparator(if (query.sort == ServerSort.COUNTRY) ServerSort.NAME else query.sort))
    }
    val comparator = if (query.sort == ServerSort.COUNTRY || query.groupByCountry) {
        val countries = candidates.associate { it.id to countryCodeOf(it).ifEmpty { "zzz" } }
        compareBy<NodeUiModel> { countries[it.id] }.then(withinGroup)
    } else withinGroup
    val protocolOrder = protocols.sortedWith(compareBy<String> { protocolRank(it) }.thenBy { it })
    // Favourites always lead the list; the chosen sort orders everything beneath them.
    val favoritesFirst = compareByDescending<NodeUiModel> { it.isFavorite }.then(comparator)
    return PreparedServerList(nodes, query, candidates.sortedWith(favoritesFirst), protocolOrder, subscribed, manual)
}
