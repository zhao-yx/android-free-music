package com.alan.musicplayer.playback

import com.alan.musicplayer.data.model.PlayMode
import com.alan.musicplayer.data.model.Song

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
    /** 当前队列来源：分组 id、心动歌单哨兵 id，或 -1（全部歌曲）。 */
    val queueGroupId: Long = -1L,
)
