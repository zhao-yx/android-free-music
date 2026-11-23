package com.alan.musicplayer.data.repo

import com.alan.musicplayer.data.media.MediaStoreScanner
import com.alan.musicplayer.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LibraryState {
    data object Loading : LibraryState
    data object Success : LibraryState
    data object Empty : LibraryState
    data class Error(val message: String) : LibraryState
}

/**
 * 本地音乐库：MediaStore 扫描结果的内存缓存，供列表与分组页共用。
 */
class LibraryRepository(private val scanner: MediaStoreScanner) {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** 重新扫描媒体库；成功返回最新歌曲列表（供分组孤儿数据清理），失败返回 null。 */
    suspend fun refresh(): List<Song>? {
        _state.value = LibraryState.Loading
        return try {
            val scanned = scanner.scan()
            _songs.value = scanned
            _state.value = if (scanned.isEmpty()) LibraryState.Empty else LibraryState.Success
            scanned
        } catch (e: Exception) {
            _state.value = LibraryState.Error(e.message ?: "扫描失败，请重试")
            // 扫描失败时不能把空列表同步给分组，否则会误删分组里的歌曲引用
            null
        }
    }
}
