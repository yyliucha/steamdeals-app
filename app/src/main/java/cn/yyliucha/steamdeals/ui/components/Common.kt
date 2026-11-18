package cn.yyliucha.steamdeals.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yyliucha.steamdeals.BuildConfig
import cn.yyliucha.steamdeals.ui.theme.DangerRed
import cn.yyliucha.steamdeals.ui.theme.InfoBlue
import cn.yyliucha.steamdeals.ui.theme.SuccessGreen
import cn.yyliucha.steamdeals.ui.theme.WarningOrange

// ---------------- 金额（服务端一律整数分，币种不折算） ----------------

fun currencySymbol(currency: String?): String = when (currency?.uppercase()) {
    "CNY" -> "¥"
    "USD" -> "$"
    "HKD" -> "HK$"
    "TWD" -> "NT$"
    "JPY" -> "JP¥"
    "EUR" -> "€"
    "GBP" -> "£"
    "KRW" -> "₩"
    "RUB" -> "₽"
    "CAD" -> "C$"
    "AUD" -> "A$"
    null, "" -> "¥"
    else -> "$currency "
}

fun formatPrice(cents: Long?, currency: String?): String {
    if (cents == null) return "—"
    val yuan = cents / 100
    val fen = (cents % 100).toString().padStart(2, '0')
    return currencySymbol(currency) + "$yuan.$fen"
}

fun formatPct(pct: Int?): String = if (pct == null || pct <= 0) "" else "-$pct%"

/** CV 批（docs/APP-SEARCH-OWNED.md 决策 3）：已拥有账号徽标文案，与 Web ownedByText 同口径（前 2 个 +N）。 */
fun ownedByText(by: List<cn.yyliucha.steamdeals.data.api.OwnedByDto>): String {
    val names = (by ?: emptyList()).mapNotNull { a ->
        a.name?.takeIf { it.isNotBlank() } ?: a.steamId
    }
    if (names.isEmpty()) return "已拥有"
    return if (names.size > 2) "已拥有 · ${names.take(2).joinToString("、")} +${names.size - 2}"
    else "已拥有 · ${names.joinToString("、")}"
}

/** 站点图片代理：封面走服务端 /img/steam/<appid>/<size>（App 不直连 Steam CDN）。 */
fun steamImageUrl(appid: Long?, size: String = "header"): String? =
    appid?.let { "${BuildConfig.API_BASE_URL.trimEnd('/')}/img/steam/$it/$size" }

fun steamStoreUrl(appid: Long?): String? =
    appid?.let { "https://store.steampowered.com/app/$it/" }

// ---------------- 通用组件 ----------------

@Composable
fun LoadingBox(modifier: Modifier = Modifier, text: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(12.dp))
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text("重试")
            }
        }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Badge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(4.dp)),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun PriceText(
    cents: Long?,
    currency: String?,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.primary,
    strikethrough: Boolean = false,
) {
    Text(
        text = formatPrice(cents, currency),
        modifier = modifier,
        style = style.copy(
            color = color,
            textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
        ),
        fontWeight = FontWeight.Bold,
    )
}

fun toast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** 外链统一走 Custom Tabs（Steam 商店 / 购买链接，APP-NATIVE.md §4.2.1）。 */
fun openCustomTab(context: android.content.Context, url: String?) {
    if (url.isNullOrBlank()) return
    val uri = android.net.Uri.parse(url)
    if (uri.scheme != "https" && uri.scheme != "http") return
    runCatching {
        androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }
}
