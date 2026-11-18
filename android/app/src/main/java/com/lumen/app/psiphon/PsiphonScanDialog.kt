package com.lumen.app.psiphon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.lumen.core.vpn.psiphon.CdnEdgeIpPreferences
import com.lumen.core.vpn.psiphon.CdnIpScanner
import com.lumen.core.vpn.psiphon.PsiphonPrefs
import com.lumen.ui.screens.LocalStrings
import com.lumen.ui.screens.psiphonScanTexts

/**
 * The Psiphon scan popup, opened from the Psiphon row of the protocol menu. Two sections:
 * the connection protocol (four choices) and the CDN settings with the edge IP scanner.
 * Every change is saved at once and used by the next Psiphon connection.
 */
@Composable
fun PsiphonScanDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val t = psiphonScanTexts(LocalStrings.current.localeCode)
    val scan by CdnIpScanner.state.collectAsState()
    var mode by remember { mutableStateOf(PsiphonPrefs.protocolMode(context)) }
    var edgeIps by remember { mutableStateOf(CdnEdgeIpPreferences.load(context)) }
    var sni by remember { mutableStateOf(PsiphonPrefs.cdnSni(context)) }
    var rangesText by remember { mutableStateOf(CdnIpScanner.DEFAULT_AKAMAI_RANGES.joinToString("\n")) }
    var manualText by remember { mutableStateOf("") }
    var beast by remember { mutableStateOf(PsiphonPrefs.beastMode(context)) }
    // Healthy IPs found by the scanner are saved in the same list; show them while the scan runs.
    LaunchedEffect(scan.healthy) { edgeIps = CdnEdgeIpPreferences.load(context) }

    val protocols = listOf(
        PsiphonPrefs.MODE_AUTO to t.protocolAuto,
        PsiphonPrefs.MODE_CONDUIT to t.protocolConduit,
        PsiphonPrefs.MODE_CDN_FRONTING to t.protocolCdn,
        PsiphonPrefs.MODE_DIRECT to t.protocolDirect,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(t.protocolSection, fontWeight = FontWeight.Bold)
                protocols.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = mode == value,
                            onClick = {
                                mode = value
                                PsiphonPrefs.setProtocolMode(context, value)
                            },
                        )
                        Text(label)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(t.cdnSection, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = edgeIps,
                    onValueChange = {
                        edgeIps = it
                        CdnEdgeIpPreferences.save(context, it)
                    },
                    label = { Text(t.edgeIps) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sni,
                    onValueChange = {
                        sni = it
                        PsiphonPrefs.setCdnSni(context, it)
                    },
                    label = { Text(t.sni) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rangesText,
                    onValueChange = { rangesText = it },
                    label = { Text(t.ranges) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text(t.manual) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.beastMode, fontWeight = FontWeight.Bold)
                        Text(t.beastModeDesc)
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
                Row {
                    TextButton(onClick = { CdnIpScanner.start(context, rangesText, manualText, manualMode = false) }) {
                        Text(t.scanRanges)
                    }
                    TextButton(onClick = { CdnIpScanner.start(context, rangesText, manualText, manualMode = true) }) {
                        Text(t.scanManual)
                    }
                    TextButton(onClick = { CdnIpScanner.stop() }) { Text(t.stop) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.close) } },
    )
}
