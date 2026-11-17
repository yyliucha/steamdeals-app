package cn.yyliucha.steamdeals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cn.yyliucha.steamdeals.ui.AppRoot
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.theme.SteamDealsTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by vm.themeMode.collectAsState()
            SteamDealsTheme(themeMode = themeMode) {
                AppRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 启动 + 回前台检查更新（24h 节流在 ViewModel 内）
        vm.checkUpdate(manual = false)
    }
}
