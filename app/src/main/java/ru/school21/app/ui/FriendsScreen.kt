package ru.school21.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.school21.app.AppSettings
import ru.school21.app.R
import ru.school21.app.api.HttpException
import ru.school21.app.api.InvalidCredentialsException
import ru.school21.app.api.OpenApi
import ru.school21.app.api.Workstation
import ru.school21.app.auth.CredentialStore
import ru.school21.app.friends.Friends
import ru.school21.app.sync.SyncWorker

/** Что известно о друге: null в полях — ещё грузится или не удалось. */
private data class FriendState(val loaded: Boolean, val failed: Boolean, val workstation: Workstation?, val level: Int?)

/** «Мои друзья»: список логинов, кто в кампусе и где сидит, уведомление о приходе. */
@Composable
fun FriendsScreen(
    onOpenProfile: (login: String) -> Unit,
    onSetupAutoLogin: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val hasCredentials = remember { CredentialStore(context).hasCredentials() }
    val api = remember { OpenApi(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var friends by remember { mutableStateOf(settings.friends) }
    val states = remember { mutableStateMapOf<String, FriendState>() }
    var input by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var notify by remember { mutableStateOf(settings.notifyFriends) }

    suspend fun load(login: String) {
        states[login] = states[login]?.copy(loaded = false) ?: FriendState(false, false, null, null)
        states[login] = try {
            coroutineScope {
                val ws = async { api.workstation(login) }
                val level = async { runCatching { api.participant(login).level }.getOrNull() }
                FriendState(true, false, ws.await(), level.await())
            }
        } catch (_: Exception) {
            FriendState(true, true, null, null)
        }
    }

    fun refreshAll() {
        scope.launch {
            val limit = Semaphore(3)
            friends.map { login -> async { limit.withPermit { load(login) } } }.awaitAll()
        }
    }

    fun save(list: List<String>) {
        friends = list
        settings.friends = list
        // Новый состав — заново «первая проверка», чтобы не прислать лишнего.
        settings.friendPlaces = null
        SyncWorker.reschedule(context)
    }

    fun add() {
        val login = Friends.normalize(input)
        if (login.isEmpty()) return
        when {
            login in friends -> { addError = context.getString(R.string.friends_already); return }
            friends.size >= Friends.MAX -> { addError = context.getString(R.string.friends_limit, Friends.MAX); return }
        }
        focusManager.clearFocus()
        adding = true
        addError = null
        scope.launch {
            try {
                val participant = api.participant(login)
                save(friends + participant.login.lowercase())
                input = ""
                load(participant.login.lowercase())
            } catch (e: HttpException) {
                addError = context.getString(
                    when (e.code) {
                        404 -> R.string.peer_not_found
                        429 -> R.string.peer_rate_limited
                        else -> R.string.peer_error
                    }
                )
            } catch (_: InvalidCredentialsException) {
                addError = context.getString(R.string.peer_bad_credentials)
            } catch (_: Exception) {
                addError = context.getString(R.string.peer_error)
            }
            adding = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        settings.notifyFriends = true
        notify = true
        SyncWorker.reschedule(context)
        SyncWorker.runNow(context)
    }

    LaunchedEffect(Unit) { if (hasCredentials) refreshAll() }

    ScreenScaffold(R.string.friends_title, onClose) {
        if (!hasCredentials) {
            SettingsCard {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.peer_needs_auto_login), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onSetupAutoLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.auto_login_setup))
                    }
                }
            }
            return@ScreenScaffold
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; addError = null },
                label = { Text(stringResource(R.string.friends_add_hint)) },
                singleLine = true,
                enabled = !adding,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = ::add, enabled = !adding && input.isNotBlank()) {
                if (adding) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.friends_add))
                }
            }
        }
        addError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Notifications,
                iconTint = S21Colors.Green,
                title = stringResource(R.string.friends_notify),
                subtitle = stringResource(R.string.friends_notify_hint),
                trailing = {
                    Switch(checked = notify, onCheckedChange = { checked ->
                        if (checked && Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            settings.notifyFriends = checked
                            notify = checked
                            SyncWorker.reschedule(context)
                            if (checked) SyncWorker.runNow(context)
                        }
                    })
                },
            )
        }

        if (friends.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.friends_empty), color = S21Colors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            return@ScreenScaffold
        }

        // В кампусе — наверху; дальше в порядке добавления.
        val sorted = friends.sortedBy { if (states[it]?.workstation != null) 0 else 1 }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Text(
                stringResource(R.string.friends_in_campus, friends.count { states[it]?.workstation != null }, friends.size).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
                color = S21Colors.Teal,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            TextButton(onClick = ::refreshAll) { Text(stringResource(R.string.friends_refresh)) }
        }
        SettingsCard {
            sorted.forEachIndexed { index, login ->
                if (index > 0) CardDivider()
                FriendRow(
                    login = login,
                    state = states[login],
                    onOpen = { onOpenProfile(login) },
                    onRemove = { save(friends - login); states.remove(login) },
                )
            }
        }
    }
}

@Composable
private fun FriendRow(login: String, state: FriendState?, onOpen: () -> Unit, onRemove: () -> Unit) {
    val ws = state?.workstation
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(S21Colors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(login.take(1).uppercase(), color = S21Colors.Background, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(S21Colors.Card)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (ws != null) S21Colors.Green else S21Colors.TextSecondary),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(login, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                state?.level?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.friends_level, it), style = MaterialTheme.typography.labelSmall, color = S21Colors.TextSecondary)
                }
            }
            Spacer(Modifier.height(2.dp))
            when {
                state == null || !state.loaded -> Text("…", color = S21Colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                state.failed -> Text(stringResource(R.string.friends_unknown), color = S21Colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                ws != null -> Text(
                    stringResource(R.string.peer_place, ws.clusterName, ws.row.uppercase(), ws.number),
                    color = S21Colors.Green,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                )
                else -> Text(stringResource(R.string.widget_not_in_campus), color = S21Colors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.friends_remove), tint = S21Colors.TextSecondary)
        }
    }
}
