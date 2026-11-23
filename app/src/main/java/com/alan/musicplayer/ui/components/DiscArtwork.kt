package com.alan.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * 播放页碟片：按当前皮肤读取对应图片（见 Skin.discRes），图内已含中心轴孔与标签。
 * 旋转角度在 graphicsLayer 绘制阶段读取，避免每帧重组整页。
 * 标签区弧形小字绘制在旋转层内部，随碟片一起转动，低对比不抢戏。
 */
@Composable
fun DiscArtwork(
    rotation: State<Float>,
    discRes: Int,
    modifier: Modifier = Modifier,
    labelTint: Color = Color.White.copy(alpha = 0.40f),
) {
    val textMeasurer = rememberTextMeasurer()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = rotation.value },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0xFF191722)),
        )
        Image(
            painter = painterResource(discRes),
            contentDescription = "播放中的唱片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
        )
        // 中心轴孔与中央标签已由碟片图自带；这里只加一圈低对比弧形英文小字
        Canvas(Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val radius = diameter * 0.30f
            val style = TextStyle(
                color = labelTint,
                fontSize = (diameter * 0.026f).toSp(),
                fontWeight = FontWeight.Medium,
            )
            val label = "HAPPYHEAR · SIDE A · HIGH FIDELITY ·"
            val startAngle = -118f
            val sweep = 236f
            label.forEachIndexed { index, ch ->
                if (ch != ' ') {
                    val angle = startAngle + sweep * index / (label.length - 1).toFloat()
                    rotate(degrees = angle, pivot = center) {
                        drawLabelChar(textMeasurer, ch, style, radius)
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabelChar(
    textMeasurer: TextMeasurer,
    char: Char,
    style: TextStyle,
    radius: Float,
) {
    val layout = textMeasurer.measure(char.toString(), style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            x = center.x - layout.size.width / 2f,
            y = center.y - radius - layout.size.height / 2f,
        ),
    )
}
