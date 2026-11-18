package com.pipa.penkit

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Hide / show the launcher icon via an activity-alias. */
object LauncherIcon {

    private fun alias(ctx: Context) = ComponentName(ctx.packageName, ctx.packageName + ".Launcher")

    fun isVisible(ctx: Context): Boolean {
        val state = ctx.packageManager.getComponentEnabledSetting(alias(ctx))
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    }

    fun setVisible(ctx: Context, visible: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx),
            if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
