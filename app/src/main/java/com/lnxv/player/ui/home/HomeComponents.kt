package com.lnxv.player.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.design.bouncyClickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.lnxv.player.core.design.videoSharedBounds
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.lnxv.player.core.design.GlassBadge
import com.lnxv.player.core.design.GlassIconButton
import com.lnxv.player.core.design.GlassPanel
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.design.SurfaceDark
import com.lnxv.player.core.design.Typography
import com.lnxv.player.core.design.liquidGlassSurface
import com.lnxv.player.core.model.FolderItem
import com.lnxv.player.core.model.VideoItem

@Composable
fun VideoItemRow(
    video: VideoItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onVaultMove: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onDelete: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = appColors.glassFill,
        borderColor = appColors.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with Duration & Resolution Badges
            Box(
                modifier = Modifier
                    .videoSharedBounds(video.id)
                    .size(width = 110.dp, height = 70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.placeholder)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .videoFrameMillis(1000)
                        .size(coil.size.Size(360, 240))
                        .crossfade(150)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Resolution Badge
                GlassBadge(
                    text = video.resolutionLabel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )

                // Duration Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationFormatted,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Video Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.title,
                    color = appColors.textPrimary,
                    style = Typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!video.isVault && com.lnxv.player.core.model.SmartCollection.isNew(video, System.currentTimeMillis() / 1000L)) {
                        Text(
                            text = "NEW",
                            color = appColors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = "${video.sizeFormatted} • ${video.folderName}",
                        color = appColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                val watched = com.lnxv.player.core.model.SmartCollection.watchedFraction(video)
                if (watched > 0f && !video.isVault) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { watched },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = appColors.accent,
                        trackColor = appColors.textTertiary.copy(alpha = 0.3f)
                    )
                }
            }

            // Options Menu
            Box {
                IconButton(onClick = { isMenuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = appColors.textTertiary
                    )
                }
                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (video.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                        onClick = {
                            onFavoriteToggle()
                            isMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        onClick = {
                            onAddToPlaylist()
                            isMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Private Vault") },
                        onClick = {
                            onVaultMove()
                            isMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = {
                            onDelete()
                            isMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderItemRow(
    folder: FolderItem,
    onClick: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = appColors.glassFill
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = appColors.accent.copy(alpha = 0.15f),
                        borderColor = appColors.accent.copy(alpha = 0.4f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = appColors.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = appColors.textPrimary,
                    style = Typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.videoCount} videos • ${folder.sizeFormatted}" +
                        if (folder.isHidden) " • Hidden" else "",
                    color = if (folder.isHidden) appColors.accent.copy(alpha = 0.7f) else appColors.textTertiary,
                    fontSize = 12.sp
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Folder options",
                        tint = appColors.textTertiary
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (folder.isHidden) "Show in library" else "Hide from library") },
                        onClick = {
                            onToggleHidden()
                            menuOpen = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    video: VideoItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = appColors.glassFill
            )
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .videoFrameMillis(video.lastPositionMs.coerceAtLeast(1000L))
                        .size(coil.size.Size(360, 240))
                        .crossfade(150)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                )
                // Resume play floating button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(appColors.accent.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = Color.Black
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    color = appColors.textPrimary,
                    style = Typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                val progress = if (video.durationMs > 0) {
                    (video.lastPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = appColors.accent,
                    trackColor = appColors.glassBorder
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(message: String) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(400),
        label = "empty_appear"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp)
            .graphicsLayer {
                alpha = appear
                translationY = (1f - appear) * 24f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(appColors.glassSubtle)
                .border(1.dp, appColors.glassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = appColors.accent.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = appColors.textSecondary,
            style = Typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
fun VideoGridCard(
    video: VideoItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onVaultMove: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onDelete: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    GlassPanel(
        modifier = modifier.bouncyClickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = appColors.glassFill,
        borderColor = appColors.glassBorder
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .videoSharedBounds(video.id)
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(appColors.placeholder)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .videoFrameMillis(1000)
                        .size(coil.size.Size(360, 240))
                        .crossfade(150)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationFormatted,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = appColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = video.sizeFormatted,
                        color = appColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Box {
                    IconButton(onClick = { isMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = appColors.textTertiary
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (video.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                            onClick = {
                                onFavoriteToggle()
                                isMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = {
                                onAddToPlaylist()
                                isMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Private Vault") },
                            onClick = {
                                onVaultMove()
                                isMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = {
                                onDelete()
                                isMenuOpen = false
                            }
                        )
                    }
                }
            }
        }
    }
}
