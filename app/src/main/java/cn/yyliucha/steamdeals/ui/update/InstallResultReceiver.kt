package cn.yyliucha.steamdeals.ui.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** PackageInstaller 会话结果回调（内置更新）：成功/失败都只记日志，UI 以系统安装确认弹窗为准。 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 安装结果由系统确认弹窗呈现；这里不弹任何 UI，避免与系统弹窗叠加
    }
}
