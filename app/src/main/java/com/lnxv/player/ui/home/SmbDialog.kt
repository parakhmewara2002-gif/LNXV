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
import com.lnxv.player.core.smb.SmbBackends
import com.lnxv.player.core.smb.SmbEntry
import com.lnxv.player.core.smb.SmbPath
import com.lnxv.player.core.smb.SmbResult
import com.lnxv.player.core.smb.SmbSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sign in to an SMB share, browse folders and play a video. The password lives in memory only. */
@Composable
fun SmbDialog(onDismiss: () -> Unit, onPlay: (url: String, title: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("smb://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<SmbPath?>(null) }
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    val badAddress = stringResource(R.string.smb_bad_address)

    fun load(target: SmbPath) {
        val backend = SmbBackends.instance ?: return
        val creds = SmbSession.forPath(target) ?: return
        loading = true
        error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { backend.list(target, creds) }
            loading = false
            when (r) {
                is SmbResult.Ok -> { current = target; entries = r.entries; signedIn = true }
                is SmbResult.Error -> { error = r.message; if (!signedIn) SmbSession.clear() }
            }
        }
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.smb_title)) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (!signedIn) {
                    GlassTextField(value = address, onValueChange = { address = it; error = null }, placeholder = stringResource(R.string.smb_url_hint), errorText = error)
                    Spacer(Modifier.height(8.dp))
                    GlassTextField(value = user, onValueChange = { user = it }, placeholder = stringResource(R.string.webdav_user))
                    Spacer(Modifier.height(8.dp))
                    GlassTextField(value = pass, onValueChange = { pass = it }, placeholder = stringResource(R.string.webdav_password), visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(8.dp))
                    GlassTextField(value = domain, onValueChange = { domain = it }, placeholder = stringResource(R.string.smb_domain))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.smb_note), color = appColors.textTertiary, fontSize = 12.sp)
                } else {
                    val cur = current
                    Text(cur?.toUrl().orEmpty(), color = appColors.textTertiary, fontSize = 12.sp, maxLines = 2)
                    error?.let { Text(it, color = appColors.textSecondary, fontSize = 13.sp) }
                    cur?.parent()?.let { p -> TextButton(onClick = { load(p) }) { Text(stringResource(R.string.webdav_up)) } }
                    if (entries.isEmpty() && !loading) Text(stringResource(R.string.webdav_empty), color = appColors.textSecondary)
                    entries.forEach { e ->
                        Row(Modifier.fillMaxWidth().clickable {
                            val base = cur ?: return@clickable
                            val next = base.child(e.name)
                            if (e.isDir) load(next) else { onPlay(next.toUrl(), e.name); onDismiss() }
                        }.padding(vertical = 10.dp)) {
                            Text(if (e.isDir) "📁 ${e.name}" else e.name, color = appColors.textPrimary, fontSize = 14.sp, maxLines = 2)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!signedIn) {
                GlassDialogButton(text = stringResource(R.string.webdav_connect), primary = true, onClick = {
                    if (loading) return@GlassDialogButton
                    val p = SmbPath.parse(address)
                    if (p == null) { error = badAddress; return@GlassDialogButton }
                    SmbSession.start(p, user, pass, domain.trim())
                    load(p)
                })
            } else {
                GlassDialogButton(text = stringResource(R.string.webdav_disconnect), primary = true, onClick = {
                    SmbSession.clear(); signedIn = false; entries = emptyList(); current = null; pass = ""
                })
            }
        },
        dismissButton = { GlassDialogButton(text = stringResource(R.string.trash_close), onClick = onDismiss) }
    )
}
