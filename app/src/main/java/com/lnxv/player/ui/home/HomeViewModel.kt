package com.lnxv.player.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.model.FolderItem
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.data.mediastore.MediaStoreScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeTab(val label: String) {
    VIDEOS("Videos"),
    FOLDERS("Folders"),
    RECENTS("Recents"),
    FAVORITES("Favorites")
}

enum class SortOption(val displayName: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Size (Largest)"),
    DURATION_DESC("Duration (Longest)")
}

data class HomeUiState(
    val selectedTab: HomeTab = HomeTab.VIDEOS,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val videos: List<VideoItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val recentlyPlayed: List<VideoItem> = emptyList(),
    val favorites: List<VideoItem> = emptyList(),
    val isRefreshing: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as LNXVApplication).videoRepository
    private val vaultRepo = (application as LNXVApplication).vaultRepository

    private val _selectedTab = MutableStateFlow(HomeTab.VIDEOS)
    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedTab,
        _searchQuery,
        _sortOption,
        _isRefreshing,
        repository.allVideosFlow,
        repository.recentlyPlayedFlow,
        repository.favoritesFlow
    ) { tab, query, sort, refreshing, allVids, recents, favs ->

        // Filter by search query
        val filteredVideos = if (query.isBlank()) {
            allVids
        } else {
            allVids.filter { it.title.contains(query, ignoreCase = true) || it.folderName.contains(query, ignoreCase = true) }
        }

        // Apply sort
        val sortedVideos = when (sort) {
            SortOption.DATE_DESC -> filteredVideos.sortedByDescending { it.dateAddedSec }
            SortOption.DATE_ASC -> filteredVideos.sortedBy { it.dateAddedSec }
            SortOption.NAME_ASC -> filteredVideos.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> filteredVideos.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> filteredVideos.sortedByDescending { it.sizeBytes }
            SortOption.DURATION_DESC -> filteredVideos.sortedByDescending { it.durationMs }
        }

        val folders = MediaStoreScanner.extractFolders(allVids)

        HomeUiState(
            selectedTab = tab,
            searchQuery = query,
            sortOption = sort,
            videos = sortedVideos,
            folders = folders,
            recentlyPlayed = recents,
            favorites = favs,
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        refreshLibrary()
    }

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshLibrary()
            _isRefreshing.value = false
        }
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(video.id, video.isFavorite)
        }
    }

    fun moveToVault(video: VideoItem) {
        viewModelScope.launch {
            vaultRepo.moveToVault(video.id)
        }
    }

    fun deleteVideo(video: VideoItem) {
        viewModelScope.launch {
            repository.deleteVideo(video.id)
        }
    }
}
