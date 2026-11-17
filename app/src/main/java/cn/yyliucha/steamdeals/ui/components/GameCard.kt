package cn.yyliucha.steamdeals.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.yyliucha.steamdeals.data.api.GameDto
import cn.yyliucha.steamdeals.ui.theme.BrandPurple
import cn.yyliucha.steamdeals.ui.theme.SuccessGreen
import cn.yyliucha.steamdeals.ui.theme.TextSecondaryDark

/**
 * 单列游戏卡片（效果图 ②：价格/折扣/史低/愿望单心形）。
 * 心形 = 本站愿望单切换（登录门槛由调用方处理）。
 */
@Composable
fun GameCard(
    game: GameDto,
    onOpen: () -> Unit,
    onWishlistToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isNewHistLow = game.prev_hist_low != null && game.current_low != null &&
        game.current_low < game.prev_hist_low
    val discounted = (game.discount ?: 0) > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            val cover = steamImageUrl(game.steam_appid)
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(460f / 215f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = game.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = game.name.take(1),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriceText(
                        cents = game.current_low,
                        currency = game.currency,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    if (discounted && game.current_original != null &&
                        game.current_original != game.current_low
                    ) {
                        PriceText(
                            cents = game.current_original,
                            currency = game.currency,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strikethrough = true,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (discounted) Badge(
                        text = formatPct(game.discount),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (isNewHistLow) Badge("新史低", SuccessGreen)
                    when (game.steam_status) {
                        "region_locked" -> Badge("国区不可售", MaterialTheme.colorScheme.error)
                        "coming_soon" -> Badge("未发售", MaterialTheme.colorScheme.tertiary)
                    }
                    if (game.app_type == "dlc") Badge("DLC", BrandPurple)
                    if (game.mature_flag) Badge("成熟内容", MaterialTheme.colorScheme.tertiary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = game.low_platform_name ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onWishlistToggle != null) {
                        IconButton(
                            onClick = onWishlistToggle,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (game.wishlisted) Icons.Filled.Favorite
                                else Icons.Outlined.FavoriteBorder,
                                contentDescription = "愿望单",
                                tint = if (game.wishlisted) BrandPurple
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
