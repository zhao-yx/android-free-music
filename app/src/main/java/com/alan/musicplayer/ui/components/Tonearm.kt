package com.alan.musicplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 唱臂：支点在碟片左上方外侧（与常见唱机同向），臂头搭向碟面外圈。
 * 播放时落下贴住碟面，暂停时抬起约 15°，不随碟片旋转。
 * 使用金属灰 + 高光自绘，五款皮肤背景上都能看清。
 */
@Composable
fun Tonearm(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val lowered by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "tonearm-lower",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val r = size.minDimension / 2f

        // 支点位于碟片左上侧圆外（距圆心约 1.11r），仍在画面内
        val pivot = Offset(center.x - r * 0.90f, center.y - r * 0.64f)
        // 臂头落在碟面外圈（半径约 0.85r）
        val tip = Offset(center.x + r * 0.72f, center.y - r * 0.46f)
        // 镜像后需逆时针旋转，臂头才会抬起
        val raiseDegrees = -15f * (1f - lowered)

        val armDir = Offset(tip.x - pivot.x, tip.y - pivot.y)
        val armLength = hypot(armDir.x, armDir.y)
        val ux = armDir.x / armLength
        val uy = armDir.y / armLength
        val normal = Offset(-uy, ux)
        val armWidth = r * 0.055f
        val metal = Color(0xFFCDD2DB)
        val metalDark = Color(0xFF6C7280)
        val metalLight = Color(0xFFF4F6FA)

        rotate(degrees = raiseDegrees, pivot = pivot) {
            // 投影
            drawLine(
                color = Color.Black.copy(alpha = 0.30f),
                start = pivot + Offset(-armWidth * 0.55f, armWidth * 0.75f),
                end = tip + Offset(-armWidth * 0.55f, armWidth * 0.75f),
                strokeWidth = armWidth * 1.05f,
                cap = StrokeCap.Round,
            )
            // 杆身：暗侧 + 主体 + 高光，形成圆柱金属质感
            drawLine(
                color = metalDark,
                start = pivot + normal * armWidth * 0.30f,
                end = tip + normal * armWidth * 0.30f,
                strokeWidth = armWidth * 0.55f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = metal,
                start = pivot - normal * armWidth * 0.06f,
                end = tip - normal * armWidth * 0.06f,
                strokeWidth = armWidth * 0.82f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = metalLight.copy(alpha = 0.85f),
                start = pivot - normal * armWidth * 0.30f,
                end = tip - normal * armWidth * 0.30f,
                strokeWidth = armWidth * 0.20f,
                cap = StrokeCap.Round,
            )

            // 唱头：沿臂方向的金属块 + 深色唱针座
            val armAngle = Math.toDegrees(atan2(armDir.y.toDouble(), armDir.x.toDouble())).toFloat()
            rotate(degrees = armAngle, pivot = tip) {
                drawRoundRect(
                    color = metalDark,
                    topLeft = Offset(tip.x - armWidth * 0.55f, tip.y - armWidth * 1.05f),
                    size = Size(armWidth * 1.10f, armWidth * 2.10f),
                    cornerRadius = CornerRadius(armWidth * 0.28f),
                )
                drawRoundRect(
                    color = Color(0xFF2C2F37),
                    topLeft = Offset(tip.x - armWidth * 0.32f, tip.y + armWidth * 0.10f),
                    size = Size(armWidth * 0.64f, armWidth * 0.95f),
                    cornerRadius = CornerRadius(armWidth * 0.18f),
                )
            }
            // 唱针尖高光点
            drawCircle(
                color = metalLight,
                radius = armWidth * 0.16f,
                center = tip + Offset(armWidth * 0.10f, armWidth * 0.55f),
            )
        }

        // 支点底座（不随摆动旋转）
        drawCircle(color = Color.Black.copy(alpha = 0.28f), radius = armWidth * 0.95f, center = pivot + Offset(2f, 3f))
        drawCircle(color = metalDark, radius = armWidth * 0.90f, center = pivot)
        drawCircle(color = metal, radius = armWidth * 0.64f, center = pivot)
        drawCircle(color = Color(0xFF43474F), radius = armWidth * 0.34f, center = pivot)
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = armWidth * 0.13f,
            center = pivot - Offset(armWidth * 0.16f, armWidth * 0.16f),
        )
    }
}
