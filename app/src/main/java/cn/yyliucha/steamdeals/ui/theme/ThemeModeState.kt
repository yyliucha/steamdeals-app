package cn.yyliucha.steamdeals.ui.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * GD（站点 TODO GD）：「我的 → 主题」切换立即生效——内存态优先 + DataStore 写入异常兜底。
 *
 * 原实现是「点深色 → 等 DataStore write 完成 → session 重新 emit → 界面才换色」：
 * 写盘一旦抛异常（协程里没人接 → 直接崩到 main）或卡住（永不 emit），用户看到的就是
 * 「点了跟没点一样」。这里把状态机单独抽出来（AppViewModel 是 AndroidViewModel，JVM 单测
 * 无法直接构造），行为与放在 ViewModel 里完全一致：
 *  1. [setTheme] 先改内存态 [override] → Compose 下一次重组就换色，不等磁盘；
 *  2. 再异步落盘；失败只回调 [onWriteError]（AppViewModel 记 logcat），override 保留 →
 *     本次会话仍按用户选择显示（重启后回退到盘上旧值，符合「写入失败仅重启后回退」的验收）；
 *  3. 同一会话内以用户最后一次选择为准（override 不主动清空；冷启动以持久化值为准）。
 */
class ThemeModeState(
    private val scope: CoroutineScope,
    persistedTheme: Flow<String>,
    private val writeTheme: suspend (String) -> Unit,
    private val onWriteError: (Throwable) -> Unit = {},
) {
    /** 本次会话内用户显式选择的主题（null = 还没选过，跟随持久化值）。 */
    private val override = MutableStateFlow<String?>(null)

    /** 界面主题：用户本次选择优先，否则用 DataStore 里的持久化值（system / light / dark）。 */
    val themeMode: StateFlow<String> = combine(persistedTheme, override) { persisted, picked ->
        picked ?: persisted
    }.stateIn(scope, SharingStarted.Eagerly, "system")

    /** 主题选择：先改内存态（立即生效），再尽力落盘；写盘失败不打断用户操作。 */
    fun setTheme(mode: String) {
        override.value = mode
        scope.launch {
            runCatching { writeTheme(mode) }
                .onFailure(onWriteError)
        }
    }
}
