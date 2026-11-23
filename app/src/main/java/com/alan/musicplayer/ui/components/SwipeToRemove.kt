package com.alan.musicplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 左滑露出两个操作块：左侧“加入分组”、右侧“从列表移除”。
 * 两个块都必须点击确认；[expanded] 由外部持有，保证同一时间只展开一行。
 * 移除仅影响当前列表展示，不触碰文件与磁盘数据。
 */
@Composable
fun SwipeToRemoveBox(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddToGroup: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val blockWidth = 58.dp
    val revealWidth = with(density) { (blockWidth * 2).toPx() }
    var dragOffset by remember { mutableFloatStateOf(if (expanded) -revealWidth else 0f) }
    var dragging by remember { mutableStateOf(false) }

    // 外部展开状态变化（例如展开另一行）时同步位移
    LaunchedEffect(expanded) {
        if (!dragging) dragOffset = if (expanded) -revealWidth else 0f
    }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (dragging) snap() else tween(durationMillis = 180),
        label = "swipe-reveal",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 用内容高度定高，保证右侧色块上下撑满整行
            .height(IntrinsicSize.Min)
            .clipToBounds(),
    ) {
        // 右侧操作块：只有露出时才可点击
        if (animatedOffset < -1f) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier
                        .width(blockWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onAddToGroup() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LibraryAdd,
                        contentDescription = "加入分组",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(blockWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "从列表移除",
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(revealWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset = (dragOffset + dragAmount).coerceIn(-revealWidth, 0f)
                        },
                        onDragEnd = {
                            dragging = false
                            val shouldExpand = dragOffset <= -revealWidth * 0.45f
                            dragOffset = if (shouldExpand) -revealWidth else 0f
                            onExpandedChange(shouldExpand)
                        },
                        onDragCancel = {
                            dragging = false
                            dragOffset = if (expanded) -revealWidth else 0f
                        },
                    )
                },
        ) {
            content()
        }
    }
}
