package com.lumen.app.psiphon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.core.vpn.psiphon.CdnEdgeIpPreferences
import com.lumen.core.vpn.psiphon.CdnIpScanner
import com.lumen.core.vpn.psiphon.PsiphonPrefs
import com.lumen.ui.screens.LocalStrings
import com.lumen.ui.screens.psiphonScanTexts

/**
 * The Psiphon scan popup, opened from the Psiphon row of the protocol menu. Every control is live:
 * the protocol is used by the next Psiphon start, the edge IPs and SNI hostnames become dial overrides,
 * Beast Mode sets AggressiveEstablishment, and the scanner writes healthy IPs to the edge IP list.
 */
@Composable
fun PsiphonScanDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val t = psiphonScanTexts(LocalStrings.current.localeCode)
    val scan by CdnIpScanner.state.collectAsState()
    var mode by remember { mutableStateOf(PsiphonPrefs.protocolMode(context)) }
    var modeMenu by remember { mutableStateOf(false) }
    var edgeIps by remember { mutableStateOf(CdnEdgeIpPreferences.load(context)) }
    var sni by remember { mutableStateOf(PsiphonPrefs.cdnSni(context)) }
    var beast by remember { mutableStateOf(PsiphonPrefs.beastMode(context)) }
    var rangesText by remember { mutableStateOf(CdnIpScanner.DEFAULT_AKAMAI_RANGES.joinToString("\n")) }
    var manualText by remember { mutableStateOf("") }
    // Healthy IPs found by the scanner are saved in the same list; show them while the scan runs.
    LaunchedEffect(scan.healthy) { edgeIps = CdnEdgeIpPreferences.load(context) }

    val protocols = listOf(
        PsiphonPrefs.MODE_AUTO to t.protocolAuto,
        PsiphonPrefs.MODE_CONDUIT to t.protocolConduit,
        PsiphonPrefs.MODE_CDN_FRONTING to t.protocolCdn,
        PsiphonPrefs.MODE_DIRECT to t.protocolDirect,
    )
    val currentLabel = protocols.firstOrNull { it.first == mode }?.second ?: t.protocolAuto

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(t.protocolSection, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(currentLabel, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("▾")
                    }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        protocols.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontWeight = if (value == mode) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    mode = value
                                    PsiphonPrefs.setProtocolMode(context, value)
                                    modeMenu = false
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(t.cdnSection, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = edgeIps,
                    onValueChange = {
                        edgeIps = it
                        CdnEdgeIpPreferences.save(context, it)
                    },
                    label = { Text(t.edgeIps) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sni,
                    onValueChange = {
                        sni = it
                        PsiphonPrefs.setCdnSni(context, it)
                    },
                    label = { Text(t.sni) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(t.ipScanner, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = rangesText,
                    onValueChange = { rangesText = it },
                    label = { Text(t.ranges) },
                    minLines = 3,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text(t.manual) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.beastMode, fontWeight = FontWeight.Bold)
                        Text(t.beastModeDesc, fontSize = 12.sp)
                    }
                    Switch(
                        checked = beast,
                        onCheckedChange = {
                            beast = it
                            PsiphonPrefs.setBeastMode(context, it)
                        },
                    )
                }
                Text(t.progress(scan.scanned, scan.healthy))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = { CdnIpScanner.start(context, rangesText, manualText, manualMode = false) },
                        modifier = Modifier.weight(1f),
                    ) { Text(t.scanRanges, maxLines = 1, fontSize = 13.sp) }
                    TextButton(
                        onClick = { CdnIpScanner.start(context, rangesText, manualText, manualMode = true) },
                        modifier = Modifier.weight(1f),
                    ) { Text(t.scanManual, maxLines = 1, fontSize = 13.sp) }
                    TextButton(onClick = { CdnIpScanner.stop() }, modifier = Modifier.weight(1f)) {
                        Text(t.stop, maxLines = 1, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.close) } },
    )
}
