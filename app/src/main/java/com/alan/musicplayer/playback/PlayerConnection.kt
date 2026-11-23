package com.alan.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.alan.musicplayer.data.model.PlayMode
import com.alan.musicplayer.data.model.Song
import com.alan.musicplayer.data.repo.PrefsRepository
import com.alan.musicplayer.util.MediaItemMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI 与播放服务之间的唯一通道。
 *
 * 约束：UI 不直接持有 ExoPlayer，全部通过 MediaController 与 [PlaybackService] 通信；
 * 退出界面不会断开连接，播放由前台服务继续。
 */
class PlayerConnection(
    private val context: Context,
    private val prefs: PrefsRepository,
    private val scope: CoroutineScope,
    /** 歌曲开始播放时回调，用于心动歌单的播放统计。 */
    private val onSongStarted: (Long) -> Unit = {},
) {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionJob: Job? = null
    private var restoreStarted = false
    private var isReady = false
    private var lastPersistedAt = 0L

    /** 已计过数的歌曲，用于避免暂停恢复时重复计数；切歌/循环时清空。 */
    private var lastCountedSongId: Long? = null

    /** 音量渐变开关（来自 DataStore），切换时立即生效。 */
    private val fadeEnabled = MutableStateFlow(true)

    /** 当前正在执行的音量渐变，任意新渐变都会取消并接管它，避免叠加。 */
    private var fadeJob: Job? = null

    init {
        scope.launch {
            prefs.fadeEnabled.collect { enabled ->
                fadeEnabled.value = enabled
                if (!enabled) {
                    // 关闭渐变时立刻退出渐变状态，恢复到正常音量
                    fadeJob?.cancel()
                    controller?.volume = 1f
                }
            }
        }
    }

    private val listener = object : Player.Listener {
        // 所有播放器事件统一在这里同步成 UI 状态，避免多处状态分叉
        override fun onEvents(player: Player, events: Player.Events) {
            val previous = _state.value
            syncState(player)
            // 音量渐变：开始播放 / 从暂停恢复时淡入；外部原因暂停时退出渐变，避免音量卡在半途
            if (fadeEnabled.value) {
                if (player.isPlaying && !previous.isPlaying) {
                    player.volume = 0f
                    startFade(1f)
                } else if (!player.isPlaying && previous.isPlaying) {
                    fadeJob?.cancel()
                    player.volume = 0f
                }
            } else if (!player.isPlaying && previous.isPlaying) {
                player.volume = 1f
            }
            // 歌曲真正开始播放即计一次；同一首暂停再继续不重复计
            val playingId = player.currentMediaItem?.mediaId?.toLongOrNull()
            if (player.isPlaying && playingId != null && playingId != lastCountedSongId) {
                lastCountedSongId = playingId
                onSongStarted(playingId)
            }
            if (isReady) {
                val songId = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (songId != null && previous.isPlaying && !player.isPlaying) {
                    // 暂停/切歌时立即落盘进度（忽略节流），避免进程被杀丢进度
                    persistPosition(songId, player.currentPosition, force = true)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 换歌或单曲循环重播都视为新的一次播放
            lastCountedSongId = null
            // 自动切歌时无法提前淡出，至少让新曲目从 0 淡入
            if (fadeEnabled.value && controller?.isPlaying == true) {
                controller?.volume = 0f
                startFade(1f)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(errorMessage = friendlyError(error)) }
        }
    }

    /** 建立与播放服务的连接，进程内只执行一次。 */
    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.addListener(listener)
                        restoreState(mediaController)
                        startPositionPolling()
                    }
                    .onFailure {
                        controllerFuture = null
                        _state.update { it.copy(errorMessage = "播放服务连接失败，请重启应用") }
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * 以指定列表为队列，从 startIndex 开始播放。
     * 这是全应用唯一的队列构建入口，分组/全部歌曲的播放都走这里。
     */
    fun playQueue(songs: List<Song>, startIndex: Int, queueGroupId: Long) {
        val mediaController = controller ?: return
        if (songs.isEmpty()) return
        val items = songs.map(MediaItemMapper::toMediaItem)
        val index = startIndex.coerceIn(0, items.lastIndex)
        var failed = false
        val startPlayback = {
            // 渐变开启时先归零，真正的淡入由 isPlaying 变化统一触发
            mediaController.volume = if (fadeEnabled.value) 0f else 1f
            // 大列表整体通过 Binder 传递，异常时给出提示而不是崩溃
            runCatching {
                mediaController.setMediaItems(items, index, 0L)
                mediaController.prepare()
                mediaController.play()
            }.onFailure {
                failed = true
                _state.update { state -> state.copy(errorMessage = "队列设置失败，歌曲过多或服务异常") }
            }
        }
        if (fadeEnabled.value && mediaController.isPlaying) {
            // 正在播放时切到新队列：先淡出当前歌曲再切换
            fadeJob?.cancel()
            val from = mediaController.volume
            fadeJob = scope.launch {
                animateVolume(from, 0f)
                startPlayback()
            }
        } else {
            startPlayback()
        }
        if (!failed) _state.update { it.copy(errorMessage = null) }
        // 切换来源后主页列表立即跟随，无需等下次进入页面
        _state.update { it.copy(queueGroupId = queueGroupId) }
        scope.launch { prefs.saveQueue(queueGroupId, index, songs) }
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        when {
            mediaController.isPlaying -> pauseWithFade(mediaController)
            mediaController.playbackState == Player.STATE_ENDED -> {
                // 顺序播放到最后一首后，再次点击播放从队列开头继续
                mediaController.seekToDefaultPosition()
                prepareAndPlay(mediaController)
            }
            else -> prepareAndPlay(mediaController)
        }
    }

    /** 上一曲：随机模式下由 ExoPlayer 的 shuffle 历史回到实际播放过的上一首。 */
    fun skipToPrevious() {
        skipWithFade { controller?.seekToPrevious() }
    }

    fun skipToNext() {
        skipWithFade { controller?.seekToNext() }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** 模式按钮：顺序 -> 列表循环 -> 单曲循环 -> 随机 -> 顺序。 */
    fun cyclePlayMode() {
        val mediaController = controller ?: return
        val current = PlayMode.fromPlayerState(
            mediaController.repeatMode,
            mediaController.shuffleModeEnabled,
        )
        applyMode(mediaController, current.next())
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    // ---------------- 音量渐变 ----------------

    private fun prepareAndPlay(mediaController: MediaController) {
        // 先归零再播放，淡入由 isPlaying 变化统一触发，避免起播瞬间爆音
        mediaController.volume = if (fadeEnabled.value) 0f else 1f
        if (mediaController.playbackState == Player.STATE_IDLE) mediaController.prepare()
        mediaController.play()
    }

    private fun pauseWithFade(mediaController: MediaController) {
        if (!fadeEnabled.value) {
            mediaController.pause()
            return
        }
        fadeJob?.cancel()
        val from = mediaController.volume
        fadeJob = scope.launch {
            animateVolume(from, 0f)
            mediaController.pause()
        }
    }

    private fun skipWithFade(action: () -> Unit) {
        val mediaController = controller
        if (mediaController == null || !fadeEnabled.value || !mediaController.isPlaying) {
            action()
            return
        }
        fadeJob?.cancel()
        val from = mediaController.volume
        fadeJob = scope.launch {
            animateVolume(from, 0f)
            action()
            // 切歌后的淡入交给 onMediaItemTransition / isPlaying 事件
        }
    }

    /** 从当前音量平滑过渡到目标音量；总是覆盖上一个渐变，不会叠加。 */
    private fun startFade(target: Float) {
        val mediaController = controller ?: return
        if (!fadeEnabled.value) {
            mediaController.volume = 1f
            return
        }
        fadeJob?.cancel()
        val from = mediaController.volume
        fadeJob = scope.launch { animateVolume(from, target) }
    }

    private suspend fun animateVolume(from: Float, to: Float) {
        val mediaController = controller ?: return
        val steps = (FADE_DURATION_MS / FADE_STEP_MS).coerceAtLeast(1)
        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            mediaController.volume = from + (to - from) * fraction
            delay(FADE_STEP_MS)
        }
        mediaController.volume = to
    }

    private fun applyMode(mediaController: MediaController, mode: PlayMode) {
        // 先关随机再设 repeat，避免切换瞬间顺序错乱
        mediaController.shuffleModeEnabled = mode.shuffleEnabled
        mediaController.repeatMode = mode.repeatMode
    }

    /**
     * 进程重启后的状态恢复：播放模式、队列、当前曲目与进度。
     * 只恢复不自动续播（符合需求）。
     */
    private fun restoreState(mediaController: MediaController) {
        if (restoreStarted) return
        restoreStarted = true
        scope.launch {
            // 恢复失败也要把 isReady 置位，否则后续进度/模式无法持久化
            try {
                val snapshot = prefs.readSnapshot()
                applyMode(mediaController, PlayMode.fromValue(snapshot.repeatMode))
                if (mediaController.mediaItemCount == 0 && snapshot.queue.isNotEmpty()) {
                    val items = snapshot.queue.map(MediaItemMapper::toMediaItem)
                    // 优先按上次播放的歌曲定位下标，其次用启动播放时的下标
                    val index = snapshot.queue.indexOfFirst { it.id == snapshot.lastSongId }
                        .takeIf { it >= 0 }
                        ?: snapshot.queueIndex.coerceIn(0, items.lastIndex)
                    mediaController.setMediaItems(
                        items,
                        index,
                        snapshot.lastPositionMs.coerceAtLeast(0L),
                    )
                    mediaController.prepare()
                    // 恢复队列来源，主页据此显示对应分组列表
                    _state.update { it.copy(queueGroupId = snapshot.queueGroupId) }
                }
            } catch (_: Exception) {
                // 恢复失败不影响本次使用
            } finally {
                isReady = true
                syncState(mediaController)
            }
        }
    }

    private fun syncState(player: Player) {
        val song = player.currentMediaItem?.let(MediaItemMapper::fromMediaItem)
        val mode = PlayMode.fromPlayerState(player.repeatMode, player.shuffleModeEnabled)
        val duration = player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L
        val previous = _state.value
        _state.value = previous.copy(
            isConnected = true,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            currentSong = song,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            playMode = mode,
        )
        if (isReady && previous.playMode != mode) {
            scope.launch { prefs.setRepeatMode(mode.ordinal) }
        }
    }

    /** 500ms 轮询进度，供进度条使用。 */
    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                delay(500)
                val mediaController = controller ?: continue
                val position = mediaController.currentPosition.coerceAtLeast(0L)
                _state.update { it.copy(positionMs = position) }
                if (mediaController.isPlaying) {
                    mediaController.currentMediaItem?.mediaId?.toLongOrNull()?.let { songId ->
                        persistPosition(songId, position)
                    }
                }
            }
        }
    }

    private fun persistPosition(songId: Long, positionMs: Long, force: Boolean = false) {
        if (!isReady) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPersistedAt < PERSIST_INTERVAL_MS) return
        lastPersistedAt = now
        scope.launch { prefs.savePlaybackPosition(songId, positionMs) }
    }

    private fun friendlyError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "歌曲文件已不存在，请刷新音乐库"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        -> "无法播放该文件，可能是格式不支持"
        else -> "播放出错：${error.errorCodeName}"
    }

    private companion object {
        const val PERSIST_INTERVAL_MS = 5_000L

        /** 单次淡入/淡出时长。 */
        const val FADE_DURATION_MS = 300L

        /** 渐变刷新步长，约 60fps。 */
        const val FADE_STEP_MS = 16L
    }
}
