package com.lnxv.player.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.R
import com.lnxv.player.core.design.GlassDialog
import com.lnxv.player.core.design.GlassDialogButton
import com.lnxv.player.core.design.GlassTextField
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.webdav.WebDavAuth
import com.lnxv.player.core.webdav.WebDavClient
import com.lnxv.player.core.webdav.WebDavEntry
import com.lnxv.player.core.webdav.WebDavResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sign in to a WebDAV server (https only), browse folders, and play a video. The password lives in memory only. */
@Composable
fun WebDavDialog(onDismiss: () -> Unit, onPlay: (String) -> String?) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var rootUrl by remember { mutableStateOf<String?>(null) }
    var handedOffToPlayer by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<WebDavEntry>>(emptyList()) }

    fun dismiss() {
        if (!handedOffToPlayer) WebDavAuth.clear()
        onDismiss()
    }

    fun load(target: String, first: Boolean) {
        loading = true
        error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { WebDavClient.list(target, user, pass) }
            loading = false
            when (r) {
                is WebDavResult.Ok -> {
                    if (first) {
                        if (!WebDavAuth.start(target, user, pass)) { error = "WebDAV needs an https:// address"; return@launch }
                        rootUrl = target
                    }
                    currentUrl = target
                    entries = r.entries
                }
                is WebDavResult.Error -> error = r.message
            }
        }
    }

    fun parentOf(u: String): String? {
        val root = rootUrl ?: return null
        if (u.trimEnd('/') == root.trimEnd('/')) return null
        val trimmed = u.trimEnd('/')
        val parent = trimmed.substringBeforeLast('/') + "/"
        return if (parent.length >= root.length || parent.trimEnd('/') == root.trimEnd('/')) parent else root
    }

    GlassDialog(
        onDismissRequest = ::dismiss,
        title = { Text(stringResource(R.string.webdav_title)) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (rootUrl == null) {
                    GlassTextField(value = url, onValueChange = { url = it; error = null }, placeholder = stringResource(R.string.webdav_url_hint), errorText = error)
                    Spacer(Modifier.height(8.dp))
                    GlassTextField(value = user, onValueChange = { user = it }, placeholder = stringResource(R.string.webdav_user))
                    Spacer(Modifier.height(8.dp))
                    GlassTextField(value = pass, onValueChange = { pass = it }, placeholder = stringResource(R.string.webdav_password), visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.webdav_note), color = appColors.textTertiary, fontSize = 12.sp)
                } else {
                    Text(currentUrl, color = appColors.textTertiary, fontSize = 12.sp, maxLines = 2)
                    error?.let { Text(it, color = appColors.textSecondary, fontSize = 13.sp) }
                    parentOf(currentUrl)?.let { p ->
                        TextButton(onClick = { load(p, false) }) { Text(stringResource(R.string.webdav_up)) }
                    }
                    if (entries.isEmpty() && !loading) Text(stringResource(R.string.webdav_empty), color = appColors.textSecondary)
                    entries.forEach { e ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (e.isDir) load(e.url, false) else {
                                val err = onPlay(e.url)
                                if (err == null) {
                                    handedOffToPlayer = true
                                    dismiss()
                                } else error = err
                            }
                        }.padding(vertical = 10.dp)) {
                            Text(if (e.isDir) "📁 ${e.name}" else e.name, color = appColors.textPrimary, fontSize = 14.sp, maxLines = 2)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (rootUrl == null) {
                GlassDialogButton(text = stringResource(R.string.webdav_connect), primary = true, onClick = { if (!loading) load(url.trim(), true) })
            } else {
                GlassDialogButton(text = stringResource(R.string.webdav_disconnect), primary = true, onClick = {
                    WebDavAuth.clear(); rootUrl = null; handedOffToPlayer = false; entries = emptyList(); pass = ""
                })
            }
        },
        dismissButton = { GlassDialogButton(text = stringResource(R.string.trash_close), onClick = ::dismiss) }
    )
}
