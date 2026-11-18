package cn.yyliucha.steamdeals.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.api.AnnouncementDto
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.components.Badge
import cn.yyliucha.steamdeals.ui.components.EmptyBox
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.theme.BrandPurple

/**
 * DC（站点 TODO DC，APP-PARITY §65 CM-6）公告列表弹层：全屏弹层，铃铛与「我的 → 公告」共用。
 * 列表行 = 级别色点 + 标题 + 时间 + 已读态；点开详情（进入即上报已读 → 角标递减）；下拉刷新。
 * 游客可看（登录用户 readIds 服务端、游客本地）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsOverlay(vm: AppViewModel) {
    val state = vm.announcementsState
    val visible by state.overlayVisible.collectAsState()
    if (!visible) return

    val announcements by state.announcements.collectAsState()
    val readIds by state.readIds.collectAsState()
    val loading by state.loading.collectAsState()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = announcements.firstOrNull { it.id == selectedId }

    Dialog(
        onDismissRequest = { state.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.notif_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    IconButton(onClick = { state.close() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back))
                    }
                }
                if (selected != null) {
                    AnnouncementDetail(a = selected, onBack = { selectedId = null })
                } else {
                    AnnouncementList(
                        items = announcements,
                        readIds = readIds,
                        loading = loading,
                        onRefresh = { state.refresh() },
                        onOpen = { id ->
                            // 进入详情即上报已读（未读才写；角标随之递减）
                            state.markRead(id)
                            selectedId = id
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnouncementList(
    items: List<AnnouncementDto>,
    readIds: Set<Long>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    when {
        items.isEmpty() && loading -> LoadingBox()
        items.isEmpty() -> EmptyBox(stringResource(R.string.notif_empty))
        else -> PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items.size, key = { items[it].id }) { i ->
                    AnnouncementRow(
                        a = items[i],
                        read = items[i].id in readIds,
                        onClick = { onOpen(items[i].id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnouncementRow(a: AnnouncementDto, read: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (read) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 级别色点（critical 红 / warning 橙 / info 蓝，与 Web 同映射）
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .background(AnnouncementsModel.levelColor(a.level), CircleShape),
            )
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = a.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!read) {
                        Badge(
                            text = stringResource(R.string.notif_unread),
                            color = BrandPurple,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                val time = AnnouncementsModel.formatTime(a.startsAt)
                if (time != null) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AnnouncementDetail(a: AnnouncementDto, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = stringResource(R.string.notif_detail),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(AnnouncementsModel.levelColor(a.level), CircleShape),
                )
                Text(
                    text = AnnouncementsModel.levelLabel(a.level),
                    color = AnnouncementsModel.levelColor(a.level),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = a.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 10.dp),
            )
            val time = AnnouncementsModel.formatTime(a.startsAt)
            if (time != null) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = a.body?.takeIf { it.isNotBlank() } ?: stringResource(R.string.notif_no_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
        }
    }
}
