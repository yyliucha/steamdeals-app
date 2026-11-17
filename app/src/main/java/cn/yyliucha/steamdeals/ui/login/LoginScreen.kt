package cn.yyliucha.steamdeals.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.PendingAction
import cn.yyliucha.steamdeals.data.PendingActionHolder
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.CaptchaResponse
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.Routes
import cn.yyliucha.steamdeals.ui.components.CaptchaImage
import cn.yyliucha.steamdeals.ui.components.toast
import kotlinx.coroutines.launch

/** 注册成功后回填登录页的用户名。 */
object LoginPrefill {
    var username: String? = null
}

/** 首次登录强制改密流程标记（mustChangePassword）。 */
object LoginFlowState {
    var pendingMustChange = false
}

/**
 * 登录页（效果图 ①）：用户名 + 密码 + 图形验证码（可刷新）+ 记住我 + 注册入口。
 * 登录成功：有 pendingAction → 回原页并自动完成刚才的操作一次；否则直接返回。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var username by remember { mutableStateOf(LoginPrefill.username ?: "") }
    var password by remember { mutableStateOf("") }
    var captchaAnswer by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var captcha by remember { mutableStateOf<CaptchaResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        LoginPrefill.username = null
    }

    suspend fun loadCaptcha() {
        captcha = null
        captchaAnswer = ""
        captcha = try {
            vm.apiPublic.captcha()
        } catch (e: ApiException) {
            errorText = when (e) {
                is ApiException.Network -> context.getString(R.string.network_error)
                else -> e.message ?: "验证码加载失败"
            }
            null
        }
    }

    LaunchedEffect(Unit) {
        loadCaptcha()
    }

    // 取消登录 → 清空 pendingAction，不执行（§4.2.2）
    BackHandler {
        PendingActionHolder.clear()
        navController.popBackStack()
    }

    fun submit() {
        if (username.isBlank() || password.isBlank() || captchaAnswer.isBlank()) return
        scope.launch {
            loading = true
            errorText = null
            try {
                val resp = vm.apiPublic.login(
                    username = username.trim(),
                    password = password,
                    captchaToken = captcha?.token.orEmpty(),
                    captchaAnswer = captchaAnswer.trim(),
                    rememberMe = rememberMe,
                    deviceName = android.os.Build.MODEL,
                )
                vm.onTokenLogin(resp)
                if (resp.user.mustChangePassword) {
                    // 首次登录必须改密：改完再走「登录成功」收尾
                    LoginFlowState.pendingMustChange = true
                    navController.navigate(Routes.CHANGE_PASSWORD)
                } else {
                    finishLogin(vm, navController)
                }
            } catch (e: ApiException) {
                errorText = when (e) {
                    is ApiException.Http -> when {
                        e.code == 400 -> context.getString(R.string.login_error_captcha)
                        e.code == 401 -> context.getString(R.string.login_error_credentials)
                        e.code == 429 -> {
                            val sec = e.retryAfterSec ?: 900
                            "${context.getString(R.string.login_error_throttled)}（${sec} 秒）"
                        }
                        else -> e.error ?: "登录失败"
                    }
                    is ApiException.Network -> context.getString(R.string.network_error)
                    else -> e.message ?: "登录失败"
                }
                loadCaptcha()
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        PendingActionHolder.clear()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            // 验证码
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = captchaAnswer,
                    onValueChange = { captchaAnswer = it },
                    label = { Text(stringResource(R.string.login_captcha)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clickable {
                            scope.launch { loadCaptcha() }
                        },
                ) {
                    CaptchaImage(svg = captcha?.svg, modifier = Modifier.fillMaxWidth())
                }
            }
            Text(
                text = stringResource(R.string.login_captcha_refresh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { scope.launch { loadCaptcha() } },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.login_remember),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = rememberMe, onCheckedChange = { rememberMe = it })
            }

            if (errorText != null) {
                Text(
                    text = errorText ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { submit() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.login_submit))
            }

            TextButton(
                onClick = {
                    PendingActionHolder.clear()
                    navController.navigate(Routes.REGISTER)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.login_go_register))
            }
        }
    }
}

/** 登录成功收尾：有 pendingAction → viewModelScope 执行一次（页面销毁不取消）；回原页面（nav 栈保存滚动/数据）。 */
fun finishLogin(vm: AppViewModel, navController: NavHostController) {
    vm.onLoginSuccessFinished()
    navController.popBackStack()
}

/**
 * 登录门槛弹层（§4.2.1）：「登录后才能使用该功能」+「去登录」/「取消」。
 * 取消 → 清空 pendingAction；去登录 → 保留，登录成功后自动执行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRequiredSheet(vm: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember { mutableStateOf<PendingAction?>(null) }

    LaunchedEffect(Unit) {
        vm.loginRequired.collect { action ->
            pending = action
            if (action != null) sheetState.show()
        }
    }

    if (pending != null) {
        ModalBottomSheet(
            onDismissRequest = {
                // 下滑取消 = 取消登录 → 清空
                PendingActionHolder.clear()
                pending = null
            },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.login_required_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(modifier = Modifier.padding(top = 20.dp)) {
                    TextButton(onClick = {
                        PendingActionHolder.clear()
                        pending = null
                        scope.launch { sheetState.hide() }
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            pending = null
                            scope.launch { sheetState.hide() }
                            navController.navigate(Routes.LOGIN)
                        },
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        Text(stringResource(R.string.login_required_go))
                    }
                }
            }
        }
    }
}
