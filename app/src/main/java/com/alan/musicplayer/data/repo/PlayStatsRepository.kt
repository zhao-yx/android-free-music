package com.alan.musicplayer.data.repo

import com.alan.musicplayer.data.db.AppDatabase
import com.alan.musicplayer.data.db.PlayStatEntity
import androidx.room.withTransaction

/**
 * 播放统计仓库：为「心动歌单」提供数据，独立于用户分组数据。
 */
class PlayStatsRepository(private val db: AppDatabase) {

    private val dao = db.playStatDao()

    /** 歌曲开始播放时调用：次数 +1，并刷新最近播放时间。 */
    suspend fun recordPlay(songId: Long) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = dao.get(songId)
            dao.upsert(
                existing?.copy(playCount = existing.playCount + 1, lastPlayedAt = now)
                    ?: PlayStatEntity(songId = songId, playCount = 1, lastPlayedAt = now),
            )
            // 只保留最近活跃的统计行，避免长期积累无限增长
            dao.trimTo(MAX_ROWS)
        }
    }

    /** 启动时读取一次，用于固定本次运行期间的心动歌单榜单。 */
    suspend fun snapshotHeartIds(): List<Long> = dao.topSongIds(HEART_LIMIT)

    companion object {
        /** 心动歌单上限。 */
        const val HEART_LIMIT = 20

        /** 统计表最多保留的歌曲行数，超出按最近播放时间淘汰。 */
        const val MAX_ROWS = 500
    }
}
