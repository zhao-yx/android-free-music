package com.alan.musicplayer.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil 默认只会把 content:// 当图片解码，本地音乐的内嵌封面需要特殊处理：
 * 用 MediaMetadataRetriever 读出音频文件里的封面字节再交给 Coil 缓存。
 * 仅拦截音频 content uri，其它请求返回 null 交回 Coil 默认流程。
 */
class AudioArtworkFetcher(
    private val data: String,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        // Coil 2.7 的 Size 由 Dimension 描述，pxOrElse 在尺寸未知时返回兜底值
        val width = options.size.width.pxOrElse { 0 }
        val height = options.size.height.pxOrElse { 0 }
        val targetSize = if (width > 0 && height > 0) minOf(width, height) else 0
        val bitmap = decodeEmbeddedPicture(options.context, data.toUri(), targetSize)
            ?: return@withContext null
        DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.startsWith("content://") || !isAudioUri(context, data)) return null
            return AudioArtworkFetcher(data, options)
        }
    }

    companion object {
        private fun isAudioUri(context: Context, data: String): Boolean {
            val mime = runCatching { context.contentResolver.getType(data.toUri()) }.getOrNull()
            return mime?.startsWith("audio/") == true
        }

        /**
         * 读取音频内嵌封面并按目标尺寸做采样。
         * targetSize <= 0 表示按原图解码。
         */
        fun decodeEmbeddedPicture(context: Context, uri: Uri, targetSize: Int): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                val bytes = retriever.embeddedPicture ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetSize)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

        private fun calculateInSampleSize(width: Int, height: Int, target: Int): Int {
            if (width <= 0 || height <= 0 || target <= 0) return 1
            var sample = 1
            while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
                sample *= 2
            }
            return sample
        }
    }
}
