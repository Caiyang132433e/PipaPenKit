package com.pipa.penkit

import android.content.Context

/** Ring buffer of debug lines persisted in SharedPreferences. */
object LogStore {
    private const val MAX = 40
    private const val KEY = "log_lines"

    fun all(ctx: Context): List<String> {
        val raw = Prefs.get(ctx).getString(KEY, "").orEmpty()
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun append(ctx: Context, msg: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val lines = (listOf("$stamp  $msg") + all(ctx)).take(MAX)
        Prefs.get(ctx).edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    fun clear(ctx: Context) {
        Prefs.get(ctx).edit().remove(KEY).apply()
    }
}
