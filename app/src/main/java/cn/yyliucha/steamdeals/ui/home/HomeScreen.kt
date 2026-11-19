package cn.yyliucha.steamdeals.ui.home

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import cn.yyliucha.steamdeals.ui.components.EmptyBox
import cn.yyliucha.steamdeals.ui.components.ErrorBox
import cn.yyliucha.steamdeals.ui.components.GameCard
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.components.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class HomeFilter(val labelRes: Int) {
    ALL(R.string.filter_all),
    DISCOUNT(R.string.filter_discount),
    HIST_LOW(R.string.filter_hist_low),
    FREE(R.string.filter_free),
    UPCOMING(R.string.filter_upcoming),
    OWNED(R.string.filter_owned),
}

enum class HomeSort(val labelRes: Int) {
    HEAT(R.string.sort_heat),
    RELEASE(R.string.sort_release),
    PRICE(R.string.sort_price),
}

/**
 * DA 批（站点 docs/APP-FILTER-SERVER-SIDE.md §2/§3）：筛选/排序 → 服务端参数映射（纯函数，可单测）。
 * 服务端契约：discountOnly / histLowOnly / freeOnly / unreleased 四个开关互斥（优先级在服务端归一）；
 * sort=heat|release|price；owned=only 是「已拥有」chip，其余 chip 沿用 CV 批口径 owned=include（不排除已拥有）。
 */
object HomeFilterParams {
    fun filterParam(filter: HomeFilter): Pair<String, String> = when (filter) {
        HomeFilter.ALL -> "owned" to "include"
        HomeFilter.DISCOUNT -> "discountOnly" to "1"
        HomeFilter.HIST_LOW -> "histLowOnly" to "1"
        HomeFilter.FREE -> "freeOnly" to "1"
        HomeFilter.UPCOMING -> "unreleased" to "1"
        HomeFilter.OWNED -> "owned" to "only"
    }

    fun sortParam(sort: HomeSort): String = when (sort) {
        HomeSort.HEAT -> "heat"
        HomeSort.RELEASE -> "release"
        HomeSort.PRICE -> "price"
    }

    fun paramsFor(filter: HomeFilter, sort: HomeSort, page: Int, pageSize: Int): Map<String, String> {
        val (key, value) = filterParam(filter)
        return buildMap {
            put("region", "cn")
            put("page", page.toString())
            put("pageSize", pageSize.toString())
            put("type", "all")
            put("sort", sortParam(sort))
            put("owned", "include") // CV 批：所有查询不排除已拥有
            put(key, value)
        }
    }
}

/**
 * 首页（效果图 ②）：搜索入口 + 筛选 chips + 单列卡片 + 加载更多。
 * DA 批（docs/APP-FILTER-SERVER-SIDE.md）：筛选与排序全部服务端化——
 * chip/排序变更 → 带参数重新请求第一页（不追加旧数据）；列表直接展示服务端结果（不再本地过滤已加载数据）。
 */
class HomeScreenState(vm: AppViewModel, private val appScope: CoroutineScope) {
    private val vm = vm
    private val api = vm.apiPublic

    val allGames = MutableStateFlow<List<GameDto>>(emptyList())
    val filter = MutableStateFlow(HomeFilter.ALL)
    val sort = MutableStateFlow(HomeSort.HEAT)
    val loading = MutableStateFlow(false)
    val loadingMore = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val hasMore = MutableStateFlow(true)
    /** 已绑定 Steam（/api/me 的 steamBound）：「已拥有」chip 仅登录且绑定后显示。 */
    val steamBound = MutableStateFlow(false)
    var page = 1
    private var requestSeq = 0

    /** 列表即服务端筛选排序后的结果（分页按当前 filter/sort 追加）。 */
    val displayed: StateFlow<List<GameDto>> = allGames

    fun refresh() {
        appScope.launch {
            loading.value = true
            error.value = null
            page = 1
            val seq = ++requestSeq
            try {
                val resp = api.games(HomeFilterParams.paramsFor(filter.value, sort.value, 1, 50))
                if (seq != requestSeq) return@launch // 更晚的请求已接管，丢弃本次结果
                allGames.value = resp.games
                hasMore.value = resp.page < resp.pages
                page = resp.page
            } catch (e: ApiException) {
                if (seq != requestSeq) return@launch
                error.value = when (e) {
                    is ApiException.Network -> "网络错误，请检查网络后重试"
                    else -> e.message ?: "加载失败"
                }
            } finally {
                if (seq == requestSeq) loading.value = false
            }
        }
    }

    fun loadMore() {
        if (loading.value || loadingMore.value || !hasMore.value) return
        appScope.launch {
            loadingMore.value = true
            val seq = requestSeq
            try {
                val resp = api.games(HomeFilterParams.paramsFor(filter.value, sort.value, page + 1, 50))
                if (seq != requestSeq) return@launch
                allGames.value = (allGames.value + resp.games).distinctBy { it.id }
                page = resp.page
                hasMore.value = resp.page < resp.pages
            } catch (_: Exception) {
                // 加载更多失败静默，滚动到底可再触发
            } finally {
                if (seq == requestSeq) loadingMore.value = false
            }
        }
    }

    fun setFilter(f: HomeFilter) {
        if (filter.value == f) return
        filter.value = f
        refresh() // chip 变更 → 服务端筛选 + 分页重置（回到第一页，不追加旧数据）
    }

    fun setSort(s: HomeSort) {
        if (sort.value == s) return
        sort.value = s
        refresh() // 排序变更同样服务端化 + 分页重置
    }

    /** 登录态变化 / 进入首页时刷新绑定状态；未登录直接复位。 */
    fun refreshSteamBound() {
        if (!vm.session.value.isLoggedIn) {
            steamBound.value = false
            return
        }
        appScope.launch {
            runCatching { api.me() }.onSuccess { steamBound.value = it.steamBound }
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, navController: NavHostController) {
    val appScope = rememberCoroutineScope()
    // DY（docs/ANDROID-POLISH.md §DY）：首页数据/筛选/排序提升到 AppViewModel——
    // 进详情/切 Tab 后返回保持离开时的筛选与已加载数据；滚动位置 rememberSaveable。
    val state = vm.homeState
    val context = LocalContext.current
    val session by vm.session.collectAsState()
    val displayed by state.displayed.collectAsState()
    val loading by state.loading.collectAsState()
    val loadingMore by state.loadingMore.collectAsState()
    val error by state.error.collectAsState()
    val hasMore by state.hasMore.collectAsState()
    val filter by state.filter.collectAsState()
    val sort by state.sort.collectAsState()
    val steamBound by state.steamBound.collectAsState()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    // DC：公告未读数（0 隐藏角标；首页铃铛 → 公告弹层）
    val unreadAnnouncements by vm.announcementsState.unreadCount.collectAsState()

    LaunchedEffect(Unit) {
        if (state.allGames.value.isEmpty()) state.refresh()
        if (vm.announcementsState.announcements.value.isEmpty()) vm.announcementsState.refresh()
    }
    // 登录态变化（登录/退出）时刷新「已拥有」chip 显隐依据；登录后进入首页也拉一次
    LaunchedEffect(session.isLoggedIn) {
        state.refreshSteamBound()
        // 登录态切换影响 owned 口径（include/only 都随登录变化）→ 重拉当前筛选
        if (state.allGames.value.isNotEmpty() || state.error.value != null) state.refresh()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：品牌标题 + 公告铃铛 + 搜索入口
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp, bottom = 4.dp),
                )
                // 公告铃铛：未读数角标（0 隐藏）→ 点开公告列表弹层
                BadgedBox(
                    badge = {
                        if (unreadAnnouncements > 0) {
                            Badge { Text(unreadAnnouncements.toString()) }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    IconButton(onClick = { vm.announcementsState.open() }) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.notif_title),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
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

            // 筛选 chips（DA 批：6 个；「已拥有」仅登录且已绑定 Steam 时显示）
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeFilter.entries.forEach { f ->
                    // DA 批（docs/APP-FILTER-SERVER-SIDE.md §3）：已拥有 chip 仅登录+已绑定 Steam 显示
                    if (f == HomeFilter.OWNED && !(session.isLoggedIn && steamBound)) return@forEach
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
            displayed.isEmpty() -> EmptyBox(
                text = if (filter == HomeFilter.OWNED) {
                    stringResource(R.string.home_owned_empty_guide)
                } else {
                    stringResource(R.string.home_empty_filter)
                },
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
