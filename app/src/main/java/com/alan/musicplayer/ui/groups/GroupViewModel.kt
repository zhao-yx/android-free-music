package com.alan.musicplayer.ui.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.alan.musicplayer.MusicApplication
import com.alan.musicplayer.data.model.Group
import com.alan.musicplayer.data.model.Song
import com.alan.musicplayer.data.repo.PrefsRepository
import com.alan.musicplayer.playback.PlaybackUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupWithCount(
    val group: Group,
    val songCount: Int,
)

data class GroupListUiState(
    val groups: List<GroupWithCount> = emptyList(),
    val allSongCount: Int = 0,
)

class GroupListViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MusicApplication).container
    private val repository = container.groupRepository

    val playback: StateFlow<PlaybackUiState> = container.playerConnection.state

    val uiState: StateFlow<GroupListUiState> = combine(
        repository.groups,
        repository.groupCounts,
        container.libraryRepository.songs,
    ) { groups, counts, songs ->
        GroupListUiState(
            groups = groups.map { GroupWithCount(it, counts[it.id] ?: 0) },
            allSongCount = songs.size,
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
)

class GroupDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val container = (application as MusicApplication).container
    private val repository = container.groupRepository
    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: -1L
    private val isAllSongs = groupId == PrefsRepository.ALL_SONGS_GROUP_ID

    val playback: StateFlow<PlaybackUiState> = container.playerConnection.state

    val uiState: StateFlow<GroupDetailUiState> = combine(
        repository.observeGroup(groupId),
        repository.observeGroupSongs(groupId),
        container.libraryRepository.songs,
    ) { group, members, allSongs ->
        // 分组内顺序由 position 决定；媒体库里已不存在的歌曲先由刷新逻辑清理再展示
        val byId = allSongs.associateBy { it.id }
        GroupDetailUiState(
            groupName = if (isAllSongs) "全部分组" else group?.name ?: "分组",
            groupExists = isAllSongs || group != null,
            songs = if (isAllSongs) allSongs else members.mapNotNull { byId[it.songId] },
            allSongs = allSongs,
            isAllSongs = isAllSongs,
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
