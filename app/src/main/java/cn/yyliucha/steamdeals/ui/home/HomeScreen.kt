package cn.yyliucha.steamdeals.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.PendingAction
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.GameDto
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.Routes
import cn.yyliucha.steamdeals.ui.components.ErrorBox
import cn.yyliucha.steamdeals.ui.components.GameCard
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.components.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeFilter(val labelRes: Int) {
    ALL(R.string.filter_all),
    DISCOUNT(R.string.filter_discount),
    HIST_LOW(R.string.filter_hist_low),
    FREE(R.string.filter_free),
    UPCOMING(R.string.filter_upcoming),
}

enum class HomeSort(val labelRes: Int) {
    HEAT(R.string.sort_heat),
    RELEASE(R.string.sort_release),
    PRICE(R.string.sort_price),
}

/**
 * 首页（效果图 ②）：搜索入口 + 筛选 chips + 单列卡片 + 加载更多。
 * 说明：服务端 /api/games 目前只支持 sort（discount/price/name）与 type/released 参数，
 * 效果图的 折扣/史低/免费/未发售 筛选与 热度/发行时间 排序先按已加载数据本地处理，
 * 服务端补 filter 参数后只需替换 applyDisplay（见 README「已知限制」）。
 */
class HomeScreenState(vm: AppViewModel, private val appScope: CoroutineScope) {
    private val api = vm.apiPublic

    val allGames = MutableStateFlow<List<GameDto>>(emptyList())
    val filter = MutableStateFlow(HomeFilter.ALL)
    val sort = MutableStateFlow(HomeSort.HEAT)
    val loading = MutableStateFlow(false)
    val loadingMore = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val hasMore = MutableStateFlow(true)
    var page = 1

    val displayed: kotlinx.coroutines.flow.StateFlow<List<GameDto>> =
        combine(allGames, filter, sort) { games, f, s ->
            val filtered = when (f) {
                HomeFilter.ALL -> games
                HomeFilter.DISCOUNT -> games.filter { (it.discount ?: 0) > 0 }
                HomeFilter.HIST_LOW -> games.filter {
                    it.prev_hist_low != null && it.current_low != null && it.current_low < it.prev_hist_low
                }
                HomeFilter.FREE -> games.filter { it.current_low == 0L }
                HomeFilter.UPCOMING -> games.filter {
                    val d = it.release_date ?: return@filter false
                    val today = java.time.LocalDate.now().toString()
                    d > today
                }
            }
            when (s) {
                HomeSort.HEAT -> filtered.sortedByDescending { it.heat ?: 0.0 }
                HomeSort.RELEASE -> filtered.sortedWith(
                    compareByDescending<GameDto> { it.release_date ?: "" },
                )
                HomeSort.PRICE -> filtered.sortedWith(
                    compareBy<GameDto> { it.current_low ?: Long.MAX_VALUE },
                )
            }
        }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        appScope.launch {
            loading.value = true
            error.value = null
            page = 1
            try {
                val resp = api.games(
                    mapOf(
                        "region" to "cn",
                        "page" to "1",
                        "pageSize" to "50",
                        "type" to "all",
                    ),
                )
                allGames.value = resp.games
                hasMore.value = resp.page < resp.pages
                page = resp.page
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

    fun loadMore() {
        if (loading.value || loadingMore.value || !hasMore.value) return
        appScope.launch {
            loadingMore.value = true
            try {
                val resp = api.games(
                    mapOf(
                        "region" to "cn",
                        "page" to (page + 1).toString(),
                        "pageSize" to "50",
                        "type" to "all",
                    ),
                )
                allGames.value = (allGames.value + resp.games).distinctBy { it.id }
                page = resp.page
                hasMore.value = resp.page < resp.pages
            } catch (_: Exception) {
                // 加载更多失败静默，滚动到底可再触发
            } finally {
                loadingMore.value = false
            }
        }
    }

    fun setFilter(f: HomeFilter) {
        if (filter.value == f) return
        filter.value = f
    }

    fun setSort(s: HomeSort) {
        if (sort.value == s) return
        sort.value = s
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, navController: NavHostController) {
    val appScope = rememberCoroutineScope()
    val state = remember { HomeScreenState(vm, appScope) }
    val context = LocalContext.current
    val session by vm.session.collectAsState()
    val displayed by state.displayed.collectAsState()
    val loading by state.loading.collectAsState()
    val loadingMore by state.loadingMore.collectAsState()
    val error by state.error.collectAsState()
    val hasMore by state.hasMore.collectAsState()
    val filter by state.filter.collectAsState()
    val sort by state.sort.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (state.allGames.value.isEmpty()) state.refresh()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：品牌标题 + 搜索入口
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            // 搜索入口（点击跳搜索页，效果图 ②）
            Surface(
                onClick = {
                    navController.navigate(Routes.SEARCH) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // 筛选 chips
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { state.setFilter(f) },
                        label = { Text(stringResource(f.labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            // 排序
            Row(
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSort.entries.forEach { s ->
                    TextButton(
                        onClick = { state.setSort(s) },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = stringResource(s.labelRes),
                            color = if (sort == s) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        when {
            loading && displayed.isEmpty() -> LoadingBox()
            error != null && displayed.isEmpty() -> ErrorBox(
                message = error ?: "",
                onRetry = { state.refresh() },
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(displayed.size, key = { displayed[it].id }) { i ->
                    val game = displayed[i]
                    GameCard(
                        game = game,
                        onOpen = { navController.navigate(Routes.detail(game.steam_appid ?: game.id)) },
                        onWishlistToggle = {
                            if (!session.isLoggedIn) {
                                vm.onLoginRequired(
                                    PendingAction(
                                        type = PendingAction.TYPE_WISHLIST,
                                        appid = game.steam_appid ?: game.id,
                                        route = Routes.detail(game.steam_appid ?: game.id),
                                    ),
                                )
                            } else {
                                appScope.launch {
                                    try {
                                        val appid = game.steam_appid ?: game.id
                                        if (game.wishlisted) {
                                            if (game.steam_appid != null) vm.apiPublic.removeWishlist(appid)
                                            else vm.apiPublic.removeWishlistById(game.id)
                                            toast(context, context.getString(R.string.toast_wishlist_removed))
                                        } else {
                                            vm.apiPublic.addWishlist(appid)
                                            toast(context, context.getString(R.string.toast_wishlist_added))
                                        }
                                        state.refresh()
                                    } catch (e: ApiException) {
                                        if (e is ApiException.LoginRequired) {
                                            vm.onLoginRequired(null)
                                        } else {
                                            toast(context, context.getString(R.string.toast_op_failed))
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
                // 加载更多
                if (displayed.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                loadingMore -> Text(
                                    stringResource(R.string.loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                hasMore -> TextButton(onClick = { state.loadMore() }) {
                                    Text(stringResource(R.string.load_more))
                                }
                                else -> Text(
                                    stringResource(R.string.no_more),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



