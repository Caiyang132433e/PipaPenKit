package com.pipa.penkit

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.migrate(this)
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            MaterialTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            ) {
                HomeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }

    var relay by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_RELAY_ENABLED, true)) }
    var tSingle by remember { mutableStateOf(prefs.getString(Prefs.KEY_TARGET_SINGLE, Prefs.DEFAULT_SINGLE).orEmpty()) }
    var tDouble by remember { mutableStateOf(prefs.getString(Prefs.KEY_TARGET_DOUBLE, Prefs.DEFAULT_DOUBLE).orEmpty()) }
    var tLong by remember { mutableStateOf(prefs.getString(Prefs.KEY_TARGET_LONG, Prefs.DEFAULT_LONG).orEmpty()) }
    var logs by remember { mutableStateOf(LogStore.all(ctx)) }
    var heartbeat by remember { mutableStateOf(prefs.getLong("hook_heartbeat", 0L)) }
    var iconVisible by remember { mutableStateOf(LauncherIcon.isVisible(ctx)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            logs = LogStore.all(ctx)
            heartbeat = prefs.getLong("hook_heartbeat", 0L)
        }
    }

    fun setAll(v: String) {
        tSingle = v; tDouble = v; tLong = v
        prefs.edit()
            .putString(Prefs.KEY_TARGET_SINGLE, v)
            .putString(Prefs.KEY_TARGET_DOUBLE, v)
            .putString(Prefs.KEY_TARGET_LONG, v)
            .apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PipaPenKit", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "小米平板6 手写笔工具箱",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(heartbeat)

            SectionCard("速记键", "笔尾蓝牙键 F20 / F19 / F18") {
                SwitchRow(
                    title = "启用映射",
                    subtitle = "由系统模块直接拦截，无需无障碍",
                    checked = relay
                ) {
                    relay = it
                    prefs.edit().putBoolean(Prefs.KEY_RELAY_ENABLED, it).apply()
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "按下尾部键时拉起的目标：包名或 包名/.Activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TargetField("单击（F20）", tSingle) { tSingle = it; prefs.edit().putString(Prefs.KEY_TARGET_SINGLE, it).apply() }
                TargetField("双击（F19）", tDouble) { tDouble = it; prefs.edit().putString(Prefs.KEY_TARGET_DOUBLE, it).apply() }
                TargetField("长按（F18）", tLong) { tLong = it; prefs.edit().putString(Prefs.KEY_TARGET_LONG, it).apply() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { setAll("com.shezik.drawanywhere") }, label = { Text("DrawAnywhere") })
                    AssistChip(onClick = { setAll("com.miui.notes") }, label = { Text("小米笔记") })
                }
            }

            SectionCard("外观", "桌面入口") {
                SwitchRow(
                    title = "显示桌面图标",
                    subtitle = "隐藏后从 LSPosed 模块页打开",
                    checked = iconVisible
                ) {
                    iconVisible = it
                    LauncherIcon.setVisible(ctx, it)
                    if (!it) {
                        Toast.makeText(
                            ctx,
                            "图标已隐藏，可从 LSPosed 模块页进入",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                Text(
                    "隐藏后入口：LSPosed 管理器 → 模块 → PipaPenKit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard("日志", "模块通过广播回传") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("最近事件", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { LogStore.clear(ctx); logs = emptyList() }) { Text("清空") }
                }
                if (logs.isEmpty()) {
                    Text(
                        "暂无事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    logs.take(14).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(heartbeat: Long) {
    val text = if (heartbeat <= 0L) {
        "未上报 —— 检查 LSPosed 是否启用、作用域是否含 system"
    } else {
        "已连接 · " + SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(heartbeat))
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (heartbeat > 0L) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("模块状态", style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun TargetField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
