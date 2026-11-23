package com.alan.musicplayer.data.model

import androidx.media3.common.Player

/**
 * 四种播放模式。
 *
 * [ordinal] 即 DataStore 中 repeatMode 的持久化值（0 顺序 / 1 列表循环 / 2 单曲循环 / 3 随机），
 * 与 ExoPlayer 的 repeatMode + shuffleModeEnabled 组合一一对应。
 */
enum class PlayMode(val label: String, val repeatMode: Int, val shuffleEnabled: Boolean) {
    SEQUENCE("顺序播放", Player.REPEAT_MODE_OFF, false),
    LOOP_ALL("列表循环", Player.REPEAT_MODE_ALL, false),
    LOOP_ONE("单曲循环", Player.REPEAT_MODE_ONE, false),
    SHUFFLE("随机播放", Player.REPEAT_MODE_ALL, true);

    /** 模式按钮点击后循环切换到的下一个模式。 */
    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromValue(value: Int): PlayMode = entries.getOrElse(value) { SEQUENCE }

        /** 根据播放器当前 repeat/shuffle 状态反推 UI 模式。 */
        fun fromPlayerState(repeatMode: Int, shuffleEnabled: Boolean): PlayMode = when {
            shuffleEnabled -> SHUFFLE
            repeatMode == Player.REPEAT_MODE_ONE -> LOOP_ONE
            repeatMode == Player.REPEAT_MODE_ALL -> LOOP_ALL
            else -> SEQUENCE
        }
    }
}
