package com.bagimliligim.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bagimliligim.app.R
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.presentation.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuotaNotifier(private val context: Context) {
    suspend fun show(threshold: Int, distance: Float) = withContext(Dispatchers.Main.immediate) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
        })
        val body = context.getString(R.string.notification_body, threshold, Analytics.distance(distance))
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE) {
            val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                manager.notify(threshold, NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.notification_title))
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER).build())
            } catch (_: SecurityException) { Toast.makeText(context, body, Toast.LENGTH_LONG).show() }
        } else Toast.makeText(context, body, Toast.LENGTH_LONG).show()
    }
    companion object { const val CHANNEL = "daily_swipe_budget" }
}
