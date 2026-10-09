package com.example.view

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FirebaseAuthService
import com.example.data.FirebaseService
import com.example.data.UserEntity
import com.example.utils.NotifierServer
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val AUDIENCES = listOf(
    "everyone" to "Everyone",
    "developers" to "Developers",
    "users" to "Regular users",
    "admins" to "Admins",
    "selected" to "Specific people"
)

/** Admin ▸ Notify: send a push to everyone, a group, or hand-picked users. */
@Composable
fun AdminNotifyPanel(
    viewModel: StoreViewModel,
    users: List<UserEntity>,
    presetUid: String,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var audience by remember { mutableStateOf(if (presetUid.isNotBlank()) "selected" else "everyone") }
    var selected by remember { mutableStateOf(if (presetUid.isNotBlank()) setOf(presetUid) else emptySet<String>()) }
    var search by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(presetUid) {
        if (presetUid.isNotBlank()) { audience = "selected"; selected = setOf(presetUid) }
    }

    val recipients: List<UserEntity>? = when (audience) {
        "everyone" -> null
        "developers" -> users.filter { it.isDeveloper }
        "users" -> users.filter { !it.isDeveloper && it.role != "admin" }
        "admins" -> users.filter { it.role == "admin" }
        else -> users.filter { it.uid in selected }
    }
    val reach = recipients?.count { it.fcmToken.isNotBlank() }
    val noToken = recipients?.count { it.fcmToken.isBlank() } ?: 0
    val canSend = title.isNotBlank() && message.isNotBlank() && !sending &&
        (audience != "selected" || selected.isNotEmpty()) && (recipients == null || (reach ?: 0) > 0)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
        focusedBorderColor = accentGreen, unfocusedBorderColor = cardBorderColor,
        cursorColor = accentGreen, focusedLabelColor = accentGreen, unfocusedLabelColor = textSecondary
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Send a notification", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)

        // ── audience ─────────────────────────────────────────────────
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AUDIENCES.forEach { (key, label) ->
                val on = audience == key
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) accentGreen.copy(alpha = 0.18f) else cardBgColor)
                        .then(Modifier.clickable { audience = key; result = null })
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (on) accentGreen else textSecondary, fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

        // ── specific people picker ───────────────────────────────────
        if (audience == "selected") {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search name or email…", fontSize = 12.sp, color = textSecondary) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), colors = fieldColors
            )
            val shown = users.filter {
                search.isBlank() || it.displayName.contains(search, true) || it.email.contains(search, true)
            }.sortedByDescending { it.uid in selected }.take(40)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (shown.isEmpty()) Text("No matching users", color = textSecondary, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
                    shown.forEach { u ->
                        val on = u.uid in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { selected = if (on) selected - u.uid else selected + u.uid }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = { selected = if (on) selected - u.uid else selected + u.uid },
                                colors = CheckboxDefaults.colors(checkedColor = accentGreen))
                            Column(Modifier.weight(1f)) {
                                Text(u.displayName.ifBlank { u.email.substringBefore("@") }, color = textPrimary,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(u.email, color = textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.size(8.dp).clip(CircleShape)
                                .background(if (u.fcmToken.isNotBlank()) Color(0xFF10B981) else Color(0xFFF59E0B)))
                        }
                    }
                }
            }
            Text("${selected.size} selected  ·  green dot = can receive push, amber = no push token yet",
                color = textSecondary, fontSize = 11.sp)
        }

        // ── message ──────────────────────────────────────────────────
        OutlinedTextField(
            value = title, onValueChange = { if (it.length <= 80) title = it },
            label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = fieldColors
        )
        OutlinedTextField(
            value = message, onValueChange = { if (it.length <= 500) message = it },
            label = { Text("Message") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = fieldColors,
            supportingText = { Text("${message.length}/500", color = textSecondary, fontSize = 10.sp) }
        )
        OutlinedTextField(
            value = imageUrl, onValueChange = { imageUrl = it },
            label = { Text("Image URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = fieldColors
        )

        // ── reach summary ────────────────────────────────────────────
        Text(
            when {
                audience == "everyone" -> "Goes to every Dark Store user (topic broadcast)."
                recipients != null && (reach ?: 0) == 0 -> "Nobody in this group has a push token yet."
                else -> "Will reach $reach device${if (reach == 1) "" else "s"}" +
                    if (noToken > 0) " · $noToken can't be reached (no push token)" else ""
            },
            color = textSecondary, fontSize = 12.sp
        )

        Button(
            onClick = { confirm = true },
            enabled = canSend,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
        ) {
            Text(if (sending) "Sending…" else "Send notification", color = Color.White, fontWeight = FontWeight.Bold)
        }

        result?.let {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = accentGreen.copy(alpha = 0.10f)),
                border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.35f))
            ) { Text(it, color = textPrimary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(12.dp)) }
        }
    }

    if (confirm) {
        val who = when (audience) {
            "everyone" -> "EVERYONE"
            "selected" -> "${selected.size} selected user${if (selected.size == 1) "" else "s"}"
            else -> AUDIENCES.first { it.first == audience }.second.lowercase()
        }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Send to $who?") },
            text = { Text("“$title”\n\n$message") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        sending = true
                        FirebaseAuthService.refreshIdTokenIfNeeded(context)
                        val body = JSONObject()
                            .put("audience", audience).put("title", title.trim())
                            .put("message", message.trim()).put("imageUrl", imageUrl.trim())
                            .put("uids", JSONArray(selected.toList()))
                        val res = NotifierServer.postJson("/admin/send", FirebaseService.activeToken, body)
                        sending = false
                        if (res == null) {
                            result = "Couldn't reach the server — check Admin ▸ Server."
                        } else if (!res.optBoolean("ok")) {
                            result = "Not sent: ${res.optString("error", "server error")}"
                        } else {
                            result = if (audience == "everyone") "Broadcast sent to everyone."
                            else "Delivered to ${res.optInt("delivered")} device(s) · ${res.optInt("failed")} failed · " +
                                "${res.optInt("no_token")} had no push token."
                            viewModel.logNotificationSent(audience, title.trim(), result ?: "")
                            title = ""; message = ""; imageUrl = ""
                        }
                        Toast.makeText(context, result ?: "", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Send", color = accentGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
