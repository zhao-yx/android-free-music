package com.local.musicplayer.ui.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.musicplayer.R
import com.local.musicplayer.data.model.Group
import com.local.musicplayer.data.model.Skin
import com.local.musicplayer.data.model.Song
import com.local.musicplayer.data.repo.LibraryState
import com.local.musicplayer.ui.components.MiniPlayerBar
import com.local.musicplayer.ui.components.SkinPickerDialog
import com.local.musicplayer.ui.components.SongRow
import com.local.musicplayer.ui.components.SwipeToRemoveBox
import com.local.musicplayer.util.audioPermission
import com.local.musicplayer.util.hasAudioPermission
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenGroups: () -> Unit,
    onOpenPlayer: () -> Unit,
    currentSkin: Skin,
    onSelectSkin: (Skin) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val scanNotice by viewModel.scanNotice.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    // 左滑操作中“加入分组”的目标歌曲
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var showSkinPicker by remember { mutableStateOf(false) }
    // 当前展开删除块的行；同一时间只允许一行展开
    var expandedSongId by remember { mutableLongStateOf(-1L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[audioPermission()] == true
        hasPermission = granted
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }
    // Android 13+ 需要通知权限才能在通知栏/锁屏显示播放控制，首次进入时单独申请
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // 从系统设置返回时重新检查权限
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = context.hasAudioPermission()
        if (granted && !hasPermission) {
            hasPermission = true
            viewModel.onPermissionGranted()
        }
    }
    // 手动扫描完成后给出数量反馈，避免“点了没反应”的错觉
    LaunchedEffect(scanNotice) {
        scanNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearScanNotice()
        }
    }
    // 播放失败（文件被删 / 格式不支持）时在列表页直接 Toast 提示
    LaunchedEffect(playback.errorMessage) {
        playback.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearPlaybackError()
        }
    }

    Scaffold(
        // 背景由全局 SkinBackground 提供，页面保持透明
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { showSkinPicker = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "切换皮肤")
                    }
                    IconButton(onClick = { viewModel.refresh(notify = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新音乐库")
                    }
                    IconButton(onClick = onOpenGroups) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "我的分组")
                    }
                },
            )
        },
        bottomBar = {
            if (playback.currentSong != null) {
                MiniPlayerBar(
                    state = playback,
                    onOpenPlayer = onOpenPlayer,
                    onTogglePlay = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipToNext,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !hasPermission -> PermissionGuide(
                    onRequest = { permissionLauncher.launch(requiredPermissions()) },
                    onOpenSettings = { context.openAppSettings() },
                )

                else -> when (val state = uiState.loadState) {
                    LibraryState.Loading -> LoadingView()
                    LibraryState.Empty -> EmptyLibraryView(onRefresh = { viewModel.refresh(notify = true) })
                    is LibraryState.Error -> ErrorView(
                        message = state.message,
                        onRetry = { viewModel.refresh(notify = true) },
                    )
                    LibraryState.Success -> SongList(
                        songs = uiState.songs,
                        currentSongId = playback.currentSong?.id,
                        expandedSongId = expandedSongId,
                        onExpandedChange = { expandedSongId = it ?: -1L },
                        onPlay = { song ->
                            expandedSongId = -1L
                            viewModel.playSong(song)
                        },
                        onAddToGroup = { song ->
                            expandedSongId = -1L
                            actionSong = song
                        },
                        onRemove = { song ->
                            expandedSongId = -1L
                            // 仅从当前列表移除，不删除文件、不写任何持久化数据
                            viewModel.removeFromList(song)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "已从列表移除「${song.title}」，歌曲文件未被删除，重新扫描可恢复",
                                    actionLabel = "撤销",
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreToList(song)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showSkinPicker) {
        SkinPickerDialog(
            currentSkin = currentSkin,
            onSelect = onSelectSkin,
            onDismiss = { showSkinPicker = false },
        )
    }

    actionSong?.let { song ->
        AddToGroupDialog(
            song = song,
            groups = groups,
            onSelect = { group ->
                viewModel.addSongToGroup(group, song)
                actionSong = null
            },
            onDismiss = { actionSong = null },
        )
    }
}

@Composable
private fun AddToGroupDialog(
    song: Song,
    groups: List<Group>,
    onSelect: (Group) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入分组") },
        text = {
            Column {
                Text(
                    text = "将「${song.title}」加入：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (groups.isEmpty()) {
                    Text(
                        text = "还没有分组，请先在「我的分组」里创建分组",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(groups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(group) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SongList(
    songs: List<Song>,
    currentSongId: Long?,
    expandedSongId: Long,
    onExpandedChange: (Long?) -> Unit,
    onPlay: (Song) -> Unit,
    onAddToGroup: (Song) -> Unit,
    onRemove: (Song) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(expandedSongId) {
                // 点空白处收起当前展开的删除块
                if (expandedSongId != -1L) {
                    detectTapGestures(onTap = { onExpandedChange(null) })
                }
            },
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item(key = "count") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "共 ${songs.size} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "点击歌曲开始播放",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(songs, key = { it.id }) { song ->
            val expanded = expandedSongId == song.id
            // 左滑只展开红色删除块，点击删除块才移除；展开状态下点行内其他区域先收起
            SwipeToRemoveBox(
                expanded = expanded,
                onExpandedChange = { expand ->
                    onExpandedChange(if (expand) song.id else null)
                },
                onAddToGroup = { onAddToGroup(song) },
                onRemove = { onRemove(song) },
            ) {
                SongRow(
                    song = song,
                    isCurrent = currentSongId == song.id,
                    onClick = {
                        if (expanded) onExpandedChange(null) else onPlay(song)
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("正在扫描本地音乐…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyLibraryView(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.empty_library),
            contentDescription = null,
            modifier = Modifier.size(168.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("本地没有找到音乐", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "请确认设备中已有音频文件（支持 mp3 / m4a / flac / ogg / wav 等格式）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onRefresh) { Text("重新扫描") }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("扫描失败", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun PermissionGuide(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.empty_permission),
            contentDescription = null,
            modifier = Modifier.size(176.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("需要读取本地音乐", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "播放器需要读取设备中的音频文件才能展示歌曲列表。全程离线运行，不会联网、不会上传任何数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("授予权限")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("打开系统设置")
        }
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(audioPermission())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}
