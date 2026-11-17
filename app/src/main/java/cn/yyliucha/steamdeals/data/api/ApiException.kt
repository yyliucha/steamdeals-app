package cn.yyliucha.steamdeals.data.api

/** 统一 API 异常。UI 层按类型区分处理（401 弹登录、429 提示冷却、403 改密等）。 */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** HTTP 非 2xx（除下列特化类型）。error 为服务端 error 字段。 */
    class Http(val code: Int, val error: String?, val retryAfterSec: Int? = null) :
        ApiException(error ?: "HTTP $code")

    /** 401 { error: "login_required" }：未登录 / Token 失效 → 全局弹登录提示。 */
    object LoginRequired : ApiException("login_required")

    /** 403 { error: "must_change_password" }：必须先改密。 */
    object MustChangePassword : ApiException("must_change_password")

    /** 网络层失败（DNS/超时/断网）。 */
    class Network(cause: Throwable) : ApiException(cause.message ?: "network", cause)

    companion object {
        const val ERROR_LOGIN_REQUIRED = "login_required"
        const val ERROR_MUST_CHANGE_PASSWORD = "must_change_password"
    }
}
