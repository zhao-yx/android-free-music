package com.local.musicplayer.playback

import com.local.musicplayer.data.model.PlayMode
import com.local.musicplayer.data.model.Song

/**
 * UI 消费的播放状态快照，由 PlayerConnection 从 MediaController 同步而来。
 */
data class PlaybackUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentSong: Song? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val errorMessage: String? = null,
)
