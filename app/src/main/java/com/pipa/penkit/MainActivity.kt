package com.pipa.penkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

    var slotSingle by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_SLOT_SINGLE, true)) }
    var slotDouble by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_SLOT_DOUBLE, true)) }
    var slotLong by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_SLOT_LONG, true)) }

    var penEnable by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_PEN_ENABLE, true)) }
    var scribeEnable by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_SCRIBE_ENABLE, true)) }
    var ignoreGesture by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_IGNORE_KEY_GESTURE, false)) }
    var driverVersion by remember { mutableStateOf(prefs.getInt(Prefs.KEY_DRIVER_VERSION, 2)) }

    var noMagicPointer by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_NO_MAGIC_POINTER, false)) }
    var restoreEsc by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_RESTORE_ESC, false)) }
    var fingerNum4 by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_FINGER_NUM_4, false)) }
    var fixGestureFilter by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_FIX_GESTURE_FILTER, false)) }
    var disableFixedOrientation by remember { mutableStateOf(prefs.getBoolean(Prefs.KEY_DISABLE_FIXED_ORIENTATION, false)) }

    var iconVisible by remember { mutableStateOf(LauncherIcon.isVisible(ctx)) }
    var logs by remember { mutableStateOf(LogStore.all(ctx)) }
    var heartbeat by remember { mutableStateOf(prefs.getLong("hook_heartbeat", 0L)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            logs = LogStore.all(ctx)
            heartbeat = prefs.getLong("hook_heartbeat", 0L)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(heartbeat)

            SectionCard("速记键", "笔尾蓝牙键：单击 F20 / 双击 F19 / 长按 F18") {
                SwitchRow("启用映射", "由系统模块直接拦截，无需无障碍", relay) {
                    relay = it
                    prefs.edit().putBoolean(Prefs.KEY_RELAY_ENABLED, it).apply()
                }
                HorizontalDivider()
                SlotTarget(
                    "单击（F20）", tSingle, slotSingle,
                    { slotSingle = it; prefs.edit().putBoolean(Prefs.KEY_SLOT_SINGLE, it).apply() },
                    { tSingle = it; prefs.edit().putString(Prefs.KEY_TARGET_SINGLE, it).apply() }
                )
                SlotTarget(
                    "双击（F19）", tDouble, slotDouble,
                    { slotDouble = it; prefs.edit().putBoolean(Prefs.KEY_SLOT_DOUBLE, it).apply() },
                    { tDouble = it; prefs.edit().putString(Prefs.KEY_TARGET_DOUBLE, it).apply() }
                )
                SlotTarget(
                    "长按（F18）", tLong, slotLong,
                    { slotLong = it; prefs.edit().putBoolean(Prefs.KEY_SLOT_LONG, it).apply() },
                    { tLong = it; prefs.edit().putString(Prefs.KEY_TARGET_LONG, it).apply() }
                )
            }

            SectionCard("手写笔", "需要模块生效，改动后重启") {
                SwitchRow("去除触控笔蓝牙限制", "非官方笔也能进入笔模式", penEnable) {
                    penEnable = it
                    prefs.edit().putBoolean(Prefs.KEY_PEN_ENABLE, it).apply()
                }
                SwitchRow("去除随手写蓝牙限制", "让随手写认这支笔", scribeEnable) {
                    scribeEnable = it
                    prefs.edit().putBoolean(Prefs.KEY_SCRIBE_ENABLE, it).apply()
                }
                SwitchRow("忽略触控笔按键手势", "不让系统吞掉笔键", ignoreGesture) {
                    ignoreGesture = it
                    prefs.edit().putBoolean(Prefs.KEY_IGNORE_KEY_GESTURE, it).apply()
                }
                HorizontalDivider()
                VersionSelector(driverVersion) {
                    driverVersion = it
                    prefs.edit().putInt(Prefs.KEY_DRIVER_VERSION, it).apply()
                }
            }

            SectionCard("系统增强", "移植自 MaxMiPad，改动后重启") {
                SwitchRow("关闭 Magic Pointer", "停用手写笔的魔法指针服务", noMagicPointer) {
                    noMagicPointer = it
                    prefs.edit().putBoolean(Prefs.KEY_NO_MAGIC_POINTER, it).apply()
                }
                SwitchRow("恢复 ESC 键", "阻止系统把 ESC 映射成返回", restoreEsc) {
                    restoreEsc = it
                    prefs.edit().putBoolean(Prefs.KEY_RESTORE_ESC, it).apply()
                }
                SwitchRow("三指手势改为四指", "降低误触", fingerNum4) {
                    fingerNum4 = it
                    prefs.edit().putBoolean(Prefs.KEY_FINGER_NUM_4, it).apply()
                }
                SwitchRow("修复三指手势被屏蔽", "按系统设置恢复三指/四指手势", fixGestureFilter) {
                    fixGestureFilter = it
                    prefs.edit().putBoolean(Prefs.KEY_FIX_GESTURE_FILTER, it).apply()
                }
                SwitchRow("禁用固定屏幕方向", "对所有应用生效", disableFixedOrientation) {
                    disableFixedOrientation = it
                    prefs.edit().putBoolean(Prefs.KEY_DISABLE_FIXED_ORIENTATION, it).apply()
                }
            }

            SectionCard("外观", "桌面入口") {
                SwitchRow("显示桌面图标", "隐藏后从 LSPosed 模块页进入", iconVisible) {
                    iconVisible = it
                    LauncherIcon.setVisible(ctx, it)
                }
            }

            SectionCard("日志", "模块通过广播回传") {
                Column(Modifier.fillMaxWidth()) {
                    logs.take(14).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    if (logs.isEmpty()) {
                        Text(
                            "暂无事件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { LogStore.clear(ctx); logs = emptyList() }) { Text("清空日志") }
            }
        }
    }
}

@Composable
fun SlotTarget(
    label: String,
    value: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onChange: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f)) {
            TargetSelector(label, value, onChange)
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSelector(label: String, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val presets = listOf(
        "速记（小米笔记）" to Prefs.PRESET_NOTES,
        "DrawAnywhere" to Prefs.PRESET_DRAW,
    )
    val preset = presets.firstOrNull { it.second == value }
    val isCustom = preset == null
    val display = preset?.first ?: "自定义"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                presets.forEach { (name, pkg) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onChange(pkg)
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("自定义") },
                    onClick = {
                        onChange("")
                        expanded = false
                    }
                )
            }
        }
        if (isCustom) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text("包名或 包名/.Activity") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSelector(value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "驱动版本 $value",
            onValueChange = {},
            readOnly = true,
            label = { Text("触控笔驱动版本") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(1, 2, 3).forEach { v ->
                DropdownMenuItem(
                    text = { Text("版本 $v") },
                    onClick = {
                        onChange(v)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun StatusCard(heartbeat: Long) {
    val connected = heartbeat > 0L
    val text = if (connected) {
        "已连接 · " + SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(heartbeat))
    } else {
        "未上报 —— 检查 LSPosed 是否启用、作用域是否含 system"
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) }
    )
}