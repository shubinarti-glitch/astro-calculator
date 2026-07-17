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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.astrosmap.app.BuildConfig
import ru.astrosmap.app.R
import ru.astrosmap.app.data.api.MeResponse
import ru.astrosmap.app.ui.PRIVACY_URL
import ru.astrosmap.app.ui.TERMS_URL
import ru.astrosmap.app.ui.openSite
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
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

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
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painterResource(if (showPassword) R.drawable.ic_eye_off else R.drawable.ic_eye),
                        contentDescription = stringResource(R.string.pwd_toggle),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (registerMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Text(
                    stringResource(R.string.consent_pd),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Row {
                TextButton(onClick = { openSite(context, PRIVACY_URL) }) {
                    Text(stringResource(R.string.acc_privacy), style = MaterialTheme.typography.labelMedium)
                }
            }
            Row {
                TextButton(onClick = { openSite(context, TERMS_URL) }) {
                    Text(stringResource(R.string.acc_terms), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        viewModel.errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        viewModel.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                if (registerMode) viewModel.register(username, email, password)
                else viewModel.login(username, password)
            },
            enabled = !viewModel.busy && username.isNotBlank() && password.isNotBlank() &&
                (!registerMode || (email.isNotBlank() && consent)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (registerMode) R.string.auth_do_register else R.string.auth_do_login))
        }
        TextButton(onClick = { registerMode = !registerMode }) {
            Text(stringResource(if (registerMode) R.string.auth_have_account else R.string.auth_no_account))
        }
        }
        LegalPanel()
    }
}

@Composable
private fun Profile(me: MeResponse, viewModel: AccountViewModel) {
    val chartsCount by viewModel.chartsCount.collectAsState()
    val context = LocalContext.current
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
            Button(
                onClick = { openSite(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.premium_buy)) }
        }
        Text(
            stringResource(R.string.acc_charts_count, chartsCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_logout))
        }
        }
        LegalPanel()
    }
}

/** Юр-блок кабинета: сайт, политика, соглашение, 18+, версия приложения. */
@Composable
private fun LegalPanel() {
    val context = LocalContext.current
    ru.astrosmap.app.ui.theme.AstroPanel {
        TextButton(onClick = { openSite(context) }) { Text(stringResource(R.string.acc_site)) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(onClick = { openSite(context, PRIVACY_URL) }) { Text(stringResource(R.string.acc_privacy)) }
        TextButton(onClick = { openSite(context, TERMS_URL) }) { Text(stringResource(R.string.acc_terms)) }
        Text(
            stringResource(R.string.acc_age),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.acc_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
