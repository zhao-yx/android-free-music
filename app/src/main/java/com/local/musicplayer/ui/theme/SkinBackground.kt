package com.local.musicplayer.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.local.musicplayer.data.model.Skin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 皮肤背景：纯色皮肤使用主题渐变；定制皮肤优先读取
 * app/src/main/assets/skins/<key>/bg.webp|png|jpg，图片缺失时退回渐变，不会白屏。
 */
@Composable
fun SkinBackground(skin: Skin, modifier: Modifier = Modifier) {
    val style = skin.style
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(style.gradient)),
    ) {
        val assetKey = style.assetKey ?: return@Box
        val bitmap = rememberSkinBackground(assetKey)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 图片上叠加遮罩，保证文字对比度
            Box(
                Modifier
                    .fillMaxSize()
                    .background(style.scrim),
            )
        }
    }
}

/** 依次尝试 webp / png / jpg，读取失败返回 null。 */
@Composable
fun rememberSkinBackground(assetKey: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, assetKey) {
        value = withContext(Dispatchers.IO) {
            listOf("webp", "png", "jpg").firstNotNullOfOrNull { extension ->
                runCatching {
                    context.assets.open("skins/$assetKey/bg.$extension").use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return bitmap
}
