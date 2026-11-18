package cn.yyliucha.steamdeals.ui.profile

/**
 * CX 批（docs/USER-PROFILE-EDIT.md §2 #5/#10）：资料表单校验，与站点服务端校验同口径——
 * 昵称 2–20 字符、简介 1–200 字符；管理员昵称/简介可空。
 * 纯函数放这里以便 JVM 单测直接锁定行为（ProfileValidationTest）。
 * 注：字符数用 UTF-16 码元数（String.length），与站点 [...str].length（码点数）对中文/英文一致，
 * 仅 emoji 等代理对字符存在 ±1 差异，不影响 2–20 / 200 的边界判定。
 */
object ProfileValidation {

    /** 昵称错误文案；合法返回 null。isAdmin=true 时允许留空（保存为 null）。 */
    fun nicknameError(value: String, isAdmin: Boolean): String? {
        val s = value.trim()
        if (isAdmin && s.isEmpty()) return null
        if (s.length < 2 || s.length > 20) return "昵称需要 2–20 个字符"
        return null
    }

    /** 简介错误文案；合法返回 null。isAdmin=true 时允许留空。 */
    fun bioError(value: String, isAdmin: Boolean): String? {
        val s = value.trim()
        if (isAdmin && s.isEmpty()) return null
        if (s.isEmpty()) return "个人简介必填（1–200 字）"
        if (s.length > 200) return "个人简介不能超过 200 字"
        return null
    }

    /** CZ 批（docs/EMAIL-CHANGE.md）：换邮箱新邮箱格式（与服务端 normalizeEmail 同口径：trim 后简单格式校验）。 */
    fun emailChangeEmailError(value: String): String? {
        val s = value.trim()
        if (s.isEmpty()) return "请输入新邮箱"
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(s)) return "邮箱格式不正确"
        return null
    }

    /** CZ 批：验证码 6 位数字。 */
    fun emailChangeCodeError(value: String): String? {
        if (!Regex("^\\d{6}$").matches(value.trim())) return "请输入 6 位验证码"
        return null
    }
}
