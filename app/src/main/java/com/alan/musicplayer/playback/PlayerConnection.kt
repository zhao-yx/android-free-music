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

    private val listener = object : Player.Listener {
        // 所有播放器事件统一在这里同步成 UI 状态，避免多处状态分叉
        override fun onEvents(player: Player, events: Player.Events) {
            val previous = _state.value
            syncState(player)
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
        // 大列表整体通过 Binder 传递，异常时给出提示而不是崩溃
        runCatching {
            mediaController.setMediaItems(items, index, 0L)
            mediaController.prepare()
            mediaController.play()
        }.onFailure {
            _state.update { state -> state.copy(errorMessage = "队列设置失败，歌曲过多或服务异常") }
            return
        }
        _state.update { it.copy(errorMessage = null) }
        scope.launch { prefs.saveQueue(queueGroupId, index, songs) }
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        when {
            mediaController.isPlaying -> mediaController.pause()
            mediaController.playbackState == Player.STATE_ENDED -> {
                // 顺序播放到最后一首后，再次点击播放从队列开头继续
                mediaController.seekToDefaultPosition()
                mediaController.prepare()
                mediaController.play()
            }
            else -> {
                if (mediaController.playbackState == Player.STATE_IDLE) mediaController.prepare()
                mediaController.play()
            }
        }
    }

    /** 上一曲：随机模式下由 ExoPlayer 的 shuffle 历史回到实际播放过的上一首。 */
    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun skipToNext() {
        controller?.seekToNext()
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
    }
}
