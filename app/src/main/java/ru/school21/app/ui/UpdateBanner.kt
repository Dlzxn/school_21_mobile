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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.school21.app.R
import ru.school21.app.update.UpdateInfo

/** Карточка «Доступна новая версия» внизу экрана. */
@Composable
fun UpdateBanner(update: UpdateInfo, onDownload: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ElevatedCard(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.update_available, update.version), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.update_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.auto_login_not_now)) }
                    Button(onClick = onDownload) { Text(stringResource(R.string.update_download)) }
                }
            }
        }
    }
}
