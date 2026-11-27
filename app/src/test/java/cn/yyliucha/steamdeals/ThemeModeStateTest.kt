package cn.yyliucha.steamdeals

import cn.yyliucha.steamdeals.ui.theme.ThemeModeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GD（站点 TODO GD）单测：主题切换「内存态优先 + 写盘异常兜底」（纯 JVM，假 store 直测）。
 *
 * 用 [UnconfinedTestDispatcher] 跑状态机：`setTheme` 之后 **完全不 pump 调度器** 就断言，
 * 正好锁死「点完立即生效、不等 DataStore 往返」这条验收（若把 override 挪到落盘之后，这里必红）。
 *
 * 反向自检：① 把 [ThemeModeState.setTheme] 的 `override.value = mode` 删掉 → 「立即生效」用例红；
 * ② 去掉 `runCatching` → 「写盘抛异常」用例把 IOException 抛出、测试失败；
 * ③ 把 themeMode 的 combine 改成只读 persisted → 假 store 不落盘时三处断言全红。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeModeStateTest {

    /** 与 backgroundScope 同一个测试 Job（测试结束自动取消），但用 Unconfined 立即执行。 */
    private fun TestScope.unconfinedScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `点深色立即生效 不等假 store 落盘`() = runTest {
        val persisted = MutableStateFlow("system")
        // 假 store：writeTheme 什么都不写（模拟 DataStore 很慢/根本没落盘）
        val state = ThemeModeState(
            scope = unconfinedScope(),
            persistedTheme = persisted,
            writeTheme = { /* 不落盘 */ },
        )
        assertEquals("system", state.themeMode.value)

        state.setTheme("dark")
        // 不调用 advanceUntilIdle / runCurrent：界面上必须已经变深色
        assertEquals("假 store 不落盘也必须立即生效", "dark", state.themeMode.value)
        assertEquals("假 store 确实没有落盘", "system", persisted.value)
    }

    @Test
    fun `写盘抛异常时 themeMode 仍为 dark（override 兜底）`() = runTest {
        val persisted = MutableStateFlow("system")
        val errors = mutableListOf<Throwable>()
        val state = ThemeModeState(
            scope = unconfinedScope(),
            persistedTheme = persisted,
            writeTheme = { throw java.io.IOException("EACCES (Permission denied)") },
            onWriteError = { errors += it },
        )

        state.setTheme("dark")

        assertEquals("写盘失败不得回退，仍按用户选择显示", "dark", state.themeMode.value)
        assertEquals("失败只上报一次，不打断用户操作", 1, errors.size)
        assertTrue("上报的应是落盘异常", errors.single() is java.io.IOException)
    }

    @Test
    fun `冷启动沿用 DataStore 里的持久化主题`() = runTest {
        val persisted = MutableStateFlow("dark")
        val state = ThemeModeState(
            scope = unconfinedScope(),
            persistedTheme = persisted,
            writeTheme = { /* 不落盘 */ },
        )

        assertEquals("重启后按盘上值恢复深色", "dark", state.themeMode.value)
    }

    @Test
    fun `连续切换以最后一次选择为准`() = runTest {
        val persisted = MutableStateFlow("system")
        val written = mutableListOf<String>()
        val state = ThemeModeState(
            scope = unconfinedScope(),
            persistedTheme = persisted,
            writeTheme = { mode ->
                written += mode
                persisted.value = mode // 模拟真实 DataStore 写成功后回写
            },
        )

        state.setTheme("dark")
        state.setTheme("light")

        assertEquals("最终显示最后一次选择", "light", state.themeMode.value)
        assertEquals("两次都尝试落盘（顺序不乱）", listOf("dark", "light"), written)
    }
}
