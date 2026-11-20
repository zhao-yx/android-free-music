package com.local.musicplayer.data

import android.content.Context
import com.local.musicplayer.data.db.AppDatabase
import com.local.musicplayer.data.media.MediaStoreScanner
import com.local.musicplayer.data.repo.GroupRepository
import com.local.musicplayer.data.repo.LibraryRepository
import com.local.musicplayer.data.repo.PrefsRepository
import com.local.musicplayer.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val playerConnection = PlayerConnection(appContext, prefsRepository, scope)
}
