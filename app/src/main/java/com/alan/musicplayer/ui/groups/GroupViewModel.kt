package com.alan.musicplayer.ui.groups

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.alan.musicplayer.MusicApplication
import com.alan.musicplayer.data.media.FolderImporter
import com.alan.musicplayer.data.model.Group
import com.alan.musicplayer.data.model.Song
import com.alan.musicplayer.data.repo.PrefsRepository
import com.alan.musicplayer.playback.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupWithCount(
    val group: Group,
    val songCount: Int,
)

/** 「心动歌单」固定哨兵 id，仅用于导航，不写入分组数据表。 */
const val HEART_PLAYLIST_ID = -2L

data class GroupListUiState(
    val groups: List<GroupWithCount> = emptyList(),
    val allSongCount: Int = 0,
    val heartSongCount: Int = 0,
)

class GroupListViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MusicApplication).container
    private val repository = container.groupRepository

    val playback: StateFlow<PlaybackUiState> = container.playerConnection.state

    val uiState: StateFlow<GroupListUiState> = combine(
        repository.groups,
        repository.groupCounts,
        container.libraryRepository.songs,
        container.heartPlaylistIds,
    ) { groups, counts, songs, heartIds ->
        val libraryIds = songs.mapTo(HashSet()) { it.id }
        GroupListUiState(
            groups = groups.map { GroupWithCount(it, counts[it.id] ?: 0) },
            allSongCount = songs.size,
            heartSongCount = heartIds.count { it in libraryIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupListUiState())

    fun createGroup(name: String) {
        viewModelScope.launch { repository.createGroup(name) }
    }

    fun renameGroup(groupId: Long, name: String) {
        viewModelScope.launch { repository.renameGroup(groupId, name) }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch { repository.deleteGroup(groupId) }
    }

    /** 目录导入结果提示，按顺序逐条展示。 */
    private val _importMessages = MutableStateFlow<List<String>>(emptyList())
    val importMessages: StateFlow<List<String>> = _importMessages.asStateFlow()

    /**
     * 从 SAF 目录导入歌曲到分组：只读目录，按文件名顺序批量追加；
     * 已在本组或不在媒体库的文件只统计、不导入。
     */
    fun importFolder(treeUri: Uri, group: Group) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            FolderImporter.persistPermission(app, treeUri)
            try {
                val library = container.libraryRepository.songs.first()
                val existing = repository.observeGroupSongs(group.id).first()
                    .mapTo(HashSet()) { it.songId }
                val plan = FolderImporter.buildPlan(
                    resolver = app.contentResolver,
                    treeUri = treeUri,
                    librarySongs = library,
                    existingGroupSongIds = existing,
                )
                val byId = library.associateBy { it.id }
                val songs = plan.matchedIds.mapNotNull { byId[it] }
                if (songs.isNotEmpty()) repository.addSongs(group.id, songs)

                val messages = mutableListOf<String>()
                if (plan.totalAudioFiles == 0) {
                    messages += "所选文件夹中没有找到音频文件"
                } else {
                    messages += "已导入 ${songs.size} 首，跳过 ${plan.duplicateCount} 首（重复）"
                    if (plan.unmatchedNames.isNotEmpty()) {
                        val preview = plan.unmatchedNames.take(3).joinToString("、")
                        val suffix = if (plan.unmatchedNames.size > 3) " 等" else ""
                        messages += "有 ${plan.unmatchedNames.size} 个文件不在音乐库中：$preview$suffix。" +
                            "请先让系统媒体库索引后再试"
                    }
                }
                _importMessages.value = messages
            } catch (e: SecurityException) {
                _importMessages.value = listOf("没有读取该文件夹的权限，请重新选择目录")
            } catch (e: Exception) {
                _importMessages.value = listOf("读取文件夹失败，请确认该目录可访问后重试")
            }
        }
    }

    fun clearImportMessages() {
        _importMessages.value = emptyList()
    }

    fun togglePlayPause() = container.playerConnection.togglePlayPause()

    fun skipToNext() = container.playerConnection.skipToNext()
}

data class GroupDetailUiState(
    val groupName: String = "",
    val groupExists: Boolean = true,
    val songs: List<Song> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    /** 「全部分组」为只读聚合视图，不参与分组数据表。 */
    val isAllSongs: Boolean = false,
    /** 「心动歌单」为系统自动维护的只读歌单。 */
    val isHeartPlaylist: Boolean = false,
) {
    /** 固定条目（全部分组 / 心动歌单）均为只读。 */
    val isReadOnly: Boolean get() = isAllSongs || isHeartPlaylist
}

class GroupDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val container = (application as MusicApplication).container
    private val repository = container.groupRepository
    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: -1L
    private val isAllSongs = groupId == PrefsRepository.ALL_SONGS_GROUP_ID
    private val isHeartPlaylist = groupId == HEART_PLAYLIST_ID

    val playback: StateFlow<PlaybackUiState> = container.playerConnection.state

    val uiState: StateFlow<GroupDetailUiState> = combine(
        repository.observeGroup(groupId),
        repository.observeGroupSongs(groupId),
        container.libraryRepository.songs,
        container.heartPlaylistIds,
    ) { group, members, allSongs, heartIds ->
        // 分组内顺序由 position 决定；媒体库里已不存在的歌曲先由刷新逻辑清理再展示
        val byId = allSongs.associateBy { it.id }
        val heartSongs = heartIds.mapNotNull { byId[it] }
        GroupDetailUiState(
            groupName = when {
                isAllSongs -> "全部歌曲"
                isHeartPlaylist -> "心动歌单"
                else -> group?.name ?: "分组"
            },
            groupExists = isAllSongs || isHeartPlaylist || group != null,
            songs = when {
                isAllSongs -> allSongs
                isHeartPlaylist -> heartSongs
                else -> members.mapNotNull { byId[it.songId] }
            },
            allSongs = allSongs,
            isAllSongs = isAllSongs,
            isHeartPlaylist = isHeartPlaylist,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState())

    fun addSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch { repository.addSongs(groupId, songs) }
    }

    fun removeSong(song: Song) {
        viewModelScope.launch { repository.removeSong(groupId, song.id) }
    }

    fun removeSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch { repository.removeSongs(groupId, songs.map { it.id }) }
    }

    fun moveUp(song: Song) {
        viewModelScope.launch { repository.moveSong(groupId, song.id, -1) }
    }

    fun moveDown(song: Song) {
        viewModelScope.launch { repository.moveSong(groupId, song.id, 1) }
    }

    /** 点击分组内歌曲：以分组顺序构建队列播放。 */
    fun playSong(song: Song) {
        val songs = uiState.value.songs
        val index = songs.indexOfFirst { it.id == song.id }
        if (index < 0) return
        container.playerConnection.playQueue(songs, index, groupId)
    }

    fun togglePlayPause() = container.playerConnection.togglePlayPause()

    fun skipToNext() = container.playerConnection.skipToNext()
}
