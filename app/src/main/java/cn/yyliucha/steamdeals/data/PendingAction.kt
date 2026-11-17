package cn.yyliucha.steamdeals.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 登录后自动完成刚才的操作（docs/APP-NATIVE.md §4.2.2）：
 * - 触发登录提示时暂存最近一次操作，新的覆盖旧的；
 * - 只在内存（App 被杀 / 取消登录 / 登录失败 → 清空，不执行）；
 * - 登录成功后由 AppViewModel 执行一次（无论成败都清空），不自动重试、不循环弹登录。
 */
data class PendingAction(
    val type: String,
    val appid: Long? = null,
    val payload: String? = null,
    val route: String? = null,
    val scrollY: Int = 0,
) {
    companion object {
        const val TYPE_INTEREST = "interest"
        const val TYPE_UNINTEREST = "uninterest"
        const val TYPE_WISHLIST = "wishlist"
    }
}

object PendingActionHolder {
    private val _current = MutableStateFlow<PendingAction?>(null)
    val current: StateFlow<PendingAction?> = _current

    fun set(action: PendingAction) {
        _current.value = action
    }

    fun clear() {
        _current.value = null
    }

    fun take(): PendingAction? {
        val v = _current.value
        _current.value = null
        return v
    }
}
