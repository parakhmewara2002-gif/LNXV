package com.lnxv.player.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.R
import com.lnxv.player.core.design.GlassPanel
import com.lnxv.player.core.design.Typography
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.subtitle.OsError
import com.lnxv.player.core.subtitle.online.OnlineSubtitleConfig
import com.lnxv.player.core.subtitle.online.OnlineSubtitleSettings
import com.lnxv.player.core.subtitle.online.OnlineSubtitleStore
import com.lnxv.player.core.subtitle.online.OpenSubtitlesClient
import kotlinx.coroutines.launch

/** Settings block for the user's own OpenSubtitles API key and optional account. Shown only where online subtitles exist. */
@Composable
fun OnlineSubtitlesSettingsPanel() {
    val context = LocalContext.current
    val settings = remember { OnlineSubtitleSettings(context.applicationContext) }
    val client = remember { OpenSubtitlesClient(settings, "LNXV") }
    val cfg by settings.config.collectAsState(initial = OnlineSubtitleConfig())
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // Fill the fields once with what is stored; afterwards the user's typing wins.
    LaunchedEffect(cfg.apiKey, cfg.username) {
        if (apiKey.isEmpty()) apiKey = cfg.apiKey
        if (username.isEmpty()) username = cfg.username
    }

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = appColors.glassFill
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.settings_os_api_key), color = appColors.textPrimary, style = Typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_os_api_key_hint), fontSize = 12.sp) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            TextButton(onClick = { scope.launch { settings.setApiKey(apiKey) } }) { Text(stringResource(R.string.settings_os_save)) }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_os_username)) }
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_os_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Text(
                text = if (cfg.loggedIn) stringResource(R.string.settings_os_logged_in, cfg.username) else stringResource(R.string.settings_os_not_logged_in),
                color = appColors.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row2(
                primary = stringResource(R.string.settings_os_login),
                secondary = stringResource(R.string.settings_os_logout),
                enabled = !busy && apiKey.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                secondaryEnabled = cfg.loggedIn,
                onPrimary = {
                    busy = true
                    val pw = password
                    password = "" // the password is dropped as soon as the request starts
                    scope.launch {
                        try {
                            settings.setApiKey(apiKey)
                            client.login(username, pw)
                            Toast.makeText(context, context.getString(R.string.settings_os_login_ok), Toast.LENGTH_SHORT).show()
                        } catch (e: OsError) {
                            Toast.makeText(context, errorText(context, e), Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                },
                onSecondary = { scope.launch { settings.logout() } }
            )
            TextButton(onClick = {
                scope.launch {
                    settings.clearAll()
                    OnlineSubtitleStore(context.applicationContext).clearAll()
                    apiKey = ""; username = ""; password = ""
                }
            }) { Text(stringResource(R.string.settings_os_remove_all)) }
            Text(stringResource(R.string.settings_os_privacy), color = appColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Row2(primary: String, secondary: String, enabled: Boolean, secondaryEnabled: Boolean, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    androidx.compose.foundation.layout.Row {
        TextButton(onClick = onPrimary, enabled = enabled) { Text(primary) }
        TextButton(onClick = onSecondary, enabled = secondaryEnabled) { Text(secondary) }
    }
}

private fun errorText(context: android.content.Context, e: OsError): String = context.getString(
    when (e) {
        OsError.NoApiKey -> R.string.os_err_no_key
        OsError.NoNetwork -> R.string.os_err_network
        OsError.Timeout -> R.string.os_err_timeout
        OsError.Unauthorized -> R.string.os_err_401
        OsError.Forbidden -> R.string.os_err_403
        OsError.QuotaExceeded -> R.string.os_err_quota
        OsError.RateLimited -> R.string.os_err_rate
        is OsError.Server -> R.string.os_err_server
        is OsError.Other -> R.string.os_err_other
        OsError.BadResponse -> R.string.os_err_bad_response
        OsError.Disallowed -> R.string.os_err_disallowed
    }
)
