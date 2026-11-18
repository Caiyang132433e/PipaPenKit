package com.pipa.penkit

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "penkit_prefs"

    const val KEY_RELAY_ENABLED = "relay_enabled"
    const val KEY_TARGET_SINGLE = "target_single"
    const val KEY_TARGET_DOUBLE = "target_double"
    const val KEY_TARGET_LONG = "target_long"

    const val KEY_PEN_ENABLE = "pen_enable"
    const val KEY_SCRIBE_ENABLE = "scribe_enable"
    const val KEY_IGNORE_KEY_GESTURE = "ignore_key_gesture"
    const val KEY_DRIVER_VERSION = "driver_version"

    const val KEY_SLOT_SINGLE = "slot_single"
    const val KEY_SLOT_DOUBLE = "slot_double"
    const val KEY_SLOT_LONG = "slot_long"
    const val KEY_NO_MAGIC_POINTER = "sys_no_magic_pointer"
    const val KEY_RESTORE_ESC = "sys_restore_esc"
    const val KEY_FINGER_NUM_4 = "sys_finger_num_4"
    const val KEY_DISABLE_FIXED_ORIENTATION = "sys_disable_fixed_orientation"

    const val PRESET_NOTES = "com.miui.notes"
    const val PRESET_DRAW = "com.shezik.drawanywhere"

    const val DEFAULT_SINGLE = PRESET_DRAW
    const val DEFAULT_DOUBLE = PRESET_DRAW
    const val DEFAULT_LONG = PRESET_DRAW

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
