package ru.school21.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.school21.app.R

/** Каркас нативного экрана: тёмный фон, заголовок с крестиком, прокрутка, ограничение ширины на планшете. */
@Composable
fun ScreenScaffold(titleRes: Int, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = S21Colors.Background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.auto_login_close))
                    }
                }
                Spacer(Modifier.height(16.dp))
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Логотип «21» на фирменном градиенте. */
@Composable
fun LogoBadge(size: Int = 48) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(S21Colors.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Text("21", color = S21Colors.Background, fontWeight = FontWeight.Black, fontSize = (size * 0.42).sp)
    }
}

/** Подпись группы настроек. */
@Composable
fun SectionLabel(textRes: Int) {
    Text(
        text = stringResource(textRes).uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
        color = S21Colors.Teal,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** Карточка-группа: строки внутри разделяются тонкой линией. */
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(S21Colors.Card),
        content = content,
    )
}

@Composable
fun CardDivider() {
    HorizontalDivider(color = S21Colors.Divider, modifier = Modifier.padding(start = 68.dp))
}

/** Строка настроек: иконка в цветном кружке, заголовок, подпись и элемент справа. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = S21Colors.Teal,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = { if (onClick != null) Chevron() },
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) S21Colors.TextPrimary else S21Colors.TextSecondary,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = S21Colors.TextSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = S21Colors.TextSecondary)
}

/** Плитка «число + подпись» — для экрана пира. */
@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(S21Colors.CardRaised)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = S21Colors.TextPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = S21Colors.TextSecondary, maxLines = 1)
    }
}

/** Небольшая «пилюля» статуса с цветной точкой. */
@Composable
fun StatusPill(text: String, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) S21Colors.Green.copy(alpha = 0.14f) else S21Colors.CardRaised)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) S21Colors.Green else S21Colors.TextSecondary)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) S21Colors.Green else S21Colors.TextSecondary,
        )
    }
}
