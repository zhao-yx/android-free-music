package com.alan.musicplayer.data.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.alan.musicplayer.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SAF 目录中的一个音频文件。 */
data class FolderAudio(
    val name: String,
    val size: Long,
    /** 由 documentId 推导出的绝对路径；无法推导时为 null。 */
    val path: String?,
)

/** 一次目录导入的匹配结果。 */
data class FolderImportPlan(
    /** 命中音乐库且不在分组内的歌曲 id，已按文件名排序。 */
    val matchedIds: List<Long>,
    val duplicateCount: Int,
    val unmatchedNames: List<String>,
    val totalAudioFiles: Int,
)

/**
 * 通过 SAF 目录读取音频并与系统媒体库匹配。
 * 只读：仅列出文件，不打开、不复制、不修改。
 */
object FolderImporter {

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "amr",
        "mid", "midi", "oga", "mka", "mp4", "3gp",
    )

    /** 记住目录授权，避免每次都重新授权。 */
    fun persistPermission(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    suspend fun buildPlan(
        resolver: ContentResolver,
        treeUri: Uri,
        librarySongs: List<Song>,
        existingGroupSongIds: Set<Long>,
    ): FolderImportPlan = withContext(Dispatchers.IO) {
        val files = collectAudioFiles(resolver, treeUri)
        val (byPath, byNameAndSize) = readMediaStoreIndex(resolver)
        val libraryIds = librarySongs.mapTo(HashSet()) { it.id }

        val matched = LinkedHashSet<Long>()
        var duplicates = 0
        val unmatched = mutableListOf<String>()

        files.sortedBy { it.name.lowercase() }.forEach { file ->
            val mediaId = file.path?.let { byPath[normalizePath(it)] }
                ?: byNameAndSize[nameSizeKey(file.name, file.size)]
            when {
                mediaId == null || mediaId !in libraryIds -> unmatched += file.name
                mediaId in existingGroupSongIds -> duplicates++
                !matched.add(mediaId) -> duplicates++
            }
        }
        FolderImportPlan(
            matchedIds = matched.toList(),
            duplicateCount = duplicates,
            unmatchedNames = unmatched,
            totalAudioFiles = files.size,
        )
    }

    /** 递归读取目录内（含子目录）的音频文件。 */
    private fun collectAudioFiles(resolver: ContentResolver, treeUri: Uri): List<FolderAudio> {
        val result = mutableListOf<FolderAudio>()
        val visited = mutableSetOf<String>()

        fun walk(documentId: String, depth: Int) {
            if (depth > MAX_DEPTH) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1).orEmpty()
                    val mime = cursor.getString(2).orEmpty()
                    val size = cursor.getLong(3)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (visited.add(childId)) walk(childId, depth + 1)
                    } else if (isAudio(mime, name)) {
                        result += FolderAudio(name, size, docIdToPath(childId))
                    }
                }
            }
        }

        walk(DocumentsContract.getTreeDocumentId(treeUri), 0)
        return result
    }

    /** 读取媒体库索引：路径 -> id，以及 文件名+大小 -> id。 */
    private fun readMediaStoreIndex(
        resolver: ContentResolver,
    ): Pair<Map<String, Long>, Map<String, Long>> {
        val byPath = HashMap<String, Long>()
        val byNameAndSize = HashMap<String, Long>()
        runCatching {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DATA,
                ),
                "${MediaStore.Audio.Media.MIME_TYPE} LIKE ? OR ${MediaStore.Audio.Media.IS_MUSIC} != 0",
                arrayOf("audio/%"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: continue
                    val size = cursor.getLong(2)
                    val data = cursor.getString(3)
                    if (!data.isNullOrBlank()) byPath.putIfAbsent(normalizePath(data), id)
                    byNameAndSize.putIfAbsent(nameSizeKey(name, size), id)
                }
            }
        }
        return byPath to byNameAndSize
    }

    private fun isAudio(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    /** 把 SAF 的 documentId 还原成绝对路径，用于优先按路径匹配。 */
    private fun docIdToPath(documentId: String): String? {
        val separator = documentId.indexOf(':')
        if (separator <= 0) return null
        val volume = documentId.substring(0, separator)
        val relative = documentId.substring(separator + 1)
        if (relative.isBlank()) return null
        return when {
            volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$relative"
            volume.length >= 4 -> "/storage/$volume/$relative"
            else -> null
        }
    }

    private fun normalizePath(path: String): String = path.replace('\\', '/').lowercase()

    private fun nameSizeKey(name: String, size: Long): String = "${name.lowercase()}|$size"

    private const val MAX_DEPTH = 12
}
