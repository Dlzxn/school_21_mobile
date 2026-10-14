package ru.school21.app

import android.content.Context
import androidx.core.content.edit

/** Пользовательские настройки и служебное состояние фоновой синхронизации. */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Уведомления «колокольчика» платформы (внутренний API). */
    var notifyPlatform: Boolean
        get() = AppConfig.PLATFORM_NOTIFICATIONS_ENABLED && prefs.getBoolean("notify_platform", false)
        set(value) = prefs.edit { putBoolean("notify_platform", value) }

    /** Напоминания о пунктах личного календаря (внутренний API). */
    var notifyAgenda: Boolean
        get() = AppConfig.PLATFORM_NOTIFICATIONS_ENABLED && prefs.getBoolean("notify_agenda", false)
        set(value) = prefs.edit { putBoolean("notify_agenda", value) }

    /** Новые события кампуса (официальный Open API). */
    var notifyEvents: Boolean
        get() = prefs.getBoolean("notify_events", false)
        set(value) = prefs.edit { putBoolean("notify_events", value) }

    var menuButton: MenuButtonPosition
        get() = MenuButtonPosition.entries.getOrElse(prefs.getInt("menu_button", 0)) { MenuButtonPosition.RIGHT }
        set(value) = prefs.edit { putInt("menu_button", value.ordinal) }

    /** Уже показанные уведомления/события; null — ещё не было первой синхронизации. */
    fun seenIds(kind: String): Set<String>? = prefs.getStringSet("seen_$kind", null)

    fun setSeenIds(kind: String, ids: Set<String>) {
        // Ограничиваем размер: старые id всё равно больше не придут из API.
        prefs.edit { putStringSet("seen_$kind", ids.toList().takeLast(MAX_SEEN).toSet()) }
    }

    fun resetSeen() {
        prefs.edit { remove("seen_platform"); remove("seen_events") }
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0)
        set(value) = prefs.edit { putLong("last_update_check", value) }

    /** Последние данные для виджета (JSON), чтобы показать их без сети. */
    var widgetData: String?
        get() = prefs.getString("widget_data", null)
        set(value) = prefs.edit { putString("widget_data", value) }

    private companion object {
        const val MAX_SEEN = 300
    }
}

enum class MenuButtonPosition { RIGHT, LEFT, HIDDEN }
