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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onFolderClick: (FolderItem) -> Unit,
    onVaultClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showStreamDialog by remember { mutableStateOf(false) }
    val recentStreams by viewModel.recentStreams.collectAsState()
    if (showStreamDialog) {
        var url by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showStreamDialog = false },
            title = { Text("Open network stream") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        label = { Text("https://, HLS (.m3u8), DASH (.mpd), rtsp://") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { e -> { Text(e) } }
                    )
                    if (recentStreams.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Recent", fontSize = 12.sp)
                        recentStreams.take(5).forEach { r ->
                            androidx.compose.material3.TextButton(onClick = { url = r; error = null }) {
                                Text(r, maxLines = 1, fontSize = 12.sp)
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = { viewModel.clearRecentStreams() }) {
                            Text("Clear recent", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val err = viewModel.openStream(url)
                    if (err == null) showStreamDialog = false else error = err
                }) { Text("Play") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showStreamDialog = false }) { Text("Cancel") }
            }
        )
    }
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = uiState.openFolderPath != null) { viewModel.closeFolder() }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        backgroundColor = appColors.barFill,
                        borderColor = appColors.glassBorder,
                        borderWidth = 1.dp
                    )
                    .padding(top = 40.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .liquidGlassSurface(
                                    shape = CircleShape,
                                    backgroundColor = appColors.accent.copy(alpha = 0.2f),
                                    borderColor = appColors.accent.copy(alpha = 0.6f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = appColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LNXV",
                            color = appColors.textPrimary,
                            style = Typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(
                            icon = Icons.Default.Search,
                            contentDescription = "Search",
                            onClick = { isSearchExpanded = !isSearchExpanded },
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassIconButton(
                            icon = Icons.Default.Link,
                            contentDescription = "Open network stream",
                            onClick = { showStreamDialog = true },
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassIconButton(
                            icon = Icons.Default.Lock,
                            contentDescription = "Private Vault",
                            onClick = onVaultClick,
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassIconButton(
                            icon = Icons.Default.Settings,
                            contentDescription = "Settings",
                            onClick = onSettingsClick,
                            size = 38.dp
                        )
                    }
                }

                if (isSearchExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Search videos or folders…", color = appColors.textTertiary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(shape = RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = appColors.textPrimary,
                            unfocusedTextColor = appColors.textPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = appColors.glassFill,
                            borderColor = appColors.glassBorder
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HomeTab.values().forEach { tab ->
                        val isSelected = uiState.selectedTab == tab
                        val selectAmount by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = tween(220),
                            label = "tab_select"
                        )
                        val tabTextColor by animateColorAsState(
                            targetValue = if (isSelected) appColors.accent else appColors.textSecondary,
                            animationSpec = tween(220),
                            label = "tab_text"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            appColors.accent.copy(alpha = 0.3f * selectAmount),
                                            Color(0xFF7C4DFF).copy(alpha = 0.25f * selectAmount)
                                        )
                                    )
                                )
                                .clickable { viewModel.selectTab(tab) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                color = tabTextColor,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Continue Watching Carousel (shown on Videos tab when available)
            if (uiState.selectedTab == HomeTab.VIDEOS && uiState.recentlyPlayed.isNotEmpty()) {
                item {
                    Text(
                        text = "Continue Watching",
                        color = appColors.textPrimary,
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.recentlyPlayed.take(5)) { video ->
                            ContinueWatchingCard(
                                video = video,
                                onClick = { onVideoClick(video) }
                            )
                        }
                    }
                }
            }

            // Tab Content
            when (uiState.selectedTab) {
                HomeTab.VIDEOS -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All Videos (${uiState.videos.size})",
                                color = appColors.textPrimary,
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(
                                    imageVector = if (uiState.isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                    contentDescription = "Toggle grid/list",
                                    tint = appColors.accent
                                )
                            }
                            Box {
                                IconButton(onClick = { isSortMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = appColors.accent
                                    )
                                }
                                DropdownMenu(
                                    expanded = isSortMenuExpanded,
                                    onDismissRequest = { isSortMenuExpanded = false }
                                ) {
                                    SortOption.values().forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.displayName) },
                                            onClick = {
                                                viewModel.updateSortOption(option)
                                                isSortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                    if (uiState.videos.isEmpty()) {
                        item { EmptyStateView(message = "No videos discovered on device") }
                    } else if (uiState.isGridView) {
                        items(uiState.videos.chunked(2), key = { row -> row.first().id }) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { video ->
                                    VideoGridCard(
                                        video = video,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onVideoClick(video) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                        onVaultMove = { viewModel.moveToVault(video) },
                                        onDelete = { viewModel.deleteVideo(video) }
                                    )
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(uiState.videos, key = { it.id }) { video ->
                            VideoItemRow(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { viewModel.moveToVault(video) },
                                onDelete = { viewModel.deleteVideo(video) }
                            )
                        }
                    }
                }
                HomeTab.FOLDERS -> {
                    if (uiState.openFolderPath != null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.closeFolder() }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = appColors.accent
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${uiState.openFolderName} (${uiState.folderVideos.size})",
                                    color = appColors.textPrimary,
                                    style = Typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (uiState.folderVideos.isEmpty()) {
                            item { EmptyStateView(message = "This folder is empty") }
                        } else {
                            items(uiState.folderVideos, key = { it.id }) { video ->
                                VideoItemRow(
                                    video = video,
                                    onClick = { onVideoClick(video) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                    onVaultMove = { viewModel.moveToVault(video) },
                                    onDelete = { viewModel.deleteVideo(video) }
                                )
                            }
                        }
                    } else if (uiState.folders.isEmpty()) {
                        item { EmptyStateView(message = "No video folders found") }
                    } else {
                        items(uiState.folders, key = { it.path }) { folder ->
                            FolderItemRow(
                                folder = folder,
                                onClick = { onFolderClick(folder) },
                                onToggleHidden = { viewModel.setFolderHidden(folder.path, !folder.isHidden) }
                            )
                        }
                    }
                }
                HomeTab.RECENTS -> {
                    if (uiState.recentlyPlayed.isEmpty()) {
                        item { EmptyStateView(message = "No recently played videos") }
                    } else {
                        items(uiState.recentlyPlayed, key = { it.id }) { video ->
                            VideoItemRow(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { viewModel.moveToVault(video) },
                                onDelete = { viewModel.deleteVideo(video) }
                            )
                        }
                    }
                }
                HomeTab.FAVORITES -> {
                    if (uiState.favorites.isEmpty()) {
                        item { EmptyStateView(message = "No favorite videos saved") }
                    } else {
                        items(uiState.favorites, key = { it.id }) { video ->
                            VideoItemRow(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { viewModel.moveToVault(video) },
                                onDelete = { viewModel.deleteVideo(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoItemRow(
    video: VideoItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onVaultMove: () -> Unit,
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
                    Text(
                        text = "${video.sizeFormatted} • ${video.folderName}",
                        color = appColors.textSecondary,
                        fontSize = 11.sp
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
