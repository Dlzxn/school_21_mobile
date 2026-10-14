package ru.school21.app

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import ru.school21.app.api.Coalition
import ru.school21.app.api.Points
import ru.school21.app.api.Workstation
import ru.school21.app.widget.WidgetData
import ru.school21.app.widget.WidgetEvent
import ru.school21.app.widget.Widgets

/**
 * Только debug-сборка: подставляет в виджеты демо-данные (для скриншотов-превью в списке виджетов).
 * Запуск: adb shell am start -n ru.school21.app/.DebugWidgetDemoActivity
 * Следующая синхронизация вернёт настоящие данные.
 */
class DebugWidgetDemoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hour = 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val demo = WidgetData(
            login = "student",
            subtitle = "Core program · 26_08",
            level = 5,
            exp = 2480,
            expToNextLevel = 320,
            points = Points(peerReviewPoints = 4, codeReviewPoints = 6, coins = 240),
            logtime = 6.5,
            workstation = Workstation("Ocean", "a", 5),
            coalition = Coalition("Capybaras", 12),
            events = listOf(
                WidgetEvent("Exam C Piscine", now + 26 * hour, "Ocean", mine = true),
                WidgetEvent("Воркшоп по Git", now + 50 * hour, "Space"),
                WidgetEvent("Встреча с работодателем", now + 74 * hour, "Hall"),
            ),
            updatedAt = now,
        )
        AppSettings(this).widgetData = demo.toJson()
        MainScope().launch {
            Widgets.updateAll(applicationContext)
            finish()
        }
    }
}
