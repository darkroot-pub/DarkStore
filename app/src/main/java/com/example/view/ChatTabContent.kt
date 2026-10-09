package com.example.view

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Brush
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
import com.example.data.ChatMessageEntity
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
    val inGlobal by viewModel.inGlobalChat.collectAsStateWithLifecycle()
    val globalMessages by viewModel.globalChatMessages.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(0) } // 0 inbox, 1 global, 2 developers
    var search by remember { mutableStateOf("") }

    if (!isLoggedIn) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .padding(32.dp)
                    .glass(isDarkMode, RoundedCornerShape(28.dp), 4.dp)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Chat, null, tint = accentGreen, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(tr("chat_messages"), color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("Chat is available after login.", color = textSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    if (inGlobal) {
        GlobalChatScreen(
            viewModel = viewModel,
            messages = globalMessages,
            myUid = myUid,
            isDarkMode = isDarkMode,
            accentGreen = accentGreen,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onBack = {
                viewModel.closeGlobalChat()
                section = 0
            }
        )
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
            surface = cardBgColor,
            border = cardBorderColor,
            onAppClick = onAppClick,
            onBack = { viewModel.closeChat() }
        )
        return
    }

    LaunchedEffect(Unit) { viewModel.refreshChatThreads() }

    Column(Modifier.fillMaxSize().padding(bottom = LocalNavBarInset.current)) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("chat_messages"), color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Text("Inbox · Global · Developers", color = textSecondary, fontSize = 12.sp)
            }
            Box(
                Modifier
                    .size(42.dp)
                    .glass(isDarkMode, CircleShape, 2.dp)
                    .clickable { viewModel.refreshChatThreads() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, null, tint = accentGreen, modifier = Modifier.size(20.dp))
            }
        }

        // Segmented tabs — liquid glass
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .glass(isDarkMode, RoundedCornerShape(16.dp), 2.dp)
                .padding(4.dp)
        ) {
            listOf(
                tr("chat_inbox") to 0,
                "Global" to 1,
                tr("chat_developers") to 2
            ).forEach { (label, idx) ->
                val sel = section == idx
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) accentGreen else Color.Transparent)
                        .clickable {
                            section = idx
                            if (idx == 1) viewModel.openGlobalChat()
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (idx == 1) {
                            Icon(
                                Icons.Default.Public, null,
                                tint = if (sel) Color.White else textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            label,
                            color = if (sel) Color.White else textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (section == 2) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search developers…", fontSize = 13.sp, color = textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = textSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentGreen,
                    unfocusedBorderColor = cardBorderColor.copy(alpha = 0.5f),
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(8.dp))
        }

        val devList = remember(developers, search, myUid) {
            developers
                .filter { it.isDeveloper || it.role.equals("admin", true) || it.role.equals("developer", true) }
                .filter { it.uid != myUid }
                .filter {
                    search.isBlank() ||
                        it.devName.contains(search, true) ||
                        it.displayName.contains(search, true) ||
                        it.email.contains(search, true)
                }
        }

        when (section) {
            0 -> {
                if (threads.isEmpty()) {
                    EmptyGlassHint(
                        icon = Icons.Default.Inbox,
                        title = "No conversations yet",
                        subtitle = "Message a developer or join Global chat",
                        isDark = isDarkMode,
                        accent = accentGreen,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                    ) {
                        items(threads, key = { it.chatId }) { thread ->
                            val peer = developers.find { it.uid == thread.otherUid }
                            val liveName = peer?.devName?.ifBlank { peer.displayName }
                                ?: thread.otherName.ifBlank { "User" }
                            val livePhoto = peer?.profilePhotoUrl ?: thread.otherPhoto
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .glass(isDarkMode, RoundedCornerShape(20.dp), 2.dp)
                                    .clickable {
                                        val p = peer ?: UserEntity(
                                            uid = thread.otherUid,
                                            email = "",
                                            displayName = thread.otherName,
                                            role = "user",
                                            profilePhotoUrl = thread.otherPhoto
                                        )
                                        viewModel.openChatWith(p)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarBubble(livePhoto, liveName, accentGreen)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            liveName,
                                            color = textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(formatChatTime(thread.updatedAt), color = textSecondary, fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        thread.lastMessage.ifBlank { "…" },
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (thread.unread > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier
                                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                            .clip(CircleShape)
                                            .background(accentGreen)
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (thread.unread > 99) "99+" else "${thread.unread}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                ) {
                    items(devList, key = { it.uid }) { dev ->
                        val name = dev.devName.ifBlank { dev.displayName }.ifBlank { dev.email }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .glass(isDarkMode, RoundedCornerShape(20.dp), 2.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarBubble(dev.profilePhotoUrl, name, accentGreen)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                                Text(
                                    if (dev.role.equals("admin", true)) "Admin" else "Developer",
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = { viewModel.openChatWith(dev) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Chat", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGlassHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isDark: Boolean,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(32.dp)
                .glass(isDark, RoundedCornerShape(24.dp), 3.dp)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = textSecondary, fontSize = 13.sp)
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
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(name.take(1).uppercase(), color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatThreadScreen(
    viewModel: StoreViewModel,
    peer: UserEntity,
    messages: List<ChatMessageEntity>,
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
    var editingMsg by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var deleteConfirm by remember { mutableStateOf<ChatMessageEntity?>(null) }
    val listState = rememberLazyListState()
    val peerName = peer.devName.ifBlank { peer.displayName }.ifBlank { peer.email }
    var showPeerPhoto by remember { mutableStateOf(false) }

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

    if (deleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text("Delete message?") },
            text = { Text("This message will be removed for everyone.") },
            confirmButton = {
                TextButton(onClick = {
                    val m = deleteConfirm!!
                    deleteConfirm = null
                    viewModel.deleteChatMessage(m.id) { ok ->
                        if (!ok) Toast.makeText(context, "Couldn't delete", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) { Text("Cancel") }
            }
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
                var err = ""
                val url = com.example.uploadImageToImgBB(context, bytes) { err = it }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    uploading = false
                    if (url.isNullOrBlank()) {
                        Toast.makeText(context, err.ifBlank { "Upload failed" }, Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.sendChatMessage("", url) { ok ->
                            if (!ok) Toast.makeText(context, "Send failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    uploading = false
                    Toast.makeText(context, "Upload error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(bottom = LocalNavBarInset.current)) {
        // Thread header — glass bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .glass(isDarkMode, RoundedCornerShape(20.dp), 2.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = textPrimary)
            }
            Box(Modifier.clickable { showPeerPhoto = true }) {
                AvatarBubble(peer.profilePhotoUrl, peerName, accentGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { showPeerPhoto = true }) {
                Text(peerName, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text("Tap for profile", color = textSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = { viewModel.refreshOpenChat() }) {
                Icon(Icons.Default.Refresh, null, tint = accentGreen)
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val mine = msg.senderId == myUid
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (mine && !msg.deleted) {
                                        // show simple menu via editing or delete
                                    }
                                }
                            )
                    ) {
                        if (msg.deleted) {
                            Text(
                                "Message deleted",
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDarkMode) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        } else {
                            Box(
                                Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = if (mine) 18.dp else 4.dp,
                                            bottomEnd = if (mine) 4.dp else 18.dp
                                        )
                                    )
                                    .then(
                                        if (mine) Modifier.background(
                                            Brush.verticalGradient(
                                                listOf(accentGreen, accentGreen.copy(alpha = 0.85f))
                                            )
                                        )
                                        else Modifier.glass(isDarkMode, RoundedCornerShape(18.dp), 1.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            if (mine) {
                                                // Prefer edit if has text
                                            }
                                        }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    if (msg.imageUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = msg.imageUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .widthIn(max = 220.dp)
                                                .heightIn(max = 200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        if (msg.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                                    }
                                    if (msg.text.isNotBlank()) {
                                        Text(
                                            msg.text,
                                            color = if (mine) Color.White else textPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            ) {
                                Text(formatChatTime(msg.timestamp), color = textSecondary, fontSize = 10.sp)
                                if (msg.editedAt > 0L) {
                                    Text(" · edited", color = textSecondary, fontSize = 10.sp)
                                }
                                if (mine) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Edit",
                                        color = accentGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable {
                                            editingMsg = msg
                                            draft = msg.text
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Delete",
                                        color = Color(0xFFEF4444),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { deleteConfirm = msg }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer — glass
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .glass(isDarkMode, RoundedCornerShape(24.dp), 3.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editingMsg != null) {
                IconButton(onClick = { editingMsg = null; draft = "" }) {
                    Icon(Icons.Default.Close, null, tint = textSecondary, modifier = Modifier.size(20.dp))
                }
            } else {
                IconButton(
                    onClick = { if (!uploading) imagePicker.launch("image/*") },
                    enabled = !uploading
                ) {
                    if (uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accentGreen)
                    else Icon(Icons.Default.Image, null, tint = accentGreen)
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(
                        if (editingMsg != null) "Edit message…" else tr("chat_hint"),
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                )
            )
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentGreen)
                    .clickable {
                        val t = draft.trim()
                        if (t.isBlank() && editingMsg == null) return@clickable
                        if (editingMsg != null) {
                            val id = editingMsg!!.id
                            editingMsg = null
                            draft = ""
                            viewModel.editChatMessage(id, t) { ok ->
                                if (!ok) Toast.makeText(context, "Edit failed", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            draft = ""
                            viewModel.sendChatMessage(t) { ok ->
                                if (!ok) Toast.makeText(context, "Send failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (editingMsg != null) Icons.Default.Check else Icons.Default.Send,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlobalChatScreen(
    viewModel: StoreViewModel,
    messages: List<ChatMessageEntity>,
    myUid: String,
    isDarkMode: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var editingMsg by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var deleteConfirm by remember { mutableStateOf<ChatMessageEntity?>(null) }
    val listState = rememberLazyListState()

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                kotlinx.coroutines.delay(3_000)
                viewModel.pollGlobalChat()
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    if (deleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text("Delete message?") },
            text = { Text("This message will be removed from Global chat.") },
            confirmButton = {
                TextButton(onClick = {
                    val m = deleteConfirm!!
                    deleteConfirm = null
                    viewModel.deleteGlobalChatMessage(m.id) { ok ->
                        if (!ok) Toast.makeText(context, "Couldn't delete", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(bottom = LocalNavBarInset.current)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .glass(isDarkMode, RoundedCornerShape(20.dp), 2.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = textPrimary)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Public, null, tint = accentGreen, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Global chat", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Everyone can see these messages", color = textSecondary, fontSize = 11.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val mine = msg.senderId == myUid
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        if (msg.deleted) {
                            Text(
                                "Message deleted",
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDarkMode) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        } else {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .then(
                                        if (mine) Modifier.background(
                                            Brush.verticalGradient(listOf(accentGreen, accentGreen.copy(0.85f)))
                                        )
                                        else Modifier.glass(isDarkMode, RoundedCornerShape(18.dp), 1.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    msg.text,
                                    color = if (mine) Color.White else textPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp
                                )
                            }
                            Row(Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)) {
                                Text(formatChatTime(msg.timestamp), color = textSecondary, fontSize = 10.sp)
                                if (msg.editedAt > 0L) Text(" · edited", color = textSecondary, fontSize = 10.sp)
                                if (mine) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("Edit", color = accentGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable {
                                            editingMsg = msg
                                            draft = msg.text
                                        })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delete", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { deleteConfirm = msg })
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .glass(isDarkMode, RoundedCornerShape(24.dp), 3.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editingMsg != null) {
                IconButton(onClick = { editingMsg = null; draft = "" }) {
                    Icon(Icons.Default.Close, null, tint = textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(if (editingMsg != null) "Edit message…" else "Say something to everyone…", fontSize = 14.sp, color = textSecondary)
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                )
            )
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentGreen)
                    .clickable {
                        val t = draft.trim()
                        if (t.isBlank()) return@clickable
                        if (editingMsg != null) {
                            val id = editingMsg!!.id
                            editingMsg = null
                            draft = ""
                            viewModel.editGlobalChatMessage(id, t) { ok ->
                                if (!ok) Toast.makeText(context, "Edit failed", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            draft = ""
                            viewModel.sendGlobalChatMessage(t) { ok ->
                                if (!ok) Toast.makeText(context, "Send failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (editingMsg != null) Icons.Default.Check else Icons.Default.Send,
                    null, tint = Color.White, modifier = Modifier.size(18.dp)
                )
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
        diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))
    }
}
