package com.alan.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alan.musicplayer.R
import com.alan.musicplayer.data.model.Song

/**
 * 歌曲封面：异步加载本地音频内嵌封面；加载中与失败时显示深色品牌占位图。
 */
@Composable
fun SongArtwork(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2C2A3A), Color(0xFF1A1922)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.32f),
            modifier = Modifier.fillMaxSize(0.42f),
        )
        if (song != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = song.title,
                // 加载中与解析失败都显示深色品牌占位图，避免空白或白块
                placeholder = painterResource(R.drawable.artwork_placeholder),
                error = painterResource(R.drawable.artwork_placeholder),
                fallback = painterResource(R.drawable.artwork_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
