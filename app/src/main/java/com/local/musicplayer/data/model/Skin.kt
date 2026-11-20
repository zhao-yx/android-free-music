package com.local.musicplayer.data.model

/**
 * 应用皮肤预设。
 *
 * storageValue 为持久化到 DataStore 的稳定值，不要复用/修改已发布的值；
 * 具体配色与背景资源映射放在 ui/theme，避免数据层依赖 Compose。
 */
enum class Skin(val storageValue: Int, val displayName: String) {
    CLASSIC_LIGHT(0, "经典白"),
    CLASSIC_DARK(1, "经典黑"),
    BUSINESS(2, "商务风"),
    ANIME(3, "二次元风"),
    CHEERFUL(4, "轻松欢快风");

    companion object {
        fun fromValue(value: Int): Skin = entries.firstOrNull { it.storageValue == value } ?: CLASSIC_DARK
    }
}
