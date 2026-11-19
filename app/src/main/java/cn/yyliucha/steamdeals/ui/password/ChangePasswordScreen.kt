package cn.yyliucha.steamdeals.ui.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.components.toast
import cn.yyliucha.steamdeals.ui.login.LoginFlowState
import cn.yyliucha.steamdeals.ui.login.finishLogin
import kotlinx.coroutines.launch

/**
 * 修改密码：POST /api/user/password（改密后其它会话退出，当前 Token 保留）。
 * 首次登录强制改密（mustChangePassword）也走本页：成功后按登录成功收尾。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(vm: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // DY（docs/ANDROID-POLISH.md §DY）：本页三个输入全是密码类字段，按安全惯例**不**落 rememberSaveable
    // （不入系统保存状态）；loading/error/mustChange 为瞬时状态，同样用 remember。
    // 旋转后密码需重输属预期行为（见规格 §DY 实现说明）。
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var mustChange by remember { mutableStateOf(LoginFlowState.pendingMustChange) }

    fun submit() {
        if (newPassword != confirm) {
            errorText = context.getString(R.string.cp_mismatch)
            return
        }
        scope.launch {
            loading = true
            errorText = null
            try {
                vm.apiPublic.changePassword(oldPassword, newPassword)
                toast(context, context.getString(R.string.cp_success))
                if (mustChange && LoginFlowState.pendingMustChange) {
                    LoginFlowState.pendingMustChange = false
                    finishLogin(vm, navController)
                } else {
                    navController.popBackStack()
                }
            } catch (e: ApiException) {
                errorText = when (e) {
                    is ApiException.Http -> e.error ?: "修改失败"
                    is ApiException.Network -> context.getString(R.string.network_error)
                    else -> e.message ?: "修改失败"
                }
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cp_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mustChange) {
                Text(
                    text = stringResource(R.string.cp_must_change),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = { Text(stringResource(R.string.cp_old)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.cp_new)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text(stringResource(R.string.cp_confirm)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
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
                Text(stringResource(R.string.cp_submit))
            }
        }
    }
}
