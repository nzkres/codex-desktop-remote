package dev.codex.remote

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun NotificationSettings(showIntroduction: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(TaskNotifications.enabled(context)) }
    var permitted by remember { mutableStateOf(TaskNotifications.allowed(context)) }
    var intro by remember { mutableStateOf(showIntroduction && !TaskNotifications.prefs(context).getBoolean("introduced", false)) }
    val status by TaskNotifications.connectionState.collectAsState()
    LaunchedEffect(status) {
        enabled = TaskNotifications.enabled(context)
        permitted = TaskNotifications.allowed(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted && TaskNotifications.allowed(context)
        enabled = permitted
        TaskNotifications.setEnabled(context, enabled)
        if (enabled) TaskNotifications.start(context)
    }
    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 && !TaskNotifications.allowed(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (TaskNotifications.allowed(context)) {
            enabled = true
            TaskNotifications.setEnabled(context, true)
            TaskNotifications.start(context)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    fun dismissIntro() {
        intro = false
        TaskNotifications.prefs(context).edit().putBoolean("introduced", true).apply()
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = TaskNotifications.enabled(context)
                permitted = TaskNotifications.allowed(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (intro) AlertDialog(onDismissRequest = { dismissIntro() }, title = { Text("让任务来提醒你") }, text = {
        Text("任务完成，或需要你回答、选择、审批时，手机会提醒你。\n\n后台会显示一条连接通知，可随时暂停。锁屏默认隐藏内容；省电模式可能延迟，强行停止 App 后需重新打开。")
    }, confirmButton = { TextButton(onClick = { dismissIntro(); enable() }) { Text("开启通知") } },
        dismissButton = { TextButton(onClick = { dismissIntro() }) { Text("稍后在“我的”开启") } })
    if (!showIntroduction) Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("任务通知", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = HapiPalette.ink)
                Text(if (!permitted) "系统通知未允许" else status, color = HapiPalette.muted, fontSize = 12.sp)
            }
            Switch(checked = enabled && permitted, onCheckedChange = {
                if (it) enable() else {
                    enabled = false
                    TaskNotifications.setEnabled(context, false)
                    TaskNotifications.stop(context)
                }
            })
        }
        Spacer(Modifier.height(6.dp))
        Text("完成、问题与审批分别提醒；点击通知回到原对话。后台使用常驻连接，强行停止后需重新打开。", color = HapiPalette.muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("声音与锁屏设置", fontSize = 12.sp) }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text("后台耗电设置", fontSize = 12.sp) }
        }
        Text("如锁屏延迟，可在系统中允许后台运行。更高及时性会增加耗电；本版未接厂商推送。", color = HapiPalette.faint, fontSize = 11.sp)
    }
}
