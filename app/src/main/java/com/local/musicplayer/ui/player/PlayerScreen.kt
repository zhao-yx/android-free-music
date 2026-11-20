package com.local.musicplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.musicplayer.data.model.Skin
import com.local.musicplayer.ui.components.DiscArtwork
import com.local.musicplayer.ui.components.ModeButton
import com.local.musicplayer.ui.components.Tonearm
import com.local.musicplayer.ui.theme.discRes
import com.local.musicplayer.ui.theme.style
import com.local.musicplayer.util.formatDuration

@Composable
fun PlayerScreen(
    skin: Skin,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val state by viewModel.playback.collectAsStateWithLifecycle()
    val angle = viewModel.discAngle.collectAsStateWithLifecycle()
    val song = state.currentSong
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 碟片旋转：只在播放时累加角度，暂停停住，切歌归零
    LaunchedEffect(song?.id, state.isPlaying) {
        DiscRotationState.sync(scope, song?.id, state.isPlaying)
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 背景由全局皮肤背景提供，播放页只保留一层柔和的压暗处理，保证文字可读
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f)),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
            ) {
                // 根据屏幕高度限制碟片尺寸，避免小屏溢出
                val discSize = minOf(maxWidth * 0.78f, maxHeight * 0.42f)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "正在播放",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.size(discSize)) {
                        DiscArtwork(
                            rotation = angle,
                            discRes = skin.discRes,
                            labelTint = if (skin.style.isLight) {
                                Color.Black.copy(alpha = 0.30f)
                            } else {
                                Color.White.copy(alpha = 0.40f)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // 唱臂覆盖在碟片上方，不参与碟片旋转，只随播放状态摆动
                        Tonearm(
                            isPlaying = state.isPlaying,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.weight(0.7f))

                    Text(
                        text = song?.title ?: "暂无播放",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = song?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(24.dp))

                    val durationMs = state.durationMs.coerceAtLeast(1L)
                    var dragPosition by remember(song?.id) { mutableStateOf<Float?>(null) }
                    val shownPosition = dragPosition ?: state.positionMs.toFloat()
                    Slider(
                        value = shownPosition.coerceIn(0f, durationMs.toFloat()),
                        onValueChange = { dragPosition = it },
                        onValueChangeFinished = {
                            dragPosition?.let { viewModel.seekTo(it.toLong()) }
                            dragPosition = null
                        },
                        valueRange = 0f..durationMs.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(shownPosition.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDuration(state.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = viewModel::skipToPrevious) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = "上一曲",
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        FilledIconButton(
                            onClick = viewModel::togglePlayPause,
                            modifier = Modifier.size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = if (state.isPlaying) "暂停" else "播放",
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        IconButton(onClick = viewModel::skipToNext) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "下一曲",
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    ModeButton(mode = state.playMode, onClick = viewModel::cyclePlayMode)
                    Spacer(Modifier.weight(0.35f))
                }
            }
        }
    }
}
