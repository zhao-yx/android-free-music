package com.local.musicplayer.ui.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.local.musicplayer.MusicApplication
import com.local.musicplayer.data.model.Group
import com.local.musicplayer.data.model.Song
import com.local.musicplayer.playback.PlaybackUiState
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
)

class GroupListViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MusicApplication).container
    private val repository = container.groupRepository

    val playback: StateFlow<PlaybackUiState> = container.playerConnection.state

    val uiState: StateFlow<GroupListUiState> = combine(
        repository.groups,
        repository.groupCounts,
    ) { groups, counts ->
        GroupListUiState(
            groups = groups.map { GroupWithCount(it, counts[it.id] ?: 0) },
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
)

class GroupDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val container = (application as MusicApplication).container
    private val repository = container.groupRepository
    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: -1L

    val playback: StateFlow<PlaybackUiState> = container.playerConnection.state

    val uiState: StateFlow<GroupDetailUiState> = combine(
        repository.observeGroup(groupId),
        repository.observeGroupSongs(groupId),
        container.libraryRepository.songs,
    ) { group, members, allSongs ->
        // 分组内顺序由 position 决定；媒体库里已不存在的歌曲先由刷新逻辑清理再展示
        val byId = allSongs.associateBy { it.id }
        GroupDetailUiState(
            groupName = group?.name ?: "分组",
            groupExists = group != null,
            songs = members.mapNotNull { byId[it.songId] },
            allSongs = allSongs,
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
