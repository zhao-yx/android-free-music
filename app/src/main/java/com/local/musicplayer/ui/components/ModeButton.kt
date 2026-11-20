package com.local.musicplayer.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.local.musicplayer.data.model.PlayMode

/**
 * 播放模式按钮：图标 + 文字同时提示当前模式，点击循环切换。
 */
@Composable
fun ModeButton(
    mode: PlayMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(mode.label, style = MaterialTheme.typography.labelMedium)
    }
}

private val PlayMode.icon: ImageVector
    get() = when (this) {
        PlayMode.SEQUENCE -> Icons.Filled.FormatListNumbered
        PlayMode.LOOP_ALL -> Icons.Filled.Repeat
        PlayMode.LOOP_ONE -> Icons.Filled.RepeatOne
        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
    }
