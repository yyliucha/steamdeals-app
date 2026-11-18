package cn.yyliucha.steamdeals.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.PendingAction
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.DlcItemDto
import cn.yyliucha.steamdeals.data.api.GameDetailResponse
import cn.yyliucha.steamdeals.data.api.OfferDto
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.Routes
import cn.yyliucha.steamdeals.ui.components.Badge
import cn.yyliucha.steamdeals.ui.components.ErrorBox
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.components.PriceHistoryChart
import cn.yyliucha.steamdeals.ui.components.PriceHistoryModel
import cn.yyliucha.steamdeals.ui.components.PriceText
import cn.yyliucha.steamdeals.ui.components.formatPct
import cn.yyliucha.steamdeals.ui.components.formatPrice
import cn.yyliucha.steamdeals.ui.components.nameCopyText
import cn.yyliucha.steamdeals.ui.components.openCustomTab
import cn.yyliucha.steamdeals.ui.components.ownedByFullText
import cn.yyliucha.steamdeals.ui.components.steamImageUrl
import cn.yyliucha.steamdeals.ui.components.steamStoreUrl
import cn.yyliucha.steamdeals.ui.components.toast
import cn.yyliucha.steamdeals.ui.theme.BrandPurple
import cn.yyliucha.steamdeals.ui.theme.SuccessGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DetailScreenState(vm: AppViewModel, private val appScope: CoroutineScope, private val appid: Long) {
    private val api = vm.apiPublic

    var detail by mutableStateOf<GameDetailResponse?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var dlcItems by mutableStateOf<List<DlcItemDto>>(emptyList())
    var dlcTotal by mutableStateOf(0)
    var dlcSynced by mutableStateOf(false)
    var dlcPage by mutableStateOf(1)
    var dlcLoading by mutableStateOf(false)
    var dlcError by mutableStateOf(false)

    fun refresh() {
        appScope.launch {
            loading = true
            error = null
            try {
                detail = api.gameDetail(appid)
            } catch (e: ApiException) {
                error = when (e) {
                    is ApiException.Network -> "网络错误，请检查网络后重试"
                    is ApiException.Http -> if (e.code == 404) "没有该游戏" else e.error ?: "加载失败"
                    else -> e.message ?: "加载失败"
                }
            } finally {
                loading = false
            }
        }
    }

    /** CM-2：DLC 分页 10 条/页（对齐 Web GAME-DLC.md §3.4），每次请求整页、不追加。 */
    fun loadDlcPage(page: Int) {
        if (dlcLoading) return
        appScope.launch {
            dlcLoading = true
            dlcError = false
            try {
                val resp = api.dlcList(appid, page = page, pageSize = 10)
                dlcItems = resp.items
                dlcTotal = resp.total
                dlcSynced = resp.synced
                dlcPage = page
            } catch (_: Exception) {
                // DLC 加载失败不阻塞详情主体，区块内提示重试
                dlcError = true
            } finally {
                dlcLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, navController: NavHostController, appid: Long) {
    val appScope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = remember(appid) { DetailScreenState(vm, appScope, appid) }
    val session by vm.session.collectAsState()
    val detail = state.detail

    LaunchedEffect(appid) {
        state.refresh()
        state.loadDlcPage(1)
    }

    // 登录后自动完成刚才的操作（pendingAction）：结果回到本页后按 appid 匹配刷新 + Toast
    LaunchedEffect(Unit) {
        vm.pendingActionResult.collect { (action, ok) ->
            if (action.appid == appid) {
                if (ok) {
                    val msg = when (action.type) {
                        PendingAction.TYPE_WISHLIST -> context.getString(R.string.toast_wishlist_added)
                        PendingAction.TYPE_INTEREST -> context.getString(R.string.toast_interested)
                        PendingAction.TYPE_UNINTEREST -> context.getString(R.string.toast_uninterested)
                        else -> ""
                    }
                    if (msg.isNotEmpty()) toast(context, msg)
                } else {
                    toast(context, context.getString(R.string.toast_op_failed))
                }
                state.refresh()
            }
        }
    }

    // 登录状态变化 → 刷新详情（wishlisted/interested 是登录用户字段）
    val loggedIn = session.isLoggedIn
    LaunchedEffect(loggedIn) {
        if (state.detail != null) state.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.game?.name ?: stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (detail != null) {
                val game = detail.game
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                ) {
                    // CV 批（docs/APP-SEARCH-OWNED.md 决策 4）：已拥有终态——只留「去 Steam 商店」
                    if (game.owned) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Button(
                                onClick = { openCustomTab(context, steamStoreUrl(game.steam_appid)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    stringResource(R.string.act_buy_steam),
                                    maxLines = 1,
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ActionToggle(
                            text = if (game.user_interested == true) {
                                stringResource(R.string.act_interested_on)
                            } else {
                                stringResource(R.string.act_interested)
                            },
                            selected = game.user_interested == true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val interested = game.user_interested != true
                                if (!session.isLoggedIn) {
                                    vm.onLoginRequired(
                                        PendingAction(
                                            type = if (interested) PendingAction.TYPE_INTEREST
                                            else PendingAction.TYPE_UNINTEREST,
                                            appid = appid,
                                            route = Routes.detail(appid),
                                        ),
                                    )
                                } else {
                                    appScope.launch {
                                        try {
                                            vm.apiPublic.setInterest(appid, interested)
                                            toast(
                                                context,
                                                context.getString(
                                                    if (interested) R.string.toast_interested
                                                    else R.string.toast_uninterested,
                                                ),
                                            )
                                            state.refresh()
                                        } catch (e: ApiException) {
                                            if (e is ApiException.LoginRequired) vm.onLoginRequired(null)
                                            else toast(context, context.getString(R.string.toast_op_failed))
                                        }
                                    }
                                }
                            },
                        )
                        ActionToggle(
                            text = if (game.user_interested == false) {
                                stringResource(R.string.act_uninterested_on)
                            } else {
                                stringResource(R.string.act_uninterested)
                            },
                            selected = game.user_interested == false,
                            modifier = Modifier.weight(1f),
                            danger = true,
                            onClick = {
                                val un = game.user_interested != false
                                if (!session.isLoggedIn) {
                                    vm.onLoginRequired(
                                        PendingAction(
                                            type = PendingAction.TYPE_UNINTEREST,
                                            appid = appid,
                                            route = Routes.detail(appid),
                                        ),
                                    )
                                } else {
                                    appScope.launch {
                                        try {
                                            vm.apiPublic.setInterest(appid, !un)
                                            toast(
                                                context,
                                                context.getString(
                                                    if (un) R.string.toast_uninterested
                                                    else R.string.toast_interested,
                                                ),
                                            )
                                            state.refresh()
                                        } catch (e: ApiException) {
                                            if (e is ApiException.LoginRequired) vm.onLoginRequired(null)
                                            else toast(context, context.getString(R.string.toast_op_failed))
                                        }
                                    }
                                }
                            },
                        )
                        IconButton(
                            onClick = {
                                if (!session.isLoggedIn) {
                                    vm.onLoginRequired(
                                        PendingAction(
                                            type = PendingAction.TYPE_WISHLIST,
                                            appid = appid,
                                            route = Routes.detail(appid),
                                        ),
                                    )
                                } else {
                                    appScope.launch {
                                        try {
                                            if (game.wishlisted) {
                                                if (game.steam_appid != null) {
                                                    vm.apiPublic.removeWishlist(game.steam_appid)
                                                } else {
                                                    vm.apiPublic.removeWishlistById(game.id)
                                                }
                                                toast(context, context.getString(R.string.toast_wishlist_removed))
                                            } else {
                                                vm.apiPublic.addWishlist(game.steam_appid ?: game.id)
                                                toast(context, context.getString(R.string.toast_wishlist_added))
                                            }
                                            state.refresh()
                                        } catch (e: ApiException) {
                                            if (e is ApiException.LoginRequired) vm.onLoginRequired(null)
                                            else toast(context, context.getString(R.string.toast_op_failed))
                                        }
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (game.wishlisted) Icons.Filled.Favorite
                                else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.act_wishlist),
                                tint = if (game.wishlisted) BrandPurple
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { openCustomTab(context, steamStoreUrl(game.steam_appid)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.act_buy_steam),
                                maxLines = 1,
                            )
                        }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading && detail == null -> LoadingBox(Modifier.padding(padding))
            state.error != null && detail == null -> ErrorBox(
                message = state.error ?: "",
                onRetry = { state.refresh() },
                modifier = Modifier.padding(padding),
            )
            detail == null -> LoadingBox(Modifier.padding(padding))
            else -> DetailContent(
                detail = detail,
                state = state,
                context = context,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ActionToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val container = when {
        danger && selected -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        danger && selected -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = container,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: GameDetailResponse,
    state: DetailScreenState,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val game = detail.game
    val isNewHistLow = game.prev_hist_low != null && game.current_low != null &&
        game.current_low < game.prev_hist_low

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // 封面
        item {
            val cover = steamImageUrl(game.steam_appid)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(460f / 215f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = game.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = game.name.take(1),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                // CM-4：标题旁复制按钮（中英文两行，缺一回退单行，对齐 Web AO 批 nameCopyText）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { copyGameNameToClipboard(context, game.name, game.name_en) }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.detail_copy_name),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!game.name_en.isNullOrBlank() && game.name_en != game.name) {
                    Text(
                        text = game.name_en,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // CV 批（docs/APP-SEARCH-OWNED.md 决策 3）：已拥有显示**全部**账号名（与 Web 详情列全一致）
                if (game.owned) {
                    Text(
                        text = ownedByFullText(game.owned_by),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // 徽章
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isNewHistLow) Badge("新史低", SuccessGreen)
                    when (game.steam_status) {
                        "region_locked" -> Badge("国区不可售", MaterialTheme.colorScheme.error)
                        "coming_soon" -> Badge("未发售", MaterialTheme.colorScheme.tertiary)
                    }
                    if (game.app_type == "dlc") Badge("DLC", BrandPurple)
                    if (game.mature_flag) Badge("成熟内容", MaterialTheme.colorScheme.tertiary)
                    if (game.adult_flag) Badge("成人内容", MaterialTheme.colorScheme.error)
                    if (game.site_wishlisted && !game.wishlisted) Badge("愿望单", BrandPurple)
                }

                // 指标：当前最低 / 历史最低 / 折扣
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCell(
                        label = stringResource(R.string.detail_metric_current),
                        value = formatPrice(game.current_low, game.currency),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCell(
                        label = stringResource(R.string.detail_metric_hist),
                        value = formatPrice(game.hist_low, game.currency),
                        valueColor = SuccessGreen,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCell(
                        label = stringResource(R.string.detail_metric_discount),
                        value = if ((game.discount ?: 0) > 0) formatPct(game.discount) else "—",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }

                // 数据时间
                val seen = game.last_seen_at?.replace('T', ' ')?.take(16)
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = context.getString(R.string.detail_days_count, game.day_count ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (seen != null) {
                        Text(
                            text = context.getString(R.string.detail_updated, seen),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 描述
                Text(
                    text = game.description?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detail_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )

                // 元信息
                InfoLine(R.string.detail_developers, game.developers.joinToString("、"))
                InfoLine(R.string.detail_publishers, game.publishers.joinToString("、"))
                InfoLine(R.string.detail_genres, game.genres.joinToString("、"))
                InfoLine(R.string.detail_tags, game.tags.joinToString("、"))
            }
        }

        // 各区服报价
        item {
            SectionCard(title = stringResource(R.string.detail_section_offers)) {
                if (detail.offers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_no_offers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    detail.offers.forEachIndexed { i, offer ->
                        OfferRow(offer = offer, context = context)
                        if (i < detail.offers.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // 价格历史（CM-3：区间 30/90/全部 + 指标条 + 史低参考线，对齐 Web）
        item {
            SectionCard(title = stringResource(R.string.detail_section_history)) {
                if (detail.series.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_no_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PriceHistorySection(game = game, series = detail.series)
                }
            }
        }

        // DLC（CM-2：仅 total>0 显示区块；分页 10 条/页；无 DLC/非 Steam 不显示）
        if (detail.dlc.total > 0) {
            item {
                SectionCard(title = context.getString(R.string.detail_dlc_title, detail.dlc.total)) {
                    Text(
                        text = stringResource(R.string.detail_dlc_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    when {
                        state.dlcLoading && state.dlcItems.isEmpty() ->
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        state.dlcError && state.dlcItems.isEmpty() ->
                            Text(
                                text = stringResource(R.string.detail_dlc_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        else -> {
                            state.dlcItems.forEachIndexed { i, dlc ->
                                DlcRow(dlc = dlc, context = context)
                                if (i < state.dlcItems.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                }
                            }
                            val totalPages = ((detail.dlc.total + 9) / 10).coerceAtLeast(1)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { state.loadDlcPage(state.dlcPage - 1) },
                                    enabled = state.dlcPage > 1 && !state.dlcLoading,
                                ) {
                                    Text(stringResource(R.string.detail_dlc_prev))
                                }
                                Text(
                                    text = context.getString(R.string.detail_dlc_page, state.dlcPage, totalPages),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                TextButton(
                                    onClick = { state.loadDlcPage(state.dlcPage + 1) },
                                    enabled = state.dlcPage < totalPages && !state.dlcLoading,
                                ) {
                                    Text(stringResource(R.string.detail_dlc_next))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** CM-4：复制中英文名到系统剪贴板并 Toast 提示（对齐 Web AO 批 nameCopyText 口径）。 */
private fun copyGameNameToClipboard(context: android.content.Context, name: String?, nameEn: String?) {
    val text = nameCopyText(name, nameEn)
    if (text.isEmpty()) return
    runCatching {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(name, text))
        toast(context, context.getString(R.string.toast_copied))
    }
}

/**
 * CM-3：价格历史 = 指标条（区间最低/最高/当前较史低/记录天数）+ 区间切换（近 30 天/近 90 天/全部，
 * 默认 90）+ 趋势线（史低虚线参考线 + 全部史低日绿点）+ 底部说明。口径与 Web 完全一致
 * （docs/PRICE-HISTORY-REPORT.md §11；计算全部走 PriceHistoryModel，可单测）。
 */
@Composable
private fun PriceHistorySection(game: cn.yyliucha.steamdeals.data.api.GameDto, series: List<cn.yyliucha.steamdeals.data.api.SeriesPoint>) {
    var range by remember(series) { mutableStateOf(PriceHistoryModel.DEFAULT_RANGE) }
    val filtered = remember(series, range) { PriceHistoryModel.filterRange(series, range) }
    val histLow = remember(series) { PriceHistoryModel.histLow(series) }
    val lowDays = remember(series) { PriceHistoryModel.lowDays(series) }
    val rangeMin = remember(filtered) { PriceHistoryModel.rangeMin(filtered) }
    val rangeMax = remember(filtered) { PriceHistoryModel.rangeMax(filtered) }
    val vsHist = PriceHistoryModel.curVsHist(game.current_low, histLow)

    // 指标条（2x2，与 Web 四项一致）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCell(
            label = stringResource(R.string.detail_metric_range_min),
            value = formatPrice(rangeMin, game.currency),
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MetricCell(
            label = stringResource(R.string.detail_metric_range_max),
            value = formatPrice(rangeMax, game.currency),
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCell(
            label = stringResource(R.string.detail_metric_vs_hist),
            value = vsHist,
            valueColor = if (vsHist == "已是史低") SuccessGreen else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MetricCell(
            label = stringResource(R.string.detail_metric_days),
            value = filtered.size.toString(),
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }

    // 区间切换
    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val labels = mapOf(
            PriceHistoryModel.RANGE_30 to stringResource(R.string.detail_range_30),
            PriceHistoryModel.RANGE_90 to stringResource(R.string.detail_range_90),
            PriceHistoryModel.RANGE_ALL to stringResource(R.string.detail_range_all),
        )
        PriceHistoryModel.RANGES.forEach { r ->
            FilterChip(
                selected = range == r,
                onClick = { range = r },
                label = { Text(labels[r].orEmpty()) },
            )
        }
    }

    if (filtered.isEmpty()) {
        Text(
            text = stringResource(R.string.detail_no_history_in_range),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        return
    }

    // 图上方轴标签行：左「¥区间最低 · 史低线」，右「区间 首日 → 末日」
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.detail_history_axis_low,
                formatPrice(rangeMin, game.currency),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = SuccessGreen,
        )
        Text(
            text = stringResource(
                R.string.detail_history_axis_range,
                filtered.first().day,
                filtered.last().day,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    PriceHistoryChart(
        points = filtered,
        histLowCents = histLow,
        modifier = Modifier.padding(top = 4.dp),
    )

    // 图注：首日 · ● 史低 ¥xxx · 最近史低日 · 末日
    val lastLowDay = lowDays.lastOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = filtered.first().day,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (histLow != null && lastLowDay != null) {
            Text(
                text = stringResource(
                    R.string.detail_history_low_cap,
                    formatPrice(histLow, game.currency),
                    lastLowDay,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = SuccessGreen,
            )
        }
        Text(
            text = filtered.last().day,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // 底部说明
    Text(
        text = stringResource(R.string.detail_history_foot, filtered.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun InfoLine(labelRes: Int, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun OfferRow(offer: OfferDto, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = offer.platform_name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!offer.region.isNullOrBlank() && offer.region != "cn") {
                    Badge(
                        text = offer.region,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if ((offer.discount_pct ?: 0) > 0) {
                Text(
                    text = formatPct(offer.discount_pct),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if ((offer.discount_pct ?: 0) > 0 && offer.original_cents != null &&
                offer.original_cents != offer.price_cents
            ) {
                PriceText(
                    cents = offer.original_cents,
                    currency = offer.currency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    strikethrough = true,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            PriceText(
                cents = offer.price_cents,
                currency = offer.currency,
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { openCustomTab(context, offer.buy_url) }) {
                Text(stringResource(R.string.detail_offer_buy))
            }
        }
    }
}

@Composable
private fun DlcRow(dlc: DlcItemDto, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openCustomTab(context, dlc.buy_url) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dlc.name.ifBlank { context.getString(R.string.detail_dlc_name_fallback, dlc.appid) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if ((dlc.discount_pct ?: 0) > 0) {
                Text(
                    text = formatPct(dlc.discount_pct),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (dlc.in_stock) {
            PriceText(
                cents = dlc.price_cents,
                currency = dlc.currency,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Badge(
                text = stringResource(R.string.detail_dlc_out_of_stock),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "Steam ↗",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}



