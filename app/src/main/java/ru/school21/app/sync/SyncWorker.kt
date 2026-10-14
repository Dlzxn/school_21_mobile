package ru.school21.app.sync

import android.content.Context
import android.text.Html
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.school21.app.AppConfig
import ru.school21.app.AppSettings
import ru.school21.app.BuildConfig
import ru.school21.app.R
import ru.school21.app.api.AgendaItem
import ru.school21.app.api.OpenApi
import ru.school21.app.api.PlatformApi
import ru.school21.app.auth.CredentialStore
import ru.school21.app.widget.WidgetData
import ru.school21.app.widget.WidgetEvent
import ru.school21.app.widget.Widgets
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Фоновая синхронизация раз в [AppConfig.SYNC_INTERVAL_MINUTES]: уведомления платформы,
 * новые события кампуса, данные виджета. Каждая часть независима: ошибка одной не мешает другим.
 * Настоящих пушей у платформы нет, поэтому задержка — до интервала (плюс Doze/ограничения вендора).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val settings = AppSettings(context)

    override suspend fun doWork(): Result {
        val credentials = CredentialStore(applicationContext).load() ?: return Result.success()

        if (settings.notifyPlatform) step("platform") { checkPlatformNotifications() }
        if (settings.notifyEvents) step("events") { checkCampusEvents() }
        if (settings.notifyAgenda) step("agenda") { scheduleAgendaReminders() }
        if (Widgets.hasWidgets(applicationContext)) step("widget") { refreshWidget(credentials.apiLogin) }
        return Result.success()
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "$name failed: $e")
        }
    }

    private suspend fun checkPlatformNotifications() {
        val notifications = PlatformApi(applicationContext).notifications()
        val seen = settings.seenIds(KIND_PLATFORM)
        // Первая синхронизация: старые уведомления не вываливаем, только запоминаем.
        if (seen != null) {
            notifications
                .filter { !it.wasRead && it.id !in seen }
                .take(MAX_PER_RUN)
                .forEach {
                    Notifications.show(
                        context = applicationContext,
                        channel = Notifications.CHANNEL_PLATFORM,
                        id = it.id.hashCode(),
                        title = applicationContext.getString(R.string.app_name),
                        text = Html.fromHtml(it.message, Html.FROM_HTML_MODE_COMPACT).toString().trim(),
                        url = "${AppConfig.PLATFORM_ORIGIN}/notifications",
                    )
                }
        }
        settings.setSeenIds(KIND_PLATFORM, seen.orEmpty() + notifications.map { it.id })
    }

    private suspend fun checkCampusEvents() {
        val now = Instant.now()
        val events = OpenApi(applicationContext).events(now, now.plus(Duration.ofDays(EVENTS_LOOKAHEAD_DAYS)))
        val seen = settings.seenIds(KIND_EVENTS)
        if (seen != null) {
            val formatter = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
                .withZone(ZoneId.systemDefault())
            events
                .filter { it.id.toString() !in seen }
                .take(MAX_PER_RUN)
                .forEach {
                    val text = listOfNotNull(formatter.format(it.start), it.location).joinToString(" · ")
                    Notifications.show(
                        context = applicationContext,
                        channel = Notifications.CHANNEL_EVENTS,
                        id = ("event" + it.id).hashCode(),
                        title = applicationContext.getString(R.string.notification_new_event, it.name),
                        text = text,
                        url = "${AppConfig.PLATFORM_ORIGIN}/calendar",
                    )
                }
        }
        settings.setSeenIds(KIND_EVENTS, seen.orEmpty() + events.map { it.id.toString() })
    }

    /** Ставит напоминания о личном календаре на ближайшие сутки (каждое — один раз). */
    private suspend fun scheduleAgendaReminders() {
        val now = Instant.now()
        val items = PlatformApi(applicationContext).agenda(now, now.plus(Duration.ofDays(1)))
        val scheduled = settings.seenIds(KIND_AGENDA).orEmpty()
        val fresh = items.filter { it.start.isAfter(now) && reminderKey(it) !in scheduled }
        fresh.forEach { ReminderWorker.schedule(applicationContext, it) }
        settings.setSeenIds(KIND_AGENDA, scheduled + fresh.map(::reminderKey))
    }

    private fun reminderKey(item: AgendaItem) = "${item.id}@${item.start.epochSecond}"

    private suspend fun refreshWidget(login: String) {
        val api = OpenApi(applicationContext)
        val participant = api.participant(login)
        val now = Instant.now()
        val until = now.plus(Duration.ofDays(EVENTS_LOOKAHEAD_DAYS))
        val campusEvents = runCatching { api.events(now, until) }.getOrDefault(emptyList())
            .map { WidgetEvent(it.name, it.start.toEpochMilli(), it.location, mine = false) }
        // Личный календарь — только если включены функции внутреннего API.
        val agenda = if (AppConfig.PLATFORM_NOTIFICATIONS_ENABLED) {
            runCatching { PlatformApi(applicationContext).agenda(now, until) }.getOrDefault(emptyList())
                .map { WidgetEvent(it.label, it.start.toEpochMilli(), it.description, mine = true) }
        } else {
            emptyList()
        }
        val data = WidgetData(
            login = participant.login,
            subtitle = listOfNotNull(participant.parallelName, participant.className).joinToString(" · ")
                .ifEmpty { null },
            level = participant.level,
            exp = participant.expValue,
            expToNextLevel = participant.expToNextLevel,
            points = api.points(login),
            logtime = runCatching { api.logtime(login) }.getOrNull(),
            workstation = api.workstation(login),
            coalition = runCatching { api.coalition(login) }.getOrNull(),
            events = (agenda + campusEvents).sortedBy { it.startMillis }.take(MAX_WIDGET_EVENTS),
            updatedAt = System.currentTimeMillis(),
        )
        settings.widgetData = data.toJson()
        Widgets.updateAll(applicationContext)
    }

    companion object {
        private const val TAG = "S21Sync"
        private const val WORK_PERIODIC = "sync"
        private const val WORK_NOW = "sync_now"
        private const val KIND_PLATFORM = "platform"
        private const val KIND_EVENTS = "events"
        private const val KIND_AGENDA = "agenda"
        private const val MAX_PER_RUN = 5
        private const val EVENTS_LOOKAHEAD_DAYS = 14L
        private const val MAX_WIDGET_EVENTS = 10

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Включает периодическую синхронизацию, если она кому-то нужна, иначе выключает. */
        /** Есть ли что синхронизировать: нужен автовход и хотя бы одно уведомление или виджет. */
        fun isNeeded(context: Context): Boolean {
            val settings = AppSettings(context)
            return CredentialStore(context).hasCredentials() &&
                (settings.notifyPlatform || settings.notifyEvents || settings.notifyAgenda || Widgets.hasWidgets(context))
        }

        fun reschedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (!isNeeded(context)) {
                workManager.cancelUniqueWork(WORK_PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(AppConfig.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Разовый запуск прямо сейчас (обновить виджет, проверить после включения). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
