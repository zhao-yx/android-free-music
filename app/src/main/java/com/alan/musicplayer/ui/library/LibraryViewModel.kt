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
import com.alan.musicplayer.ui.groups.HEART_PLAYLIST_ID
import com.alan.musicplayer.util.hasAudioPermission
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val loadState: LibraryState = LibraryState.Loading,
    val hasPermission: Boolean = true,
    /** 当前列表对应的播放来源：分组 id、心动歌单或全部歌曲（-1）。 */
    val sourceGroupId: Long = PrefsRepository.ALL_SONGS_GROUP_ID,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MusicApplication).container
    private val permissionGranted = MutableStateFlow(application.hasAudioPermission())
    private var refreshJob: Job? = null

    /** 本次会话中从列表移除的歌曲 id：仅影响列表展示，不写数据库、不删文件。 */
    private val removedSongIds = MutableStateFlow<Set<Long>>(emptySet())

    private data class SourceContent(val sourceId: Long, val songs: List<Song>)

    /**
     * 主列表内容跟随当前播放来源：分组按其顺序、心动歌单按榜单顺序、
     * 其余情况（含来源分组已删除）显示全部歌曲。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sourceContent: Flow<SourceContent> = container.playerConnection.state
        .map { it.queueGroupId }
        .distinctUntilChanged()
        .flatMapLatest { sourceId ->
            when (sourceId) {
                HEART_PLAYLIST_ID -> combine(
                    container.libraryRepository.songs,
                    container.heartPlaylistIds,
                ) { songs, heartIds ->
                    val byId = songs.associateBy { it.id }
                    SourceContent(sourceId, heartIds.mapNotNull { byId[it] })
                }

                PrefsRepository.ALL_SONGS_GROUP_ID -> container.libraryRepository.songs
                    .map { SourceContent(sourceId, it) }

                else -> combine(
                    container.libraryRepository.songs,
                    container.groupRepository.groups,
                ) { songs, groups -> songs to groups }
                    .flatMapLatest { (songs, groups) ->
                        if (groups.none { it.id == sourceId }) {
                            // 来源分组已被删除，退回全部歌曲
                            container.libraryRepository.songs
                                .map { SourceContent(PrefsRepository.ALL_SONGS_GROUP_ID, it) }
                        } else {
                            container.groupRepository.observeGroupSongs(sourceId).map { members ->
                                val byId = songs.associateBy { it.id }
                                SourceContent(sourceId, members.mapNotNull { byId[it.songId] })
                            }
                        }
                    }
            }
        }

    val uiState: StateFlow<LibraryUiState> = combine(
        sourceContent,
        container.libraryRepository.state,
        permissionGranted,
        removedSongIds,
    ) { content, state, granted, removed ->
        LibraryUiState(
            songs = content.songs.filterNot { it.id in removed },
            loadState = state,
            hasPermission = granted,
            sourceGroupId = content.sourceId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val playback: StateFlow<PlaybackUiState> = container.playerConnection.state

    /** 供“加入分组”弹窗使用的分组列表。 */
    val groups: StateFlow<List<Group>> = container.groupRepository.groups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 音量渐变开关，默认开启，改动即时写入 DataStore。 */
    val fadeEnabled: StateFlow<Boolean> = container.prefsRepository.fadeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setFadeEnabled(enabled: Boolean) {
        viewModelScope.launch { container.prefsRepository.setFadeEnabled(enabled) }
    }

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
     * 以当前展示的列表为队列，从点击的歌曲开始播放，并沿用当前来源。
     */
    fun playSong(song: Song) {
        val state = uiState.value
        val index = state.songs.indexOfFirst { it.id == song.id }
        if (index < 0) return
        container.playerConnection.playQueue(state.songs, index, state.sourceGroupId)
    }

    fun togglePlayPause() = container.playerConnection.togglePlayPause()

    fun skipToNext() = container.playerConnection.skipToNext()

    fun clearPlaybackError() = container.playerConnection.clearError()
}
