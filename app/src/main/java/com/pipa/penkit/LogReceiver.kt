package com.pipa.penkit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives debug lines from the system_server module. */
class LogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG) return
        val msg = intent.getStringExtra(EXTRA_MSG) ?: return
        LogStore.append(context, msg)
    }

    companion object {
        const val ACTION_LOG = "com.pipa.penkit.LOG"
        const val EXTRA_MSG = "msg"
    }
}
