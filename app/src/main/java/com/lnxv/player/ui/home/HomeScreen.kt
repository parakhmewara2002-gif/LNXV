package com.lnxv.player.ui.home

import com.lnxv.player.core.design.GlassDropdownMenu
import com.lnxv.player.core.design.GlassDialog
import com.lnxv.player.core.design.GlassDialogButton
import com.lnxv.player.core.design.GlassSearchField
import com.lnxv.player.core.design.GlassTextField
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import com.lnxv.player.core.design.ambientBackground
import com.lnxv.player.core.design.appColors
import com.lnxv.player.core.design.bouncyClickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import com.lnxv.player.core.design.icons.VideoLibrary
import com.lnxv.player.core.design.icons.GraphicEq
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
import com.lnxv.player.core.design.liquidLensTarget
import com.lnxv.player.core.design.liquidLensSource
import androidx.compose.foundation.layout.navigationBarsPadding
import com.lnxv.player.core.design.IosTabBar
import com.lnxv.player.core.design.IosPillIcon
import com.lnxv.player.core.design.IosPill
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
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit = {},
    onYouTubeClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection by viewModel.selection.collectAsState()
    // Adaptive grid: ~170dp per card, 2 columns on phones, more on landscape/tablets.
    val thumbDp by viewModel.thumbSizeDp.collectAsState()
    val gridColumns = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / thumbDp).coerceIn(2, 6)
    val openPlaylistId by viewModel.openPlaylistId.collectAsState()
    val openPlaylistVideos by viewModel.openPlaylistVideos.collectAsState()
    val selectionPool = remember(uiState) {
        (uiState.videos + uiState.folderVideos + uiState.favorites + uiState.recentlyPlayed).distinctBy { it.id }
    }
    var confirmRemove by remember { mutableStateOf(false) }
    var vaultConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmDeleteDevice by remember { mutableStateOf(false) }
    var showMoreActions by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    // What to do once the system grants the MediaStore write/delete request (API 30+).
    var pendingAfterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val actionCtx = LocalContext.current
    val grantLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        val cb = pendingAfterGrant
        pendingAfterGrant = null
        if (res.resultCode == android.app.Activity.RESULT_OK) cb?.invoke()
    }
    fun toast(id: Int) = android.widget.Toast.makeText(actionCtx, id, android.widget.Toast.LENGTH_SHORT).show()
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showStreamDialog by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsState()
    val openPl = playlists.firstOrNull { it.id == openPlaylistId }
    val playlistTarget by viewModel.playlistTarget.collectAsState()
    playlistTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf("") }
        GlassDialog(
            onDismissRequest = viewModel::dismissAddToPlaylist,
            title = { Text("Add to playlist") },
            text = {
                Column {
                    playlists.forEach { pl ->
                        androidx.compose.material3.TextButton(onClick = { viewModel.addToPlaylist(pl.id) }) {
                            Text("${pl.name} (${pl.itemCount})", maxLines = 1)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "New playlist name"
                    )
                }
            },
            confirmButton = {
                GlassDialogButton(
                    text = "Create",
                    onClick = { viewModel.createPlaylistAndAdd(newName) },
                    enabled = newName.isNotBlank(),
                    primary = true
                )
            },
            dismissButton = { GlassDialogButton(text = "Cancel", onClick = viewModel::dismissAddToPlaylist) }
        )
    }
    val recentStreams by viewModel.recentStreams.collectAsState()
    val webDavOn by viewModel.webDavEnabled.collectAsState()
    var showWebDav by remember { mutableStateOf(false) }
    val smbOn by viewModel.smbEnabled.collectAsState()
    var showSmb by remember { mutableStateOf(false) }
    if (showSmb) {
        SmbDialog(onDismiss = { showSmb = false }, onPlay = { url, title -> viewModel.openSmb(url, title) })
    }
    if (showWebDav) {
        WebDavDialog(onDismiss = { showWebDav = false }, onPlay = { viewModel.openStream(it, remember = false) })
    }
    if (showStreamDialog) {
        var url by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        GlassDialog(
            onDismissRequest = { showStreamDialog = false },
            title = { Text("Open network stream") },
            text = {
                Column {
                    GlassTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        placeholder = "YouTube link or URL (HLS, DASH, MP4)",
                        errorText = error
                    )
                    if (smbOn) {
                        androidx.compose.material3.TextButton(onClick = { showStreamDialog = false; showSmb = true }) {
                            Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.smb_browse), fontSize = 13.sp)
                        }
                    }
                    if (webDavOn) {
                        androidx.compose.material3.TextButton(onClick = { showStreamDialog = false; showWebDav = true }) {
                            Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.webdav_browse), fontSize = 13.sp)
                        }
                    }
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
                GlassDialogButton(text = "Play", primary = true, onClick = {
                    val err = viewModel.openStream(url)
                    if (err == null) showStreamDialog = false else error = err
                })
            },
            dismissButton = {
                GlassDialogButton(text = "Cancel", onClick = { showStreamDialog = false })
            }
        )
    }
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    // One blur layer: the top bar blurs the list scrolling under it (skipped when "Reduce effects" is on).
    val hazeState = remember { dev.chrisbanes.haze.HazeState() }
    val reduceEffects = com.lnxv.player.core.design.LocalReduceEffects.current
    // Live backdrop blur (Haze / AGSL lens) re-renders the scrolling list every frame, which is what makes scrolling
    // heavy on mid-range GPUs. Only the HIGH tier pays for it; BALANCED/LOW use a translucent flat bar with the layered glass edge.
    val flatBars = reduceEffects || com.lnxv.player.core.design.LocalGlassProfile.current != com.lnxv.player.core.design.GlassProfile.HIGH
    // HIGH tier on API 33+: real AGSL refraction/frost of the list behind the bars (see core/design/LiquidLens.kt).
    // Any shader failure flips lensState.failed and the bars fall back to Haze automatically.
    val lensState = remember { com.lnxv.player.core.design.LiquidLensState() }
    val lensOn = !flatBars && !lensState.failed &&
        com.lnxv.player.core.design.lensSupported(com.lnxv.player.core.design.LocalGlassProfile.current, android.os.Build.VERSION.SDK_INT)
    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }
    BackHandler(enabled = uiState.openFolderPath != null && selection.isEmpty()) { viewModel.closeFolder() }
    val recycleBinOn by viewModel.recycleBinEnabled.collectAsState()
    var showTrash by remember { mutableStateOf(false) }
    val deletablePicked = selection.mapNotNull { id -> selectionPool.firstOrNull { it.id == id } }.filter { !it.isVault && it.id != com.lnxv.player.LNXVApplication.TRANSIENT_VIDEO_ID }
    if (confirmDeleteDevice) {
        GlassDialog(
            onDismissRequest = { confirmDeleteDevice = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.delete_device_title, deletablePicked.size)) },
            text = { Text(androidx.compose.ui.res.stringResource(if (recycleBinOn) com.lnxv.player.R.string.trash_move_text else com.lnxv.player.R.string.delete_device_text)) },
            confirmButton = {
                GlassDialogButton(
                    text = androidx.compose.ui.res.stringResource(if (recycleBinOn) com.lnxv.player.R.string.trash_move_action else com.lnxv.player.R.string.action_delete_device),
                    primary = true,
                    destructive = true,
                    onClick = {
                    confirmDeleteDevice = false
                    val uris = deletablePicked.map { it.uri }
                    val sender = if (recycleBinOn) com.lnxv.player.data.mediastore.MediaActions.trashRequest(actionCtx, uris, true)
                        else com.lnxv.player.data.mediastore.MediaActions.deleteRequest(actionCtx, uris)
                    val finish = { viewModel.removeSelected(selectionPool) }
                    if (sender != null) {
                        pendingAfterGrant = finish
                        grantLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                    } else if (com.lnxv.player.data.mediastore.MediaActions.deleteDirect(actionCtx, uris) > 0) finish() else toast(com.lnxv.player.R.string.action_failed)
                })
            },
            dismissButton = { GlassDialogButton(text = "Cancel", onClick = { confirmDeleteDevice = false }) }
        )
    }
    if (showTrash) {
        TrashDialog(
            onDismiss = { showTrash = false },
            requestGrant = { sender, after ->
                pendingAfterGrant = after
                grantLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
            },
            onChanged = { viewModel.refreshLibrary() }
        )
    }
    renameTarget?.let { target ->
        GlassDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_rename)) },
            text = {
                GlassTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.rename_hint)
                )
            },
            confirmButton = {
                GlassDialogButton(
                    text = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.rename_confirm),
                    primary = true,
                    onClick = renameClick@{
                    val newName = com.lnxv.player.data.mediastore.MediaActions.sanitizeName(renameText, target.path.substringAfterLast('/'))
                    renameTarget = null
                    if (newName == null) { toast(com.lnxv.player.R.string.action_failed); return@renameClick }
                    val doRename = {
                        if (com.lnxv.player.data.mediastore.MediaActions.rename(actionCtx, target.uri, newName)) {
                            viewModel.clearSelection(); viewModel.refreshLibrary(); toast(com.lnxv.player.R.string.action_done)
                        } else toast(com.lnxv.player.R.string.action_failed)
                    }
                    val sender = com.lnxv.player.data.mediastore.MediaActions.writeRequest(actionCtx, listOf(target.uri))
                    if (sender != null) {
                        pendingAfterGrant = doRename
                        grantLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                    } else doRename()
                })
            },
            dismissButton = { GlassDialogButton(text = "Cancel", onClick = { renameTarget = null }) }
        )
    }
    vaultConfirm?.let { action ->
        GlassDialog(
            onDismissRequest = { vaultConfirm = null },
            title = { Text("Move to Private Vault?") },
            text = { Text("The video is encrypted into the vault and the original is removed from your gallery. You can restore it from the vault later.") },
            confirmButton = { GlassDialogButton(text = "Move", onClick = { vaultConfirm = null; action() }, primary = true) },
            dismissButton = { GlassDialogButton(text = "Cancel", onClick = { vaultConfirm = null }) }
        )
    }
    if (confirmRemove) {
        GlassDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${selection.size} from library?") },
            text = { Text("This removes them from LNXV's library list. It does not delete the files from your device.") },
            confirmButton = {
                GlassDialogButton(text = "Remove", primary = true, destructive = true, onClick = {
                    viewModel.removeSelected(selectionPool)
                    confirmRemove = false
                })
            },
            dismissButton = { GlassDialogButton(text = "Cancel", onClick = { confirmRemove = false }) }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().ambientBackground(),
        containerColor = Color.Transparent,
bottomBar = {
            val reduceMotion = com.lnxv.player.core.design.LocalReduceMotion.current || com.lnxv.player.core.design.LocalReduceEffects.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = selection.isNotEmpty(),
                    transitionSpec = {
                        if (reduceMotion) {
                            androidx.compose.animation.ContentTransform(
                                targetContentEnter = androidx.compose.animation.EnterTransition.None,
                                initialContentExit = androidx.compose.animation.ExitTransition.None
                            )
                        } else {
                            androidx.compose.animation.ContentTransform(
                                targetContentEnter = androidx.compose.animation.slideInVertically(
                                    com.lnxv.player.core.design.MotionTokens.SpringOffset
                                ) { it / 2 } + androidx.compose.animation.fadeIn(tween(180)),
                                initialContentExit = androidx.compose.animation.slideOutVertically(
                                    tween(180, easing = com.lnxv.player.core.design.MotionTokens.IosStandardAccelerate)
                                ) { it / 2 } + androidx.compose.animation.fadeOut(tween(140))
                            )
                        }
                    },
                    label = "bottom_bar"
                ) { isSelecting ->
                if (isSelecting) {
                    val selShape = RoundedCornerShape(26.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (flatBars) Modifier
                                else if (lensOn) Modifier.liquidLensTarget(lensState, 1, 26.dp)
                                else Modifier.clip(selShape).hazeChild(state = hazeState)
                            )
                            .liquidGlassSurface(
                                shape = selShape,
                                backgroundColor = if (flatBars) appColors.barFill else appColors.barFill.copy(alpha = if (lensOn) 0.32f else 0.6f),
                                borderColor = appColors.glassBorder
                            )
                    ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selection.size.toString(),
                        color = appColors.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    )
                    SelectionAction(
                        icon = Icons.Default.Check,
                        label = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_all),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectAll(selectionPool.filter { v -> v.id in (if (uiState.openFolderPath != null) uiState.folderVideos else uiState.videos).map { it.id } }) }
                    )
                    SelectionAction(
                        icon = Icons.Default.Favorite,
                        label = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_fav),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.favoriteSelected(selectionPool) }
                    )
                    SelectionAction(
                        icon = Icons.Default.Add,
                        label = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_playlist),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.requestAddSelectedToPlaylist(selectionPool) }
                    )
                    SelectionAction(
                        icon = Icons.Default.Lock,
                        label = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_vault),
                        modifier = Modifier.weight(1f),
                        onClick = { vaultConfirm = { viewModel.vaultSelected(selectionPool) } }
                    )
                    SelectionAction(
                        icon = Icons.Default.Delete,
                        label = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_remove),
                        modifier = Modifier.weight(1f),
                        tint = com.lnxv.player.core.design.DestructiveRed,
                        onClick = { confirmRemove = true }
                    )
                    Box {
                        SelectionAction(
                            icon = Icons.Default.MoreVert,
                            label = "More",
                            modifier = Modifier.width(52.dp),
                            onClick = { showMoreActions = true }
                        )
                        GlassDropdownMenu(expanded = showMoreActions, onDismissRequest = { showMoreActions = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_share)) },
                                enabled = deletablePicked.isNotEmpty(),
                                onClick = {
                                    showMoreActions = false
                                    actionCtx.startActivity(com.lnxv.player.data.mediastore.MediaActions.shareIntent(deletablePicked.map { it.uri }))
                                }
                            )
                            if (deletablePicked.size == 1 && selection.size == 1) androidx.compose.material3.DropdownMenuItem(
                                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_rename)) },
                                onClick = {
                                    showMoreActions = false
                                    renameText = deletablePicked[0].path.substringAfterLast('/').substringBeforeLast('.')
                                    renameTarget = deletablePicked[0]
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.action_delete_device)) },
                                enabled = deletablePicked.isNotEmpty(),
                                onClick = { showMoreActions = false; confirmDeleteDevice = true }
                            )
                        }
                    }
                    SelectionAction(
                        icon = Icons.Default.Close,
                        label = "Done",
                        modifier = Modifier.width(52.dp),
                        onClick = { viewModel.clearSelection() }
                    )
                }
                    }
                } else {
                    IosTabBar(
                        modifier = if (lensOn) Modifier.liquidLensTarget(lensState, 1, 24.dp) else Modifier,
                        backdropHandled = lensOn,
                        labels = HomeTab.values().map { androidx.compose.ui.res.stringResource(it.labelRes) },
                        selectedIndex = HomeTab.values().indexOf(uiState.selectedTab),
                        onSelect = { viewModel.selectTab(HomeTab.values()[it]) },
                        hazeState = if (flatBars || lensOn) null else hazeState
                    )
                }
                }
            }
        },
topBar = {
            val barShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (flatBars) Modifier
                        else if (lensOn) Modifier.liquidLensTarget(lensState, 0, 28.dp, bleedTop = 64.dp)
                        else Modifier.clip(barShape).hazeChild(state = hazeState)
                    )
                    .liquidGlassSurface(
                        shape = barShape,
                        backgroundColor = if (flatBars) appColors.barFill else appColors.barFill.copy(alpha = if (lensOn) 0.30f else 0.55f),
                        borderColor = appColors.glassBorder,
                        borderWidth = 1.dp
                    )
                    .padding(top = 44.dp, bottom = 14.dp, start = 20.dp, end = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "LNXV",
                            color = appColors.textPrimary,
                            style = Typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "YOUR PERSONAL CINEMA",
                            color = appColors.textTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp
                        )
                    }
                    IosPill(hazeState = null) {
                        val pillIconSize = 36.dp
                        IosPillIcon(Icons.Default.Search, "Search", { isSearchExpanded = !isSearchExpanded }, size = pillIconSize)
                        if (recycleBinOn) {
                            IosPillIcon(Icons.Default.Delete, androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.trash_title), { showTrash = true }, size = pillIconSize)
                        }
                        IosPillIcon(Icons.Default.Link, "Open network stream", { showStreamDialog = true }, size = pillIconSize)
                        IosPillIcon(Icons.Default.PlayArrow, "YouTube", onYouTubeClick, size = pillIconSize)
                        IosPillIcon(Icons.Default.GraphicEq, "Join screen", onReceiveClick, size = pillIconSize)
                        IosPillIcon(Icons.Default.Lock, "Private Vault", onVaultClick, size = pillIconSize)
                        IosPillIcon(Icons.Default.Settings, "Settings", onSettingsClick, size = pillIconSize)
                    }
                }
                if (isSearchExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassSearchField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.search_hint_filters)
                    )
                }

            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (flatBars) Modifier
                    else if (lensOn) Modifier.liquidLensSource(lensState)
                    else Modifier.haze(state = hazeState)
                ),
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
                            com.lnxv.player.core.design.GlassIconButton(
                                icon = if (uiState.isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                contentDescription = "Toggle grid/list",
                                onClick = { viewModel.toggleViewMode() },
                                tint = appColors.accent,
                                size = 40.dp
                            )
                            Box {
                                com.lnxv.player.core.design.GlassIconButton(
                                    icon = Icons.Default.Sort,
                                    contentDescription = "Sort",
                                    onClick = { isSortMenuExpanded = true },
                                    tint = appColors.accent,
                                    size = 40.dp
                                )
                                GlassDropdownMenu(
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
                                com.lnxv.player.core.design.GlassFilterChip(
                                    selected = uiState.collection == c,
                                    label = androidx.compose.ui.res.stringResource(c.labelRes),
                                    onClick = { viewModel.selectCollection(c) }
                                )
                            }
                        }
                    }
                    if (uiState.videos.isEmpty()) {
                        item {
                            EmptyStateView(
                                message = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.empty_discovered),
                                actionLabel = "Refresh library",
                                onAction = { viewModel.refreshLibrary() }
                            )
                        }
                    } else if (uiState.isGridView) {
                        items(uiState.videos.chunked(gridColumns), key = { row -> row.first().id }) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().animateItem(),
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
                                        onVaultMove = { vaultConfirm = { viewModel.moveToVault(video) } },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(video) },
                                        onDelete = { viewModel.deleteVideo(video) }
                                    )
                                }
                                repeat(gridColumns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        items(uiState.videos, key = { it.id }) { video ->
                            VideoItemRow(
                                modifier = Modifier.animateItem(),
                                video = video,
                                onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { vaultConfirm = { viewModel.moveToVault(video) } },
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
                            item { EmptyStateView(message = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.empty_folder)) }
                        } else {
                            items(uiState.folderVideos, key = { it.id }) { video ->
                                VideoItemRow(
                                    modifier = Modifier.animateItem(),
                                    video = video,
                                    onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                    onVaultMove = { vaultConfirm = { viewModel.moveToVault(video) } },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(video) },
                                    onDelete = { viewModel.deleteVideo(video) }
                                )
                            }
                        }
                    } else if (uiState.folders.isEmpty()) {
                        item { EmptyStateView(message = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.empty_no_folders)) }
                    } else {
                        items(uiState.folders, key = { it.path }) { folder ->
                            FolderItemRow(
                                modifier = Modifier.animateItem(),
                                folder = folder,
                                onClick = { onFolderClick(folder) },
                                onToggleHidden = { viewModel.setFolderHidden(folder.path, !folder.isHidden) }
                            )
                        }
                    }
                }
                HomeTab.RECENTS -> {
                    if (uiState.recentlyPlayed.isEmpty()) {
                        item { EmptyStateView(message = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.empty_recents)) }
                    } else {
                        items(uiState.recentlyPlayed, key = { it.id }) { video ->
                            VideoItemRow(
                                modifier = Modifier.animateItem(),
                                video = video,
                                onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { vaultConfirm = { viewModel.moveToVault(video) } },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(video) },
                                onDelete = { viewModel.deleteVideo(video) }
                            )
                        }
                    }
                }
                HomeTab.PLAYLISTS -> {
                    if (openPlaylistId != null && openPl == null) {
                        item { EmptyStateView(message = "Playlist not found") }
                    } else if (openPl != null) {
                        item {
                            var renaming by remember(openPl.id) { mutableStateOf(false) }
                            var newName by remember(openPl.id, openPl.name) { mutableStateOf(openPl.name) }
                            if (renaming) {
                                GlassDialog(
                                    onDismissRequest = { renaming = false },
                                    title = { Text("Rename playlist") },
                                    text = {
                                        GlassTextField(
                                            value = newName,
                                            onValueChange = { newName = it },
                                            placeholder = "Playlist name"
                                        )
                                    },
                                    confirmButton = {
                                        GlassDialogButton(
                                            text = "Save",
                                            primary = true,
                                            onClick = { viewModel.renamePlaylist(openPl.id, newName); renaming = false },
                                            enabled = newName.isNotBlank()
                                        )
                                    },
                                    dismissButton = { GlassDialogButton(text = "Cancel", onClick = { renaming = false }) }
                                )
                            }
                            BackHandler { viewModel.closePlaylist() }
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.TextButton(onClick = { viewModel.closePlaylist() }) { Text("← Back") }
                                Text(openPl.name, color = appColors.textPrimary, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                androidx.compose.material3.TextButton(onClick = { renaming = true }) { Text("Rename") }
                                androidx.compose.material3.TextButton(onClick = { viewModel.playPlaylist(openPl.id) }) { Text("Play all") }
                            }
                        }
                        if (openPlaylistVideos.isEmpty()) {
                            item { EmptyStateView(message = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.empty_playlist)) }
                        } else {
                            items(openPlaylistVideos, key = { it.id }) { video ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.playPlaylistFrom(openPl.id, video.id) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(video.title, color = appColors.textPrimary, fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f))
                                    androidx.compose.material3.TextButton(onClick = { viewModel.moveInPlaylist(openPl.id, video.id, -1) }) { Text("▲") }
                                    androidx.compose.material3.TextButton(onClick = { viewModel.moveInPlaylist(openPl.id, video.id, 1) }) { Text("▼") }
                                    androidx.compose.material3.TextButton(onClick = { viewModel.removeFromPlaylist(openPl.id, video.id) }) { Text("✕", color = com.lnxv.player.core.design.DestructiveRed) }
                                }
                            }
                        }
                    } else if (playlists.isEmpty()) {
                        item { EmptyStateView(message = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.empty_playlists)) }
                    } else {
                        items(playlists, key = { it.id }) { pl ->
                            var confirmDelete by remember(pl.id) { mutableStateOf(false) }
                            if (confirmDelete) {
                                GlassDialog(
                                    onDismissRequest = { confirmDelete = false },
                                    title = { Text("Delete \"${pl.name}\"?") },
                                    text = { Text("The videos themselves are not deleted.") },
                                    confirmButton = {
                                        GlassDialogButton(text = "Delete", primary = true, destructive = true, onClick = { viewModel.deletePlaylist(pl.id); confirmDelete = false })
                                    },
                                    dismissButton = { GlassDialogButton(text = "Cancel", onClick = { confirmDelete = false }) }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openPlaylist(pl.id) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, color = appColors.textPrimary, fontSize = 15.sp, maxLines = 1)
                                    Text("${pl.itemCount} video(s)", color = appColors.textTertiary, fontSize = 12.sp)
                                }
                                androidx.compose.material3.TextButton(onClick = { viewModel.playPlaylist(pl.id) }) { Text("Play") }
                                androidx.compose.material3.TextButton(onClick = { confirmDelete = true }) {
                                    Text("Delete", color = com.lnxv.player.core.design.DestructiveRed)
                                }
                            }
                        }
                    }
                }
                HomeTab.FAVORITES -> {
                    if (uiState.favorites.isEmpty()) {
                        item { EmptyStateView(message = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.empty_fav)) }
                    } else {
                        items(uiState.favorites, key = { it.id }) { video ->
                            VideoItemRow(
                                modifier = Modifier.animateItem(),
                                video = video,
                                onClick = { if (selection.isNotEmpty()) viewModel.toggleSelect(video.id) else onVideoClick(video) },
                                selected = video.id in selection,
                                onLongClick = { viewModel.toggleSelect(video.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onVaultMove = { vaultConfirm = { viewModel.moveToVault(video) } },
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

/** Icon + tiny label action used inside the glass selection bar. */
@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = appColors.textPrimary
) {
    Column(
        modifier = modifier
            .bouncyClickable(pressedScale = 0.9f) { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            color = tint.copy(alpha = 0.85f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
