package com.alan.musicplayer.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alan.musicplayer.data.model.Skin
import com.alan.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_prefs")

/**
 * 播放偏好与进度持久化（DataStore）。
 * 队列以 JSON 形式保存，进程被杀后重进可以恢复歌曲、进度、模式与队列。
 */
class PrefsRepository(private val context: Context) {

    private object Keys {
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val LAST_SONG_ID = longPreferencesKey("last_song_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val LAST_QUEUE_GROUP_ID = longPreferencesKey("last_queue_group_id")
        val LAST_QUEUE_INDEX = intPreferencesKey("last_queue_index")
        val LAST_QUEUE_JSON = stringPreferencesKey("last_queue_json")
        val SKIN = intPreferencesKey("skin")
    }

    data class Snapshot(
        val repeatMode: Int,
        val lastSongId: Long,
        val lastPositionMs: Long,
        val queueGroupId: Long,
        val queueIndex: Int,
        val queue: List<Song>,
    )

    val repeatMode: Flow<Int> = context.playerDataStore.data.map { it[Keys.REPEAT_MODE] ?: 0 }

    /** 当前皮肤，默认经典黑（沿用旧版视觉）。 */
    val skin: Flow<Skin> = context.playerDataStore.data.map {
        Skin.fromValue(it[Keys.SKIN] ?: Skin.CLASSIC_DARK.storageValue)
    }

    suspend fun setSkin(skin: Skin) {
        context.playerDataStore.edit { it[Keys.SKIN] = skin.storageValue }
    }

    suspend fun setRepeatMode(mode: Int) {
        context.playerDataStore.edit { it[Keys.REPEAT_MODE] = mode }
    }

    /** 记录当前歌曲与进度（节流后由 PlayerConnection 周期性调用）。 */
    suspend fun savePlaybackPosition(songId: Long, positionMs: Long) {
        context.playerDataStore.edit {
            it[Keys.LAST_SONG_ID] = songId
            it[Keys.LAST_POSITION_MS] = positionMs
        }
    }

    /** 用户点歌构建新队列时保存队列快照与来源分组。 */
    suspend fun saveQueue(groupId: Long, index: Int, songs: List<Song>) {
        context.playerDataStore.edit {
            it[Keys.LAST_QUEUE_GROUP_ID] = groupId
            it[Keys.LAST_QUEUE_INDEX] = index
            it[Keys.LAST_QUEUE_JSON] = encodeSongs(songs)
        }
    }

    suspend fun readSnapshot(): Snapshot {
        val prefs = context.playerDataStore.data.first()
        return Snapshot(
            repeatMode = prefs[Keys.REPEAT_MODE] ?: 0,
            lastSongId = prefs[Keys.LAST_SONG_ID] ?: -1L,
            lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
            queueGroupId = prefs[Keys.LAST_QUEUE_GROUP_ID] ?: ALL_SONGS_GROUP_ID,
            queueIndex = prefs[Keys.LAST_QUEUE_INDEX] ?: 0,
            queue = decodeSongs(prefs[Keys.LAST_QUEUE_JSON].orEmpty()),
        )
    }

    private fun encodeSongs(songs: List<Song>): String {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("uri", song.uri)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("albumId", song.albumId)
                    put("duration", song.durationMs)
                    put("dateAdded", song.dateAdded)
                },
            )
        }
        return array.toString()
    }

    private fun decodeSongs(json: String): List<Song> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        Song(
                            id = item.getLong("id"),
                            uri = item.getString("uri"),
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            album = item.optString("album"),
                            albumId = item.optLong("albumId"),
                            durationMs = item.optLong("duration"),
                            dateAdded = item.optLong("dateAdded"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        /** -1 表示队列来源是全部歌曲。 */
        const val ALL_SONGS_GROUP_ID = -1L
    }
}
