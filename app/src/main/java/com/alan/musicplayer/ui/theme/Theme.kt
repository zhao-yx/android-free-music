package com.alan.musicplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alan.musicplayer.R
import com.alan.musicplayer.data.model.Skin

/** 一款皮肤的完整视觉参数（配色、背景渐变、图片资源 key、遮罩）。 */
data class SkinStyle(
    val colorScheme: ColorScheme,
    val isLight: Boolean,
    val gradient: List<Color>,
    val scrim: Color,
    /** 定制皮肤的 assets/skins/<key>/bg.* 目录名；纯色皮肤为 null。 */
    val assetKey: String?,
)

val Skin.style: SkinStyle
    get() = when (this) {
        Skin.CLASSIC_LIGHT -> SkinStyle(
            colorScheme = ClassicLightColors,
            isLight = true,
            gradient = listOf(Color(0xFFF7F5FB), Color(0xFFEDE8FA), Color(0xFFF7F5FB)),
            scrim = Color.Transparent,
            assetKey = null,
        )

        Skin.CLASSIC_DARK -> SkinStyle(
            colorScheme = ClassicDarkColors,
            isLight = false,
            gradient = listOf(Color(0xFF1A1726), Color(0xFF0E0E12), Color(0xFF0E0E12)),
            scrim = Color.Transparent,
            assetKey = null,
        )

        Skin.BUSINESS -> SkinStyle(
            colorScheme = BusinessColors,
            isLight = false,
            gradient = listOf(Color(0xFF0B1522), Color(0xFF12253C), Color(0xFF0B1522)),
            // 背景图本身是深藏青，遮罩只做轻微压暗，避免整屏糊成纯黑
            scrim = Color.Black.copy(alpha = 0.15f),
            assetKey = "business",
        )

        Skin.ANIME -> SkinStyle(
            colorScheme = AnimeColors,
            isLight = true,
            gradient = listOf(Color(0xFFFFF0F7), Color(0xFFF2E9FF), Color(0xFFFFF7FB)),
            scrim = Color.White.copy(alpha = 0.26f),
            assetKey = "anime",
        )

        Skin.CHEERFUL -> SkinStyle(
            colorScheme = CheerfulColors,
            isLight = true,
            gradient = listOf(Color(0xFFFFFBF0), Color(0xFFFFEFD8), Color(0xFFF2FFF8)),
            scrim = Color.White.copy(alpha = 0.14f),
            assetKey = "cheerful",
        )
    }

/** 播放页碟片：经典黑沿用现有 disc_brand.png，其余皮肤各用专属图。 */
val Skin.discRes: Int
    get() = when (this) {
        Skin.CLASSIC_LIGHT -> R.drawable.disc_classic_light
        Skin.CLASSIC_DARK -> R.drawable.disc_brand
        Skin.BUSINESS -> R.drawable.disc_business
        Skin.ANIME -> R.drawable.disc_anime
        Skin.CHEERFUL -> R.drawable.disc_cheerful
    }

private val ClassicLightColors = lightColorScheme(
    primary = Color(0xFF6C5CE7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DEFF),
    onPrimaryContainer = Color(0xFF221B4D),
    secondary = Color(0xFF00A39B),
    onSecondary = Color.White,
    background = Color(0xFFF7F5FB),
    onBackground = Color(0xFF1A1A22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A22),
    surfaceVariant = Color(0xFFEDEAF5),
    onSurfaceVariant = Color(0xFF5A5768),
    outline = Color(0xFFC9C4DA),
)

private val ClassicDarkColors = darkColorScheme(
    primary = Color(0xFFB9A8FF),
    onPrimary = Color(0xFF1A1330),
    primaryContainer = Color(0xFF3A2E66),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFF7FD8D2),
    onSecondary = Color(0xFF062B29),
    background = Color(0xFF0E0E12),
    onBackground = Color(0xFFECEAEF),
    surface = Color(0xFF15151C),
    onSurface = Color(0xFFECEAEF),
    surfaceVariant = Color(0xFF24232E),
    onSurfaceVariant = Color(0xFFB9B6C6),
    outline = Color(0xFF47455A),
)

private val BusinessColors = darkColorScheme(
    primary = Color(0xFF4F7CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16305C),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF46C2B0),
    onSecondary = Color(0xFF04211D),
    background = Color(0xFF0B1522),
    onBackground = Color(0xFFE4ECF7),
    surface = Color(0xFF142233),
    onSurface = Color(0xFFE4ECF7),
    surfaceVariant = Color(0xFF1C2C40),
    onSurfaceVariant = Color(0xFF9FB0C7),
    outline = Color(0xFF2A3D55),
)

private val AnimeColors = lightColorScheme(
    primary = Color(0xFFFF6FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1EC),
    onPrimaryContainer = Color(0xFF4A1230),
    secondary = Color(0xFF9B7BFF),
    onSecondary = Color.White,
    background = Color(0xFFFFF3F8),
    onBackground = Color(0xFF2B1B26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B1B26),
    surfaceVariant = Color(0xFFFCE3EF),
    onSurfaceVariant = Color(0xFF7A5A6A),
    outline = Color(0xFFF0CBDD),
)

private val CheerfulColors = lightColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6D1),
    onPrimaryContainer = Color(0xFF4A2408),
    secondary = Color(0xFF2FBF9F),
    onSecondary = Color(0xFF04231C),
    background = Color(0xFFFFFBF0),
    onBackground = Color(0xFF33291E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF33291E),
    surfaceVariant = Color(0xFFFFF0DA),
    onSurfaceVariant = Color(0xFF8A7152),
    outline = Color(0xFFEAD8BD),
)

@Composable
fun LocalMusicTheme(skin: Skin, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = skin.style.colorScheme,
        typography = Typography(),
        content = content,
    )
}
