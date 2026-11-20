package cn.yyliucha.steamdeals.ui.components

/**
 * EJ 批（站点 docs/DISPLAY-NAME-RULE.md）：昵称显示规则统一。
 *
 * 规则：显示名 = 昵称（trim 后非空）? 昵称 : 用户名。
 * - 登录 / 账号安全仍用 username（本文件只管展示，不参与认证）；
 * - 昵称可清空：清空后回退用户名，不残留空白；
 * - 不做「昵称 + 用户名」并列展示。
 *
 * 返回 null 表示两者都没有可用值（展示层自行给占位符，如「—」）。
 */
fun displayNameOrNull(displayName: String?, username: String?): String? {
    val nick = displayName?.trim().orEmpty()
    if (nick.isNotEmpty()) return nick
    return username?.trim()?.takeIf { it.isNotEmpty() }
}

/** 显示名（必定非空）：昵称 → 用户名 → 「—」占位。 */
fun displayNameOf(displayName: String?, username: String?): String =
    displayNameOrNull(displayName, username) ?: "—"
