package com.pipa.penkit

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService

/** Quick settings entry, so the app stays reachable when the launcher icon is hidden. */
class PenTileService : TileService() {

    override fun onStartListening() {
        val current = getTile() ?: return
        current.label = "PipaPenKit"
        current.icon = Icon.createWithResource(this, android.R.drawable.ic_menu_edit)
        current.updateTile()
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } catch (t: Throwable) {
            startActivity(intent)
        }
    }
}
