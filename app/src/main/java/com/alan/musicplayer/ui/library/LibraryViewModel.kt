package com.alan.musicplayer.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alan.musicplayer.MusicApplication
import com.alan.musicplayer.data.model.Group
import com.alan.musicplayer.data.model.Song
import com.alan.musicplayer.data.repo.LibraryState
import com.alan.musicplayer.data.repo.PrefsRepository
import com.alan.musicplayer.playback.PlaybackUiState
import com.alan.musicplayer.util.hasAudioPermission
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val loadState: LibraryState = LibraryState.Loading,
    val hasPermission: Boolean = true,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MusicApplication).container
    private val permissionGranted = MutableStateFlow(application.hasAudioPermission())
    private var refreshJob: Job? = null

    /** 本次会话中从列表移除的歌曲 id：仅影响列表展示，不写数据库、不删文件。 */
    private val removedSongIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<LibraryUiState> = combine(
        container.libraryRepository.songs,
        container.libraryRepository.state,
        permissionGranted,
        removedSongIds,
    ) { songs, state, granted, removed ->
        LibraryUiState(
            songs = songs.filterNot { it.id in removed },
            loadState = state,
            hasPermission = granted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val playback: StateFlow<PlaybackUiState> = container.playerConnection.state

    /** 供“加入分组”弹窗使用的分组列表。 */
    val groups: StateFlow<List<Group>> = container.groupRepository.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 手动刷新后的结果提示，避免“点了扫描没反应”的错觉。 */
    private val _scanNotice = MutableStateFlow<String?>(null)
    val scanNotice: StateFlow<String?> = _scanNotice.asStateFlow()

    /** 把单曲加入分组；已存在时给出“已在组内”的提示。 */
    fun addSongToGroup(group: Group, song: Song) {
        viewModelScope.launch {
            val added = container.groupRepository.addSongToGroup(group.id, song)
            _scanNotice.value = if (added) {
                "已加入分组「${group.name}」"
            } else {
                "「${song.title}」已在分组「${group.name}」中"
            }
        }
    }

    init {
        if (permissionGranted.value) refresh()
    }

    fun onPermissionGranted() {
        if (permissionGranted.value) return
        permissionGranted.value = true
        refresh()
    }

    fun onPermissionDenied() {
        permissionGranted.value = false
    }

    /**
     * 重新查询 MediaStore（每次都真实查询，不使用缓存）。
     * @param notify 手动刷新时为 true，完成后给出“共 N 首”的提示
     */
    fun refresh(notify: Boolean = false) {
        if (!permissionGranted.value) return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val songs = container.libraryRepository.refresh()
            // 扫描成功后才同步分组数据：媒体库重建时按 uri 重匹配，删除已不存在的歌曲引用
            if (songs != null) {
                // 重新扫描媒体库后，被移除的曲目恢复正常展示
                removedSongIds.value = emptySet()
                container.groupRepository.syncWithMediaStore(songs)
                if (notify) {
                    _scanNotice.value = if (songs.isEmpty()) {
                        "扫描完成，没有找到音乐"
                    } else {
                        "扫描完成，共 ${songs.size} 首"
                    }
                }
            } else if (notify) {
                _scanNotice.value = "扫描失败，请确认已授予音乐读取权限"
            }
        }
    }

    fun clearScanNotice() {
        _scanNotice.value = null
    }

    /**
     * 从当前歌曲列表移除（仅内存过滤，不删除文件、不修改任何磁盘数据）。
     */
    fun removeFromList(song: Song) {
        removedSongIds.value = removedSongIds.value + song.id
    }

    /** 撤销移除。 */
    fun restoreToList(song: Song) {
        removedSongIds.value = removedSongIds.value - song.id
    }

    /**
     * 以当前全部歌曲列表为队列，从点击的歌曲开始播放。
     */
    fun playSong(song: Song) {
        val songs = uiState.value.songs
        val index = songs.indexOfFirst { it.id == song.id }
        if (index < 0) return
        container.playerConnection.playQueue(songs, index, PrefsRepository.ALL_SONGS_GROUP_ID)
    }

    fun togglePlayPause() = container.playerConnection.togglePlayPause()

    fun skipToNext() = container.playerConnection.skipToNext()

    fun clearPlaybackError() = container.playerConnection.clearError()
}
