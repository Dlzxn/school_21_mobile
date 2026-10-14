package ru.school21.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONException
import ru.school21.app.R
import ru.school21.app.api.Coalition
import ru.school21.app.api.HttpException
import ru.school21.app.api.InvalidCredentialsException
import ru.school21.app.api.OpenApi
import ru.school21.app.api.Participant
import ru.school21.app.api.Points
import ru.school21.app.api.Workstation
import ru.school21.app.auth.CredentialStore
import ru.school21.app.widget.formatHours
import ru.school21.app.widget.formatNumber
import java.io.IOException

private class PeerInfo(
    val participant: Participant,
    val workstation: Workstation?,
    val points: Points?,
    val logtime: Double?,
    val coalition: Coalition?,
)

/** «Где сидит пир»: место в кампусе и основные данные по логину (официальный Open API). */
@Composable
fun PeerScreen(
    onOpenProfile: (login: String) -> Unit,
    onSetupAutoLogin: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val ownLogin = remember { CredentialStore(context).load()?.apiLogin }
    val api = remember { OpenApi(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var login by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<PeerInfo?>(null) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    fun search(target: String = login) {
        val query = target.trim().substringBefore('@').lowercase()
        if (query.isEmpty()) return
        login = query
        focusManager.clearFocus()
        loading = true
        errorRes = null
        info = null
        scope.launch {
            try {
                info = coroutineScope {
                    val participant = async { api.participant(query) }
                    val workstation = async { api.workstation(query) }
                    // Очки и логтайм второстепенны: без них карточка всё равно полезна.
                    val points = async { runCatching { api.points(query) }.getOrNull() }
                    val logtime = async { runCatching { api.logtime(query) }.getOrNull() }
                    val coalition = async { runCatching { api.coalition(query) }.getOrNull() }
                    PeerInfo(participant.await(), workstation.await(), points.await(), logtime.await(), coalition.await())
                }
            } catch (e: HttpException) {
                errorRes = when (e.code) {
                    404 -> R.string.peer_not_found
                    429 -> R.string.peer_rate_limited
                    else -> R.string.peer_error
                }
            } catch (_: InvalidCredentialsException) {
                errorRes = R.string.peer_bad_credentials
            } catch (_: IOException) {
                errorRes = R.string.peer_error
            } catch (_: JSONException) {
                // Неожиданный формат ответа API — не падаем, показываем ошибку.
                errorRes = R.string.peer_error
            }
            loading = false
        }
    }

    ScreenScaffold(R.string.peer_title, onClose) {
        if (ownLogin == null) {
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

        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text(stringResource(R.string.peer_login_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { search() }, enabled = !loading && login.isNotBlank()) {
                Text(stringResource(R.string.peer_search))
            }
            OutlinedButton(onClick = { search(ownLogin) }, enabled = !loading) {
                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.peer_me))
            }
        }
        Spacer(Modifier.height(20.dp))

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorRes != null -> Text(stringResource(errorRes!!), color = MaterialTheme.colorScheme.error)
            info != null -> PeerCard(info!!, onOpenProfile)
        }
    }
}

@Composable
private fun PeerCard(info: PeerInfo, onOpenProfile: (String) -> Unit) {
    val p = info.participant
    SettingsCard {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Аватар-заглушка с первой буквой логина на фирменном градиенте.
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(S21Colors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        p.login.take(1).uppercase(),
                        color = S21Colors.Background,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.login, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    listOfNotNull(p.parallelName, p.className, p.campus).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = S21Colors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            val ws = info.workstation
            StatusPill(
                text = if (ws != null) {
                    stringResource(R.string.peer_place, ws.clusterName, ws.row.uppercase(), ws.number)
                } else {
                    stringResource(R.string.widget_not_in_campus)
                },
                active = ws != null,
            )
            info.coalition?.let { coalition ->
                Spacer(Modifier.height(8.dp))
                Text(
                    listOfNotNull(
                        "🛡 ${coalition.name}",
                        coalition.rank?.let { stringResource(R.string.widget_coalition_rank, it) },
                    ).joinToString("  "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text("${p.level}", fontSize = 44.sp, fontWeight = FontWeight.Black, color = S21Colors.Teal)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.widget_level_label), style = MaterialTheme.typography.labelMedium, color = S21Colors.TextSecondary)
                    Text("${formatNumber(p.expValue)} XP", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.weight(1f))
                if (p.expToNextLevel > 0) {
                    Column(Modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.widget_to_next_level, p.level + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = S21Colors.TextSecondary,
                        )
                        Text("${formatNumber(p.expToNextLevel)} XP", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            info.points?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("${it.coins}", stringResource(R.string.widget_coins), Modifier.weight(1f))
                    StatBox("${it.peerReviewPoints}", "PRP", Modifier.weight(1f))
                    StatBox("${it.codeReviewPoints}", "CRP", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
            }
            info.logtime?.let {
                Text(
                    stringResource(R.string.widget_logtime, formatHours(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = S21Colors.TextSecondary,
                )
            }
            if (p.status != null && p.status != "ACTIVE") {
                Text(stringResource(R.string.peer_status, p.status), color = S21Colors.TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { onOpenProfile(p.login) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = S21Colors.Teal.copy(alpha = 0.16f),
                    contentColor = S21Colors.Teal,
                ),
            ) {
                Text(stringResource(R.string.peer_open_profile))
            }
        }
    }
}
