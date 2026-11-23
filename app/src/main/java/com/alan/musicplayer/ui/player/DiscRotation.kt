package com.alan.musicplayer.ui.player

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 碟片旋转状态（进程级）。
 *
 * 不使用 Compose 的无限动画（无法暂停/无法保存角度），而是用累积角度 StateFlow 驱动：
 * - 播放时按真实时间累加角度（20 秒 / 圈），暂停即停止累加，角度保持不变；
 * - 切歌时角度归零；
 * - 离开播放页再返回不会重置角度，因为状态保存在本单例里。
 */
object DiscRotationState {

    private const val DEGREES_PER_MS = 360f / 20_000f

    private val _angle = MutableStateFlow(0f)
    val angle: StateFlow<Float> = _angle.asStateFlow()

    private var job: Job? = null
    private var boundSongId: Long? = null

    fun sync(scope: CoroutineScope, songId: Long?, isPlaying: Boolean) {
        if (songId != boundSongId) {
            boundSongId = songId
            _angle.value = 0f
            stop()
        }
        if (isPlaying) start(scope) else stop()
    }

    private fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(16)
                val now = SystemClock.elapsedRealtime()
                _angle.value = (_angle.value + (now - lastTick) * DEGREES_PER_MS) % 360f
                lastTick = now
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
    }
}
