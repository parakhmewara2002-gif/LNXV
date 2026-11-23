package com.lnxv.player.ui.home

import com.lnxv.player.core.design.GlassButton
import com.lnxv.player.core.design.DestructiveRed
import com.lnxv.player.core.design.GlassDropdownMenu
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.design.bouncyClickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import com.lnxv.player.core.design.icons.VideoLibrary
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.lnxv.player.core.design.icons.Folder
import androidx.compose.material.icons.filled.ArrowBack
import com.lnxv.player.core.design.icons.ViewAgenda
import com.lnxv.player.core.design.icons.GridView
import com.lnxv.player.core.design.icons.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.lnxv.player.core.design.icons.Sort
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
    onDelete: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    GlassPanel(
        lite = true,
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(onLongClick = onLongClick) { onClick() }
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(22.dp),
        backgroundColor = if (selected) appColors.accent.copy(alpha = 0.22f) else appColors.glassFill,
        borderColor = if (selected) appColors.accent else appColors.glassBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with Duration & Resolution Badges
            Box(
                modifier = Modifier
                    .videoSharedBounds(video.id)
                    .size(width = 112.dp, height = 74.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(appColors.placeholder)
            ) {
                AsyncImage(
                    model = com.lnxv.player.data.thumb.videoThumbRequest(LocalContext.current, video),
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
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
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
                    com.lnxv.player.core.design.GlassProgress(
                        progress = { watched },
                        modifier = Modifier.fillMaxWidth(),
                        height = 3.dp
                    )
                }
            }

            // Options Menu
            Box {
                com.lnxv.player.core.design.GlassIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    onClick = { isMenuOpen = true },
                    tint = appColors.textSecondary,
                    size = 40.dp
                )
                GlassDropdownMenu(
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
                        text = { Text(stringResource(R.string.remove_from_library), color = DestructiveRed) },
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
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    GlassPanel(
        lite = true,
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        backgroundColor = appColors.glassFill
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(16.dp),
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
                com.lnxv.player.core.design.GlassIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Folder options",
                    onClick = { menuOpen = true },
                    tint = appColors.textSecondary,
                    size = 40.dp
                )
                GlassDropdownMenu(
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
            .width(216.dp)
            .bouncyClickable(pressedScale = 0.975f) { onClick() }
            .liquidGlassSurface(
                shape = RoundedCornerShape(22.dp),
                backgroundColor = appColors.glassFill,
                borderColor = appColors.glassBorder
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(topStart = 21.dp, topEnd = 21.dp))
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
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
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
                com.lnxv.player.core.design.GlassProgress(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    height = 3.dp
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    message: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.VideoLibrary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val reduceEffects = com.lnxv.player.core.design.LocalReduceEffects.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown || reduceEffects) 1f else 0f,
        animationSpec = tween(if (reduceEffects) 0 else 400),
        label = "empty_appear"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp)
            .graphicsLayer {
                alpha = appear
                translationY = (1f - appear) * 24f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = appColors.textTertiary.copy(alpha = 0.45f),
            modifier = Modifier.size(88.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = message,
            color = appColors.textPrimary,
            style = Typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = appColors.textSecondary,
                style = Typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            GlassButton(text = actionLabel, onClick = onAction, isPrimary = true)
        }
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
    onDelete: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    GlassPanel(
        lite = true,
        modifier = modifier
            .bouncyClickable(onLongClick = onLongClick) { onClick() }
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(22.dp),
        backgroundColor = if (selected) appColors.accent.copy(alpha = 0.22f) else appColors.glassFill,
        borderColor = if (selected) appColors.accent else appColors.glassBorder
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .videoSharedBounds(video.id)
                    .height(116.dp)
                    .clip(RoundedCornerShape(topStart = 21.dp, topEnd = 21.dp))
                    .background(appColors.placeholder)
            ) {
                AsyncImage(
                    model = com.lnxv.player.data.thumb.videoThumbRequest(LocalContext.current, video),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                GlassBadge(
                    text = video.resolutionLabel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
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
                    .padding(start = 12.dp, top = 9.dp, bottom = 9.dp),
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
                    com.lnxv.player.core.design.GlassIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    onClick = { isMenuOpen = true },
                    tint = appColors.textSecondary,
                    size = 40.dp
                )
                    GlassDropdownMenu(
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
                            text = { Text(stringResource(R.string.remove_from_library), color = DestructiveRed) },
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
