package cn.yyliucha.steamdeals.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.yyliucha.steamdeals.BuildConfig
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.LibraryItemDto
import cn.yyliucha.steamdeals.data.api.LibraryStatsDto
import cn.yyliucha.steamdeals.data.api.ProfileRequests
import cn.yyliucha.steamdeals.data.api.SteamAccountDto
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.components.Badge
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.components.formatPrice
import cn.yyliucha.steamdeals.ui.components.openCustomTab
import cn.yyliucha.steamdeals.ui.components.steamImageUrl
import cn.yyliucha.steamdeals.ui.components.toast
import cn.yyliucha.steamdeals.ui.theme.BrandPurple
import cn.yyliucha.steamdeals.ui.theme.DangerRed
import cn.yyliucha.steamdeals.ui.theme.SuccessGreen
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 头像裁剪：cover 居中裁正方形 → 缩放 256×256（与 Web 端同口径，上传前客户端完成）。 */
fun cropToSquare(src: Bitmap, size: Int): Bitmap {
    val side = minOf(src.width, src.height)
    val sx = (src.width - side) / 2
    val sy = (src.height - side) / 2
    return Bitmap.createBitmap(src, sx, sy, side, side).let { crop ->
        Bitmap.createScaledBitmap(crop, size, size, true)
    }
}

/** 头像/账号首字（显示名 → 用户名 → 'S'）。 */
fun initialOf(name: String?): String {
    val s = name?.trim().orEmpty()
    return if (s.isEmpty()) "S" else s.substring(0, 1).uppercase()
}

/** 资料页状态：表单 + 账号卡 + 统计 + 弹窗条目（网络操作都收敛到这里）。 */
class ProfileEditState(
    private val vm: AppViewModel,
    private val appScope: CoroutineScope,
) {
    private val api = vm.apiPublic

    val loading = MutableStateFlow(true)
    val saving = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    /** /api/me 的完整本人资料（只读行与头像用它，不用登录响应里的旧 user）。 */
    val user = MutableStateFlow<cn.yyliucha.steamdeals.data.api.UserDto?>(null)
    val displayName = MutableStateFlow("")
    val bio = MutableStateFlow("")
    val theme = MutableStateFlow("system")
    val emailAlerts = MutableStateFlow(true)
    val avatarSource = MutableStateFlow<String?>(null)
    val avatarUpdatedAt = MutableStateFlow<String?>(null)
    val accounts = MutableStateFlow<List<SteamAccountDto>>(emptyList())
    val stats = MutableStateFlow(LibraryStatsDto())
    val dirty = MutableStateFlow(false)

    // 查看已拥有/愿望单（底部弹层）
    val sheetOpen = MutableStateFlow(false)
    val sheetSteamId = MutableStateFlow("")
    val sheetName = MutableStateFlow("")
    val sheetOwned = mutableIntStateOf(0)
    val sheetWishlist = mutableIntStateOf(0)
    val sheetTab = MutableStateFlow("library") // library | wishlist
    val sheetQ = MutableStateFlow("")
    val sheetItems = MutableStateFlow<List<LibraryItemDto>>(emptyList())
    val sheetTotal = mutableIntStateOf(0)
    val sheetPage = mutableIntStateOf(1)
    val sheetPageSize = 10
    val sheetLoading = MutableStateFlow(false)

    val isAdmin: Boolean get() = vm.session.value.user?.role == "admin"

    // 首次加载才整页 LoadingBox；后续刷新原地更新（否则每次操作后页面滚回顶部）
    private var loadedOnce = false

    fun load() {
        if (!vm.session.value.isLoggedIn) return
        appScope.launch {
            if (!loadedOnce) loading.value = true
            error.value = null
            try {
                val me = api.me()
                val lib = api.meLibrary()
                val u = me.user
                user.value = u
                if (!dirty.value) {
                    displayName.value = u?.displayName ?: ""
                    bio.value = u?.bio ?: ""
                    theme.value = me.prefs?.theme ?: "system"
                    emailAlerts.value = me.prefs?.email_alerts != false
                }
                avatarSource.value = u?.avatarSource
                avatarUpdatedAt.value = u?.avatarUpdatedAt
                accounts.value = lib.accounts
                stats.value = lib.stats
            } catch (e: ApiException) {
                error.value = when (e) {
                    is ApiException.Network -> "网络错误，请检查网络后重试"
                    else -> e.message ?: "加载失败"
                }
            } finally {
                loading.value = false
                loadedOnce = true
            }
        }
    }

    fun save(onDone: () -> Unit) {
        appScope.launch {
            saving.value = true
            try {
                api.meProfile(
                    displayName = displayName.value.trim(),
                    bio = bio.value.trim(),
                    theme = if (theme.value == "system") null else theme.value,
                    emailAlerts = emailAlerts.value,
                )
                dirty.value = false
                vm.refreshUser()
                toast(vm.getApplication(), "资料已保存")
                load()
                onDone()
            } catch (e: ApiException) {
                toast(
                    vm.getApplication(),
                    when (e) {
                        is ApiException.Http -> e.error ?: "保存失败"
                        else -> "保存失败，请稍后再试"
                    },
                )
            } finally {
                saving.value = false
            }
        }
    }

    /** 相册选图 → 裁剪 256×256 → PNG dataUrl 上传。 */
    fun uploadAvatar(uri: Uri) {
        appScope.launch {
            saving.value = true
            try {
                val dataUrl = withContext(Dispatchers.IO) {
                    val resolver = vm.getApplication<android.app.Application>().contentResolver
                    val bmp = BitmapFactory.decodeStream(resolver.openInputStream(uri))
                        ?: throw IllegalStateException("无法读取这张图片")
                    val cropped = cropToSquare(bmp, 256)
                    val out = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    val bytes = out.toByteArray()
                    if (bytes.size > 380 * 1024) throw IllegalStateException("裁剪后的头像超过 512KB，请换一张图")
                    "data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
                val resp = api.meAvatar(dataUrl)
                avatarSource.value = resp.avatarSource
                avatarUpdatedAt.value = resp.avatarUpdatedAt
                dirty.value = false
                toast(vm.getApplication(), "头像已更新")
                load()
            } catch (e: Exception) {
                toast(
                    vm.getApplication(),
                    if (e is ApiException.Http) e.error ?: "头像上传失败" else e.message ?: "头像上传失败",
                )
            } finally {
                saving.value = false
            }
        }
    }

    fun removeAvatar() {
        appScope.launch {
            try {
                api.meAvatarDelete()
                avatarSource.value = null
                avatarUpdatedAt.value = null
                toast(vm.getApplication(), "头像已移除")
                load()
            } catch (e: ApiException) {
                toast(vm.getApplication(), "移除失败，请稍后再试")
            }
        }
    }

    fun setDefault(a: SteamAccountDto) {
        if (avatarSource.value == "steam:${a.steamId}") return
        appScope.launch {
            try {
                val resp = api.steamAccountDefault(a.steamId)
                avatarSource.value = resp.avatarSource
                toast(vm.getApplication(), "已将「${a.name}」设为默认账号")
                load()
            } catch (e: ApiException) {
                toast(vm.getApplication(), "设置失败，请稍后再试")
            }
        }
    }

    fun unbind(a: SteamAccountDto) {
        appScope.launch {
            try {
                api.steamAccountUnbind(a.steamId)
                toast(vm.getApplication(), "已解绑")
                load()
            } catch (e: ApiException) {
                toast(vm.getApplication(), "解绑失败，请稍后再试")
            }
        }
    }

    /** 新增绑定：系统浏览器打开站点 Steam 绑定流程（返回 App 后 load() 刷新列表）。 */
    fun bindNew(context: android.content.Context) {
        openCustomTab(context, "${BuildConfig.API_BASE_URL.trimEnd('/')}/console/steam")
    }

    fun openSheet(a: SteamAccountDto, tab: String) {
        sheetSteamId.value = a.steamId
        sheetName.value = a.name ?: a.steamId
        sheetOwned.intValue = a.owned
        sheetWishlist.intValue = a.wishlist
        sheetTab.value = tab
        sheetQ.value = ""
        sheetOpen.value = true
        loadSheetItems(1)
    }

    fun loadSheetItems(page: Int) {
        appScope.launch {
            sheetLoading.value = true
            try {
                val resp = api.steamAccountItems(
                    steamId = sheetSteamId.value,
                    kind = sheetTab.value,
                    q = sheetQ.value.trim(),
                    page = page,
                    pageSize = sheetPageSize,
                )
                sheetItems.value = resp.list
                sheetTotal.intValue = resp.total
                sheetPage.intValue = resp.page
            } catch (e: ApiException) {
                toast(vm.getApplication(), "加载失败，请稍后再试")
            } finally {
                sheetLoading.value = false
            }
        }
    }

    val sheetPages: Int get() = maxOf(1, (sheetTotal.intValue + sheetPageSize - 1) / sheetPageSize)
}

/**
 * 编辑资料（CX，docs/USER-PROFILE-EDIT.md；对齐 docs/design/profile-android-2026-11-18.html +
 * my-profile-android-2026-11-18.html）：头像（Steam 账号/自定义）+ 昵称/简介/主题/邮件开关 +
 * Steam 账号卡（设为默认/解绑/新增）+ 我的游戏库与统计 + 底部弹层（已拥有/愿望单）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(vm: AppViewModel, navController: NavHostController) {
    val appScope = rememberCoroutineScope()
    val context = LocalContext.current
    val session by vm.session.collectAsState()
    val state = remember { ProfileEditState(vm, appScope) }

    val loading by state.loading.collectAsState()
    val saving by state.saving.collectAsState()
    val error by state.error.collectAsState()
    val me by state.user.collectAsState()
    val displayName by state.displayName.collectAsState()
    val bio by state.bio.collectAsState()
    val theme by state.theme.collectAsState()
    val emailAlerts by state.emailAlerts.collectAsState()
    val avatarSource by state.avatarSource.collectAsState()
    val avatarUpdatedAt by state.avatarUpdatedAt.collectAsState()
    val accounts by state.accounts.collectAsState()
    val stats by state.stats.collectAsState()

    var removeConfirm by remember { mutableStateOf(false) }
    var unbindTarget by remember { mutableStateOf<SteamAccountDto?>(null) }
    var themeDialog by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { state.uploadAvatar(it) }
    }

    LaunchedEffect(session.isLoggedIn) {
        if (session.isLoggedIn) state.load()
    }
    // 新增绑定在系统浏览器完成 → 回到 App（ON_RESUME）自动刷新账号列表
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { state.save {} },
                    enabled = !saving && !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                ) {
                    Text(if (saving) stringResource(R.string.saving) else stringResource(R.string.profile_save))
                }
            }
        },
    ) { innerPadding ->
        when {
            !session.isLoggedIn -> EmptyLoginGuide(navController)
            loading -> LoadingBox(modifier = Modifier.padding(innerPadding))
            error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { state.load() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ---- 头像 ----
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val avatarUrl = ProfileRequests.avatarUrl(
                        BuildConfig.API_BASE_URL,
                        me?.id ?: 0,
                        avatarSource,
                        avatarUpdatedAt,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(BrandPurple, Color(0xFF5F49D3))))
                            .clickable { pickImage.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = initialOf(me?.displayName ?: me?.username),
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(BrandPurple),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📷", fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = stringResource(R.string.profile_avatar_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = stringResource(R.string.profile_avatar_change),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandPurple,
                            modifier = Modifier
                                .clickable { pickImage.launch("image/*") }
                                .padding(vertical = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.profile_avatar_remove),
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed,
                            modifier = Modifier
                                .clickable { removeConfirm = true }
                                .padding(vertical = 4.dp),
                        )
                    }
                    // 三个头像来源 chips：各 Steam 账号 + 自定义
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        accounts.forEach { a ->
                            AvatarSourceChip(
                                label = a.name ?: a.steamId,
                                selected = avatarSource == "steam:${a.steamId}",
                            ) { state.setDefault(a) }
                        }
                        AvatarSourceChip(
                            label = stringResource(R.string.profile_avatar_custom),
                            selected = avatarSource == "custom",
                        ) { pickImage.launch("image/*") }
                    }
                }

                // ---- 基本信息 ----
                SectionCard(title = stringResource(R.string.profile_basic)) {
                    FieldRow(label = stringResource(R.string.profile_nickname) + " *") {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { state.displayName.value = it; state.dirty.value = true },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text(stringResource(R.string.profile_nickname_hint))
                            },
                        )
                    }
                    FieldRow(label = stringResource(R.string.login_username)) {
                        ReadonlyValue("${me?.username ?: "—"}  🔒 ${stringResource(R.string.profile_readonly)}")
                    }
                    FieldRow(label = stringResource(R.string.register_email)) {
                        ReadonlyValue(
                            if (me?.email.isNullOrBlank()) "—" else "${me?.email}  🔒",
                        )
                    }
                }

                // ---- 个人简介 ----
                SectionCard(title = stringResource(R.string.profile_bio_title)) {
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 200) { state.bio.value = it; state.dirty.value = true } },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${bio.length} / 200",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ---- Steam 绑定账号 ----
                SectionCard(
                    title = stringResource(R.string.profile_steam_title),
                    badge = "${accounts.size}",
                ) {
                    accounts.forEach { a ->
                        SteamAccountRow(
                            account = a,
                            onSetDefault = { state.setDefault(a) },
                            onUnbind = { unbindTarget = a },
                        )
                        if (a != accounts.lastOrNull()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                    if (accounts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.profile_steam_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { state.bindNew(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("＋ ${stringResource(R.string.profile_steam_bind)}")
                    }
                    Text(
                        text = stringResource(R.string.profile_steam_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                // ---- 偏好与通知 ----
                SectionCard(title = stringResource(R.string.profile_prefs_title)) {
                    FieldRow(label = stringResource(R.string.me_theme)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { themeDialog = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (theme) {
                                    "light" -> stringResource(R.string.theme_light)
                                    "dark" -> stringResource(R.string.theme_dark)
                                    else -> stringResource(R.string.theme_system)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FieldRow(label = stringResource(R.string.profile_email_alerts)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (emailAlerts) stringResource(R.string.profile_on) else stringResource(R.string.profile_off),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = emailAlerts,
                                onCheckedChange = {
                                    state.emailAlerts.value = it
                                    state.dirty.value = true
                                },
                            )
                        }
                    }
                }

                // ---- 我的游戏库 ----
                SectionCard(
                    title = stringResource(R.string.profile_library_title),
                    badge = "${accounts.size}",
                ) {
                    accounts.forEach { a ->
                        AccountCard(
                            account = a,
                            onOpenLibrary = { state.openSheet(a, "library") },
                            onOpenWishlist = { state.openSheet(a, "wishlist") },
                            onSetDefault = { state.setDefault(a) },
                            onUnbind = { unbindTarget = a },
                        )
                        Spacer(modifier = Modifier.height(9.dp))
                    }
                    if (accounts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.profile_library_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ---- 我的统计 ----
                SectionCard(title = stringResource(R.string.profile_stats_title)) {
                    Text(
                        text = listOf(
                            "${stringResource(R.string.profile_stat_owned)} ${stats.ownedTotal}",
                            "${stringResource(R.string.profile_stat_wishlist)} ${stats.wishlistTotal}",
                            "${stringResource(R.string.profile_stat_site_wishlist)} ${stats.siteWishlist}",
                            "${stringResource(R.string.profile_stat_alerts)} ${stats.alerts}",
                            "${stringResource(R.string.profile_stat_hist_hits)} ${stats.histLowHits}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ---- 底部弹层：查看已拥有 / 愿望单（Android 规格：同 Web 结构，搜索 + 分页） ----
    LibrarySheet(state)

    // ---- 主题选择 ----
    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text(stringResource(R.string.me_theme)) },
            text = {
                Column {
                    listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
                        .forEach { (value, labelRes) ->
                            Text(
                                text = stringResource(labelRes) + if (theme == value) "  ✓" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (theme == value) BrandPurple else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        state.theme.value = value
                                        state.dirty.value = true
                                        themeDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                            )
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // ---- 移除头像确认 ----
    if (removeConfirm) {
        AlertDialog(
            onDismissRequest = { removeConfirm = false },
            title = { Text(stringResource(R.string.profile_avatar_remove_title)) },
            text = { Text(stringResource(R.string.profile_avatar_remove_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    removeConfirm = false
                    state.removeAvatar()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { removeConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // ---- 解绑确认（二次确认，与 Web 一致） ----
    unbindTarget?.let { a ->
        AlertDialog(
            onDismissRequest = { unbindTarget = null },
            title = { Text(stringResource(R.string.profile_unbind_title)) },
            text = {
                Text(
                    stringResource(R.string.profile_unbind_hint, a.name ?: a.steamId),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    unbindTarget = null
                    state.unbind(a)
                }) { Text(stringResource(R.string.profile_unbind_confirm), color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { unbindTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyLoginGuide(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.me_login_guide), style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { navController.navigate(cn.yyliucha.steamdeals.ui.Routes.LOGIN) },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.login_required_go))
        }
    }
}

@Composable
private fun AvatarSourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) BrandPurple.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) BrandPurple else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionCard(title: String, badge: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!badge.isNullOrBlank()) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ReadonlyValue(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun SteamAccountRow(
    account: SteamAccountDto,
    onSetDefault: () -> Unit,
    onUnbind: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.linearGradient(listOf(BrandPurple, Color(0xFF5F49D3)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialOf(account.name),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.name ?: account.steamId,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (account.isDefault) {
                    Badge(
                        text = stringResource(R.string.profile_default),
                        color = SuccessGreen,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Text(
                text = account.steamId.take(8) + "…" + account.steamId.takeLast(4),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!account.isDefault) {
            Text(
                text = stringResource(R.string.profile_set_default),
                style = MaterialTheme.typography.bodySmall,
                color = BrandPurple,
                modifier = Modifier
                    .clickable(onClick = onSetDefault)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            )
        }
        Text(
            text = stringResource(R.string.profile_unbind),
            style = MaterialTheme.typography.bodySmall,
            color = DangerRed,
            modifier = Modifier
                .clickable(onClick = onUnbind)
                .padding(start = 6.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
        )
    }
}

/** 我的游戏库账号卡（Android 规格：无「最近同步」，其余同 Web）。 */
@Composable
private fun AccountCard(
    account: SteamAccountDto,
    onOpenLibrary: () -> Unit,
    onOpenWishlist: () -> Unit,
    onSetDefault: () -> Unit,
    onUnbind: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(11.dp, 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Brush.linearGradient(listOf(BrandPurple, Color(0xFF5F49D3)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initialOf(account.name),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.padding(start = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.name ?: account.steamId,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (account.isDefault) {
                            Badge(
                                text = stringResource(R.string.profile_default),
                                color = SuccessGreen,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = account.steamId.take(8) + "…" + account.steamId.takeLast(4),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                StatCell(label = stringResource(R.string.profile_stat_owned), value = account.owned.toString())
                Spacer(modifier = Modifier.width(16.dp))
                StatCell(label = stringResource(R.string.profile_stat_wishlist), value = account.wishlist.toString())
            }
            Row(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(onClick = onOpenLibrary) { Text(stringResource(R.string.profile_view_owned)) }
                OutlinedButton(onClick = onOpenWishlist) { Text(stringResource(R.string.profile_view_wishlist)) }
                if (!account.isDefault) {
                    OutlinedButton(onClick = onSetDefault) { Text(stringResource(R.string.profile_set_default)) }
                }
                OutlinedButton(
                    onClick = onUnbind,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                ) { Text(stringResource(R.string.profile_unbind)) }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 查看已拥有/愿望单底部弹层：tab + 搜索 + 列表 + 上下页（Android 规格与 Web 对话框同结构）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySheet(state: ProfileEditState) {
    val open by state.sheetOpen.collectAsState()
    if (!open) return
    val name by state.sheetName.collectAsState()
    val tab by state.sheetTab.collectAsState()
    val q by state.sheetQ.collectAsState()
    val items by state.sheetItems.collectAsState()
    val loading by state.sheetLoading.collectAsState()
    val total = state.sheetTotal.intValue
    val page = state.sheetPage.intValue
    val pages = state.sheetPages

    ModalBottomSheet(onDismissRequest = { state.sheetOpen.value = false }) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "$name · ${stringResource(R.string.profile_sheet_library)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SheetTab(
                    label = "${stringResource(R.string.profile_view_owned)} ${state.sheetOwned.intValue}",
                    selected = tab == "library",
                ) {
                    state.sheetTab.value = "library"
                    state.sheetQ.value = ""
                    state.loadSheetItems(1)
                }
                SheetTab(
                    label = "${stringResource(R.string.profile_view_wishlist)} ${state.sheetWishlist.intValue}",
                    selected = tab == "wishlist",
                ) {
                    state.sheetTab.value = "wishlist"
                    state.sheetQ.value = ""
                    state.loadSheetItems(1)
                }
            }
            OutlinedTextField(
                value = q,
                onValueChange = { state.sheetQ.value = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.profile_sheet_search)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
            TextButton(
                onClick = { state.loadSheetItems(1) },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.profile_sheet_search_go)) }

            if (loading) {
                LoadingBox(modifier = Modifier.height(180.dp))
            } else if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_list),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(items, key = { it.appid }) { g ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = steamImageUrl(g.appid, "capsule_sm_120"),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 9.dp),
                            ) {
                                Text(
                                    text = g.name ?: "appid:${g.appid}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = g.name_en ?: " ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatPrice(g.current_low, "CNY"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandPurple,
                                )
                                if (g.hist_low != null) {
                                    Text(
                                        text = "${stringResource(R.string.detail_metric_hist)} ${formatPrice(g.hist_low, "CNY")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_sheet_total, total) + " · " + stringResource(R.string.profile_sheet_page, page, pages),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(enabled = page > 1, onClick = { state.loadSheetItems(page - 1) }) {
                        Text(stringResource(R.string.profile_sheet_prev))
                    }
                    TextButton(enabled = page < pages, onClick = { state.loadSheetItems(page + 1) }) {
                        Text(stringResource(R.string.profile_sheet_next))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SheetTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
