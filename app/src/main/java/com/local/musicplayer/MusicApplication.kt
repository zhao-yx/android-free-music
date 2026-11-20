package com.local.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.local.musicplayer.data.AppContainer
import com.local.musicplayer.data.media.AudioArtworkFetcher

class MusicApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 进程启动即连接播放服务，保证界面退出后播放不中断、杀进程后能恢复状态
        container.playerConnection.connect()
    }

    /** 注册本地音频内嵌封面的 Coil 解析器。 */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(AudioArtworkFetcher.Factory(this@MusicApplication)) }
        .crossfade(true)
        .build()
}
