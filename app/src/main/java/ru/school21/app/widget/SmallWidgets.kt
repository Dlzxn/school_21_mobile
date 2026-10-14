package ru.school21.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import ru.school21.app.R

/** Компактный виджет «Коины · PRP · CRP». */
class PointsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = rememberWidgetState(context)
            WidgetSurface(Widgets.openUrlIntent(context, "/"), padding = 10.dp) {
                if (state is WidgetState.Ready) {
                    val points = state.data.points
                    val size = LocalSize.current
                    // Число должно занимать плитку: ограничиваем и высотой, и шириной плитки.
                    val valueSize = minOf(size.height.value * 0.32f, size.width.value / 3 * 0.38f).coerceIn(18f, 44f)
                    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        val tile = GlanceModifier.defaultWeight().fillMaxHeight()
                        StatTile("${points.coins}", context.getString(R.string.widget_coins), tile, accent = true, valueSize = valueSize)
                        Spacer(GlanceModifier.width(8.dp))
                        StatTile("${points.peerReviewPoints}", "PRP", tile, valueSize = valueSize)
                        Spacer(GlanceModifier.width(8.dp))
                        StatTile("${points.codeReviewPoints}", "CRP", tile, valueSize = valueSize)
                    }
                } else {
                    WidgetPlaceholder(context, state)
                }
            }
        }
    }
}

/** Виджет «Где я»: статус в кампусе, кластер и место, логтайм. */
class CampusWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = rememberWidgetState(context)
            WidgetSurface(Widgets.openUrlIntent(context, "/campus"), padding = 14.dp) {
                if (state is WidgetState.Ready) {
                    val data = state.data
                    val ws = data.workstation
                    val k = widgetScale(150.dp, 75.dp)
                    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        // Вне кампуса крупная надпись уже всё говорит — пилюля только для «в кампусе».
                        if (ws != null) {
                            CampusPill(context, data, showPlace = false, k = k)
                            Spacer(GlanceModifier.height((6 * k).dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LogoBadge((18 * k).dp)
                                Spacer(GlanceModifier.width((6 * k).dp))
                                Text(
                                    context.getString(R.string.widget_campus_label),
                                    style = textStyle((11 * k).sp, WidgetColors.secondary, bold = true),
                                )
                            }
                            Spacer(GlanceModifier.height((6 * k).dp))
                        }
                        Text(
                            if (ws != null) "${ws.clusterName} · ${ws.row.uppercase()}${ws.number}"
                            else context.getString(R.string.widget_out_of_campus_big),
                            style = textStyle((21 * k).sp, if (ws != null) WidgetColors.text else WidgetColors.secondary, bold = true),
                            maxLines = 1,
                        )
                        data.logtime?.let {
                            Spacer(GlanceModifier.height((2 * k).dp))
                            Text(
                                context.getString(R.string.widget_logtime_short, formatHours(it)),
                                style = textStyle((12 * k).sp, WidgetColors.secondary),
                                maxLines = 1,
                            )
                        }
                    }
                } else {
                    WidgetPlaceholder(context, state)
                }
            }
        }
    }
}
