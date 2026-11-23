package com.alan.musicplayer.data.model

/**
 * 用户自定义分组（歌单）。
 */
data class Group(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int,
)
