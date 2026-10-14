package ru.school21.app.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.school21.app.AppSettings
import ru.school21.app.BuildConfig
import ru.school21.app.R
import ru.school21.app.auth.CredentialStore
import java.util.Locale

/** Цвета виджетов — палитра сайта; виджеты всегда тёмные, как и платформа. */
object WidgetColors {
    val text = ColorProvider(Color(0xFFFFFFFF))
    val secondary = ColorProvider(Color(0xFF9AA6B8))
    val teal = ColorProvider(Color(0xFF3DDABF))
    val green = ColorProvider(Color(0xFF44EB99))
    val onAccent = ColorProvider(Color(0xFF14202C))
}

/**
 * Во сколько раз крупнее рисовать относительно базового размера [baseWidth]×[baseHeight].
 * На планшетах клетки рабочего стола большие, а MIUI вдобавок уменьшает шрифты виджетов ~на 20%,
 * поэтому фиксированные размеры выглядят мелко.
 */
@Composable
fun widgetScale(baseWidth: Dp, baseHeight: Dp, max: Float = 2f): Float {
    val size = LocalSize.current
    val scale = minOf(size.width / baseWidth, size.height / baseHeight).coerceIn(0.85f, max)
    if (BuildConfig.DEBUG) Log.d("S21Widget", "size=$size base=${baseWidth}x$baseHeight scale=$scale")
    return scale
}

/** Что показывать виджету: данные, «загрузка» или «нужен автовход». */
sealed interface WidgetState {
    data class Ready(val data: WidgetData) : WidgetState
    data object Loading : WidgetState
    data object NeedsLogin : WidgetState
}

/** Текущий снимок данных; перечитывается, когда [Widgets.updateAll] меняет версию в состоянии Glance. */
@Composable
fun rememberWidgetState(context: Context): WidgetState {
    val version = currentState(Widgets.DATA_VERSION)
    return remember(version) { loadWidgetState(context) }
}

fun loadWidgetState(context: Context): WidgetState {
    val data = WidgetData.fromJson(AppSettings(context).widgetData)
    return when {
        data != null -> WidgetState.Ready(data)
        CredentialStore(context).hasCredentials() -> WidgetState.Loading
        else -> WidgetState.NeedsLogin
    }
}

fun formatTime(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(millis)

/** 12345 → «12 345». */
fun formatNumber(value: Long): String = String.format(Locale.forLanguageTag("ru"), "%,d", value)

fun textStyle(size: TextUnit, color: ColorProvider = WidgetColors.text, bold: Boolean = false, align: TextAlign? = null) =
    TextStyle(
        fontSize = size,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
    )

/** Фон виджета (градиент + обводка); тап открывает [intent]. */
@Composable
fun WidgetSurface(intent: Intent, padding: Dp = 16.dp, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.widget_bg))
            .cornerRadius(26.dp)
            .padding(padding)
            .clickable(actionStartActivity(intent)),
    ) {
        content()
    }
}

/** Плашка «21» на фирменном градиенте. */
@Composable
fun LogoBadge(size: Dp = 26.dp) {
    Box(
        modifier = GlanceModifier.size(size).background(ImageProvider(R.drawable.widget_badge)),
        contentAlignment = Alignment.Center,
    ) {
        Text("21", style = textStyle((size.value * 0.44f).sp, WidgetColors.onAccent, bold = true))
    }
}

/** Пилюля статуса «в кампусе / вне кампуса» с цветной точкой. */
@Composable
fun CampusPill(context: Context, data: WidgetData, showPlace: Boolean = true, k: Float = 1f) {
    val ws = data.workstation
    Row(
        modifier = GlanceModifier
            .background(ImageProvider(if (ws != null) R.drawable.widget_pill_on else R.drawable.widget_pill_off))
            .padding(horizontal = (9 * k).dp, vertical = (4 * k).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier.size((7 * k).dp)
                .background(ImageProvider(if (ws != null) R.drawable.widget_dot_on else R.drawable.widget_dot_off)),
        ) {}
        Spacer(GlanceModifier.width((5 * k).dp))
        Text(
            text = when {
                ws == null -> context.getString(R.string.widget_out_of_campus)
                showPlace -> "${ws.clusterName} · ${ws.row.uppercase()}${ws.number}"
                else -> context.getString(R.string.widget_in_campus)
            },
            style = textStyle((11 * k).sp, if (ws != null) WidgetColors.green else WidgetColors.secondary, bold = true),
            maxLines = 1,
        )
    }
}

/** «Стеклянная» плитка: крупное число и подпись. [accent] — плитка на фирменном градиенте. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: GlanceModifier = GlanceModifier,
    accent: Boolean = false,
    valueSize: Float = 19f,
) {
    Column(
        modifier = modifier
            .background(ImageProvider(if (accent) R.drawable.widget_accent else R.drawable.widget_tile))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = textStyle(valueSize.sp, if (accent) WidgetColors.onAccent else WidgetColors.text, bold = true, align = TextAlign.Center),
            maxLines = 1,
        )
        Text(
            label,
            style = textStyle((valueSize * 0.42f).coerceAtLeast(10f).sp, if (accent) WidgetColors.onAccent else WidgetColors.secondary, align = TextAlign.Center),
            maxLines = 1,
        )
    }
}

/** Заглушка: «Загрузка…» или «Включите автовход». */
@Composable
fun WidgetPlaceholder(context: Context, state: WidgetState) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoBadge(30.dp)
        Spacer(GlanceModifier.height(8.dp))
        Text(
            context.getString(if (state is WidgetState.NeedsLogin) R.string.widget_needs_login else R.string.widget_loading),
            style = textStyle(12.sp, WidgetColors.secondary, align = TextAlign.Center),
        )
    }
}
