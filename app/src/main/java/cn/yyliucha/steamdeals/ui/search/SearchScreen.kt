package cn.yyliucha.steamdeals.ui.search

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
 * 搜索页（效果图 ④）：自动聚焦、历史关键词、结果卡片（含 DLC）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by vm.session.collectAsState()

    var keyword by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GameDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var pages by remember { mutableStateOf(1) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 搜索历史（本地 DataStore）
    val sessionData by vm.session.collectAsState()
    val historyList = sessionData.searchHistory

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun doSearch(keyword: String, nextPage: Int = 1, append: Boolean = false) {
        scope.launch {
            if (!append) loading = true
            errorMsg = null
            try {
                val resp = vm.apiPublic.games(
                    mapOf(
                        "region" to "cn",
                        "q" to keyword.trim(),
                        "page" to nextPage.toString(),
                        "pageSize" to "50",
                        "type" to "all",
                        // CV 批（docs/APP-SEARCH-OWNED.md）：搜索不排除已拥有
                        "owned" to "include",
                    ),
                )
                results = if (append) (results + resp.games).distinctBy { it.id } else resp.games
                total = resp.total
                page = resp.page
                pages = resp.pages
            } catch (e: ApiException) {
                if (!append) {
                    errorMsg = when (e) {
                        is ApiException.Network -> context.getString(R.string.network_error)
                        else -> e.message ?: "搜索失败"
                    }
                }
            } finally {
                loading = false
            }
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
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            TextButton(
                onClick = {
                    if (keyword.trim().isNotEmpty()) {
                        keyboard?.hide()
                        submitted = keyword.trim()
                        scope.launch { vm.storePublic.addSearchKeyword(keyword.trim()) }
                        doSearch(keyword.trim())
                    }
                },
            ) {
                Text(stringResource(R.string.tab_search))
            }
        }

        when {
            submitted.isEmpty() -> {
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
                                        keyword = h
                                        submitted = h
                                        doSearch(h)
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
            loading && results.isEmpty() -> LoadingBox()
            errorMsg != null && results.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = errorMsg ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { doSearch(submitted) }) {
                    Text(stringResource(R.string.retry))
                }
            }
            results.isEmpty() -> EmptyBox(context.getString(R.string.empty_list))
            else -> {
                Text(
                    text = context.getString(R.string.search_result_count, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results.size, key = { results[it].id }) { i ->
                        val game = results[i]
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
                                            doSearch(submitted)
                                        } catch (e: ApiException) {
                                            if (e is ApiException.LoginRequired) vm.onLoginRequired(null)
                                            else toast(context, context.getString(R.string.toast_op_failed))
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (page < pages) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(onClick = { doSearch(submitted, page + 1, append = true) }) {
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
