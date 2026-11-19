package cn.yyliucha.steamdeals.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.GameDto
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.Routes
import cn.yyliucha.steamdeals.ui.components.EmptyBox
import cn.yyliucha.steamdeals.ui.components.GameCard
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.components.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WishlistFilter(val labelRes: Int) {
    ALL(R.string.filter_all),
    DROPPED(R.string.wishlist_filter_dropped),
    HIST_LOW(R.string.filter_hist_low),
}

class WishlistScreenState(vm: AppViewModel, private val appScope: CoroutineScope) {
    private val api = vm.apiPublic

    val games = MutableStateFlow<List<GameDto>>(emptyList())
    val filter = MutableStateFlow(WishlistFilter.ALL)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    var page = 1
    var pages = 1

    /** DY：账号切换（登录/退出）时复位，避免闪现上一账号的愿望单。 */
    fun reset() {
        games.value = emptyList()
        filter.value = WishlistFilter.ALL
        loading.value = false
        error.value = null
        page = 1
        pages = 1
    }

    val displayed = combine(games, filter) { games, f ->
        when (f) {
            WishlistFilter.ALL -> games
            WishlistFilter.DROPPED -> games.filter {
                it.current_low != null && it.prev_hist_low != null && it.current_low < it.prev_hist_low
            }
            WishlistFilter.HIST_LOW -> games.filter {
                it.current_low != null && it.hist_low != null && it.current_low <= it.hist_low
            }
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        appScope.launch {
            loading.value = true
            error.value = null
            try {
                // 愿望单列表 = 游戏列表的 wishlist=true 维度（web 契约：POST /api/games/search）
                val body = """
                    {"region":"cn","page":1,"pageSize":100,"sort":"discount","interest":"all",
                     "owned":"exclude","blocked":"exclude","type":"all","released":"all",
                     "marks":"any","adult":"hide","wishlist":true}
                """.trimIndent()
                val resp = api.gamesSearch(body)
                games.value = resp.games
                page = resp.page
                pages = resp.pages
            } catch (e: ApiException) {
                error.value = when (e) {
                    is ApiException.Network -> "网络错误，请检查网络后重试"
                    else -> e.message ?: "加载失败"
                }
            } finally {
                loading.value = false
            }
        }
    }
}

/**
 * 愿望单（效果图 ⑤）：未登录 → 登录引导页；已登录 → 与 web 账号共享的愿望单列表
 * （已降价/史低筛选 + 移除）。
 */
@Composable
fun WishlistScreen(vm: AppViewModel, navController: NavHostController) {
    val appScope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by vm.session.collectAsState()
    // DY（docs/ANDROID-POLISH.md §DY）：愿望单数据/筛选提升到 AppViewModel；
    // 滚动位置 rememberSaveable（进详情/切 Tab 返回保持位置）。
    val state = vm.wishlistState
    val displayed by state.displayed.collectAsState()
    val loading by state.loading.collectAsState()
    val filter by state.filter.collectAsState()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LaunchedEffect(session.isLoggedIn) {
        if (session.isLoggedIn) state.refresh()
    }

    if (!session.isLoggedIn) {
        LoginGuide(
            text = stringResource(R.string.wishlist_login_guide),
            onLogin = { navController.navigate(Routes.LOGIN) },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.tab_wishlist),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WishlistFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { state.filter.value = f },
                    label = { Text(stringResource(f.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        when {
            loading && displayed.isEmpty() -> LoadingBox()
            displayed.isEmpty() -> EmptyBox(stringResource(R.string.wishlist_empty))
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(displayed.size, key = { displayed[it].id }) { i ->
                    val game = displayed[i]
                    GameCard(
                        game = game,
                        onOpen = { navController.navigate(Routes.detail(game.steam_appid ?: game.id)) },
                        onWishlistToggle = {
                            appScope.launch {
                                try {
                                    if (game.steam_appid != null) {
                                        vm.apiPublic.removeWishlist(game.steam_appid)
                                    } else {
                                        vm.apiPublic.removeWishlistById(game.id)
                                    }
                                    toast(context, context.getString(R.string.toast_wishlist_removed))
                                    state.refresh()
                                } catch (e: ApiException) {
                                    if (e is ApiException.LoginRequired) vm.onLoginRequired(null)
                                    else toast(context, context.getString(R.string.toast_op_failed))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun LoginGuide(text: String, onLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onLogin,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text(stringResource(R.string.login_required_go))
        }
    }
}



