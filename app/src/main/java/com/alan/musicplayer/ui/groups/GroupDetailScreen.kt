package com.alan.musicplayer.ui.groups

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alan.musicplayer.R
import com.alan.musicplayer.data.model.Song
import com.alan.musicplayer.ui.components.MiniPlayerBar
import com.alan.musicplayer.ui.components.SongArtwork
import com.alan.musicplayer.ui.components.SongRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: GroupDetailViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val songs = uiState.songs
    // 分组内容变化（如批量移出）后，剔除已不存在的选中项
    val validSelected = remember(selectedIds, songs) {
        selectedIds.intersect(songs.map { it.id }.toSet())
    }
    val allSelected = songs.isNotEmpty() && validSelected.size == songs.size
    LaunchedEffect(songs) {
        if (selectionMode && validSelected != selectedIds) selectedIds = validSelected
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 多选模式下系统返回键先退出多选，而不是直接离开页面
    BackHandler(enabled = selectionMode) { exitSelection() }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    when {
                        selectionMode -> Text("已选择 ${validSelected.size} 项")
                        uiState.isReadOnly -> Column {
                            Text(uiState.groupName)
                            Text(
                                text = "${uiState.songs.size} 首",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> Text(uiState.groupName)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) exitSelection() else onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        TextButton(
                            onClick = {
                                val allIds = songs.map { it.id }.toSet()
                                selectedIds = if (allSelected) {
                                    validSelected - allIds
                                } else {
                                    validSelected + allIds
                                }
                            },
                        ) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        TextButton(onClick = { exitSelection() }) { Text("取消") }
                    } else if (!uiState.isReadOnly) {
                        // 固定只读条目（全部分组 / 心动歌单）不提供选择与添加
                        TextButton(
                            onClick = { selectionMode = true },
                            enabled = songs.isNotEmpty(),
                        ) {
                            Text("选择")
                        }
                        IconButton(
                            onClick = { showAddDialog = true },
                            enabled = uiState.allSongs.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "添加歌曲",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (selectionMode) {
                SelectionBar(count = validSelected.size) {
                    val selectedSongs = songs.filter { it.id in validSelected }
                    viewModel.removeSongs(selectedSongs)
                    exitSelection()
                    scope.launch {
                        snackbarHostState.showSnackbar("已从分组移出 ${selectedSongs.size} 首")
                    }
                }
            } else if (playback.currentSong != null) {
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
            if (songs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 固定正方形绘制区 + Fit 等比缩放，避免插画被拉伸，并用淡色底盘保证深浅皮肤都清晰
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.empty_group),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(0.86f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = when {
                            uiState.isHeartPlaylist -> "心动歌单还是空的"
                            uiState.isAllSongs -> "音乐库里还没有歌曲"
                            else -> "分组里还没有歌曲"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            uiState.isHeartPlaylist -> "多播放几首歌，下次启动后会按播放次数自动生成榜单"
                            uiState.isAllSongs -> "返回音乐库点击刷新，或确认系统媒体库已扫描到音频文件"
                            else -> "点击右上角 + 从音乐库添加"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "count") {
                        Text(
                            text = if (selectionMode) {
                                "已选 ${validSelected.size} 首 · 点按歌曲勾选"
                            } else {
                                "共 ${songs.size} 首 · 按当前顺序播放"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        if (selectionMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = song.id in validSelected,
                                    onCheckedChange = null,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                                SongRow(
                                    song = song,
                                    isCurrent = playback.currentSong?.id == song.id,
                                    onClick = {
                                        selectedIds = if (song.id in selectedIds) {
                                            selectedIds - song.id
                                        } else {
                                            selectedIds + song.id
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            SongRow(
                                song = song,
                                isCurrent = playback.currentSong?.id == song.id,
                                onClick = { viewModel.playSong(song) },
                                trailing = if (uiState.isReadOnly) {
                                    null
                                } else {
                                    {
                                        SongActionsMenu(
                                            isFirst = index == 0,
                                            isLast = index == songs.lastIndex,
                                            onMoveUp = { viewModel.moveUp(song) },
                                            onMoveDown = { viewModel.moveDown(song) },
                                            onRemove = { viewModel.removeSong(song) },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSongsDialog(
            groupName = uiState.groupName,
            allSongs = uiState.allSongs,
            existingIds = songs.map { it.id }.toSet(),
            onAdd = { added ->
                viewModel.addSongs(added)
                showAddDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("已添加 ${added.size} 首")
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已选 $count 首", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Button(onClick = onRemove, enabled = count > 0) {
                Text("移出分组")
            }
        }
    }
}

@Composable
private fun SongActionsMenu(
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "歌曲操作",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("上移") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                enabled = !isFirst,
                onClick = {
                    expanded = false
                    onMoveUp()
                },
            )
            DropdownMenuItem(
                text = { Text("下移") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                enabled = !isLast,
                onClick = {
                    expanded = false
                    onMoveDown()
                },
            )
            DropdownMenuItem(
                text = { Text("从分组移除") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun AddSongsDialog(
    groupName: String,
    allSongs: List<Song>,
    existingIds: Set<Long>,
    onAdd: (List<Song>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 勾选集合独立于搜索词，切换搜索时已勾选的歌曲保留
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val candidates = remember(allSongs, existingIds, query) {
        allSongs.filter { song ->
            song.id !in existingIds && (
                query.isBlank() ||
                    song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true)
                )
        }
    }
    val allSelected = candidates.isNotEmpty() && candidates.all { it.id in selectedIds }

    // 拖动勾选：记录每行在根坐标中的范围，把手指纵坐标映射回对应歌曲
    val rowBounds = remember { mutableStateMapOf<Long, Rect>() }
    fun toggle(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加歌曲到「$groupName」",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("搜索歌曲或歌手") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            val ids = candidates.map { it.id }.toSet()
                            selectedIds = if (checked) selectedIds + ids else selectedIds - ids
                        },
                        enabled = candidates.isNotEmpty(),
                    )
                    Text(
                        if (allSelected) "取消全选" else "全选当前结果",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "已选 ${selectedIds.size} 首",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (candidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (allSongs.isEmpty()) "音乐库为空" else "没有可添加的歌曲",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(candidates, key = { it.id }) { song ->
                            val checked = song.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (checked) {
                                            selectedIds - song.id
                                        } else {
                                            selectedIds + song.id
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .onGloballyPositioned { rowBounds[song.id] = it.boundsInRoot() }
                                        .pointerInput(song.id) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                down.consume()
                                                val anchor = rowBounds[song.id] ?: return@awaitEachGesture
                                                // 本次拖动中已切换过的行，保证每行只翻转一次
                                                val toggled = mutableSetOf<Long>()
                                                if (toggled.add(song.id)) toggle(song.id)
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes
                                                        .firstOrNull { it.id == down.id } ?: break
                                                    if (change.changedToUpIgnoreConsumed()) {
                                                        change.consume()
                                                        break
                                                    }
                                                    val y = anchor.top + change.position.y
                                                    val target = rowBounds.entries
                                                        .firstOrNull { y >= it.value.top && y <= it.value.bottom }
                                                        ?.key
                                                    if (target != null && toggled.add(target)) toggle(target)
                                                    change.consume()
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                }
                                Spacer(Modifier.width(12.dp))
                                SongArtwork(song = song, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(allSongs.filter { it.id in selectedIds }) },
                enabled = selectedIds.isNotEmpty(),
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
