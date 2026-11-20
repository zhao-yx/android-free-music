package com.local.musicplayer.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.local.musicplayer.MainActivity
import com.local.musicplayer.R

/**
 * 播放前台服务：ExoPlayer + MediaSession。
 *
 * 通知栏、锁屏控制、耳机/蓝牙按键、音频焦点全部由 Media3 接管；
 * 服务与 Activity 生命周期解耦，退出界面后仍继续播放。
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var noisyReceiver: BroadcastReceiver? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            // handleAudioFocus = true：音频焦点交给 ExoPlayer，来电/其它 App 播放时自动暂停或降低音量，
            // 焦点永久丢失（AUDIOFOCUS_LOSS）时也会主动暂停
            .setAudioAttributes(audioAttributes, true)
            // 本地 wake lock，保证锁屏、切后台长时间播放不因 CPU 休眠中断
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player = exoPlayer

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            // 通知栏/锁屏封面：从本地音频文件内嵌封面解析
            .setBitmapLoader(AudioBitmapLoader(this))
            .build()

        // 通知栏小图标：使用项目内的纯白音符矢量，替换 Media3 默认的实心圆
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.ic_stat_music)
            },
        )

        registerNoisyReceiver()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        noisyReceiver?.let { runCatching { unregisterReceiver(it) } }
        noisyReceiver = null
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /** 耳机拔出 / 蓝牙断开时立即暂停播放。 */
    private fun registerNoisyReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    player?.pause()
                }
            }
        }
        noisyReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
