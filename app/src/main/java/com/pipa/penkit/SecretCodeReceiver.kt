package com.pipa.penkit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fallback entry for devices without a dialer: pipa://open (also kept the dial code). */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}
