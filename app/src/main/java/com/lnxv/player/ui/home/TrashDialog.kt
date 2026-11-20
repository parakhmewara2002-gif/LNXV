package com.lnxv.player.ui.home

import android.content.IntentSender
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.R
import com.lnxv.player.core.design.GlassDialog
import com.lnxv.player.core.design.GlassDialogButton
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.model.TrashItem
import com.lnxv.player.core.model.TrashPolicy
import com.lnxv.player.data.mediastore.MediaActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists videos in the system trash (Android 11+) with Restore and Delete forever. Each action asks the system for consent. */
@Composable
fun TrashDialog(
    onDismiss: () -> Unit,
    requestGrant: (IntentSender, () -> Unit) -> Unit,
    onChanged: () -> Unit
) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    LaunchedEffect(reloadTick) {
        items = withContext(Dispatchers.IO) { MediaActions.queryTrashed(ctx) }
        loaded = true
    }
    val now = System.currentTimeMillis() / 1000L
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (loaded && items.isEmpty()) Text(stringResource(R.string.trash_empty), color = appColors.textSecondary)
                items.forEach { item ->
                    val days = TrashPolicy.daysLeft(item.expiresSec, now)
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(item.name, color = appColors.textPrimary, fontSize = 14.sp, maxLines = 2)
                        if (days != null) {
                            Text(pluralStringResource(R.plurals.trash_days_left, days, days), color = appColors.textTertiary, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                MediaActions.trashRequest(ctx, listOf(MediaActions.uriForId(item.id)), false)?.let { s ->
                                    requestGrant(s) { reloadTick++; onChanged() }
                                }
                            }) { Text(stringResource(R.string.trash_restore)) }
                            TextButton(onClick = {
                                MediaActions.deleteRequest(ctx, listOf(MediaActions.uriForId(item.id)))?.let { s ->
                                    requestGrant(s) { reloadTick++; onChanged() }
                                }
                            }) { Text(stringResource(R.string.trash_delete_forever)) }
                        }
                    }
                }
            }
        },
        confirmButton = { GlassDialogButton(text = stringResource(R.string.trash_close), primary = true, onClick = onDismiss) },
        dismissButton = null
    )
}
