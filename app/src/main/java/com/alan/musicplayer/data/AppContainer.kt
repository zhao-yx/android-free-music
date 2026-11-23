package com.alan.musicplayer.data

import android.content.Context
import com.alan.musicplayer.data.db.AppDatabase
import com.alan.musicplayer.data.media.MediaStoreScanner
import com.alan.musicplayer.data.repo.GroupRepository
import com.alan.musicplayer.data.repo.LibraryRepository
import com.alan.musicplayer.data.repo.PlayStatsRepository
import com.alan.musicplayer.data.repo.PrefsRepository
import com.alan.musicplayer.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 进程级依赖容器，Activity / Service / ViewModel 共用同一套仓库与播放连接。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database = AppDatabase.get(appContext)

    val prefsRepository = PrefsRepository(appContext)
    val groupRepository = GroupRepository(database)
    val libraryRepository = LibraryRepository(MediaStoreScanner(appContext))
    val playStatsRepository = PlayStatsRepository(database)

    /**
     * 心动歌单定榜：App 启动时读取一次后保持固定，运行期间播放不实时重排，
     * 下次启动才按最新统计刷新。
     */
    private val _heartPlaylistIds = MutableStateFlow<List<Long>>(emptyList())
    val heartPlaylistIds: StateFlow<List<Long>> = _heartPlaylistIds.asStateFlow()

    init {
        scope.launch {
            _heartPlaylistIds.value = playStatsRepository.snapshotHeartIds()
        }
    }

    val playerConnection = PlayerConnection(
        context = appContext,
        prefs = prefsRepository,
        scope = scope,
        onSongStarted = { songId ->
            scope.launch { playStatsRepository.recordPlay(songId) }
        },
    )
}
