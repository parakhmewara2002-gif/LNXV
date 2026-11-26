package com.lnxv.player.ui.youtube

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.design.GlassSearchField
import com.lnxv.player.core.design.GlassTopBar
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.session.SessionState
import com.lnxv.player.core.youtube.YouTubeClient
import kotlinx.coroutines.delay
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
    var showSignInDialog by remember { mutableStateOf(false) }

    // Debounced search: one request per pause in typing, and LaunchedEffect cancels the previous request so
    // out-of-order responses cannot overwrite newer results.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 3) {
            if (q.isEmpty()) results = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        delay(350)
        isLoading = true
        YouTubeClient.search(q).onSuccess { list ->
            results = list
            isLoading = false
        }.onFailure { err ->
            isLoading = false
            Toast.makeText(context, err.message ?: "Search failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun playVideo(item: YouTubeClient.SearchItem) {
        resolvingId = item.videoId
        scope.launch {
            YouTubeClient.resolve(item.videoId, context).onSuccess { stream ->
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
                    folderName = "YouTube",
                    audioUrl = stream.audioUrl
                )
                session.pendingOpenVideoId.value = LNXVApplication.TRANSIENT_VIDEO_ID
            }.onFailure { err ->
                resolvingId = null
                // Never fall back to the embed WebView: embed-disabled videos fail there with "error 152-4". Hand the
                // video to the YouTube app/browser instead of showing a broken black player.
                Toast.makeText(context, err.message ?: "This video cannot be played in-app", Toast.LENGTH_LONG).show()
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.youtube.com/watch?v=${item.videoId}")
                        )
                    )
                }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    GlassSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search YouTube videos..."
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showSignInDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.glassFill),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Sign In / Verify",
                        tint = appColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Verify",
                        color = appColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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

    if (showSignInDialog) {
        Dialog(
            onDismissRequest = {
                CookieManager.getInstance().flush()
                showSignInDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(appColors.barFill)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "YouTube Human Verification",
                                color = appColors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sign in or pass robot check to enable native playback",
                                color = appColors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = {
                            CookieManager.getInstance().flush()
                            showSignInDialog = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = appColors.textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 11; TECNO CG7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        cookieManager.flush()
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            CookieManager.getInstance().flush()
                            showSignInDialog = false
                            Toast.makeText(context, "Session verified! Ready for native playback.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = appColors.accent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done / Verified", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
