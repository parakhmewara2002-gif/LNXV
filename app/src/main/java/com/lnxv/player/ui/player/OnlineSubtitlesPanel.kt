package com.lnxv.player.ui.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.R
import com.lnxv.player.core.design.GlassFilterChip
import com.lnxv.player.core.design.GlassSearchField
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.subtitle.OsError
import com.lnxv.player.core.subtitle.OsLanguages
import com.lnxv.player.core.subtitle.OsSubtitle
import com.lnxv.player.core.subtitle.online.OnlineSubtitlesController
import kotlinx.coroutines.launch

@Composable
private fun osErrorText(e: OsError): String = when (e) {
    OsError.NoApiKey -> stringResource(R.string.os_err_no_key)
    OsError.NoNetwork -> stringResource(R.string.os_err_network)
    OsError.Timeout -> stringResource(R.string.os_err_timeout)
    OsError.Unauthorized -> stringResource(R.string.os_err_401)
    OsError.Forbidden -> stringResource(R.string.os_err_403)
    OsError.QuotaExceeded -> stringResource(R.string.os_err_quota)
    OsError.RateLimited -> stringResource(R.string.os_err_rate)
    is OsError.Server -> stringResource(R.string.os_err_server)
    is OsError.Other -> stringResource(R.string.os_err_other)
    OsError.BadResponse -> stringResource(R.string.os_err_bad_response)
    OsError.Disallowed -> stringResource(R.string.os_err_disallowed)
}

/** Body of the "Online subtitles" side panel. The network is only touched when the user taps Search or a result. */
@Composable
fun ColumnScope.OnlineSubtitlesContent(
    controller: OnlineSubtitlesController,
    video: VideoItem?,
    onPick: (OsSubtitle) -> Unit
) {
    val cfg by controller.config.collectAsState()
    val ui by controller.state.collectAsState()
    val scope = rememberCoroutineScope()

    if (cfg.apiKey.isBlank()) {
        Text(stringResource(R.string.online_subs_need_key), color = Color.White, fontSize = 14.sp)
        return
    }

    val defaultText = remember(video?.id) { controller.defaultQueryFor(video) }
    var text by remember(video?.id) { mutableStateOf(defaultText) }

    GlassSearchField(value = text, onValueChange = { text = it }, placeholder = stringResource(R.string.online_subs_search_hint))
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        OsLanguages.ALL.forEach { (code, label) ->
            GlassFilterChip(
                selected = code in cfg.languages,
                label = label,
                onClick = { scope.launch { controller.settings.setLanguages(OsLanguages.toggle(cfg.languages, code)) } }
            )
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    PanelOptionRow(
        label = stringResource(R.string.online_subs_search),
        selected = false,
        icon = Icons.Default.Search,
        onClick = { controller.search(video, text, cfg.languages, useHash = text == defaultText) }
    )
    Text(stringResource(R.string.online_subs_privacy_line), color = Color(0x80FFFFFF), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

    cfg.remainingQuota?.let {
        Text(stringResource(R.string.online_subs_quota_left, it), color = Color(0xB3FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
    if (ui.loading) Text(stringResource(R.string.online_subs_search) + "…", color = Color.White, fontSize = 13.sp)
    ui.error?.let { Text(osErrorText(it), color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }
    if (ui.searched && ui.error == null && ui.results.isEmpty()) {
        Text(stringResource(R.string.online_subs_no_results), color = Color.White, fontSize = 13.sp)
    }
    if (ui.usedHash) Text(stringResource(R.string.online_subs_hash_note), color = Color(0x80FFFFFF), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))

    ui.results.forEach { r ->
        val badges = listOfNotNull(
            if (r.hashMatch) stringResource(R.string.online_subs_badge_match) else null,
            if (r.hearingImpaired) stringResource(R.string.online_subs_badge_hi) else null,
            if (r.aiTranslated) stringResource(R.string.online_subs_badge_ai) else null,
            if (r.machineTranslated) stringResource(R.string.online_subs_badge_machine) else null
        )
        val meta = buildList {
            add(r.language.uppercase())
            add(stringResource(R.string.online_subs_downloads, r.downloadCount))
            if (r.rating > 0.0) add("★ %.1f".format(r.rating))
            addAll(badges)
        }.joinToString(" · ")
        PanelOptionRow(
            label = r.release.ifBlank { r.fileName.ifBlank { "#${r.fileId}" } },
            subtitle = if (ui.downloadingId == r.fileId) stringResource(R.string.online_subs_downloading) else meta,
            selected = r.hashMatch,
            icon = Icons.Default.Subtitles,
            onClick = { if (ui.downloadingId == null) onPick(r) }
        )
    }
}
