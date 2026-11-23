package com.alan.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 分组下的歌曲数量，用于分组列表展示。 */
data class GroupSongCount(
    val groupId: Long,
    val count: Int,
)

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY sortOrder ASC, createdAt ASC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun observeGroup(groupId: Long): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroup(groupId: Long): GroupEntity?

    @Query("SELECT * FROM groups ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getGroups(): List<GroupEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM groups")
    suspend fun maxGroupSortOrder(): Int

    @Insert
    suspend fun insertGroup(group: GroupEntity): Long

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("SELECT * FROM group_songs WHERE groupId = :groupId ORDER BY position ASC")
    fun observeGroupSongs(groupId: Long): Flow<List<GroupSongEntity>>

    @Query("SELECT * FROM group_songs WHERE groupId = :groupId ORDER BY position ASC")
    suspend fun getGroupSongs(groupId: Long): List<GroupSongEntity>

    @Query("SELECT * FROM group_songs ORDER BY groupId ASC, position ASC")
    suspend fun getAllGroupSongs(): List<GroupSongEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM group_songs WHERE groupId = :groupId")
    suspend fun maxPosition(groupId: Long): Int

    @Query("SELECT groupId AS groupId, COUNT(*) AS count FROM group_songs GROUP BY groupId")
    fun observeGroupCounts(): Flow<List<GroupSongCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupSong(entity: GroupSongEntity): Long

    @Query("DELETE FROM group_songs WHERE groupId = :groupId AND songId = :songId")
    suspend fun deleteGroupSong(groupId: Long, songId: Long)

    @Query("DELETE FROM group_songs WHERE groupId = :groupId")
    suspend fun deleteSongsOfGroup(groupId: Long)

    @Query("UPDATE group_songs SET position = :position WHERE groupId = :groupId AND songId = :songId")
    suspend fun updatePosition(groupId: Long, songId: Long, position: Int)
}
