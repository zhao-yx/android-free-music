package com.alan.musicplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alan.musicplayer.data.model.Skin
import com.alan.musicplayer.ui.nav.AppNavHost
import com.alan.musicplayer.ui.theme.LocalMusicTheme
import com.alan.musicplayer.ui.theme.style
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // target 35 需处理系统栏 insets；具体图标明暗随皮肤切换
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val app = application as MusicApplication
            val skin by app.container.prefsRepository.skin
                .collectAsStateWithLifecycle(initialValue = Skin.CLASSIC_DARK)
            val scope = rememberCoroutineScope()
            val view = LocalView.current

            // 浅色皮肤用深色系统栏图标，深色皮肤用浅色图标
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = skin.style.isLight
                    isAppearanceLightNavigationBars = skin.style.isLight
                }
            }

            LocalMusicTheme(skin = skin) {
                AppNavHost(
                    skin = skin,
                    onSelectSkin = { selected ->
                        scope.launch { app.container.prefsRepository.setSkin(selected) }
                    },
                )
            }
        }
    }
}
