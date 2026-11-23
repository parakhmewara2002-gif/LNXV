package com.lnxv.player.ui.youtube

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.design.GlassSearchField
import com.lnxv.player.core.design.GlassTopBar
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.session.SessionState
import com.lnxv.player.core.youtube.YouTubeClient
import kotlinx.coroutines.launch

@Composable
fun YouTubeScreen(
    session: SessionState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YouTubeClient.SearchItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var resolvingId by remember { mutableStateOf<String?>(null) }

    fun performSearch(q: String) {
        if (q.isBlank()) return
        isLoading = true
        scope.launch {
            YouTubeClient.search(q).onSuccess { list ->
                results = list
                isLoading = false
            }.onFailure { err ->
                isLoading = false
                Toast.makeText(context, err.message ?: "Search failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playVideo(item: YouTubeClient.SearchItem) {
        resolvingId = item.videoId
        scope.launch {
            YouTubeClient.resolve(item.videoId).onSuccess { stream ->
                resolvingId = null
                session.transientVideo = VideoItem(
                    id = LNXVApplication.TRANSIENT_VIDEO_ID,
                    title = stream.title,
                    uri = android.net.Uri.parse(stream.videoUrl),
                    path = stream.videoUrl,
                    durationMs = stream.durationMs,
                    sizeBytes = 0L,
                    width = stream.width,
                    height = stream.height,
                    mimeType = "video/mp4",
                    dateAddedSec = System.currentTimeMillis() / 1000,
                    folderPath = "YouTube",
                    folderName = "YouTube"
                )
                session.pendingOpenVideoId.value = LNXVApplication.TRANSIENT_VIDEO_ID
            }.onFailure { err ->
                resolvingId = null
                Toast.makeText(context, err.message ?: "Could not stream video", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = "YouTube",
                onBack = onBack,
                hazeState = null
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            GlassSearchField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.length >= 3) performSearch(it)
                },
                placeholder = "Search YouTube videos or channels..."
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = appColors.accent)
                }
            } else if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Search YouTube to play directly with LNXV features",
                            color = appColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(results, key = { it.videoId }) { item ->
                        val isResolvingThis = resolvingId == item.videoId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(appColors.glassFill)
                                .clickable { playVideo(item) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 66.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.thumbnailUrl != null) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (isResolvingThis) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = appColors.accent,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(item.duration, color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = appColors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.channel,
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
