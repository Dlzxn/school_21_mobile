package ru.school21.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ru.school21.app.AppConfig
import ru.school21.app.R
import ru.school21.app.api.AgendaItem
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Напоминание о пункте личного календаря (запись на событие, проверка).
 * Ставится SyncWorker'ом заранее с задержкой до момента «за N минут до начала».
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        Notifications.show(
            context = applicationContext,
            channel = Notifications.CHANNEL_AGENDA,
            id = inputData.getString(KEY_ID).hashCode(),
            title = title,
            text = inputData.getString(KEY_TEXT).orEmpty(),
            url = "${AppConfig.PLATFORM_ORIGIN}/calendar",
        )
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_TEXT = "text"

        /** За сколько минут до начала напоминать. */
        const val MINUTES_BEFORE = 15L

        fun schedule(context: Context, item: AgendaItem) {
            val now = Instant.now()
            val remindAt = item.start.minus(Duration.ofMinutes(MINUTES_BEFORE))
            val delay = Duration.between(now, remindAt).toMillis().coerceAtLeast(0)
            val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(item.start)
            val text = listOfNotNull(
                context.getString(R.string.reminder_starts_at, time),
                item.description?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to item.id, KEY_TITLE to item.label, KEY_TEXT to text))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("remind_${item.id}_${item.start.epochSecond}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
