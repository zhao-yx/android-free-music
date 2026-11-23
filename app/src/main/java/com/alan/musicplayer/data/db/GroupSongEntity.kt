package com.alan.musicplayer.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * 分组与歌曲的多对多关系。
 *
 * 联合主键 (groupId, songId) 保证同一首歌在一个分组内只出现一次；
 * uri 作为歌曲被媒体库重新索引（_ID 变化）后的兜底匹配依据。
 */
@Entity(
    tableName = "group_songs",
    primaryKeys = ["groupId", "songId"],
    indices = [
        Index(value = ["groupId", "position"]),
        Index(value = ["songId"]),
    ],
)
data class GroupSongEntity(
    val groupId: Long,
    val songId: Long,
    val position: Int,
    val uri: String,
)
