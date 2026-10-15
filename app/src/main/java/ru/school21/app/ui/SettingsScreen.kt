package ru.school21.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ru.school21.app.AppConfig
import ru.school21.app.AppSettings
import ru.school21.app.BuildConfig
import ru.school21.app.MenuButtonPosition
import ru.school21.app.R
import ru.school21.app.auth.CredentialStore
import ru.school21.app.sync.SyncWorker

/** Настройки: аккаунт, уведомления, виджеты, кнопка меню, о приложении. */
@Composable
fun SettingsScreen(
    onOpenAutoLogin: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenSite: () -> Unit,
    onReportBug: () -> Unit,
    onOpenAuthor: () -> Unit,
    onMenuButtonChanged: (MenuButtonPosition) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val savedLogin = remember { CredentialStore(context).load()?.login }
    var notifyPlatform by remember { mutableStateOf(settings.notifyPlatform) }
    var notifyEvents by remember { mutableStateOf(settings.notifyEvents) }
    var notifyAgenda by remember { mutableStateOf(settings.notifyAgenda) }
    var menuButton by remember { mutableStateOf(settings.menuButton) }

    // Включение уведомлений: запросить разрешение (Android 13+) и сразу запустить проверку.
    // Первая проверка ничего не показывает — только запоминает текущие уведомления/события.
    var pendingEnable by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingEnable?.invoke()
        pendingEnable = null
    }
    fun enableNotifications(apply: () -> Unit) {
        val enable = {
            apply()
            SyncWorker.reschedule(context)
            SyncWorker.runNow(context)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            pendingEnable = enable
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enable()
        }
    }

    ScreenScaffold(R.string.settings_title, onClose) {
        // Шапка: логотип, название, версия.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            LogoBadge(56)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    stringResource(R.string.settings_header_subtitle, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = S21Colors.TextSecondary,
                )
            }
        }

        SectionLabel(R.string.settings_account)
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.auto_login_title),
                subtitle = if (savedLogin != null) stringResource(R.string.auto_login_enabled_for, savedLogin)
                else stringResource(R.string.settings_auto_login_off),
                onClick = onOpenAutoLogin,
            )
        }

        SectionLabel(R.string.settings_notifications)
        SettingsCard {
            if (AppConfig.PLATFORM_NOTIFICATIONS_ENABLED) {
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_notify_platform),
                    subtitle = stringResource(R.string.settings_notify_platform_hint),
                    enabled = savedLogin != null,
                    trailing = {
                        Switch(checked = notifyPlatform, enabled = savedLogin != null, onCheckedChange = { checked ->
                            if (checked) {
                                enableNotifications { settings.notifyPlatform = true; notifyPlatform = true }
                            } else {
                                settings.notifyPlatform = false
                                notifyPlatform = false
                                SyncWorker.reschedule(context)
                            }
                        })
                    },
                )
                CardDivider()
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    iconTint = S21Colors.Green,
                    title = stringResource(R.string.settings_notify_agenda),
                    subtitle = stringResource(R.string.settings_notify_agenda_hint),
                    enabled = savedLogin != null,
                    trailing = {
                        Switch(checked = notifyAgenda, enabled = savedLogin != null, onCheckedChange = { checked ->
                            if (checked) {
                                enableNotifications { settings.notifyAgenda = true; notifyAgenda = true }
                            } else {
                                settings.notifyAgenda = false
                                notifyAgenda = false
                                SyncWorker.reschedule(context)
                            }
                        })
                    },
                )
                CardDivider()
            }
            SettingsRow(
                icon = Icons.Filled.DateRange,
                iconTint = S21Colors.Cyan,
                title = stringResource(R.string.settings_notify_events),
                subtitle = stringResource(
                    if (savedLogin != null) R.string.settings_notify_events_hint else R.string.settings_needs_auto_login
                ),
                enabled = savedLogin != null,
                trailing = {
                    Switch(checked = notifyEvents, enabled = savedLogin != null, onCheckedChange = { checked ->
                        if (checked) {
                            enableNotifications { settings.notifyEvents = true; notifyEvents = true }
                        } else {
                            settings.notifyEvents = false
                            notifyEvents = false
                            SyncWorker.reschedule(context)
                        }
                    })
                },
            )
            CardDivider()
            SettingsRow(
                icon = Icons.Filled.Build,
                iconTint = S21Colors.TextSecondary,
                title = stringResource(R.string.settings_background_title),
                subtitle = stringResource(R.string.settings_background_hint),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    )
                },
            )
        }

        SectionLabel(R.string.settings_widget)
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Home,
                iconTint = S21Colors.Green,
                title = stringResource(R.string.settings_widget_how_title),
                subtitle = stringResource(R.string.settings_widget_how),
                trailing = null,
            )
        }

        SectionLabel(R.string.settings_menu_button)
        SettingsCard {
            listOf(
                MenuButtonPosition.RIGHT to R.string.settings_menu_right,
                MenuButtonPosition.LEFT to R.string.settings_menu_left,
                MenuButtonPosition.HIDDEN to R.string.settings_menu_hidden,
            ).forEachIndexed { index, (position, label) ->
                if (index > 0) CardDivider()
                val select = {
                    menuButton = position
                    settings.menuButton = position
                    onMenuButtonChanged(position)
                }
                SettingsRow(
                    icon = Icons.Filled.Menu,
                    iconTint = if (menuButton == position) S21Colors.Teal else S21Colors.TextSecondary,
                    title = stringResource(label),
                    onClick = select,
                    trailing = { RadioButton(selected = menuButton == position, onClick = select) },
                )
            }
        }
        Text(
            stringResource(R.string.settings_menu_hint),
            style = MaterialTheme.typography.bodySmall,
            color = S21Colors.TextSecondary,
            modifier = Modifier.padding(start = 6.dp, top = 8.dp, end = 6.dp),
        )

        SectionLabel(R.string.settings_about)
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.settings_check_updates),
                subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                onClick = onCheckUpdates,
            )
            CardDivider()
            SettingsRow(
                icon = Icons.Filled.Info,
                iconTint = S21Colors.Cyan,
                title = stringResource(R.string.settings_repository),
                subtitle = "github.com/${AppConfig.GITHUB_REPO}",
                onClick = onOpenRepository,
            )
            CardDivider()
            SettingsRow(
                icon = Icons.Filled.Home,
                iconTint = S21Colors.Green,
                title = stringResource(R.string.settings_site),
                subtitle = AppConfig.SITE_URL.removePrefix("https://"),
                onClick = onOpenSite,
            )
        }

        SectionLabel(R.string.settings_feedback)
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Warning,
                iconTint = S21Colors.Error,
                title = stringResource(R.string.settings_report_bug),
                subtitle = stringResource(R.string.settings_report_bug_hint),
                onClick = onReportBug,
            )
            CardDivider()
            SettingsRow(
                icon = Icons.Filled.Person,
                iconTint = S21Colors.Green,
                title = stringResource(R.string.settings_author),
                subtitle = stringResource(R.string.settings_author_value),
                onClick = onOpenAuthor,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = S21Colors.TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
    }
}
