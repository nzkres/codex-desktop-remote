package dev.codex.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal data class ReleaseChange(val title: String, val experience: String, val verify: String)
internal data class ReleaseEntry(
    val version: String,
    val versionCode: Int,
    val date: String,
    val summary: String,
    val changes: List<ReleaseChange>,
    val limitations: List<String>,
)
internal data class ReleaseHistory(val name: String, val releases: List<ReleaseEntry>)

internal fun parseReleaseHistory(raw: String, installedVersion: String, installedCode: Int): ReleaseHistory {
    val root = JSONObject(raw)
    require(root.getInt("schema") == 1 && root.getString("component") == "android")
    val entries = root.getJSONArray("releases")
    require(entries.length() > 0)
    val releases = List(entries.length()) { index ->
        val item = entries.getJSONObject(index)
        val changes = item.getJSONArray("changes")
        val limitations = item.getJSONArray("limitations")
        ReleaseEntry(
            item.getString("version"), item.getInt("versionCode"), item.getString("date"),
            item.getString("summary"),
            List(changes.length()) { n ->
                val change = changes.getJSONObject(n)
                ReleaseChange(change.getString("title"), change.getString("experience"), change.getString("verify"))
            },
            List(limitations.length()) { n -> limitations.getString(n) },
        )
    }
    require(releases.first().version == installedVersion && releases.first().versionCode == installedCode)
    return ReleaseHistory(root.getString("name"), releases)
}

@Composable
internal fun ReleaseNotesEntry() {
    var opened by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { opened = true }.padding(horizontal = 18.dp, vertical = 18.dp)) {
        Column(Modifier.weight(1f)) {
            Text("版本与更新说明", color = HapiPalette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("当前 ${BuildConfig.VERSION_NAME} · 查看如何体验", color = HapiPalette.muted, fontSize = 12.sp)
        }
        Text("查看", color = HapiPalette.muted, fontSize = 13.sp)
    }
    if (opened) ReleaseNotesDialog { opened = false }
}

@Composable
private fun ReleaseNotesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val result = remember(context) {
        runCatching {
            val raw = context.assets.open("releases.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseReleaseHistory(raw, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = HapiPalette.background,
        title = { Text("版本与更新说明", color = HapiPalette.ink) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("当前安装版本 ${BuildConfig.VERSION_NAME}", color = HapiPalette.ink, fontSize = 14.sp)
                Spacer(Modifier.height(5.dp))
                Text("说明随安装包离线提供；新版本需手动安装。", color = HapiPalette.muted, fontSize = 12.sp)
                val history = result.getOrNull()
                if (history == null) {
                    Spacer(Modifier.height(16.dp))
                    Text("内置版本说明读取失败或与当前安装版本不一致，请核对此安装包的版本记录。", color = HapiPalette.ink, fontSize = 13.sp)
                } else {
                    history.releases.forEach { release ->
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("${history.name} ${release.version} · ${release.date}", color = HapiPalette.ink,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text(release.summary, color = HapiPalette.muted, fontSize = 13.sp)
                        release.changes.forEach { change ->
                            Spacer(Modifier.height(12.dp))
                            Text(change.title, color = HapiPalette.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("体验：${change.experience}", color = HapiPalette.muted, fontSize = 13.sp)
                            Text("验证：${change.verify}", color = HapiPalette.muted, fontSize = 13.sp)
                        }
                        if (release.limitations.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("当前限制", color = HapiPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            release.limitations.forEach { limitation ->
                                Text("• $limitation", color = HapiPalette.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("返回") } },
    )
}
