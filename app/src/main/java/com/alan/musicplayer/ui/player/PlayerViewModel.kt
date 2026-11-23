package com.alan.musicplayer.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.alan.musicplayer.MusicApplication
import com.alan.musicplayer.playback.PlaybackUiState
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val connection = (application as MusicApplication).container.playerConnection

    val playback: StateFlow<PlaybackUiState> = connection.state
    val discAngle: StateFlow<Float> = DiscRotationState.angle

    fun togglePlayPause() = connection.togglePlayPause()

    fun skipToPrevious() = connection.skipToPrevious()

    fun skipToNext() = connection.skipToNext()

    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

    fun cyclePlayMode() = connection.cyclePlayMode()

    fun clearError() = connection.clearError()
}
