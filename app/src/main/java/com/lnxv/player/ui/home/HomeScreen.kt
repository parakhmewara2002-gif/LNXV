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
    val selection by viewModel.selection.collectAsState()
    val selectionPool = remember(uiState) {
        (uiState.videos + uiState.folderVideos + uiState.favorites + uiState.recentlyPlayed).distinctBy { it.id }
    }
    var confirmRemove by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showStreamDialog by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsState()
    val playlistTarget by viewModel.playlistTarget.collectAsState()
    playlistTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissAddToPlaylist,
            title = { Text("Add to playlist") },
            text = {
                Column {
                    playlists.forEach { pl ->
                        androidx.compose.material3.TextButton(onClick = { viewModel.addToPlaylist(pl.id) }) {
                            Text("${pl.name} (${pl.itemCount})", maxLines = 1)
                        }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New playlist name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.createPlaylistAndAdd(newName) },
                    enabled = newName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = viewModel::dismissAddToPlaylist) { Text("Cancel") } }
        )
    }
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
    // One blur layer: the top bar blurs the list scrolling under it (skipped when "Reduce effects" is on).
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    val reduceEffects = com.lnxv.player.core.design.LocalReduceEffects.current
    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }
    BackHandler(enabled = uiState.openFolderPath != null && selection.isEmpty()) { viewModel.closeFolder() }
    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${selection.size} from library?") },
            text = { Text("This removes them from LNXV's library list. It does not delete the files from your device.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.removeSelected(selectionPool)
                    confirmRemove = false
                }) { Text("Remove") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
        bottomBar = {
            if (selection.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.barFill)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${selection.size} selected", color = appColors.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    Row {
                        androidx.compose.material3.TextButton(onClick = { viewModel.selectAll(selectionPool.filter { v -> v.id in (if (uiState.openFolderPath != null) uiState.folderVideos else uiState.videos).map { it.id } }) }) { Text("All") }
                        androidx.compose.material3.TextButton(onClick = { viewModel.favoriteSelected(selectionPool) }) { Text("Fav") }
                        androidx.compose.material3.TextButton(onClick = { viewModel.requestAddSelectedToPlaylist(selectionPool) }) { Text("Playlist") }
                        androidx.compose.material3.TextButton(onClick = { viewModel.vaultSelected(selectionPool) }) { Text("Vault") }
                        androidx.compose.material3.TextButton(onClick = { confirmRemove = true }) { Text("Remove") }
                        androidx.compose.material3.TextButton(onClick = { viewModel.clearSelection() }) { Text("✕") }
                    }
                }
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (reduceEffects) Modifier else Modifier
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .hazeChild(state = hazeState)
                    )
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        backgroundColor = if (reduceEffects) appColors.barFill else appColors.barFill.copy(alpha = 0.45f),
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
                        placeholder = { Text("Search… (ext:mkv res:1080 size>500mb)", color = appColors.textTertiary) },
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
                .then(if (reduceEffects) Modifier else Modifier.haze(state = hazeState)),
            // The list scrolls under the top bar (so there is something to blur); padding keeps first item clear of it.
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 16.dp,
                bottom = paddingValues.calculateBottomPadding() + 16.dp
            ),
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
                    item {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(com.lnxv.player.core.model.SmartCollection.values().toList()) { c ->
                                androidx.compose.material3.FilterChip(
                                    selected = uiState.collection == c,
                                    onClick = { viewModel.selectCollection(c) },
                                    label = { Text(c.label) }
                                )
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
                                        onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                        onVaultMove = { viewModel.moveToVault(video) },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(video) },
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
                                onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { viewModel.moveToVault(video) },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(video) },
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
                                    onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                    onVaultMove = { viewModel.moveToVault(video) },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(video) },
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
                                onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { viewModel.moveToVault(video) },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(video) },
                                onDelete = { viewModel.deleteVideo(video) }
                            )
                        }
                    }
                }
                HomeTab.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        item { EmptyStateView(message = "No playlists yet. Use \"Add to playlist\" on a video.") }
                    } else {
                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.playPlaylist(pl.id) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, color = appColors.textPrimary, fontSize = 15.sp, maxLines = 1)
                                    Text("${pl.itemCount} video(s)", color = appColors.textTertiary, fontSize = 12.sp)
                                }
                                androidx.compose.material3.TextButton(onClick = { viewModel.deletePlaylist(pl.id) }) {
                                    Text("Delete", color = Color.Red)
                                }
                            }
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
                                onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { viewModel.moveToVault(video) },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(video) },
                                onDelete = { viewModel.deleteVideo(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}
