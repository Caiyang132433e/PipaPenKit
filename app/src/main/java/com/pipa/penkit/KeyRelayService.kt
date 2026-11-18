package com.pipa.penkit

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KeyRelayService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        EventLog.add("无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = Prefs.get(this)
        if (!prefs.getBoolean(Prefs.KEY_RELAY_ENABLED, true)) return false
        if (event.action != KeyEvent.ACTION_UP) return false

        val key: String
        val label: String
        when (event.keyCode) {
            KeyEvent.KEYCODE_F20 -> { key = Prefs.KEY_TARGET_SINGLE; label = "单击" }
            KeyEvent.KEYCODE_F19 -> { key = Prefs.KEY_TARGET_DOUBLE; label = "双击" }
            KeyEvent.KEYCODE_F18 -> { key = Prefs.KEY_TARGET_LONG; label = "长按" }
            else -> return false
        }

        val target = prefs.getString(key, "").orEmpty()
        EventLog.add("尾部键 " + label + " -> " + (if (target.isBlank()) "(未配置)" else target))
        if (target.isNotBlank()) launchTarget(target)
        return true
    }

    private fun launchTarget(target: String) {
        try {
            val cn = ComponentName.unflattenFromString(target)
            val intent = if (cn != null) Intent().setComponent(cn)
            else packageManager.getLaunchIntentForPackage(target)
            if (intent == null) {
                EventLog.add("找不到目标：" + target)
                return
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (t: Throwable) {
            EventLog.add("启动失败：" + t.message)
        }
    }

    companion object {
        @Volatile
        var instance: KeyRelayService? = null
            private set
    }
}
