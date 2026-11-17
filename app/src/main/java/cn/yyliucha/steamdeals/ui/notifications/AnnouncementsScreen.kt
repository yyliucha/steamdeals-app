package cn.yyliucha.steamdeals.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.yyliucha.steamdeals.R
import cn.yyliucha.steamdeals.data.api.AnnouncementDto
import cn.yyliucha.steamdeals.data.api.ApiException
import cn.yyliucha.steamdeals.ui.AppViewModel
import cn.yyliucha.steamdeals.ui.components.EmptyBox
import cn.yyliucha.steamdeals.ui.components.LoadingBox
import cn.yyliucha.steamdeals.ui.theme.BrandPurple
import kotlinx.coroutines.launch

/**
 * 通知（公告）：公开可读；已读状态登录用户跟随账号（服务端 readIds），游客本地记录。
 * 展开即标记已读（登录用户 POST /api/announcements/read）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(vm: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val session by vm.session.collectAsState()

    var items by remember { mutableStateOf<List<AnnouncementDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var readIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val resp = vm.apiPublic.announcements()
            items = resp.announcements
            readIds = if (session.isLoggedIn) resp.readIds.toSet()
            else session.readAnnouncementIds.toSet()
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    fun markRead(id: Long) {
        if (id in readIds) return
        readIds = readIds + id
        if (session.isLoggedIn) {
            scope.launch {
                runCatching { vm.apiPublic.markAnnouncementsRead(listOf(id)) }
            }
        } else {
            scope.launch { vm.storePublic.setReadAnnouncementIds((readIds).toList()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            loading -> LoadingBox(Modifier.padding(padding))
            items.isEmpty() -> EmptyBox(
                text = stringResource(R.string.notif_empty),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(items.size, key = { items[it].id }) { i ->
                    val a = items[i]
                    val read = a.id in readIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { markRead(a.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (read) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (!read) {
                                    Text(
                                        text = stringResource(R.string.notif_unread),
                                        color = BrandPurple,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                }
                                Text(
                                    text = a.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (!a.body.isNullOrBlank()) {
                                Text(
                                    text = a.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            val start = a.startsAt?.replace('T', ' ')?.take(16)
                            if (start != null) {
                                Text(
                                    text = start,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
