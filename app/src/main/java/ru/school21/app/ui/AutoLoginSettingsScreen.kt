package ru.school21.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.school21.app.R
import ru.school21.app.auth.CredentialChecker
import ru.school21.app.auth.CredentialStore
import ru.school21.app.auth.Credentials

/** Настройка автовхода: ввод и проверка логина/пароля, отключение. */
@Composable
fun AutoLoginSettingsScreen(
    store: CredentialStore,
    onSaved: () -> Unit,
    onDisabled: () -> Unit,
    onClose: () -> Unit,
) {
    var savedLogin by remember { mutableStateOf(store.load()?.login) }
    var editing by remember { mutableStateOf(savedLogin == null) }
    var login by remember { mutableStateOf(savedLogin.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    fun save() {
        val credentials = Credentials(login.trim(), password)
        checking = true
        errorRes = null
        scope.launch {
            when (CredentialChecker.check(credentials)) {
                CredentialChecker.Result.OK -> {
                    store.save(credentials)
                    savedLogin = credentials.login
                    password = ""
                    editing = false
                    onSaved()
                }
                CredentialChecker.Result.WRONG_CREDENTIALS -> errorRes = R.string.auto_login_wrong_credentials
                CredentialChecker.Result.NETWORK_ERROR -> errorRes = R.string.auto_login_network_error
            }
            checking = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.auto_login_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text(stringResource(R.string.auto_login_close)) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.auto_login_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                val currentLogin = savedLogin
                if (!editing && currentLogin != null) {
                    Text(
                        text = stringResource(R.string.auto_login_enabled_for, currentLogin),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.auto_login_change))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            store.clear()
                            savedLogin = null
                            login = ""
                            editing = true
                            onDisabled()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.auto_login_disable))
                    }
                } else {
                    OutlinedTextField(
                        value = login,
                        onValueChange = { login = it; errorRes = null },
                        label = { Text(stringResource(R.string.auto_login_login)) },
                        singleLine = true,
                        enabled = !checking,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorRes = null },
                        label = { Text(stringResource(R.string.auto_login_password)) },
                        singleLine = true,
                        enabled = !checking,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorRes?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = ::save,
                        enabled = !checking && login.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (checking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.auto_login_save))
                        }
                    }
                    if (currentLogin != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { editing = false; password = ""; errorRes = null }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}
