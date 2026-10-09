package com.example.view

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.data.UserEntity
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatTabContent(
    viewModel: StoreViewModel,
    isLoggedIn: Boolean,
    isDarkMode: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onAppClick: ((com.example.data.AppEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val threads by viewModel.chatThreads.collectAsStateWithLifecycle()
    val developers by viewModel.developers.collectAsStateWithLifecycle()
    val activePeer by viewModel.activeChatPeer.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val myUid by viewModel.userUid.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(0) } // 0 inbox, 1 developers
    var search by remember { mutableStateOf("") }

    val bg = if (isDarkMode) Color(0xFF0F1115) else Color(0xFFF8FAFC)
    val surface = cardBgColor
    val border = cardBorderColor

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.refreshChatThreads()
            viewModel.refreshDevelopers()
        }
    }

    if (!isLoggedIn) {
        Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Chat, null, tint = textSecondary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Sign in to message developers", color = textPrimary, fontWeight = FontWeight.Bold)
                Text("Chat is available after login.", color = textSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    if (activePeer != null) {
        ChatThreadScreen(
            viewModel = viewModel,
            peer = activePeer!!,
            messages = messages,
            myUid = myUid,
            isDarkMode = isDarkMode,
            accentGreen = accentGreen,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            surface = surface,
            border = border,
            onAppClick = onAppClick,
            onBack = { viewModel.closeChat() }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(bg)) {
        Text(
            tr("chat_messages"),
            color = textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(surface, RoundedCornerShape(12.dp))
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            listOf(tr("chat_inbox") to 0, tr("chat_developers") to 1).forEach { (label, idx) ->
                val sel = section == idx
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) accentGreen else Color.Transparent)
                        .clickable { section = idx }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (sel) Color.White else textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (section == 1) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search developers…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = textSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentGreen,
                    unfocusedBorderColor = border,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                )
            )
            Spacer(Modifier.height(8.dp))
        }

        val devList = remember(developers, search, myUid) {
            developers
                .filter { it.isDeveloper || it.role.equals("admin", true) }
                .filter { it.uid != myUid }
                .filter {
                    if (search.isBlank()) true
                    else {
                        val q = search.lowercase()
                        it.devName.contains(q, true) || it.displayName.contains(q, true) ||
                            it.email.contains(q, true) || it.devBio.contains(q, true)
                    }
                }
                .sortedBy { it.devName.ifBlank { it.displayName }.lowercase() }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp + LocalNavBarInset.current)
        ) {
            if (section == 0) {
                if (threads.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No conversations yet", color = textPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Open Developers and tap Message.", color = textSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(threads, key = { it.chatId }) { thread ->
                        val livePeer = developers.find { it.uid == thread.otherUid }
                        val livePhoto = livePeer?.profilePhotoUrl?.takeIf { it.isNotBlank() } ?: thread.otherPhoto
                        val liveName = livePeer?.let { it.devName.ifBlank { it.displayName } }?.takeIf { it.isNotBlank() }
                            ?: thread.otherName
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(surface)
                                .border(1.dp, border, RoundedCornerShape(14.dp))
                                .clickable {
                                    val peer = developers.find { it.uid == thread.otherUid }
                                        ?: UserEntity(
                                            uid = thread.otherUid,
                                            email = "",
                                            displayName = thread.otherName,
                                            role = "user",
                                            isDeveloper = true,
                                            devName = thread.otherName,
                                            profilePhotoUrl = thread.otherPhoto
                                        )
                                    viewModel.openChatWith(peer)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarBubble(livePhoto, liveName, accentGreen)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        liveName.ifBlank { "Developer" },
                                        color = textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(formatChatTime(thread.updatedAt), color = textSecondary, fontSize = 11.sp)
                                }
                                Text(
                                    thread.lastMessage.ifBlank { "…" },
                                    color = if (thread.unread > 0) textPrimary else textSecondary,
                                    fontWeight = if (thread.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (thread.unread > 0) {
                                Spacer(Modifier.width(8.dp))
                                // Numeric unread badge: 1, 2, 3 … 99+
                                Box(
                                    Modifier
                                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                        .clip(CircleShape)
                                        .background(accentGreen)
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (thread.unread > 99) "99+" else thread.unread.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(devList, key = { it.uid }) { dev ->
                    val name = dev.devName.ifBlank { dev.displayName }.ifBlank { dev.email }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(surface)
                            .border(1.dp, border, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarBubble(dev.profilePhotoUrl, name, accentGreen)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                                if (dev.role.equals("admin", true)) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Verified, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                dev.devBio.ifBlank { if (dev.isDeveloper) "Developer" else "User" },
                                color = textSecondary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { viewModel.openChatWith(dev) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Message", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarBubble(photo: String, name: String, accent: Color) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (photo.isNotBlank()) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(name.take(1).uppercase(), color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ChatThreadScreen(
    viewModel: StoreViewModel,
    peer: UserEntity,
    messages: List<com.example.data.ChatMessageEntity>,
    myUid: String,
    isDarkMode: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    surface: Color,
    border: Color,
    onAppClick: ((com.example.data.AppEntity) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val peerName = peer.devName.ifBlank { peer.displayName }.ifBlank { peer.email }
    var showPeerPhoto by remember { mutableStateOf(false) }

    // New messages while a conversation is open: poll every 3s, but only while
    // the app is actually on screen (stops automatically when backgrounded).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(peer.uid, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                kotlinx.coroutines.delay(3_000)
                viewModel.pollChats()
            }
        }
    }

    if (showPeerPhoto) {
        DeveloperProfileDialog(
            viewModel = viewModel,
            user = peer,
            accentGreen = accentGreen,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            cardBgColor = surface,
            cardBorderColor = border,
            onAppClick = onAppClick,
            onDismiss = { showPeerPhoto = false }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        uploading = false
                        Toast.makeText(context, "Could not read image", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val form = okhttp3.FormBody.Builder().add("image", b64).build()
                val req = okhttp3.Request.Builder()
                    .url("https://api.imgbb.com/1/upload?key=a046c848dfa5230136f107106d4bb187")
                    .post(form)
                    .build()
                val client = okhttp3.OkHttpClient()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                val url = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.replace("\\/", "/")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    uploading = false
                    if (url != null) {
                        viewModel.sendChatMessage("", url) { ok ->
                            if (!ok) Toast.makeText(context, "Failed to send photo", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    uploading = false
                    Toast.makeText(context, "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(if (isDarkMode) Color(0xFF0F1115) else Color(0xFFF8FAFC)).padding(bottom = LocalNavBarInset.current)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(surface)
                .border(BorderStroke(1.dp, border))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = textPrimary)
            }
            Box(Modifier.clip(CircleShape).clickable { showPeerPhoto = true }) {
                AvatarBubble(peer.profilePhotoUrl, peerName, accentGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { showPeerPhoto = true }) {
                Text(peerName, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text(if (peer.isDeveloper) "Developer" else "User", color = textSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = { viewModel.refreshOpenChat() }) {
                Icon(Icons.Default.Refresh, null, tint = textSecondary)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val mine = msg.senderId == myUid
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                ) {
                    Box(
                        Modifier
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (mine) accentGreen else surface)
                            .then(if (!mine) Modifier.border(1.dp, border, RoundedCornerShape(16.dp)) else Modifier)
                            .padding(10.dp)
                    ) {
                        Column {
                            if (msg.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = msg.imageUrl,
                                    contentDescription = "Photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (msg.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                            }
                            if (msg.text.isNotBlank()) {
                                Text(
                                    msg.text,
                                    color = if (mine) Color.White else textPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    Text(
                        formatChatTime(msg.timestamp),
                        color = textSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(surface)
                .border(BorderStroke(1.dp, border))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = !uploading
            ) {
                if (uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accentGreen)
                else Icon(Icons.Default.Image, null, tint = accentGreen)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(tr("chat_hint"), fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentGreen,
                    unfocusedBorderColor = border,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                )
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val t = draft.trim()
                    if (t.isBlank()) return@IconButton
                    draft = ""
                    viewModel.sendChatMessage(t) { ok ->
                        if (!ok) Toast.makeText(context, "Failed to send", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentGreen)
            ) {
                Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatChatTime(ts: Long): String {
    if (ts <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}
