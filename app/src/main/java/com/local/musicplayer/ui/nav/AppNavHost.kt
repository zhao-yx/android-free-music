package com.local.musicplayer.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.musicplayer.data.model.Skin
import com.local.musicplayer.ui.groups.GroupDetailScreen
import com.local.musicplayer.ui.groups.GroupListScreen
import com.local.musicplayer.ui.library.LibraryScreen
import com.local.musicplayer.ui.player.PlayerScreen
import com.local.musicplayer.ui.theme.SkinBackground

private object Routes {
    const val LIBRARY = "library"
    const val GROUPS = "groups"
    const val GROUP_DETAIL = "group"
    const val PLAYER = "player"

    fun groupDetail(groupId: Long): String = "$GROUP_DETAIL/$groupId"
}

/**
 * 全局皮肤背景铺在所有页面下方，各页面 Scaffold 使用透明底。
 */
@Composable
fun AppNavHost(
    skin: Skin,
    onSelectSkin: (Skin) -> Unit,
) {
    val navController = rememberNavController()
    Box(Modifier.fillMaxSize()) {
        SkinBackground(skin = skin)
        NavHost(navController = navController, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenGroups = { navController.navigate(Routes.GROUPS) },
                    onOpenPlayer = navController::openPlayer,
                    currentSkin = skin,
                    onSelectSkin = onSelectSkin,
                )
            }
            composable(Routes.GROUPS) {
                GroupListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGroup = { groupId -> navController.navigate(Routes.groupDetail(groupId)) },
                    onOpenPlayer = navController::openPlayer,
                )
            }
            composable(
                route = "${Routes.GROUP_DETAIL}/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType }),
            ) {
                GroupDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = navController::openPlayer,
                )
            }
            composable(Routes.PLAYER) {
                PlayerScreen(
                    skin = skin,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** 播放页只保留一个实例，避免从不同页面重复入栈。 */
private fun NavHostController.openPlayer() {
    navigate(Routes.PLAYER) { launchSingleTop = true }
}
