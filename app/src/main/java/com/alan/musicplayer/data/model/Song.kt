package com.alan.musicplayer.data.model

/**
 * 歌曲元数据，来自 MediaStore 运行时查询，不落库（只存引用，避免与媒体库不同步）。
 */
data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long,
)
