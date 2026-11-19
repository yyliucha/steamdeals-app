package cn.yyliucha.steamdeals.ui.detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * EA-5（站点 docs/ANDROID-POLISH.md §EA-5）：详情页骨架屏——加载中把 loading 圈换成
 * 与详情布局同构的占位（封面/标题/价格/指标卡/描述行），呼吸 alpha 动画；
 * 结构镜像 DetailContent 首屏，避免慢网下「转圈 → 内容」的闪跳。
 */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "detail-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "detail-skeleton-alpha",
    )
    val block = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // 封面占位（与 GameCard 同比例 460:215）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(460f / 215f)
                .clip(RoundedCornerShape(12.dp))
                .background(block),
        )
        Spacer(modifier = Modifier.height(16.dp))
        // 标题两行
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(block),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(block),
        )
        Spacer(modifier = Modifier.height(16.dp))
        // 价格行（现价 + 划线价）
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(block),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(block),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // 指标三卡（当前最低/历史最低/折扣）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(block),
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        // 描述行 × 5（长短交错）
        repeat(5) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (i % 2 == 0) 1f else 0.72f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(block),
            )
            Spacer(modifier = Modifier.height(9.dp))
        }
    }
}
