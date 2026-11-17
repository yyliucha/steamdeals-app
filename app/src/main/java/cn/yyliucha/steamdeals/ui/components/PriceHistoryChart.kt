package cn.yyliucha.steamdeals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yyliucha.steamdeals.data.api.SeriesPoint
import cn.yyliucha.steamdeals.ui.theme.SuccessGreen

/**
 * 详情价格历史：Compose Canvas 原生绘制（不引图表库，APP-NATIVE.md §4.2.1）。
 * 数据 = /api/games/:appid 的 series（官方渠道国区每日最低价，按天升序）。
 */
@Composable
fun PriceHistoryChart(
    points: List<SeriesPoint>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (points.size < 2) return@Canvas

        val leftPad = 46.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 24.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        val prices = points.mapNotNull { it.price_cents }
        if (prices.isEmpty()) return@Canvas
        var min: Double = prices.min().toDouble()
        var max: Double = prices.max().toDouble()
        if (min == max) {
            max = min + 1
            min -= 1
        }
        val pad = (max - min) * 0.12
        min -= pad
        max += pad

        fun yFor(price: Double): Float {
            val t = (price - min).toFloat() / (max - min).toFloat()
            return topPad + chartH * (1f - t)
        }

        fun xFor(i: Int): Float = leftPad + chartW * i.toFloat() / (points.size - 1).toFloat()

        // 横向网格 + 价格标签（3 档）
        val labelStyle = androidx.compose.ui.text.TextStyle(
            color = labelColor,
            fontSize = 9.sp,
            textAlign = TextAlign.Left,
        )
        for (k in 0..2) {
            val price = min + (max - min) * k / 2
            val y = yFor(price)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1f,
            )
            drawText(
                textMeasurer = textMeasurer,
                text = formatPrice(price.toLong(), points.firstOrNull()?.currency),
                style = labelStyle,
                topLeft = Offset(0f, y - 5.dp.toPx()),
            )
        }

        // 折线 + 渐变填充
        val line = Path()
        points.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p.price_cents?.toDouble() ?: min)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(xFor(points.size - 1), topPad + chartH)
            lineTo(xFor(0), topPad + chartH)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f)),
                startY = topPad,
                endY = topPad + chartH,
            ),
        )
        drawPath(path = line, color = lineColor, style = Stroke(width = 2.5f))

        // 史低点标记
        val minIdx = prices.indexOfFirst { it == prices.min() }
        if (minIdx >= 0) {
            val mx = xFor(minIdx)
            val my = yFor(points[minIdx].price_cents?.toDouble() ?: min)
            drawCircle(color = SuccessGreen, radius = 4.dp.toPx(), center = Offset(mx, my))
            drawText(
                textMeasurer = textMeasurer,
                text = "史低",
                style = labelStyle.copy(color = SuccessGreen),
                topLeft = Offset(mx + 6.dp.toPx(), my - 14.dp.toPx()),
            )
        }

        // 最新点
        val lastIdx = points.size - 1
        val lx = xFor(lastIdx)
        val ly = yFor(points[lastIdx].price_cents?.toDouble() ?: min)
        drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(lx, ly))

        // 首末日期
        drawText(
            textMeasurer = textMeasurer,
            text = points.first().day,
            style = labelStyle,
            topLeft = Offset(leftPad, size.height - bottomPad + 4.dp.toPx()),
        )
        val lastLabel = points.last().day
        val lastW = textMeasurer.measure(lastLabel, labelStyle).size.width
        drawText(
            textMeasurer = textMeasurer,
            text = lastLabel,
            style = labelStyle,
            topLeft = Offset(size.width - rightPad - lastW, size.height - bottomPad + 4.dp.toPx()),
        )
    }
}
