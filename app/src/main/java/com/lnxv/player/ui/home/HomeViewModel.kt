package com.lnxv.player.ui.home

import androidx.compose.runtime.Immutable
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.model.FolderItem
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.data.mediastore.MediaStoreScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class HomeTab(val label: String) {
    VIDEOS("Videos"),
    FOLDERS("Folders"),
    RECENTS("Recents"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists")
}

enum class SortOption(val displayName: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Size (Largest)"),
    DURATION_DESC("Duration (Longest)")
}

@Immutable
data class HomeUiState(
    val selectedTab: HomeTab = HomeTab.VIDEOS,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val videos: List<VideoItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val recentlyPlayed: List<VideoItem> = emptyList(),
    val favorites: List<VideoItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val openFolderPath: String? = null,
    val openFolderName: String = "",
    val folderVideos: List<VideoItem> = emptyList(),
    val isGridView: Boolean = false,
    val collection: com.lnxv.player.core.model.SmartCollection = com.lnxv.player.core.model.SmartCollection.ALL
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LNXVApplication
    private val repository = app.videoRepository
    private val vaultRepo = app.vaultRepository
    private val dataStore = app.dataStore

    private val _selectedTab = MutableStateFlow(HomeTab.VIDEOS)
    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    private val _isRefreshing = MutableStateFlow(false)
    private val _openFolder = MutableStateFlow<String?>(null)
    private val _collection = MutableStateFlow(com.lnxv.player.core.model.SmartCollection.ALL)

    private data class Controls(
        val tab: HomeTab,
        val query: String,
        val sort: SortOption,
        val refreshing: Boolean,
        val openFolder: String?,
        val collection: com.lnxv.player.core.model.SmartCollection = com.lnxv.player.core.model.SmartCollection.ALL
    )

    private val controlsFlow = combine(
        combine(
            _selectedTab,
            _searchQuery,
            _sortOption,
            _isRefreshing,
            _openFolder
        ) { tab, query, sort, refreshing, folder -> Controls(tab, query, sort, refreshing, folder) },
        _collection
    ) { c, collection -> c.copy(collection = collection) }

    // hidden folders + grid/list mode
    private val libraryPrefsFlow = dataStore.userPreferencesFlow
        .map { it.hiddenFolders to (it.libraryViewMode == "GRID") }
        .distinctUntilChanged()

    private fun sortVideos(list: List<VideoItem>, sort: SortOption): List<VideoItem> = when (sort) {
        SortOption.DATE_DESC -> list.sortedByDescending { it.dateAddedSec }
        SortOption.DATE_ASC -> list.sortedBy { it.dateAddedSec }
        SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
        SortOption.NAME_DESC -> list.sortedByDescending { it.title.lowercase() }
        SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
        SortOption.DURATION_DESC -> list.sortedByDescending { it.durationMs }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        controlsFlow,
        repository.allVideosFlow,
        repository.recentlyPlayedFlow,
        repository.favoritesFlow,
        libraryPrefsFlow
    ) { controls, allVids, recents, favs, libPrefs ->
        val query = controls.query
        val sort = controls.sort
        val hidden = libPrefs.first

        // Hidden folders are removed from Videos / Recents / Favorites (but stay in the Folders tab)
        val visible = if (hidden.isEmpty()) allVids else allVids.filter { it.folderPath !in hidden }

        val nowSec = System.currentTimeMillis() / 1000L
        val inCollection = when (controls.collection) {
            com.lnxv.player.core.model.SmartCollection.ALL -> visible
            com.lnxv.player.core.model.SmartCollection.DUPLICATES -> com.lnxv.player.core.model.DuplicateFinder.flatten(visible)
            else -> visible.filter { controls.collection.matches(it, nowSec) }
        }
        val filteredVideos = if (query.isBlank()) {
            inCollection
        } else {
            val parsed = com.lnxv.player.core.model.SearchQuery.parse(query)
            inCollection.filter { parsed.matches(it) }
        }

        val folders = MediaStoreScanner.extractFolders(allVids)
            .map { it.copy(isHidden = it.path in hidden) }

        val openPath = controls.openFolder
        val folderVideos = if (openPath == null) {
            emptyList()
        } else {
            sortVideos(allVids.filter { it.folderPath == openPath }, sort)
        }

        HomeUiState(
            selectedTab = controls.tab,
            searchQuery = query,
            sortOption = sort,
            videos = if (controls.collection == com.lnxv.player.core.model.SmartCollection.DUPLICATES) filteredVideos else sortVideos(filteredVideos, sort),
            folders = folders,
            recentlyPlayed = if (hidden.isEmpty()) recents else recents.filter { it.folderPath !in hidden },
            favorites = if (hidden.isEmpty()) favs else favs.filter { it.folderPath !in hidden },
            isRefreshing = controls.refreshing,
            openFolderPath = openPath,
            openFolderName = folders.firstOrNull { it.path == openPath }?.name ?: "",
            folderVideos = folderVideos,
            isGridView = libPrefs.second,
            collection = controls.collection
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        refreshLibrary()
    }

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
        if (tab != HomeTab.FOLDERS) _openFolder.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCollection(c: com.lnxv.player.core.model.SmartCollection) {
        _collection.value = c
    }

    fun updateSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun openFolder(path: String) {
        _openFolder.value = path
    }

    fun closeFolder() {
        _openFolder.value = null
    }

    fun setFolderHidden(path: String, hidden: Boolean) {
        viewModelScope.launch { dataStore.setFolderHidden(path, hidden) }
    }

    fun toggleViewMode() {
        val grid = uiState.value.isGridView
        viewModelScope.launch { dataStore.updateViewMode(if (grid) "LIST" else "GRID") }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshLibrary()
            _isRefreshing.value = false
        }
    }

    // ------------------------------------------------------------------ multi-select (Phase 6)

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: kotlinx.coroutines.flow.StateFlow<Set<Long>> = _selection

    fun toggleSelect(id: Long) {
        _selection.value = _selection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() { _selection.value = emptySet() }

    fun selectAll(videos: List<VideoItem>) { _selection.value = videos.map { it.id }.toSet() }

    private fun selectedVideos(pool: List<VideoItem>): List<VideoItem> =
        pool.filter { it.id in _selection.value }

    /** Favorites every selected video, or un-favorites them when all are already favorites. */
    fun favoriteSelected(pool: List<VideoItem>) {
        val picked = selectedVideos(pool)
        val makeFavorite = picked.any { !it.isFavorite }
        viewModelScope.launch {
            picked.forEach { if (it.isFavorite != makeFavorite) repository.toggleFavorite(it.id, it.isFavorite) }
        }
        clearSelection()
    }

    fun vaultSelected(pool: List<VideoItem>) {
        selectedVideos(pool).forEach { moveToVault(it) }
        clearSelection()
    }

    fun removeSelected(pool: List<VideoItem>) {
        val picked = selectedVideos(pool)
        viewModelScope.launch { picked.forEach { repository.deleteVideo(it.id) } }
        clearSelection()
    }

    private var bulkPlaylistIds: List<Long>? = null

    fun requestAddSelectedToPlaylist(pool: List<VideoItem>) {
        val picked = selectedVideos(pool)
        if (picked.isEmpty()) return
        bulkPlaylistIds = picked.map { it.id }
        _playlistTarget.value = picked.first()
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(video.id, video.isFavorite)
        }
    }

    fun moveToVault(video: VideoItem) {
        viewModelScope.launch {
            if (!vaultRepo.isPinSet()) {
                app.vaultTransfers.postMessage("Open the Private Vault once to create your PIN, then move videos.")
                return@launch
            }
            val prefs = dataStore.userPreferencesFlow.first()
            if (!prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.ENCRYPTED_VAULT)) {
                app.vaultTransfers.postMessage("Encrypted vault is turned off in Settings > Advanced > Experimental.")
                return@launch
            }
            app.vaultTransfers.enqueueMove(video)
        }
    }

    fun deleteVideo(video: VideoItem) {
        viewModelScope.launch {
            repository.deleteVideo(video.id)
        }
    }

    /** Recent network stream URLs (credentials already stripped when saved). */
    val recentStreams: kotlinx.coroutines.flow.StateFlow<List<String>> = dataStore.userPreferencesFlow
        .map { com.lnxv.player.core.util.StreamUrl.decodeList(it.recentStreams) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun rememberStream(url: String) {
        viewModelScope.launch {
            val clean = com.lnxv.player.core.util.StreamUrl.stripCredentials(url)
            val current = com.lnxv.player.core.util.StreamUrl.decodeList(dataStore.userPreferencesFlow.first().recentStreams)
            dataStore.saveRecentStreams(
                com.lnxv.player.core.util.StreamUrl.encodeList(com.lnxv.player.core.util.StreamUrl.addRecent(current, clean))
            )
        }
    }

    fun clearRecentStreams() {
        viewModelScope.launch { dataStore.saveRecentStreams("") }
    }

    /** Validates and opens a network stream through the transient-video player route. Returns an error text or null. */
    fun openStream(rawUrl: String): String? {
        val debuggable = app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        return when (val r = com.lnxv.player.core.util.StreamUrl.validate(rawUrl, allowCleartextHttp = debuggable)) {
            is com.lnxv.player.core.util.StreamUrl.Result.Error -> r.message
            is com.lnxv.player.core.util.StreamUrl.Result.Ok -> {
                rememberStream(r.url)
                app.transientVideo = VideoItem(
                    id = LNXVApplication.TRANSIENT_VIDEO_ID,
                    title = com.lnxv.player.core.util.StreamUrl.titleFor(r.url),
                    uri = android.net.Uri.parse(r.url),
                    path = "", durationMs = 0L, sizeBytes = 0L, width = 0, height = 0,
                    mimeType = "video/*", dateAddedSec = System.currentTimeMillis() / 1000,
                    folderPath = "Network Streams", folderName = "Network"
                )
                app.pendingOpenVideoId.value = LNXVApplication.TRANSIENT_VIDEO_ID
                null
            }
        }
    }

    // ------------------------------------------------------------------ playlists

    private val playlistDao = app.playlistDao

    val playlists: kotlinx.coroutines.flow.StateFlow<List<com.lnxv.player.core.database.PlaylistSummary>> =
        playlistDao.observePlaylists().stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _playlistTarget = MutableStateFlow<VideoItem?>(null)
    /** Video the user wants to add to a playlist (drives the picker dialog). */
    val playlistTarget: kotlinx.coroutines.flow.StateFlow<VideoItem?> = _playlistTarget

    fun requestAddToPlaylist(video: VideoItem) { _playlistTarget.value = video }
    fun dismissAddToPlaylist() { _playlistTarget.value = null; bulkPlaylistIds = null }

    fun addToPlaylist(playlistId: Long) {
        val video = _playlistTarget.value ?: return
        val ids = bulkPlaylistIds ?: listOf(video.id)
        _playlistTarget.value = null
        bulkPlaylistIds = null
        viewModelScope.launch {
            var pos = playlistDao.nextPosition(playlistId)
            ids.forEach { id ->
                playlistDao.insertItem(com.lnxv.player.core.database.PlaylistItemEntity(playlistId, id, pos++))
            }
        }
        clearSelection()
    }

    fun createPlaylistAndAdd(name: String) {
        val video = _playlistTarget.value
        val ids = bulkPlaylistIds ?: listOfNotNull(video?.id)
        _playlistTarget.value = null
        bulkPlaylistIds = null
        val clean = name.trim().take(60)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val id = playlistDao.insertPlaylist(com.lnxv.player.core.database.PlaylistEntity(name = clean, createdAt = System.currentTimeMillis()))
            ids.forEachIndexed { i, vid -> playlistDao.insertItem(com.lnxv.player.core.database.PlaylistItemEntity(id, vid, i)) }
        }
        clearSelection()
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.clearItems(playlistId)
            playlistDao.deletePlaylist(playlistId)
        }
    }

    /** Plays the whole playlist as the queue. */
    fun playPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val ids = playlistDao.getVideoIds(playlistId)
            if (ids.isEmpty()) return@launch
            app.queueOverride = ids
            app.pendingOpenVideoId.value = ids.first()
        }
    }
}
