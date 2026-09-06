package com.omix.alleq.logger

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object AppLogger {
    private const val MAX_LOGS = 60
    private val buffer = ConcurrentLinkedDeque<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val entry = "[$timestamp] [$tag] $message" 
        
        buffer.addLast(entry)
        while (buffer.size > MAX_LOGS) {
            buffer.pollFirst()
        }
        
        Log.d(tag, message)
    }

    fun getLogs(): List<String> {
        return buffer.toList()
    }

    fun clear() {
        buffer.clear()
    }
}
