package ru.school21.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.school21.app.R
import ru.school21.app.web.AuthBanner
import ru.school21.app.web.WebUiState

/** Экран «Выполняется вход» поверх формы Keycloak и баннеры автовхода. */
@Composable
fun AuthOverlays(state: WebUiState, onOpenSettings: () -> Unit) {
    if (state.autoLoginInProgress) {
        // Страховка: если форма так и не отправилась (изменилась вёрстка), не держим экран вечно.
        LaunchedEffect(Unit) {
            delay(IN_PROGRESS_TIMEOUT_MS)
            state.autoLoginInProgress = false
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.auto_login_in_progress))
            }
        }
    }

    val banner = state.authBanner ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ElevatedCard(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(
                        if (banner == AuthBanner.OFFER) R.string.auto_login_offer else R.string.auto_login_failed
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        state.authBanner = null
                        if (banner == AuthBanner.OFFER) state.autoLoginOfferDismissed = true
                    }) {
                        Text(stringResource(R.string.auto_login_not_now))
                    }
                    Button(onClick = {
                        state.authBanner = null
                        onOpenSettings()
                    }) {
                        Text(
                            stringResource(
                                if (banner == AuthBanner.OFFER) R.string.auto_login_setup else R.string.auto_login_fix
                            )
                        )
                    }
                }
            }
        }
    }
}

private const val IN_PROGRESS_TIMEOUT_MS = 15_000L
