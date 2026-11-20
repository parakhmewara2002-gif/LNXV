package com.lnxv.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnxv.player.R
import com.lnxv.player.core.bookmarks.BookmarkList
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.model.ChapterItem
import com.lnxv.player.core.model.ChapterList

@Composable
fun ColumnScope.ChaptersContent(items: List<ChapterItem>, positionMs: Long, onJump: (Long) -> Unit) {
    val current = ChapterList.currentIndex(items, positionMs)
    items.forEachIndexed { i, c ->
        val time = BookmarkList.formatTime(c.startMs)
        val desc = stringResource(R.string.chapters_jump_to, c.title, time)
        Row(
            Modifier.fillMaxWidth().clickable { onJump(c.startMs) }.padding(vertical = 10.dp).semantics { contentDescription = desc },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                c.title,
                color = if (i == current) NeonCyan else Color.White,
                fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Text(time, color = Color(0xB3FFFFFF))
        }
    }
}
