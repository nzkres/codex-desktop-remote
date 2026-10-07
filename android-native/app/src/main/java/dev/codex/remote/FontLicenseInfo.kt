package dev.codex.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FontLicenseInfo() {
    var opened by remember { mutableStateOf(false) }
    var hapiOpened by remember { mutableStateOf(false) }
    TextButton(onClick = { opened = true }, modifier = Modifier.padding(horizontal = 10.dp)) {
        Text("字体与开源许可", color = HapiPalette.muted, fontSize = 12.sp)
    }
    TextButton(onClick = { hapiOpened = true }, modifier = Modifier.padding(horizontal = 10.dp)) {
        Text("HAPI · AGPL-3.0 许可", color = HapiPalette.muted, fontSize = 12.sp)
    }
    if (opened) {
        val context = LocalContext.current
        val license = remember(context) { context.assets.open("licenses/JetBrainsMono-OFL.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { opened = false }, title = { Text("JetBrains Mono · OFL 1.1") },
            text = { Text(license, fontSize = 12.sp, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { opened = false }) { Text("关闭") } })
    }
    if (hapiOpened) {
        val context = LocalContext.current
        val license = remember(context) { context.assets.open("licenses/HAPI-AGPL-3.0.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { hapiOpened = false }, title = { Text("HAPI · AGPL-3.0") },
            text = { Text(license, fontSize = 12.sp, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { hapiOpened = false }) { Text("关闭") } })
    }
}
