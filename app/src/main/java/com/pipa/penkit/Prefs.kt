package com.pipa.penkit

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "penkit_prefs"

    const val KEY_RELAY_ENABLED = "relay_enabled"
    const val KEY_TARGET_SINGLE = "target_single"
    const val KEY_TARGET_DOUBLE = "target_double"
    const val KEY_TARGET_LONG = "target_long"

    const val DEFAULT_SINGLE = "com.shezik.drawanywhere"
    const val DEFAULT_DOUBLE = "com.miui.notes"
    const val DEFAULT_LONG = "com.miui.notes"

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun migrate(ctx: Context) {
        val p = get(ctx)
        if (!p.getBoolean("migrated", false)) {
            p.edit()
                .putBoolean("migrated", true)
                .putString(KEY_TARGET_SINGLE, p.getString(KEY_TARGET_SINGLE, null) ?: DEFAULT_SINGLE)
                .putString(KEY_TARGET_DOUBLE, p.getString(KEY_TARGET_DOUBLE, null) ?: DEFAULT_DOUBLE)
                .putString(KEY_TARGET_LONG, p.getString(KEY_TARGET_LONG, null) ?: DEFAULT_LONG)
                .apply()
        }
    }
}
