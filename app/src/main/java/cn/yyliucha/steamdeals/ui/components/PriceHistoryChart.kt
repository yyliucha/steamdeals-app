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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * CM-3（docs/APP-PARITY.md §3 / PRICE-HISTORY-REPORT.md §11 BO 修订）对齐 Web：
 * - 入参 points 已是「当前区间」过滤后的序列（按天升序）；
 * - 史低参考线：全期史低价画绿色虚线（区间不含史低时贴边显示，仍可见"史低在本区间之外"）；
 * - 低点标记：区间内所有价格等于史低的日期都画绿点（不是只标一个最低点）；
 * - 平线（区间最低=最高）垂直居中；单点居中只画点；
 * - 首末日期在图内底部；轴标签/区间文案/史低图注由调用方在图外排版（同 Web 布局）。
 */
@Composable
fun PriceHistoryChart(
    points: List<SeriesPoint>,
    histLowCents: Long?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currency = points.firstOrNull()?.currency

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (points.isEmpty()) return@Canvas

        val leftPad = 46.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 24.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        val prices = points.mapNotNull { it.price_cents }
        var min: Double = prices.min().toDouble()
        var max: Double = prices.max().toDouble()
        val flat = min == max
        if (flat) {
            max = min + 1
            min -= 1
        }
        val pad = (max - min) * 0.12
        min -= pad
        max += pad

        fun yFor(price: Double): Float {
            if (flat) return topPad + chartH / 2f // 平线垂直居中（BO 批：不贴底）
            val t = (price - min).toFloat() / (max - min).toFloat()
            return topPad + chartH * (1f - t)
        }

        fun xFor(i: Int): Float =
            if (points.size == 1) leftPad + chartW / 2f
            else leftPad + chartW * i.toFloat() / (points.size - 1).toFloat()

        // 横向网格 + 价格标签（3 档；平线时不画网格只画一条中线）
        val labelStyle = androidx.compose.ui.text.TextStyle(
            color = labelColor,
            fontSize = 9.sp,
            textAlign = TextAlign.Left,
        )
        if (flat) {
            val y = topPad + chartH / 2f
            drawLine(color = gridColor, start = Offset(leftPad, y), end = Offset(size.width - rightPad, y), strokeWidth = 1f)
        } else {
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
                    text = formatPrice(price.toLong(), currency),
                    style = labelStyle,
                    topLeft = Offset(0f, y - 5.dp.toPx()),
                )
            }
        }

        // 史低虚线参考线（全期史低；钳制在绘图区内）
        histLowCents?.let { hl ->
            val lowY = yFor(hl.toDouble()).coerceIn(topPad, topPad + chartH)
            drawLine(
                color = SuccessGreen.copy(alpha = 0.55f),
                start = Offset(leftPad, lowY),
                end = Offset(size.width - rightPad, lowY),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        // 折线 + 渐变填充
        val line = Path()
        points.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p.price_cents?.toDouble() ?: min)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        if (points.size >= 2) {
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
        }
        drawPath(path = line, color = lineColor, style = Stroke(width = 2.5f))

        // 低点标记：区间内所有价格 == 史低的日期（Web lowDots 同口径）
        if (histLowCents != null) {
            points.forEachIndexed { i, p ->
                if (p.price_cents == histLowCents) {
                    drawCircle(
                        color = SuccessGreen,
                        radius = 3.5.dp.toPx(),
                        center = Offset(xFor(i), yFor(p.price_cents.toDouble())),
                    )
                }
            }
        }

        // 最新点
        val lastIdx = points.size - 1
        drawCircle(
            color = lineColor,
            radius = 3.5.dp.toPx(),
            center = Offset(xFor(lastIdx), yFor(points[lastIdx].price_cents?.toDouble() ?: min)),
        )

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
