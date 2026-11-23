package com.alan.musicplayer.data.media

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.core.net.toUri
import com.alan.musicplayer.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 通过 MediaStore 扫描本地音乐。不遍历目录，扫描全程在 IO 线程执行。
 */
class MediaStoreScanner(private val context: Context) {

    /** 时长为 0 的歌曲用 MediaMetadataRetriever 兜底后的缓存，避免重复解析。 */
    private val durationCache = ConcurrentHashMap<Long, Long>()

    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
        )
        // 不能只认 IS_MUSIC：下载目录、录音、部分第三方下载器入库的音频会被标记为 0，
        // 只查 IS_MUSIC != 0 会让这些新歌永远进不了列表。
        val selection =
            "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' " +
                "OR ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = ArrayList<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeIndex)
                val displayName = cursor.getString(nameIndex).orEmpty()
                val dataPath = if (dataIndex >= 0) cursor.getString(dataIndex).orEmpty() else ""
                // 跳过系统提示音目录，避免铃声/闹钟污染歌曲列表
                if (isSystemSoundPath(dataPath)) continue
                // 过滤 ExoPlayer 无法解码的格式（APE / WMA / DSD 等），避免点击后崩溃或静音
                if (!isSupportedFormat(mimeType, displayName)) continue

                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                ).toString()
                val rawDuration = cursor.getLong(durationIndex)
                val duration = if (rawDuration > 0) rawDuration else resolveDuration(id, uri)
                val rawTitle = cursor.getString(titleIndex)?.trim().orEmpty()
                val artist = cursor.getString(artistIndex)?.trim().orEmpty()

                songs += Song(
                    id = id,
                    uri = uri,
                    title = rawTitle.ifBlank { displayName.substringBeforeLast('.').ifBlank { "未知歌曲" } },
                    artist = if (artist.isBlank() || artist == MediaStore.UNKNOWN_STRING) "未知艺术家" else artist,
                    album = cursor.getString(albumIndex)?.trim().orEmpty().ifBlank { "未知专辑" },
                    albumId = cursor.getLong(albumIdIndex),
                    durationMs = duration,
                    dateAdded = cursor.getLong(dateAddedIndex),
                )
            }
        }
        songs
    }

    /** MediaStore 时长字段可能为 0，用 MediaMetadataRetriever 兜底并缓存。 */
    private fun resolveDuration(id: Long, uri: String): Long {
        durationCache[id]?.let { return it }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri.toUri())
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            if (duration > 0) durationCache[id] = duration
            duration
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        /** Media3 明确无法解码、需要排除的格式。 */
        private val UNSUPPORTED_MIME_TYPES = setOf(
            "audio/ape", "audio/x-ape", "audio/x-monkeys-audio",
            "audio/x-ms-wma", "audio/x-wma",
            "audio/wavpack", "audio/x-wavpack",
            "audio/x-dsd", "audio/dsd",
        )

        private val UNSUPPORTED_EXTENSIONS = setOf(
            "ape", "wma", "wv", "dsf", "dff", "tak", "tta",
        )

        private val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "flac", "ogg", "opus", "wav", "amr", "3gp", "mid", "midi",
        )

        /** 系统提示音目录，避免铃声/通知音/闹钟进入歌曲列表。 */
        private val SYSTEM_SOUND_DIRS = listOf(
            "/ringtones/", "/notifications/", "/alarms/", "/system/media/audio/",
        )

        fun isSystemSoundPath(path: String): Boolean {
            if (path.isBlank()) return false
            val lower = path.lowercase()
            return SYSTEM_SOUND_DIRS.any { lower.contains(it) }
        }

        /**
         * 采用“黑名单 + 宽进”策略：只排除确定解不了的格式，其余 audio 类型一律放行。
         * 之前用白名单会把某些正常音频（如 audio/mp4a-latm、audio/x-m4a、audio/mpeg3）
         * 静默丢弃，表现为“下载了新歌但永远扫不到”。
         */
        fun isSupportedFormat(mimeType: String?, displayName: String): Boolean {
            val mime = mimeType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
            val extension = displayName.substringAfterLast('.', "").lowercase()
            if (mime in UNSUPPORTED_MIME_TYPES) return false
            if (extension in UNSUPPORTED_EXTENSIONS) return false
            if (mime.startsWith("audio/")) return true
            return extension in SUPPORTED_EXTENSIONS
        }
    }
}
