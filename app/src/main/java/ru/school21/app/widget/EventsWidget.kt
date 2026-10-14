package ru.school21.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import ru.school21.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Виджет «Ближайшие события кампуса» со скроллом. */
class EventsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = rememberWidgetState(context)
            WidgetSurface(Widgets.openUrlIntent(context, "/calendar")) {
                if (state is WidgetState.Ready) Content(context, state.data) else WidgetPlaceholder(context, state)
            }
        }
    }

    @Composable
    private fun Content(context: Context, data: WidgetData) {
        val k = widgetScale(190.dp, 150.dp, max = 2f)
        val now = System.currentTimeMillis()
        val events = data.events.filter { it.startMillis > now }
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LogoBadge((22 * k).dp)
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    context.getString(R.string.widget_events_title),
                    style = textStyle((15 * k).sp, bold = true),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(TODAY.format(LocalDate.now()), style = textStyle((12 * k).sp, WidgetColors.secondary))
            }
            Spacer(GlanceModifier.height(10.dp))
            if (events.isEmpty()) {
                Column(
                    GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(context.getString(R.string.widget_events_empty_title), style = textStyle((14 * k).sp, bold = true))
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        context.getString(R.string.widget_events_empty),
                        style = textStyle((12 * k).sp, WidgetColors.secondary, align = TextAlign.Center),
                    )
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(events, itemId = { it.startMillis + it.name.hashCode() }) { event ->
                        EventRow(event, k)
                    }
                }
            }
        }
    }

    @Composable
    private fun EventRow(event: WidgetEvent, k: Float) {
        val start = Instant.ofEpochMilli(event.startMillis).atZone(ZoneId.systemDefault())
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = (5 * k).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Дата на фирменном градиенте.
            Column(
                modifier = GlanceModifier.size((46 * k).dp)
                    .background(ImageProvider(if (event.mine) R.drawable.widget_accent else R.drawable.widget_tile)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dateColor = if (event.mine) WidgetColors.onAccent else WidgetColors.text
                Text("${start.dayOfMonth}", style = textStyle((17 * k).sp, dateColor, bold = true, align = TextAlign.Center))
                Text(
                    MONTH.format(start),
                    style = textStyle((10 * k).sp, if (event.mine) WidgetColors.onAccent else WidgetColors.secondary, align = TextAlign.Center),
                )
            }
            Spacer(GlanceModifier.width((12 * k).dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(event.name, style = textStyle((14 * k).sp, bold = true), maxLines = 1)
                Text(
                    listOfNotNull(
                        TIME.format(start),
                        LocalContext.current.getString(R.string.widget_event_mine).takeIf { event.mine },
                        event.location,
                    ).joinToString("  ·  "),
                    style = textStyle((12 * k).sp, WidgetColors.secondary),
                    maxLines = 1,
                )
            }
        }
    }

    private companion object {
        val RU: Locale = Locale.forLanguageTag("ru")
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", RU)
        val TODAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", RU)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
