package cn.yyliucha.steamdeals.ui

import android.app.Activity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.ui.about.AboutScreen
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.detail.DetailScreen
import cn.yyliucha.steamdeals.ui.home.HomeScreen
import cn.yyliucha.steamdeals.ui.login.LoginRequiredSheet
import cn.yyliucha.steamdeals.ui.login.LoginScreen
import cn.yyliucha.steamdeals.ui.me.MeScreen
import cn.yyliucha.steamdeals.ui.notifications.AnnouncementsOverlay
import cn.yyliucha.steamdeals.ui.profile.ProfileEditScreen
import cn.yyliucha.steamdeals.ui.password.ChangePasswordScreen
import cn.yyliucha.steamdeals.ui.register.RegisterScreen
import cn.yyliucha.steamdeals.ui.search.SearchScreen
import cn.yyliucha.steamdeals.ui.update.UpdateDialogHost
import cn.yyliucha.steamdeals.ui.wishlist.WishlistScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val WISHLIST = "wishlist"
    const val ME = "me"
    const val DETAIL = "detail/{appid}"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CHANGE_PASSWORD = "changePassword"
    const val ABOUT = "about"
    // CX 批（docs/USER-PROFILE-EDIT.md）：编辑资料页
    const val PROFILE_EDIT = "profileEdit"

    fun detail(appid: Long) = "detail/$appid"

    val TAB_ROUTES = setOf(HOME, SEARCH, WISHLIST, ME)
}

private data class TabItem(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppRoot(vm: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val banState by vm.banState.collectAsState()
    val context = LocalContext.current

    val tabs = remember {
        listOf(
            TabItem(Routes.HOME, R.string.tab_home, Icons.Filled.Home),
            TabItem(Routes.SEARCH, R.string.tab_search, Icons.Filled.Search),
            TabItem(Routes.WISHLIST, R.string.tab_wishlist, Icons.Filled.Favorite),
            TabItem(Routes.ME, R.string.tab_me, Icons.Filled.Person),
        )
    }

    when (banState) {
        is BanState.Checking -> {
            LoadingBox()
            return
        }
        is BanState.Banned -> {
            // 启动封禁检查命中 → 不可取消对话框 → 退出（docs/APP-NATIVE.md §3.4；无申诉入口）
            AlertDialog(
                onDismissRequest = { /* 不可取消 */ },
                title = { Text(stringResource(R.string.ban_dialog_title)) },
                confirmButton = {
                    TextButton(onClick = {
                        (context as? Activity)?.finishAffinity()
                        (context as? Activity)?.finish()
                        // §3.4：点击后退出 App（finishAffinity/结束进程）
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }) {
                        Text(stringResource(R.string.ban_dialog_confirm))
                    }
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
            )
            return
        }
        else -> Unit
    }

    Scaffold(
        bottomBar = {
            // EA-6（docs/ANDROID-POLISH.md §EA-6）：全局弱网提示条（非阻塞，位于底栏上方；可重试）
            NetworkBanner(vm)
            if (currentRoute in Routes.TAB_ROUTES) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) { HomeScreen(vm, navController) }
            composable(Routes.SEARCH) { SearchScreen(vm, navController) }
            composable(Routes.WISHLIST) { WishlistScreen(vm, navController) }
            composable(Routes.ME) { MeScreen(vm, navController) }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("appid") { type = NavType.LongType }),
            ) { entry ->
                val appid = entry.arguments?.getLong("appid") ?: 0L
                DetailScreen(vm, navController, appid)
            }
            composable(Routes.LOGIN) { LoginScreen(vm, navController) }
            composable(Routes.REGISTER) { RegisterScreen(vm, navController) }
            composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(vm, navController) }
            composable(Routes.PROFILE_EDIT) { ProfileEditScreen(vm, navController) }
            composable(Routes.ABOUT) { AboutScreen(navController) }
        }
    }

    // 登录门槛弹层（游客点用户功能 → 「去登录」/「取消」）
    LoginRequiredSheet(vm, navController)
    // 内置更新对话框宿主
    UpdateDialogHost(vm)
    // 公告列表弹层（DC：首页铃铛 + 「我的 → 公告」共用入口）
    AnnouncementsOverlay(vm)
}

/**
 * EA-6（docs/ANDROID-POLISH.md §EA-6）：全局弱网提示条——网络层失败自动亮起、任一请求成功自动隐藏；
 * 「重试」轻量 ping /api/app/status，恢复后 toast「网络已恢复」。不遮挡任何页面内容。
 */
@Composable
private fun NetworkBanner(vm: AppViewModel) {
    val show by vm.networkBanner.collectAsState()
    if (!show) return
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.network_banner_msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.retryNetwork() }) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
