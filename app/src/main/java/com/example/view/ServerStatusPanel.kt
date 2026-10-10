package com.example.view

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.FirebaseAuthService
import com.example.data.FirebaseService
import com.example.utils.NotifierServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GREEN = Color(0xFF10B981)
private val RED = Color(0xFFEF4444)
private val AMBER = Color(0xFFF59E0B)

private fun fmtUptime(s: Long): String {
    val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m ${s % 60}s"
    }
}

private fun fmtClock(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

private fun fmtAgo(ms: Long): String {
    if (ms <= 0) return "never"
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

private val KIND_LABELS = linkedMapOf(
    "new_app" to "New apps", "app_update" to "Updates", "submission_new" to "To admins",
    "submission_status" to "To developers", "announcement" to "Announcements", "chat" to "Chat"
)

/** Admin ▸ Server: live ON/OFF check of the notifier + stats, activity feed and test tools. */
@Composable
fun ServerStatusPanel(
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var health by remember { mutableStateOf<NotifierServer.Health?>(null) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var statusDenied by remember { mutableStateOf(false) }
    var checkedAt by remember { mutableStateOf(0L) }
    var checking by remember { mutableStateOf(false) }
    var busyAction by remember { mutableStateOf("") }

    suspend fun refresh() {
        checking = true
        val h = NotifierServer.health()
        health = h
        if (h.online) {
            FirebaseAuthService.refreshIdTokenIfNeeded(context)
            val st = NotifierServer.status(FirebaseService.activeToken)
            status = st
            statusDenied = st == null
        } else {
            status = null
            statusDenied = false
        }
        checkedAt = System.currentTimeMillis()
        checking = false
    }

    // Auto-refresh every 15 s, only while this tab is on screen and the app is in front.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(15_000)
            }
        }
    }

    val online = health?.online == true
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha"
    )
    val statusColor = when {
        health == null -> AMBER
        online -> GREEN
        else -> RED
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── ON / OFF card ─────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(statusColor)
                        .alpha(if (online || health == null) pulse else 1f)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            health == null -> "CHECKING…"
                            online -> "SERVER ONLINE"
                            else -> "SERVER OFFLINE"
                        },
                        color = statusColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                    )
                    Text(
                        when {
                            health == null -> "Contacting notifier…"
                            online -> "Responded in ${health!!.latencyMs} ms · up ${fmtUptime(health!!.uptimeS)} · v${health!!.version}"
                            else -> "${health!!.error.ifBlank { "No response" }} — start it on Wispbyte"
                        },
                        color = textSecondary, fontSize = 11.sp
                    )
                    Text(
                        "${NotifierServer.BASE_URL.removePrefix("https://")} · checked ${fmtClock(checkedAt)}",
                        color = textSecondary.copy(alpha = 0.8f), fontSize = 10.sp
                    )
                }
                IconButton(onClick = { scope.launch { refresh() } }, enabled = !checking) {
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = statusColor)
                    else Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textSecondary)
                }
            }
        }

        if (health != null && !online) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = RED.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, RED.copy(alpha = 0.3f))
            ) {
                Text(
                    "While the server is offline, NO push notifications are sent (new apps, updates, " +
                        "submissions, status, chat). Pending events are not lost — the server catches up " +
                        "from where it stopped once it is back.",
                    color = textPrimary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(12.dp)
                )
            }
        }

        val st = status
        if (online && st != null) {
            val today = st.optJSONObject("today")
            val sentToday = today?.optJSONObject("sent")
            val failedToday = today?.optJSONObject("failed")
            val sentTotal = KIND_LABELS.keys.sumOf { sentToday?.optInt(it, 0) ?: 0 }
            val failedTotal = KIND_LABELS.keys.sumOf { failedToday?.optInt(it, 0) ?: 0 }
            val users = st.optJSONObject("users")
            val tracked = st.optJSONObject("tracked")

            // ── stat tiles ────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Pushes today", "$sentTotal", GREEN, textPrimary, textSecondary, cardBgColor, cardBorderColor, Modifier.weight(1f))
                StatTile("Failed today", "$failedTotal", if (failedTotal > 0) RED else textPrimary, textPrimary, textSecondary, cardBgColor, cardBorderColor, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val withToken = users?.optInt("with_token") ?: 0
                val totalUsers = users?.optInt("total") ?: 0
                StatTile("Users with push", "$withToken / $totalUsers", if (withToken < totalUsers) AMBER else GREEN, textPrimary, textSecondary, cardBgColor, cardBorderColor, Modifier.weight(1f))
                StatTile("Pending submissions", "${tracked?.optInt("pending_submissions") ?: 0}", textPrimary, textPrimary, textSecondary, cardBgColor, cardBorderColor, Modifier.weight(1f))
            }

            // ── delivered today by type ───────────────────────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Delivered today", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    KIND_LABELS.forEach { (key, label) ->
                        val ok = sentToday?.optInt(key, 0) ?: 0
                        val bad = failedToday?.optInt(key, 0) ?: 0
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = textSecondary, fontSize = 12.sp)
                            Text(
                                if (bad > 0) "$ok  ·  $bad failed" else "$ok",
                                color = if (bad > 0) RED else textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    HorizontalDivider(color = cardBorderColor.copy(alpha = 0.7f))
                    Text("Last push: ${fmtAgo(st.optLong("last_push_at"))}", color = textSecondary, fontSize = 11.sp)
                    val lastErr = st.optString("last_error")
                    if (lastErr.isNotBlank()) {
                        Text("Last error (${fmtAgo(st.optLong("last_error_at"))}): $lastErr", color = RED, fontSize = 11.sp)
                    }
                    Text(
                        "User sync: ${fmtAgo(st.optLong("last_backfill_at"))} · ${st.optInt("last_backfill_created")} added",
                        color = textSecondary, fontSize = 11.sp
                    )
                }
            }

            // ── actions ───────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busyAction = "test"
                            FirebaseAuthService.refreshIdTokenIfNeeded(context)
                            val (ok, j) = NotifierServer.action("/admin/test-push", FirebaseService.activeToken)
                            busyAction = ""
                            Toast.makeText(
                                context,
                                if (ok) "Test push sent — check your notifications" else (j?.optString("error")?.ifBlank { null } ?: "Test failed"),
                                Toast.LENGTH_LONG
                            ).show()
                            refresh()
                        }
                    },
                    enabled = busyAction.isEmpty(),
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                ) { Text(if (busyAction == "test") "Sending…" else "Send test push to me", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busyAction = "sync"
                            FirebaseAuthService.refreshIdTokenIfNeeded(context)
                            val (ok, j) = NotifierServer.action("/admin/backfill", FirebaseService.activeToken)
                            busyAction = ""
                            Toast.makeText(
                                context,
                                if (ok) "User sync done — ${j?.optInt("created") ?: 0} added" else "Sync failed",
                                Toast.LENGTH_LONG
                            ).show()
                            refresh()
                        }
                    },
                    enabled = busyAction.isEmpty(),
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) { Text(if (busyAction == "sync") "Syncing…" else "Sync users now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary) }
            }

            // ── recent activity ───────────────────────────────────────
            val feed = st.optJSONArray("activity")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recent activity", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (feed == null || feed.length() == 0) {
                        Text("Nothing yet since the server started.", color = textSecondary, fontSize = 12.sp)
                    } else {
                        for (i in 0 until minOf(feed.length(), 15)) {
                            val e = feed.getJSONObject(i)
                            val lvl = e.optString("level")
                            val c = when (lvl) { "ERROR" -> RED; "WARNING" -> AMBER; else -> GREEN }
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(c))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(e.optString("msg"), color = textPrimary, fontSize = 11.sp, lineHeight = 15.sp)
                                    Text(fmtClock(e.optLong("t")), color = textSecondary, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else if (online && statusDenied) {
            Text(
                "Server is up, but details need an admin sign-in. Reopen the app or log in again as admin.",
                color = textSecondary, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String, value: String, valueColor: Color,
    textPrimary: Color, textSecondary: Color, bg: Color, border: Color, modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, color = valueColor, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(label, color = textSecondary, fontSize = 11.sp)
        }
    }
}
