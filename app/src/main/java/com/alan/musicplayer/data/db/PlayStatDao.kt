package com.alan.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayStatDao {

    @Query("SELECT * FROM play_stats WHERE songId = :songId")
    suspend fun get(songId: Long): PlayStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: PlayStatEntity)

    /** 心动歌单定榜：次数降序，次数相同按最近播放时间降序。 */
    @Query("SELECT songId FROM play_stats ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    suspend fun topSongIds(limit: Int): List<Long>

    /** 只保留最近播放的若干行，防止统计表无限增长。 */
    @Query(
        "DELETE FROM play_stats WHERE songId NOT IN " +
            "(SELECT songId FROM play_stats ORDER BY lastPlayedAt DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)
}
