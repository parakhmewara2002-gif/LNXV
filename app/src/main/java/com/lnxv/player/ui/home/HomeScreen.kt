package com.lnxv.player.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.FavoriteBorder
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
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        backgroundColor = Color(0xCC0D1117),
                        borderColor = Color(0x26FFFFFF),
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
                                    backgroundColor = NeonCyan.copy(alpha = 0.2f),
                                    borderColor = NeonCyan.copy(alpha = 0.6f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LNXV",
                            color = Color.White,
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
                        placeholder = { Text("Search videos or folders…", color = Color(0x80FFFFFF)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(shape = RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
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
                            backgroundColor = Color(0x40161B22),
                            borderColor = Color(0x26FFFFFF)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HomeTab.values().forEach { tab ->
                        val isSelected = uiState.selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (isSelected) Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(NeonCyan.copy(alpha = 0.3f), Color(0x407C4DFF))
                                        )
                                    ) else Modifier
                                )
                                .clickable { viewModel.selectTab(tab) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                color = if (isSelected) NeonCyan else Color(0x99FFFFFF),
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
                        color = Color.White,
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
                                color = Color.White,
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box {
                                IconButton(onClick = { isSortMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = NeonCyan
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
                    if (uiState.videos.isEmpty()) {
                        item { EmptyStateView(message = "No videos discovered on device") }
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
                    if (uiState.folders.isEmpty()) {
                        item { EmptyStateView(message = "No video folders found") }
                    } else {
                        items(uiState.folders, key = { it.path }) { folder ->
                            FolderItemRow(
                                folder = folder,
                                onClick = { onFolderClick(folder) }
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
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x261E293B),
        borderColor = Color(0x26FFFFFF)
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
                    .background(Color(0xFF0F141C))
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
                    color = Color.White,
                    style = Typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${video.sizeFormatted} • ${video.folderName}",
                        color = Color(0x99FFFFFF),
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
                        tint = Color(0x80FFFFFF)
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
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x261E293B)
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
                        backgroundColor = NeonCyan.copy(alpha = 0.15f),
                        borderColor = NeonCyan.copy(alpha = 0.4f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = Color.White,
                    style = Typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.videoCount} videos • ${folder.sizeFormatted}",
                    color = Color(0x80FFFFFF),
                    fontSize = 12.sp
                )
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
                backgroundColor = Color(0x331E293B)
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
                        .background(NeonCyan.copy(alpha = 0.85f)),
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
                    color = Color.White,
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
                    color = NeonCyan,
                    trackColor = Color(0x33FFFFFF)
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color(0x66FFFFFF),
            style = Typography.bodyMedium
        )
    }
}
