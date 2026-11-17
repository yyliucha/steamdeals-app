package cn.yyliucha.steamdeals.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG

/**
 * 图形验证码（服务端返回 SVG + token，App 渲染为位图，APP-NATIVE.md §3.2）。
 * 点击图片刷新由外层处理。
 */
@Composable
fun CaptchaImage(svg: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(svg) { svg?.let { renderCaptchaSvg(it) } }
    Box(
        modifier = modifier
            .height(44.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "验证码",
                modifier = Modifier.fillMaxWidth().height(44.dp),
            )
        } else {
            Text(
                text = "点击加载验证码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun renderCaptchaSvg(svgText: String): Bitmap? = runCatching {
    val svg: SVG = SVG.getFromString(svgText)
    // 原始 132x44，2 倍渲染保证清晰
    val scale = 2f
    val w = (132 * scale).toInt()
    val h = (44 * scale).toInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    svg.renderToCanvas(canvas)
    bitmap
}.getOrNull()
