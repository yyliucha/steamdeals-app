package cn.yyliucha.steamdeals.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.Routes
import cn.yyliucha.steamdeals.ui.components.Badge
import cn.yyliucha.steamdeals.ui.components.toast
import cn.yyliucha.steamdeals.ui.theme.BrandPurple
import cn.yyliucha.steamdeals.ui.wishlist.LoginGuide

/**
 * 我的（效果图 ⑥）：账号信息、通知、主题（跟随系统/深/浅）、检查更新、修改密码、关于、退出登录。
 * 未登录 → 登录引导页（不空白、不报错）。
 */
@Composable
fun MeScreen(vm: AppViewModel, navController: NavHostController) {
    val session by vm.session.collectAsState()
    val context = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }

    if (!session.isLoggedIn) {
        LoginGuide(
            text = stringResource(R.string.me_login_guide),
            onLogin = { navController.navigate(Routes.LOGIN) },
        )
        return
    }

    val user = session.user
    val themeMode = session.theme
    // DC：公告未读数（「我的 → 公告」入口展示，0 不显示）
    val unreadAnnouncements by vm.announcementsState.unreadCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 账号卡片
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = BrandPurple,
                    modifier = Modifier.size(44.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = user?.username ?: "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (user?.role == "admin") {
                        Badge(
                            text = "管理员",
                            color = BrandPurple,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        MeSection {
            MeRow(
                icon = Icons.Filled.Notifications,
                label = stringResource(R.string.me_notifications),
                value = if (unreadAnnouncements > 0) {
                    context.getString(R.string.notif_unread_count, unreadAnnouncements)
                } else {
                    null
                },
                onClick = { vm.announcementsState.open() },
            )
            MeRow(
                icon = Icons.Filled.DarkMode,
                label = stringResource(R.string.me_theme),
                value = when (themeMode) {
                    "light" -> stringResource(R.string.theme_light)
                    "dark" -> stringResource(R.string.theme_dark)
                    else -> stringResource(R.string.theme_system)
                },
                onClick = { themeDialog = true },
            )
            MeRow(
                icon = Icons.Filled.SystemUpdate,
                label = stringResource(R.string.me_check_update),
                onClick = { vm.checkUpdate(manual = true) },
            )
            MeRow(
                icon = Icons.Filled.Lock,
                label = stringResource(R.string.me_change_password),
                onClick = { navController.navigate(Routes.CHANGE_PASSWORD) },
            )
            MeRow(
                icon = Icons.Filled.Info,
                label = stringResource(R.string.me_about),
                onClick = { navController.navigate(Routes.ABOUT) },
            )
            MeRow(
                icon = Icons.Filled.Logout,
                label = stringResource(R.string.me_logout),
                danger = true,
                onClick = { logoutDialog = true },
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // 主题选择
    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text(stringResource(R.string.me_theme)) },
            text = {
                Column {
                    ThemeOption(stringResource(R.string.theme_system), themeMode == "system") {
                        vm.setTheme("system")
                        themeDialog = false
                    }
                    ThemeOption(stringResource(R.string.theme_light), themeMode == "light") {
                        vm.setTheme("light")
                        themeDialog = false
                    }
                    ThemeOption(stringResource(R.string.theme_dark), themeMode == "dark") {
                        vm.setTheme("dark")
                        themeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // 退出确认
    if (logoutDialog) {
        AlertDialog(
            onDismissRequest = { logoutDialog = false },
            title = { Text(stringResource(R.string.me_logout)) },
            text = { Text(stringResource(R.string.me_logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    logoutDialog = false
                    vm.logout()
                    toast(context, context.getString(R.string.toast_logged_out))
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MeSection(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun MeRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
