package com.alan.musicplayer.data.repo

import androidx.room.withTransaction
import com.alan.musicplayer.data.db.AppDatabase
import com.alan.musicplayer.data.db.GroupEntity
import com.alan.musicplayer.data.db.GroupSongEntity
import com.alan.musicplayer.data.model.Group
import com.alan.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 分组数据仓库：分组的增删改、歌曲与分组的关联、组内排序，以及扫描后的孤儿清理。
 */
class GroupRepository(private val db: AppDatabase) {

    private val dao = db.groupDao()

    val groups: Flow<List<Group>> = dao.observeGroups().map { list ->
        list.map { Group(it.id, it.name, it.createdAt, it.sortOrder) }
    }

    val groupCounts: Flow<Map<Long, Int>> = dao.observeGroupCounts().map { counts ->
        counts.associate { it.groupId to it.count }
    }

    fun observeGroup(groupId: Long): Flow<Group?> = dao.observeGroup(groupId).map { entity ->
        entity?.let { Group(it.id, it.name, it.createdAt, it.sortOrder) }
    }

    /** 分组内的歌曲引用，按 position 升序。 */
    fun observeGroupSongs(groupId: Long): Flow<List<GroupSongEntity>> = dao.observeGroupSongs(groupId)

    suspend fun createGroup(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return dao.insertGroup(
            GroupEntity(
                name = trimmed,
                createdAt = System.currentTimeMillis(),
                sortOrder = dao.maxGroupSortOrder() + 1,
            ),
        )
    }

    suspend fun renameGroup(groupId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val group = dao.getGroup(groupId) ?: return
        dao.updateGroup(group.copy(name = trimmed))
    }

    /** 删除分组只删关联关系，不删除歌曲文件。 */
    suspend fun deleteGroup(groupId: Long) {
        db.withTransaction {
            dao.deleteSongsOfGroup(groupId)
            dao.deleteGroup(groupId)
        }
    }

    /** 批量添加：一次事务内按顺序追加，避免多次写库与位置冲突。 */
    suspend fun addSongs(groupId: Long, songs: List<Song>) {
        if (songs.isEmpty()) return
        db.withTransaction {
            var position = dao.maxPosition(groupId) + 1
            songs.forEach { song ->
                dao.insertGroupSong(GroupSongEntity(groupId, song.id, position, song.uri))
                position++
            }
        }
    }

    /** 加入单个分组；返回 false 表示该歌曲已在此分组内。 */
    suspend fun addSongToGroup(groupId: Long, song: Song): Boolean = db.withTransaction {
        val exists = dao.getGroupSongs(groupId).any { it.songId == song.id }
        if (exists) return@withTransaction false
        dao.insertGroupSong(
            GroupSongEntity(groupId, song.id, dao.maxPosition(groupId) + 1, song.uri),
        )
        true
    }

    suspend fun removeSong(groupId: Long, songId: Long) {
        db.withTransaction {
            dao.deleteGroupSong(groupId, songId)
            reindex(groupId)
        }
    }

    /** 批量移出分组，只删关联关系，不删除歌曲文件。 */
    suspend fun removeSongs(groupId: Long, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        db.withTransaction {
            songIds.forEach { dao.deleteGroupSong(groupId, it) }
            reindex(groupId)
        }
    }

    /** 组内上移/下移；direction 为 -1 上移、1 下移。 */
    suspend fun moveSong(groupId: Long, songId: Long, direction: Int) {
        db.withTransaction {
            val members = dao.getGroupSongs(groupId)
            val index = members.indexOfFirst { it.songId == songId }
            val targetIndex = index + direction
            if (index < 0 || targetIndex !in members.indices) return@withTransaction
            val current = members[index]
            val target = members[targetIndex]
            dao.updatePosition(groupId, current.songId, target.position)
            dao.updatePosition(groupId, target.songId, current.position)
        }
    }

    /**
     * 扫描完成后同步分组数据：
     * 1. songId 仍存在的记录保留；
     * 2. songId 不存在但 uri 能匹配到的，说明媒体库重建导致 _ID 变化，用新 id 重建关联；
     * 3. 两者都匹配不到的是孤儿记录，直接删除。
     */
    suspend fun syncWithMediaStore(songs: List<Song>) {
        val byId = songs.associateBy { it.id }
        val byUri = songs.associateBy { it.uri }
        db.withTransaction {
            val all = dao.getAllGroupSongs()
            all.forEach { ref ->
                if (byId.containsKey(ref.songId)) return@forEach
                val matched = byUri[ref.uri]
                if (matched != null) {
                    dao.deleteGroupSong(ref.groupId, ref.songId)
                    dao.insertGroupSong(
                        GroupSongEntity(ref.groupId, matched.id, ref.position, matched.uri),
                    )
                } else {
                    dao.deleteGroupSong(ref.groupId, ref.songId)
                }
            }
        }
    }

    private suspend fun reindex(groupId: Long) {
        dao.getGroupSongs(groupId).forEachIndexed { index, entity ->
            if (entity.position != index) dao.updatePosition(groupId, entity.songId, index)
        }
    }
}
