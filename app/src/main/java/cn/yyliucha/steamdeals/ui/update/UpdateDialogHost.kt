package cn.yyliucha.steamdeals.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.UpdateUiState
import java.util.Locale

/**
 * 内置更新对话框宿主（原生对话框，BY 契约 §4.2/§4.3）：
 * 发现新版本（版本/大小/说明）→ Wi-Fi 默认 → 下载进度 → SHA-256 校验 → 系统安装确认。
 * 强制更新（minVersionCode）只给「立即更新」，返回键不可取消。
 */
@Composable
fun UpdateDialogHost(vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.updateState.collectAsState()

    when (val s = state) {
        is UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.me_check_update)) },
            text = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.update_checking))
                }
            },
            confirmButton = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        )

        is UpdateUiState.Available -> {
            val notes = s.info.notes
            AlertDialog(
                onDismissRequest = { if (!s.force) vm.onDismissUpdate() },
                title = { Text(stringResource(R.string.update_title, s.info.version)) },
                text = {
                    Column {
                        s.info.size?.let {
                            Text(
                                text = stringResource(
                                    R.string.update_size,
                                    String.format(Locale.US, "%.1f MB", it / 1024.0 / 1024.0),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.force) {
                            Text(
                                text = stringResource(R.string.update_force_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        notes.forEach { note ->
                            Text(
                                text = "· $note",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.onUpdateNow() }) {
                        Text(stringResource(R.string.update_now))
                    }
                },
                dismissButton = if (s.force) {
                    null
                } else {
                    {
                        Row {
                            TextButton(onClick = { vm.onIgnoreVersion() }) {
                                Text(stringResource(R.string.update_ignore))
                            }
                            TextButton(onClick = { vm.onDismissUpdate() }) {
                                Text(stringResource(R.string.update_later))
                            }
                        }
                    }
                },
                properties = DialogProperties(
                    dismissOnBackPress = !s.force,
                    dismissOnClickOutside = !s.force,
                ),
            )
        }

        is UpdateUiState.AskNetwork -> AlertDialog(
            onDismissRequest = { vm.onDismissUpdate() },
            title = { Text(stringResource(R.string.update_wifi_title)) },
            text = { Text(stringResource(R.string.update_wifi_message)) },
            confirmButton = {
                TextButton(onClick = { vm.onUseDataToDownload() }) {
                    Text(stringResource(R.string.update_use_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onDismissUpdate() }) {
                    Text(stringResource(R.string.update_wifi_only_btn))
                }
            },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = { /* 下载中不取消 */ },
            title = { Text(stringResource(R.string.update_downloading)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (s.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { s.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(s.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text(stringResource(R.string.update_downloading))
                        }
                    }
                }
            },
            confirmButton = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        )

        is UpdateUiState.VerifyFailed -> AlertDialog(
            onDismissRequest = { vm.onDismissUpdate() },
            title = { Text(stringResource(R.string.update_now)) },
            text = { Text(stringResource(R.string.update_verify_failed)) },
            confirmButton = {
                TextButton(onClick = { vm.onRetryDownload() }) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onDismissUpdate() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )

        is UpdateUiState.DownloadFailed -> AlertDialog(
            onDismissRequest = { vm.onDismissUpdate() },
            title = { Text(stringResource(R.string.update_now)) },
            text = { Text(stringResource(R.string.update_download_failed)) },
            confirmButton = {
                TextButton(onClick = { vm.onRetryDownload() }) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onDismissUpdate() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )

        is UpdateUiState.Ready -> AlertDialog(
            onDismissRequest = { vm.onDismissUpdate() },
            title = { Text(stringResource(R.string.update_title, s.info.version)) },
            text = { Text(stringResource(R.string.update_install_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.onInstallNow() }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onDismissUpdate() }) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )

        is UpdateUiState.Latest -> AlertDialog(
            onDismissRequest = { vm.onDismissUpdate() },
            title = { Text(stringResource(R.string.me_check_update)) },
            text = { Text(stringResource(R.string.update_latest)) },
            confirmButton = {
                TextButton(onClick = { vm.onDismissUpdate() }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )

        is UpdateUiState.CheckFailed -> AlertDialog(
            onDismissRequest = { vm.onDismissUpdate() },
            title = { Text(stringResource(R.string.me_check_update)) },
            text = { Text(stringResource(R.string.update_check_failed)) },
            confirmButton = {
                TextButton(onClick = { vm.onDismissUpdate() }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )

        // DK（站点 TODO DK B）：有新版但 apkPath 缺失——提示到站点 /app 页下载，不再笼统报「检查更新失败」
        is UpdateUiState.Unavailable -> AlertDialog(
            onDismissRequest = { vm.onDismissUpdate() },
            title = { Text(stringResource(R.string.update_title, s.version)) },
            text = { Text(stringResource(R.string.update_apk_unavailable)) },
            confirmButton = {
                TextButton(onClick = { vm.onDismissUpdate() }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )

        UpdateUiState.Idle -> Unit
    }
}
