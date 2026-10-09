package com.example.view

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.NoticeDetailsDialog
import com.example.data.NoticeEntity
import com.example.viewmodel.StoreViewModel

private fun ago(ts: Long): String {
    if (ts <= 0) return ""
    val s = (System.currentTimeMillis() - ts) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        s < 7 * 86400 -> "${s / 86400} d ago"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}

/** Announcements & alerts. Opened from the bell in the header (replaces the old box in Settings). */
@Composable
fun NotificationsScreen(
    viewModel: StoreViewModel,
    notices: List<NoticeEntity>,
    readIds: Set<String>,
    isAdmin: Boolean,
    isDark: Boolean,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<NoticeEntity?>(null) }
    val sorted = remember(notices) { notices.sortedByDescending { it.timestamp } }
    val unread = sorted.count { !it.isRead && it.id !in readIds }

    LaunchedEffect(Unit) { viewModel.refreshNotices() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).glass(isDark, CircleShape, 3.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.ArrowBack, "Back", tint = textPrimary, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("notif_title"), color = textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (unread > 0) "$unread new" else tr("notif_caught_up"),
                    color = if (unread > 0) accent else textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                Modifier.size(44.dp).glass(isDark, CircleShape, 3.dp).clickable {
                    viewModel.refreshNotices(force = true)
                    Toast.makeText(context, "Checking for new notifications…", Toast.LENGTH_SHORT).show()
                },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Refresh, "Refresh", tint = accent, modifier = Modifier.size(21.dp)) }
        }

        if (unread > 0) {
            Text(
                tr("notif_mark_all"), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(10.dp))
                    .clickable { viewModel.markAllNoticesRead(sorted.map { it.id }) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).glass(isDark, CircleShape, 4.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.NotificationsNone, null, tint = accent, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(tr("notif_empty"), color = textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(tr("notif_empty_sub"), color = textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 30.dp, end = 30.dp))
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp + LocalNavBarInset.current),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sorted, key = { it.id }) { n ->
                    val isUnread = !n.isRead && n.id !in readIds
                    val critical = n.targetAppId == "critical_announcement"
                    val tint = if (critical) Color(0xFFEF5350) else accent
                    Row(
                        Modifier.fillMaxWidth()
                            .glass(isDark, RoundedCornerShape(22.dp), 3.dp)
                            .clickable { viewModel.markNoticeRead(n.id); preview = n }
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (n.imageUrl.startsWith("http")) {
                                AsyncImage(model = n.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(if (critical) Icons.Default.Warning else Icons.Default.Campaign, null, tint = tint, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    n.title, color = textPrimary, fontSize = 14.sp,
                                    fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                                )
                                if (critical) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(tint.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("CRITICAL", color = tint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(n.message, color = textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            Text(ago(n.timestamp), color = textSecondary.copy(alpha = 0.75f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (isUnread) Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                            if (isAdmin) {
                                Icon(
                                    Icons.Default.Delete, "Delete", tint = Color(0xFFEF5350),
                                    modifier = Modifier.padding(top = 10.dp).size(20.dp).clip(CircleShape).clickable {
                                        viewModel.deleteNotice(n.id) { ok ->
                                            Toast.makeText(context, if (ok) "Announcement deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    preview?.let { NoticeDetailsDialog(notice = it, onDismiss = { preview = null }) }
}
