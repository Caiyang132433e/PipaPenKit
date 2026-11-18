package com.pipa.penkit

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.migrate(this)

        // Keep the module's target strings in sync with the UI defaults.
        val i = Intent(LogReceiver.ACTION_LOG)
        i.setPackage(packageName)

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

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            logs = LogStore.all(ctx)
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
        topBar = { TopAppBar(title = { Text("PipaPenKit") }) }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("速记键（笔尾蓝牙键）") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("启用映射")
                        Text(
                            "由系统模块直接拦截，无需无障碍",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = relay, onCheckedChange = {
                        relay = it
                        prefs.edit().putBoolean(Prefs.KEY_RELAY_ENABLED, it).apply()
                    })
                }
                HorizontalDivider()
                Text("按下尾部键时拉起的目标（包名或 包名/.Activity）", style = MaterialTheme.typography.bodySmall)
                TargetField("单击 (F20)", tSingle) { tSingle = it; prefs.edit().putString(Prefs.KEY_TARGET_SINGLE, it).apply() }
                TargetField("双击 (F19)", tDouble) { tDouble = it; prefs.edit().putString(Prefs.KEY_TARGET_DOUBLE, it).apply() }
                TargetField("长按 (F18)", tLong) { tLong = it; prefs.edit().putString(Prefs.KEY_TARGET_LONG, it).apply() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { setAll("com.shezik.drawanywhere") }, label = { Text("DrawAnywhere") })
                    AssistChip(onClick = { setAll("com.miui.notes") }, label = { Text("小米笔记") })
                }
            }

            SectionCard("手写笔（模块功能）") {
                Text("去除触控笔蓝牙限制 / 去除随手写限制 / 忽略按键手势", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "需要在 LSPosed 里启用本模块、作用域勾 system，然后重启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard("日志") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("最近事件", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { LogStore.clear(ctx); logs = emptyList() }) { Text("清空") }
                }
                if (logs.isEmpty()) {
                    Text("暂无事件", style = MaterialTheme.typography.bodySmall)
                } else {
                    logs.take(14).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
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
