package cn.yyliucha.steamdeals.ui.register

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import cn.yyliucha.steamdeals.data.PendingActionHolder
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.data.api.CaptchaResponse
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.components.CaptchaImage
import cn.yyliucha.steamdeals.ui.login.LoginPrefill
import cn.yyliucha.steamdeals.ui.components.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 注册页：邮箱验证码流程（web 契约 /api/auth/register/request → /verify）。
 * 验证成功 → 回登录页并预填用户名（App 的 Token 登录仍需再输一次图形验证码）。
 * 注册未开放（/api/site registration）→ 显示「暂未开放注册」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(vm: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var captchaAnswer by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf<CaptchaResponse?>(null) }
    var emailCode by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }
    var regEnabled by remember { mutableStateOf(true) }

    BackHandler {
        PendingActionHolder.clear()
        navController.popBackStack()
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
        runCatching {
            vm.apiPublic.site()
        }.onSuccess { regEnabled = it.registration.enabled }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    fun sendCode() {
        if (username.isBlank() || email.isBlank() || captchaAnswer.isBlank()) {
            errorText = "请填写用户名、邮箱和图形验证码"
            return
        }
        scope.launch {
            sending = true
            errorText = null
            infoText = null
            try {
                val resp = vm.apiPublic.registerRequest(
                    username = username.trim(),
                    email = email.trim(),
                    captchaToken = captcha?.token.orEmpty(),
                    captchaAnswer = captchaAnswer.trim(),
                )
                infoText = resp.hint ?: context.getString(R.string.register_code_sent)
                countdown = 60
            } catch (e: ApiException) {
                errorText = when (e) {
                    is ApiException.Http -> e.error ?: "发送失败"
                    is ApiException.Network -> context.getString(R.string.network_error)
                    else -> e.message ?: "发送失败"
                }
                loadCaptcha()
            } finally {
                sending = false
            }
        }
    }

    fun submit() {
        if (password != confirm) {
            errorText = context.getString(R.string.register_password_mismatch)
            return
        }
        if (emailCode.isBlank()) {
            errorText = "请输入邮箱验证码"
            return
        }
        scope.launch {
            loading = true
            errorText = null
            try {
                vm.apiPublic.registerVerify(
                    email = email.trim(),
                    code = emailCode.trim(),
                    username = username.trim(),
                    password = password,
                )
                toast(context, context.getString(R.string.register_success))
                LoginPrefill.username = username.trim()
                PendingActionHolder.clear()
                navController.popBackStack()
            } catch (e: ApiException) {
                errorText = when (e) {
                    is ApiException.Http -> e.error ?: "注册失败"
                    is ApiException.Network -> context.getString(R.string.network_error)
                    else -> e.message ?: "注册失败"
                }
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.register_title)) },
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
        if (!regEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.register_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

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
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.register_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.register_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text(stringResource(R.string.register_confirm_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            // 图形验证码
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
                        .clickable { scope.launch { loadCaptcha() } },
                ) {
                    CaptchaImage(svg = captcha?.svg, modifier = Modifier.fillMaxWidth())
                }
            }

            // 邮箱验证码
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = emailCode,
                    onValueChange = { emailCode = it },
                    label = { Text(stringResource(R.string.register_code)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { sendCode() },
                    enabled = countdown == 0 && !sending,
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (countdown > 0) {
                                context.getString(R.string.register_resend_countdown, countdown)
                            } else {
                                stringResource(R.string.register_get_code)
                            },
                        )
                    }
                }
            }

            if (infoText != null) {
                Text(
                    text = infoText ?: "",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
                Text(stringResource(R.string.register_submit))
            }

            TextButton(
                onClick = {
                    PendingActionHolder.clear()
                    navController.popBackStack()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.register_back_to_login))
            }
        }
    }
}
