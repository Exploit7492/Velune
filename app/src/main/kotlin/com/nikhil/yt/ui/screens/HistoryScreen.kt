package com.nikhil.yt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nikhil.yt.R
import com.nikhil.yt.viewmodels.HistoryViewModel
import com.nikhil.yt.viewmodels.HistoryViewModel.ContentType
import com.nikhil.yt.viewmodels.HistoryViewModel.NotificationType
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController, viewModel: HistoryViewModel = hiltViewModel()) {
    val notifications by viewModel.notifications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val seenIds by viewModel.seenIds.collectAsState()
    val appName = stringResource(R.string.app_name)

    var activeTab by remember { mutableStateOf(NotificationTab.ALL) }
    val snackbarHostState = remember { SnackbarHostState() }

    val filtered = remember(notifications, activeTab) {
        when (activeTab) {
            NotificationTab.ALL -> notifications
            NotificationTab.MUSIC -> notifications.filter { it.contentType == ContentType.MUSIC }
            NotificationTab.VIDEO -> notifications.filter { it.contentType == ContentType.VIDEO }
            NotificationTab.CHANNELS -> notifications.filter { it.type == NotificationType.APP_UPDATE }
        }
    }

    val unreadCount = remember(notifications, seenIds) { notifications.count { !seenIds.contains(it.id) } }

    LaunchedEffect(Unit) { if (notifications.isEmpty()) viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = { viewModel.markAllSeen() }) {
                            Text("Mark all read", fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationTab.values().forEach { tab ->
                    val label = when (tab) {
                        NotificationTab.ALL -> "All"
                        NotificationTab.MUSIC -> "Music"
                        NotificationTab.VIDEO -> "Video"
                        NotificationTab.CHANNELS -> appName
                    }
                    val count = when (tab) {
                        NotificationTab.ALL -> notifications.count { !seenIds.contains(it.id) }
                        NotificationTab.MUSIC -> notifications.filter { it.contentType == ContentType.MUSIC }.count { !seenIds.contains(it.id) }
                        NotificationTab.VIDEO -> notifications.filter { it.contentType == ContentType.VIDEO }.count { !seenIds.contains(it.id) }
                        NotificationTab.CHANNELS -> 0
                    }
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontSize = 12.sp, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal)
                                if (count > 0) { Spacer(Modifier.width(4.dp)); Badge { Text(count.toString(), fontSize = 10.sp) } }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (isLoading && notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (error != null && notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.error), null, Modifier.size(48.dp))
                        Text(error ?: "Unknown error", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.history), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Nothing to show yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pull down to check for new releases.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        NotificationCard(item, !seenIds.contains(item.id), appName) {
                            viewModel.markSeen(item.id)
                            if (item.type == NotificationType.TRENDING) {
                                navController.navigate("search?query=${java.net.URLEncoder.encode("${item.title} ${item.source}", "UTF-8")}")
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class NotificationTab { ALL, MUSIC, VIDEO, CHANNELS }

@Composable
private fun NotificationCard(item: HistoryViewModel.NotificationItem, isUnread: Boolean, appName: String, onClick: () -> Unit) {
    val isVideo = item.contentType == ContentType.VIDEO
    val isAppUpdate = item.type == NotificationType.APP_UPDATE
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(
        containerColor = if (isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
    )) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isUnread) {
                Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(10.dp))
            } else Spacer(Modifier.width(13.dp))

            if (item.thumbnailUrl != null) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(item.thumbnailUrl).crossfade(true).build(), contentDescription = null,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (isAppUpdate) R.drawable.history else if (isVideo) R.drawable.play else R.drawable.music_note), null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isAppUpdate) appName else item.source, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (isAppUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (isVideo && !isAppUpdate) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), modifier = Modifier.padding(start = 4.dp)) {
                            Text("MV", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(formatRelativeTime(item.publishedAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            }
            Icon(if (isAppUpdate) Icons.Default.ChevronRight else Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatRelativeTime(dateString: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(dateString)
        val diff = Date().time - (date?.time ?: 0)
        when {
            diff < 60000 -> "just now"
            diff < 3600000 -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < 86400000 -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < 604800000 -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            diff < 2419200000 -> "${TimeUnit.MILLISECONDS.toDays(diff) / 7}w ago"
            diff < 29030400000 -> "${TimeUnit.MILLISECONDS.toDays(diff) / 30}mo ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff) / 365}y ago"
        }
    } catch (_: Exception) { dateString }
}
