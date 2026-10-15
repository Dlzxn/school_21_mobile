package ru.school21.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.school21.app.MenuButtonPosition
import ru.school21.app.R

/** Небольшая полупрозрачная кнопка поверх сайта, открывающая меню приложения. */
@Composable
fun MenuButton(position: MenuButtonPosition, onClick: () -> Unit) {
    if (position == MenuButtonPosition.HIDDEN) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        contentAlignment = if (position == MenuButtonPosition.LEFT) Alignment.BottomStart else Alignment.BottomEnd,
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.alpha(0.8f),
        ) {
            Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.menu_title))
        }
    }
}

enum class MenuAction { RELOAD, SHARE, OPEN_IN_BROWSER, PEER, FRIENDS, SETTINGS }

/**
 * Шторка меню. Рисуется в окне приложения (а не отдельным диалогом, как ModalBottomSheet),
 * чтобы системные панели оставались тёмными, как у остального приложения.
 */
@Composable
fun AppMenu(onAction: (MenuAction) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(S21Colors.Card)
                    .navigationBarsPadding()
                    .padding(top = 10.dp, bottom = 12.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(S21Colors.TextSecondary.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = Icons.Filled.Place,
                    iconTint = S21Colors.Green,
                    title = stringResource(R.string.menu_peer),
                    subtitle = stringResource(R.string.menu_peer_hint),
                    onClick = { onAction(MenuAction.PEER) },
                )
                SettingsRow(
                    icon = Icons.Filled.Face,
                    iconTint = S21Colors.Teal,
                    title = stringResource(R.string.menu_friends),
                    subtitle = stringResource(R.string.menu_friends_hint),
                    onClick = { onAction(MenuAction.FRIENDS) },
                )
                SettingsRow(Icons.Filled.Refresh, stringResource(R.string.menu_reload), onClick = { onAction(MenuAction.RELOAD) })
                SettingsRow(
                    icon = Icons.Filled.Share,
                    iconTint = S21Colors.Cyan,
                    title = stringResource(R.string.menu_share),
                    onClick = { onAction(MenuAction.SHARE) },
                )
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = S21Colors.Cyan,
                    title = stringResource(R.string.menu_open_in_browser),
                    onClick = { onAction(MenuAction.OPEN_IN_BROWSER) },
                )
                SettingsRow(
                    icon = Icons.Filled.Settings,
                    iconTint = S21Colors.TextSecondary,
                    title = stringResource(R.string.menu_settings),
                    subtitle = stringResource(R.string.menu_settings_hint),
                    onClick = { onAction(MenuAction.SETTINGS) },
                )
            }
        }
    }
}
