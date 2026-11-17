package com.lumen.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One exit country in the picker: its two-letter code and the name in the app language. */
data class TorCountryChoice(val code: String, val name: String)

private fun flagOf(code: String): String =
    code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

/**
 * Tor controls shown above the server list when the Tor filter is selected: the exit country,
 * the bridge type, and a button that connects through the pool of bridges automatically.
 */
@Composable
fun TorControlsCard(
    exitCountry: String,
    countries: List<TorCountryChoice>,
    onExitCountry: (String) -> Unit,
    bridgeType: String,
    types: List<Pair<String, String>>,
    onBridgeType: (String) -> Unit,
    autoConnectEnabled: Boolean,
    onAutoConnect: () -> Unit,
) {
    val s = LocalStrings.current
    // The picker is usable once the repository's country index has loaded.
    val geoReady = countries.isNotEmpty()
    var countryMenu by remember { mutableStateOf(false) }
    val chosen = countries.firstOrNull { it.code == exitCountry }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(s.torExitLabel, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                onClick = { countryMenu = true },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                enabled = geoReady,
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(
                    if (chosen == null) s.torAnyCountry else "${flagOf(chosen.code)}  ${chosen.name}",
                    Modifier.weight(1f),
                    maxLines = 1,
                    fontSize = 13.sp,
                )
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
            }
            LumenMenu(countryMenu, { countryMenu = false }) {
                DropdownMenuItem(
                    text = { Text(s.torAnyCountry) },
                    onClick = { onExitCountry(""); countryMenu = false },
                    trailingIcon = { if (chosen == null) Icon(Icons.Default.Check, null) },
                )
                countries.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${flagOf(c.code)}  ${c.name}") },
                        onClick = { onExitCountry(c.code); countryMenu = false },
                        trailingIcon = { if (chosen?.code == c.code) Icon(Icons.Default.Check, null) },
                    )
                }
            }
        }

        Text(s.torTypeLabel, style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            types.forEach { (id, label) ->
                FilterChip(
                    selected = bridgeType == id,
                    onClick = { onBridgeType(id) },
                    label = { Text(label) },
                )
            }
        }

        Button(
            onClick = onAutoConnect,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            enabled = autoConnectEnabled,
        ) {
            Text(s.torAutoConnect)
        }
        Text(s.torAutoHint, style = MaterialTheme.typography.labelSmall)
        if (!geoReady) {
            Text(s.torGeoMissing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
