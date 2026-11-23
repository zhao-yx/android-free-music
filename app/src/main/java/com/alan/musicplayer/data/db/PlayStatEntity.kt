package com.alan.musicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单曲播放统计：心动歌单的唯一数据来源，与用户分组表完全独立。
 * 每首歌一行，播放时自增次数并刷新最近播放时间。
 */
@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int,
    val lastPlayedAt: Long,
)
