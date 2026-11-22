package com.lnxv.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lnxv.player.R
import com.lnxv.player.core.bookmarks.Bookmark
import com.lnxv.player.core.bookmarks.BookmarkList

@Composable
fun ColumnScope.BookmarksContent(
    items: List<Bookmark>,
    onAdd: () -> Unit,
    onJump: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious, enabled = items.isNotEmpty()) { Text(stringResource(R.string.bookmarks_previous)) }
        TextButton(onClick = onAdd) { Text(stringResource(R.string.bookmarks_add_here)) }
        TextButton(onClick = onNext, enabled = items.isNotEmpty()) { Text(stringResource(R.string.bookmarks_next)) }
    }
    if (items.isEmpty()) {
        Text(stringResource(R.string.bookmarks_empty), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 12.dp))
    }
    items.forEach { b ->
        val time = BookmarkList.formatTime(b.positionMs)
        val jumpDesc = stringResource(R.string.bookmarks_jump_to, time)
        val removeDesc = stringResource(R.string.bookmarks_remove_at, time)
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(shape)
                .background(Color(0x331C1F26))
                .clickable { onJump(b.positionMs) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).semantics { contentDescription = jumpDesc }) {
                Text(
                    text = if (b.label.isEmpty()) time else "$time  ${b.label}",
                    color = com.lnxv.player.core.design.NeonCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(
                onClick = { onRemove(b.positionMs) },
                modifier = Modifier.semantics { contentDescription = removeDesc }
            ) {
                Text(stringResource(R.string.bookmarks_remove), color = com.lnxv.player.core.design.DestructiveRed)
            }
        }
    }
}
