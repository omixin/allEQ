package com.omix.alleq.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omix.alleq.R
import com.omix.alleq.logger.AppLogger

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // restore only if master switch was on before reboot
        val prefs = context.getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_enabled", true)) return

        AudioProcessorService.launchedFromBoot = true
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioProcessorService::class.java),
            )
            AppLogger.log(TAG, "Boot completed: audio service started")
        } catch (e: Exception) {
            AudioProcessorService.launchedFromBoot = false
            AppLogger.log(TAG, "Boot autostart blocked: ${e.message}")
            postResumeNotification(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        const val RESUME_NOTIFICATION_ID = 2002

        // fallback when os refuses fgs start: tap from notification is an allowed start
        fun postResumeNotification(context: Context) {
            AudioProcessorService.createNotificationChannel(context)
            val startIntent = Intent(context, AudioProcessorService::class.java).apply {
                action = AudioProcessorService.ACTION_START
            }
            val pendingIntent = PendingIntent.getForegroundService(
                context, 3, startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, AudioProcessorService.CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notif_resume_title))
                .setContentText(context.getString(R.string.notif_resume_text))
                .setSmallIcon(R.drawable.ic_qs_alleq)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            try {
                context.getSystemService(NotificationManager::class.java)
                    .notify(RESUME_NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
            }
        }
    }
}
