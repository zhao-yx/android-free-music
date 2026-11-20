package com.local.musicplayer.util

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.local.musicplayer.data.model.Song

/**
 * 队列构建的唯一入口：Song -> MediaItem。
 * 其它地方不允许自行拼 MediaItem，避免队列逻辑分叉。
 *
 * 注意：2000 首队列会整体通过 Binder 传给播放服务，MediaItem 里只放必要字段
 * （uri + 标题/歌手/封面），避免超出 Binder 事务大小限制。
 */
object MediaItemMapper {

    fun toMediaItem(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(song.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                // 本地音频没有独立封面文件，artworkUri 指向歌曲本身，
                // 由 AudioBitmapLoader（通知/锁屏）与 AudioArtworkFetcher（列表/播放页）解析内嵌封面
                .setArtworkUri(song.uri.toUri())
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    /** 从 MediaController 当前 MediaItem 还原 UI 需要的歌曲信息。 */
    fun fromMediaItem(item: MediaItem): Song? {
        val id = item.mediaId.toLongOrNull() ?: return null
        val metadata = item.mediaMetadata
        val uri = item.localConfiguration?.uri?.toString()
            ?: metadata.artworkUri?.toString()
            ?: ""
        return Song(
            id = id,
            uri = uri,
            title = metadata.title?.toString().orEmpty().ifBlank { "未知歌曲" },
            artist = metadata.artist?.toString().orEmpty().ifBlank { "未知艺术家" },
            album = "",
            albumId = 0L,
            durationMs = 0L,
            dateAdded = 0L,
        )
    }
}
