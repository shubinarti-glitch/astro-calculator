package ru.astrosmap.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.astrosmap.app.R
import ru.astrosmap.app.data.api.MeResponse
import ru.astrosmap.app.ui.theme.GoodColor

/** Кабинет: вход/регистрация, а после входа — профиль и статус подписки. */
@Composable
fun AccountScreen(viewModel: AccountViewModel = hiltViewModel()) {
    when (val s = viewModel.state) {
        AccountState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AccountState.LoggedOut -> AuthForm(viewModel)
        AccountState.Offline -> OfflineNote(viewModel)
        is AccountState.LoggedIn -> Profile(s.me, viewModel)
    }
}

@Composable
private fun AuthForm(viewModel: AccountViewModel) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ru.astrosmap.app.ui.theme.AppHeader(stringResource(R.string.section_account))
        ru.astrosmap.app.ui.theme.AstroPanel {
        Text(
            stringResource(if (registerMode) R.string.auth_register else R.string.auth_login),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.take(80) },
            label = { Text(stringResource(if (registerMode) R.string.auth_username else R.string.auth_username_or_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (registerMode) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(120) },
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        viewModel.errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        viewModel.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                if (registerMode) viewModel.register(username, email, password)
                else viewModel.login(username, password)
            },
            enabled = !viewModel.busy && username.isNotBlank() && password.isNotBlank() &&
                (!registerMode || email.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (registerMode) R.string.auth_do_register else R.string.auth_do_login))
        }
        TextButton(onClick = { registerMode = !registerMode }) {
            Text(stringResource(if (registerMode) R.string.auth_have_account else R.string.auth_no_account))
        }
        }
    }
}

@Composable
private fun Profile(me: MeResponse, viewModel: AccountViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ru.astrosmap.app.ui.theme.AppHeader(stringResource(R.string.section_account))
        ru.astrosmap.app.ui.theme.AstroPanel {
        Text(me.username, style = MaterialTheme.typography.headlineSmall)
        me.email?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!me.emailVerified) {
                    Text(
                        stringResource(R.string.email_unverified),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (me.premium) {
            Text(
                stringResource(R.string.premium_active, me.premiumUntilDate() ?: ""),
                color = GoodColor,
            )
        } else {
            Text(stringResource(R.string.premium_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val context = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = { ru.astrosmap.app.ui.openSite(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.premium_buy)) }
        }
        OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_logout))
        }
        }
    }
}

@Composable
private fun OfflineNote(viewModel: AccountViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.net_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
        TextButton(onClick = viewModel::logout) { Text(stringResource(R.string.auth_logout)) }
    }
}
