package ru.school21.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import ru.school21.app.AppConfig
import ru.school21.app.MainActivity
import ru.school21.app.sync.SyncWorker

/** Все виджеты приложения: проверка наличия, обновление, общие действия. */
object Widgets {

    private val receivers = listOf(
        ProfileWidgetReceiver::class.java,
        PointsWidgetReceiver::class.java,
        CampusWidgetReceiver::class.java,
        EventsWidgetReceiver::class.java,
    )

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return receivers.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    /** Версия данных в состоянии Glance: её смена заставляет виджет перечитать снимок. */
    internal val DATA_VERSION = longPreferencesKey("data_version")

    /**
     * Перерисовать все виджеты со свежими данными. Glance вызывает provideGlance один раз на сессию,
     * поэтому новые данные доставляем через состояние виджета, а не повторным provideGlance.
     */
    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val version = System.currentTimeMillis()
        listOf(ProfileWidget(), PointsWidget(), CampusWidget(), EventsWidget()).forEach { widget ->
            manager.getGlanceIds(widget.javaClass).forEach { id ->
                updateAppWidgetState(context, id) { it[DATA_VERSION] = version }
                widget.update(context, id)
            }
        }
    }

    /** Открыть страницу платформы в приложении. */
    fun openUrlIntent(context: Context, path: String): Intent =
        Intent(Intent.ACTION_VIEW, (AppConfig.PLATFORM_ORIGIN + path).toUri(), context, MainActivity::class.java)

    internal const val MIN_REFRESH_INTERVAL_MS = 5 * 60_000L
    @Volatile internal var lastRefreshRequestAt = 0L
}

/**
 * Общий ресивер: при добавлении виджета подтягивает свежие данные и включает фоновую синхронизацию.
 * Частота запросов ограничена: WorkManager при постановке задачи переключает свои компоненты,
 * система на это шлёт виджетам onUpdate — без ограничения получился бы бесконечный цикл.
 */
abstract class BaseWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val now = System.currentTimeMillis()
        if (now - Widgets.lastRefreshRequestAt > Widgets.MIN_REFRESH_INTERVAL_MS) {
            Widgets.lastRefreshRequestAt = now
            SyncWorker.runNow(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SyncWorker.reschedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        SyncWorker.reschedule(context)
    }
}

class ProfileWidgetReceiver : BaseWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProfileWidget()
}

class PointsWidgetReceiver : BaseWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PointsWidget()
}

class CampusWidgetReceiver : BaseWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CampusWidget()
}

class EventsWidgetReceiver : BaseWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EventsWidget()
}
