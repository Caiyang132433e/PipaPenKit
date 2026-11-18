package com.pipa.penkit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recovery entry. Trigger from adb or any automation app:
 *   adb shell am broadcast -a com.pipa.penkit.OPEN -n com.pipa.penkit/.OpenReceiver
 */
class OpenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}
