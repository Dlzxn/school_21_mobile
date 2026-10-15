package ru.school21.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import ru.school21.app.MainActivity
import ru.school21.app.R

/** Каналы и показ уведомлений. Тап открывает нужную страницу платформы в приложении. */
object Notifications {

    const val CHANNEL_PLATFORM = "platform"
    const val CHANNEL_EVENTS = "events"
    const val CHANNEL_AGENDA = "agenda"
    const val CHANNEL_FRIENDS = "friends"
    const val CHANNEL_UPDATES = "updates"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_PLATFORM,
                    context.getString(R.string.channel_platform),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CHANNEL_AGENDA,
                    context.getString(R.string.channel_agenda),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    CHANNEL_EVENTS,
                    context.getString(R.string.channel_events),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CHANNEL_FRIENDS,
                    context.getString(R.string.channel_friends),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CHANNEL_UPDATES,
                    context.getString(R.string.channel_updates),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        )
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (android.os.Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    fun show(context: Context, channel: String, id: Int, title: String, text: String, url: String) {
        if (!canPost(context)) return
        val intent = Intent(Intent.ACTION_VIEW, url.toUri(), context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом.
        }
    }
}
