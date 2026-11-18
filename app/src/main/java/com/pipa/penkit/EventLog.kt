package com.pipa.penkit

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EventLog {
    private const val MAX = 60
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val lines = mutableStateListOf<String>()

    fun add(msg: String) {
        lines.add(0, fmt.format(Date()) + "  " + msg)
        while (lines.size > MAX) lines.removeAt(lines.size - 1)
    }
}
