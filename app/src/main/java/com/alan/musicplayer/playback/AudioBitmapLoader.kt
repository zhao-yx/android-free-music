package com.alan.musicplayer.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.alan.musicplayer.data.media.AudioArtworkFetcher
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/**
 * Media3 通知栏/锁屏封面加载器：从本地音频文件读取内嵌封面。
 * 只在后台线程解析，并按 uri 做内存缓存，避免通知刷新时重复读文件。
 */
@OptIn(UnstableApi::class)
class AudioBitmapLoader(context: Context) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("audio/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        return if (bitmap != null) {
            Futures.immediateFuture(bitmap)
        } else {
            Futures.immediateFailedFuture(IllegalArgumentException("封面数据无法解码"))
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        cache.get(uri.toString())?.let { return Futures.immediateFuture(it) }
        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            val bitmap = AudioArtworkFetcher.decodeEmbeddedPicture(appContext, uri, NOTIFICATION_ARTWORK_SIZE)
            if (bitmap != null) {
                cache.put(uri.toString(), bitmap)
                future.set(bitmap)
            } else {
                future.setException(FileNotFoundException("该音频没有内嵌封面"))
            }
        }
        return future
    }

    private companion object {
        /** 通知栏封面按 1024 像素采样，足够清晰且不至于占用过多内存。 */
        const val NOTIFICATION_ARTWORK_SIZE = 1024
    }
}
