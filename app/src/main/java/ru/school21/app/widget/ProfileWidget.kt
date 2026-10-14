package ru.school21.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import ru.school21.app.R

/** Большой виджет профиля: уровень, XP, коины/PRP/CRP, статус в кампусе, логтайм. */
class ProfileWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = rememberWidgetState(context)
            val login = (state as? WidgetState.Ready)?.data?.login
            WidgetSurface(Widgets.openUrlIntent(context, if (login != null) "/profile/$login" else "/")) {
                when (state) {
                    is WidgetState.Ready -> Content(context, state.data)
                    else -> WidgetPlaceholder(context, state)
                }
            }
        }
    }

    @Composable
    private fun Content(context: Context, data: WidgetData) {
        val size = LocalSize.current
        val wide = size.width >= WIDE.width
        val tall = size.height >= TALL.height
        val k = widgetScale(180.dp, 140.dp, max = 2f)

        Column(GlanceModifier.fillMaxSize()) {
            // Шапка: «21», логин, программа, статус в кампусе.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LogoBadge((26 * k).dp)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(data.login, style = textStyle((15 * k).sp, bold = true), maxLines = 1)
                    data.subtitle?.let { Text(it, style = textStyle((11 * k).sp, WidgetColors.secondary), maxLines = 1) }
                }
                if (wide) CampusPill(context, data, k = k)
            }

            data.coalition?.let { coalition ->
                Spacer(GlanceModifier.height((10 * k).dp))
                Row(
                    GlanceModifier
                        .background(ImageProvider(R.drawable.widget_tile))
                        .padding(horizontal = (10 * k).dp, vertical = (5 * k).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🛡 ${coalition.name}", style = textStyle((12 * k).sp, bold = true), maxLines = 1)
                    coalition.rank?.let {
                        Spacer(GlanceModifier.width((6 * k).dp))
                        Text(
                            context.getString(R.string.widget_coalition_rank, it),
                            style = textStyle((12 * k).sp, WidgetColors.teal, bold = true),
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            // Уровень крупно + XP и сколько осталось до следующего.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("${data.level}", style = textStyle(((if (tall) 46 else 36) * k).sp, WidgetColors.teal, bold = true))
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight().padding(bottom = 6.dp)) {
                    Text(context.getString(R.string.widget_level_label), style = textStyle((11 * k).sp, WidgetColors.secondary))
                    Text("${formatNumber(data.exp)} XP", style = textStyle((14 * k).sp, bold = true), maxLines = 1)
                }
                if (wide && data.expToNextLevel > 0) {
                    Column(GlanceModifier.padding(bottom = 6.dp), horizontalAlignment = Alignment.End) {
                        Text(
                            context.getString(R.string.widget_to_next_level, data.level + 1),
                            style = textStyle((11 * k).sp, WidgetColors.secondary),
                        )
                        Text("${formatNumber(data.expToNextLevel)} XP", style = textStyle((14 * k).sp, bold = true), maxLines = 1)
                    }
                }
            }

            if (tall) {
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    StatTile("${data.points.coins}", context.getString(R.string.widget_coins), GlanceModifier.defaultWeight(), accent = true, valueSize = 20 * k)
                    Spacer(GlanceModifier.width(8.dp))
                    StatTile("${data.points.peerReviewPoints}", "PRP", GlanceModifier.defaultWeight(), valueSize = 20 * k)
                    Spacer(GlanceModifier.width(8.dp))
                    StatTile("${data.points.codeReviewPoints}", "CRP", GlanceModifier.defaultWeight(), valueSize = 20 * k)
                }
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        data.logtime?.let { context.getString(R.string.widget_logtime_short, formatHours(it)) }.orEmpty(),
                        style = textStyle((11 * k).sp, WidgetColors.secondary),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        context.getString(R.string.widget_updated, formatTime(context, data.updatedAt)),
                        style = textStyle((11 * k).sp, WidgetColors.secondary),
                        maxLines = 1,
                    )
                }
            } else {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    context.getString(
                        R.string.widget_points, data.points.coins, data.points.peerReviewPoints, data.points.codeReviewPoints,
                    ),
                    style = textStyle((12 * k).sp, WidgetColors.secondary),
                    maxLines = 1,
                )
            }
        }
    }

    private companion object {
        val WIDE = DpSize(240.dp, 100.dp)
        val TALL = DpSize(240.dp, 175.dp)
    }
}
