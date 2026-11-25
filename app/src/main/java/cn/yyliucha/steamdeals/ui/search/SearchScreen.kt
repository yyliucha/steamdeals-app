package cn.yyliucha.steamdeals.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.PendingAction
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.GameDto
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.Routes
import cn.yyliucha.steamdeals.ui.components.EmptyBox
import cn.yyliucha.steamdeals.ui.components.GameCard
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.components.toast
import kotlinx.coroutines.launch

/**
 * DY（站点 docs/ANDROID-POLISH.md §DY）：搜索状态提升到 AppViewModel——
 * 搜索词 / 提交词 / 结果 / 总数 / 分页 / 加载态 / 错误 / 滚动位置全部由这里持有，
 * 进详情、切 Tab、旋转后返回页面保持离开时的状态（不再回到空白搜索页）。
 */
class SearchScreenState(private val vm: AppViewModel) {
    private val api = vm.apiPublic
    private val scope = vm.viewModelScope

    var keyword by mutableStateOf("")
        private set
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf<List<GameDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set
    var total by mutableStateOf(0)
        private set
    var page by mutableStateOf(1)
        private set
    var pages by mutableStateOf(1)
        private set

    /** 滚动位置镜像（列表滚动时由 UI 同步；列表离开组合重建时兜底恢复）。 */
    var scrollIndex = 0
    var scrollOffset = 0

    fun onKeywordChange(value: String) {
        keyword = value
    }

    fun onScroll(index: Int, offset: Int) {
        scrollIndex = index
        scrollOffset = offset
    }

    /** 提交搜索：记录历史 + 请求第一页（历史关键词点击同样走这里）。 */
    fun submitKeyword(raw: String) {
        val kw = raw.trim()
        if (kw.isEmpty()) return
        submitted = kw
        scope.launch { vm.storePublic.addSearchKeyword(kw) }
        doSearch(kw)
    }

    fun doSearch(keyword: String, nextPage: Int = 1, append: Boolean = false) {
        scope.launch {
            if (!append) loading = true
            errorMsg = null
            try {
                val resp = api.games(
                    buildMap {
                        put("region", "cn")
                        put("q", keyword.trim())
                        put("page", nextPage.toString())
                        put("pageSize", "50")
                        put("type", "all")
                        // CV 批（docs/APP-SEARCH-OWNED.md）：搜索不排除已拥有
                        put("owned", "include")
                        // FT（站点 docs/EXPLICIT-RATING.md §2.3）：adult 恒传站点策略——
                        // hide=服务端过滤成人内容（宽）；badge/show=不过滤；选中色情游戏（本地开关）→ rating=explicit。
                        val display = if (vm.siteAdultDisplay.value == "badge" || vm.siteAdultDisplay.value == "show") {
                            vm.siteAdultDisplay.value
                        } else {
                            "hide"
                        }
                        put("adult", display)
                        if (vm.adultEnabled.value && display != "hide") put("rating", "explicit")
                    },
                )
                results = if (append) (results + resp.games).distinctBy { it.id } else resp.games
                total = resp.total
                page = resp.page
                pages = resp.pages
            } catch (e: ApiException) {
                if (!append) {
                    errorMsg = when (e) {
                        is ApiException.Network ->
                            vm.getApplication<android.app.Application>().getString(R.string.network_error)
                        else -> e.message ?: "搜索失败"
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        if (page < pages) doSearch(submitted, page + 1, append = true)
    }

    fun retry() {
        doSearch(submitted)
    }

    /** EA-2（docs/ANDROID-POLISH.md §EA-2）：清空搜索词与结果，回到历史关键词页。 */
    fun resetQuery() {
        keyword = ""
        submitted = ""
    }
}

/**
 * 搜索页（效果图 ④）：自动聚焦、历史关键词、结果卡片（含 DLC）。
 * DY：状态全部走 SearchScreenState（ViewModel 持有），滚动位置 rememberSaveable。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by vm.session.collectAsState()
    val state = vm.searchState

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // DY：滚动位置 rememberSaveable（导航返回/旋转自动恢复），并镜像到 ViewModel。
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> state.onScroll(index, offset) }
    }
    // 列表重建而 Saver 未恢复时（ViewModel 存活、列表短暂离开组合），用镜像兜底定位。
    LaunchedEffect(state.results) {
        if (state.results.isNotEmpty() && state.scrollIndex > 0 &&
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        ) {
            listState.scrollToItem(state.scrollIndex, state.scrollOffset)
        }
    }

    // 搜索历史（本地 DataStore）
    val sessionData by vm.session.collectAsState()
    val historyList = sessionData.searchHistory
    // FT（站点 docs/EXPLICIT-RATING.md §2.3）：色情游戏筛选开关/年龄确认/站点策略（与首页共用 AppViewModel 状态）
    val adultEnabled by vm.adultEnabled.collectAsState()
    val adultConfirmed by vm.adultConfirmed.collectAsState()
    val adultDisplay by vm.siteAdultDisplay.collectAsState()
    var showAdultConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshSiteInfo() // 进入页面刷新站点策略
        focusRequester.requestFocus()
    }
    // FT：色情游戏开关变化 → 已提交搜索词时带新 adult/rating 参数重搜当前词
    LaunchedEffect(adultEnabled) {
        if (state.submitted.isNotEmpty()) state.doSearch(state.submitted)
    }
    // FI：站点策略变化（如 badge→hide）→ hide 强制关开关；有已提交词时重搜
    LaunchedEffect(adultDisplay) {
        if (adultDisplay == "hide" && adultEnabled) vm.setAdultEnabled(false)
        if (state.submitted.isNotEmpty()) state.doSearch(state.submitted)
    }

    // FO（站点 docs/TODO.md §FO）：搜索按钮与软键盘 IME「搜索」共用同一提交逻辑——
    // trim 非空 → 隐藏键盘 → submitKeyword；空关键词回车无动作。
    val submitFromInput = {
        if (state.keyword.trim().isNotEmpty()) {
            keyboard?.hide()
            state.submitKeyword(state.keyword)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            OutlinedTextField(
                value = state.keyword,
                onValueChange = { state.onKeywordChange(it) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                // FO：IME 动作设为「搜索」，回车即提交（与「搜索」按钮同一逻辑；历史页/结果页共此输入框）
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitFromInput() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            TextButton(
                onClick = { submitFromInput() },
            ) {
                Text(stringResource(R.string.tab_search))
            }
        }

        // FT（站点 docs/EXPLICIT-RATING.md §2.3）：搜索页色情游戏 chip 按站点策略显隐
        // （与首页同一开关；开=只看色情游戏，关=恢复全部；首次开启弹年龄确认）
        if (adultDisplay != "hide") {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 2.dp)
                    .horizontalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = adultEnabled,
                    onClick = {
                        if (adultEnabled) {
                            vm.setAdultEnabled(false)
                        } else if (adultConfirmed) {
                            vm.setAdultEnabled(true)
                        } else {
                            showAdultConfirm = true
                        }
                    },
                    label = { Text(stringResource(R.string.filter_adult)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        // EA-1（docs/ANDROID-POLISH.md §EA-1）：下拉刷新——已提交且有结果时 pull 重搜当前词；
        // 未提交时下拉无效（保持历史页）；onRefresh 仅在非加载中触发（不重复请求）。
        PullToRefreshBox(
            isRefreshing = state.loading && state.results.isNotEmpty(),
            onRefresh = { if (state.submitted.isNotEmpty() && !state.loading) state.retry() },
            modifier = Modifier.fillMaxSize(),
        ) {
        when {
            state.submitted.isEmpty() -> {
                // 历史关键词
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.search_history),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (historyList.isNotEmpty()) {
                            TextButton(onClick = { scope.launch { vm.storePublic.clearSearchHistory() } }) {
                                Text(stringResource(R.string.search_clear_history))
                            }
                        }
                    }
                    if (historyList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    } else {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            historyList.forEach { h ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        state.onKeywordChange(h)
                                        state.submitKeyword(h)
                                    },
                                    label = { Text(h) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            state.loading && state.results.isEmpty() -> LoadingBox()
            state.errorMsg != null && state.results.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.errorMsg ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { state.retry() }) {
                    Text(stringResource(R.string.retry))
                }
            }
            state.results.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // EA-2（docs/ANDROID-POLISH.md §EA-2）：空态引导——告诉用户改关键词/拼音/英文名，
                // 并提供「重试」（同词重搜）与「清空搜索」（回到历史关键词页，历史词可一键重搜）。
                Text(
                    text = context.getString(R.string.search_empty_guide, state.submitted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    TextButton(onClick = { state.retry() }) {
                        Text(stringResource(R.string.retry))
                    }
                    TextButton(onClick = { state.resetQuery() }) {
                        Text(stringResource(R.string.search_clear_query))
                    }
                }
            }
            else -> {
                Text(
                    text = context.getString(R.string.search_result_count, state.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.results.size, key = { state.results[it].id }) { i ->
                        val game = state.results[i]
                        GameCard(
                            game = game,
                            onOpen = { navController.navigate(Routes.detail(game.steam_appid ?: game.id)) },
                            showAdultBadge = adultDisplay == "badge",
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
                                    scope.launch {
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
                                            state.doSearch(state.submitted)
                                        } catch (e: ApiException) {
                                            if (e is ApiException.LoginRequired) vm.onLoginRequired(null)
                                            else toast(context, context.getString(R.string.toast_op_failed))
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (state.page < state.pages) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(onClick = { state.loadMore() }) {
                                    Text(stringResource(R.string.load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // FT（站点 docs/EXPLICIT-RATING.md §2.3）：首次开启色情游戏筛选弹「确认已满 18 岁」
    // （确认后 DataStore 记忆不再弹，未确认不切换开关——EB 批交互保留）
    if (showAdultConfirm) {
        AlertDialog(
            onDismissRequest = { showAdultConfirm = false },
            title = { Text(stringResource(R.string.adult_confirm_title)) },
            text = { Text(stringResource(R.string.adult_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showAdultConfirm = false
                    vm.confirmAdult()
                    vm.setAdultEnabled(true)
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdultConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
