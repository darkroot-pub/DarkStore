package com.example.view

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.example.data.ChatMessageEntity
import com.example.data.UserEntity
import com.example.uploadImageToImgBB
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

// ───────────────────────────── top level ─────────────────────────────

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
    val threads by viewModel.chatThreads.collectAsStateWithLifecycle()
    val developers by viewModel.developers.collectAsStateWithLifecycle()
    val activePeer by viewModel.activeChatPeer.collectAsStateWithLifecycle()
    val myUid by viewModel.userUid.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(0) } // 0 inbox · 1 global · 2 developers
    var search by remember { mutableStateOf("") }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.refreshChatThreads()
            viewModel.refreshDevelopers()
        }
    }

    if (!isLoggedIn) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.glass(isDarkMode, RoundedCornerShape(28.dp), 4.dp).padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Chat, null, tint = accentGreen, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text("Sign in to chat", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("Message developers and join the global chat after you log in.", color = textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        return
    }

    if (activePeer != null) {
        ChatThreadScreen(
            viewModel = viewModel, peer = activePeer!!, myUid = myUid, isDarkMode = isDarkMode,
            accent = accentGreen, textPrimary = textPrimary, textSecondary = textSecondary,
            cardBgColor = cardBgColor, cardBorderColor = cardBorderColor, onAppClick = onAppClick,
            onBack = { viewModel.closeChat() }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp)) {
            Text(tr("chat_messages"), color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text(tr("chat_sub"), color = textSecondary, fontSize = 12.sp)
        }
        ChatTabs(
            labels = listOf(tr("chat_inbox"), tr("chat_global"), tr("chat_developers")),
            selected = section, isDark = isDarkMode, accent = accentGreen, textSecondary = textSecondary,
            onSelect = { section = it }
        )
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (section) {
                0 -> InboxList(
                    threads = threads, developers = developers, viewModel = viewModel, isDark = isDarkMode,
                    accent = accentGreen, textPrimary = textPrimary, textSecondary = textSecondary
                )
                1 -> GlobalChatScreen(
                    viewModel = viewModel, myUid = myUid, developers = developers, isDark = isDarkMode,
                    accent = accentGreen, textPrimary = textPrimary, textSecondary = textSecondary,
                    cardBgColor = cardBgColor, cardBorderColor = cardBorderColor, onAppClick = onAppClick
                )
                else -> DevelopersList(
                    developers = developers, myUid = myUid, search = search, onSearch = { search = it },
                    viewModel = viewModel, isDark = isDarkMode, accent = accentGreen,
                    textPrimary = textPrimary, textSecondary = textSecondary
                )
            }
        }
    }
}

/** Glass segmented control with a liquid bubble that slides between the tabs. */
@Composable
private fun ChatTabs(
    labels: List<String>, selected: Int, isDark: Boolean, accent: Color, textSecondary: Color, onSelect: (Int) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).glass(isDark, RoundedCornerShape(24.dp), 3.dp)) {
        val itemW = (maxWidth - 8.dp) / labels.size
        val bubbleX by animateDpAsState(
            targetValue = itemW * selected,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "chat_tab_bubble"
        )
        Box(Modifier.padding(4.dp)) {
            Box(
                Modifier.offset(x = bubbleX).width(itemW).height(40.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.68f))))
                    .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
            )
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).clickable { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selected == i) Color.White else textSecondary, fontSize = 13.sp,
                            fontWeight = if (selected == i) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── inbox / developers ─────────────────────────────

@Composable
private fun InboxList(
    threads: List<com.example.data.ChatThreadEntity>, developers: List<UserEntity>, viewModel: StoreViewModel,
    isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color
) {
    if (threads.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth().glass(isDark, RoundedCornerShape(26.dp), 3.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ChatBubbleOutline, null, tint = accent, modifier = Modifier.size(40.dp))
                Text(tr("chat_no_convos"), color = textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text(tr("chat_no_convos_sub"), color = textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp + LocalNavBarInset.current)
    ) {
        items(threads, key = { it.chatId }) { thread ->
            val livePeer = developers.find { it.uid == thread.otherUid }
            val livePhoto = livePeer?.profilePhotoUrl?.takeIf { it.isNotBlank() } ?: thread.otherPhoto
            val liveName = livePeer?.let { it.devName.ifBlank { it.displayName } }?.takeIf { it.isNotBlank() } ?: thread.otherName
            Row(
                Modifier.fillMaxWidth().glass(isDark, RoundedCornerShape(24.dp), 3.dp).clickable {
                    viewModel.openChatWith(
                        livePeer ?: UserEntity(
                            uid = thread.otherUid, email = "", displayName = thread.otherName, role = "user",
                            isDeveloper = false, devName = thread.otherName, profilePhotoUrl = thread.otherPhoto
                        )
                    )
                }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarBubble(livePhoto, liveName, accent, isDark = isDark)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(liveName.ifBlank { "User" }, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(shortAgo(thread.updatedAt), color = if (thread.unread > 0) accent else textSecondary, fontSize = 11.sp)
                    }
                    Text(
                        thread.lastMessage.ifBlank { "…" },
                        color = if (thread.unread > 0) textPrimary else textSecondary,
                        fontWeight = if (thread.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (thread.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.7f))))
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (thread.unread > 99) "99+" else thread.unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DevelopersList(
    developers: List<UserEntity>, myUid: String, search: String, onSearch: (String) -> Unit, viewModel: StoreViewModel,
    isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color
) {
    val list = remember(developers, search, myUid) {
        developers
            .filter { it.isDeveloper || it.role.equals("admin", true) }
            .filter { it.uid != myUid }
            .filter {
                search.isBlank() || it.devName.contains(search, true) || it.displayName.contains(search, true) ||
                    it.email.contains(search, true) || it.devBio.contains(search, true)
            }
            .sortedBy { it.devName.ifBlank { it.displayName }.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(46.dp).glass(isDark, RoundedCornerShape(23.dp), 3.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = search, onValueChange = onSearch, singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(accent), modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (search.isEmpty()) Text(tr("chat_search_dev"), color = textSecondary, fontSize = 14.sp)
                            inner()
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp + LocalNavBarInset.current)
        ) {
            items(list, key = { it.uid }) { dev ->
                val name = dev.devName.ifBlank { dev.displayName }.ifBlank { dev.email }
                Row(
                    Modifier.fillMaxWidth().glass(isDark, RoundedCornerShape(24.dp), 3.dp).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarBubble(dev.profilePhotoUrl, name, accent, isDark = isDark)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (dev.role.equals("admin", true)) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(15.dp))
                            }
                        }
                        Text(dev.devBio.ifBlank { if (dev.isDeveloper) "Developer" else "Member" }, color = textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(18.dp))
                            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.7f))))
                            .clickable { viewModel.openChatWith(dev) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Chat, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(tr("chat_message_btn"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarBubble(photo: String, name: String, accent: Color, isDark: Boolean, size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(accent.copy(alpha = 0.16f)).border(1.dp, glassEdge(isDark), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photo.isNotBlank()) {
            AsyncImage(model = photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.38f).sp)
        }
    }
}

// ───────────────────────────── 1:1 thread ─────────────────────────────

@Composable
private fun ChatThreadScreen(
    viewModel: StoreViewModel, peer: UserEntity, myUid: String, isDarkMode: Boolean,
    accent: Color, textPrimary: Color, textSecondary: Color, cardBgColor: Color, cardBorderColor: Color,
    onAppClick: ((com.example.data.AppEntity) -> Unit)?, onBack: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val peerTyping by viewModel.peerTyping.collectAsStateWithLifecycle()
    val peerReadAt by viewModel.peerReadAt.collectAsStateWithLifecycle()
    val myRole by viewModel.userRole.collectAsStateWithLifecycle()
    val peerName = peer.devName.ifBlank { peer.displayName }.ifBlank { peer.email }
    var showProfile by remember { mutableStateOf(false) }

    // Poll every 3 s while this conversation is on screen (stops when the app is in the background)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(peer.uid, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                kotlinx.coroutines.delay(3_000)
                viewModel.pollChats()
            }
        }
    }

    if (showProfile) {
        DeveloperProfileDialog(
            viewModel = viewModel, user = peer, accentGreen = accent, textPrimary = textPrimary,
            textSecondary = textSecondary, cardBgColor = cardBgColor, cardBorderColor = cardBorderColor,
            onAppClick = onAppClick, onDismiss = { showProfile = false }
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().glass(isDarkMode, RoundedCornerShape(26.dp), 4.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowBack, "Back", tint = textPrimary, modifier = Modifier.size(22.dp))
            }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { showProfile = true }, verticalAlignment = Alignment.CenterVertically) {
                AvatarBubble(peer.profilePhotoUrl, peerName, accent, isDarkMode, 42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(peerName, color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (peerTyping) tr("chat_typing") else if (peer.isDeveloper) "Developer" else "Member",
                        color = if (peerTyping) accent else textSecondary, fontSize = 11.sp,
                        fontWeight = if (peerTyping) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { viewModel.refreshOpenChat() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Refresh, "Refresh", tint = textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        ChatConversation(
            messages = messages, myUid = myUid, isGlobal = false, isAdmin = myRole.equals("admin", true),
            peerTyping = peerTyping, peerReadAt = peerReadAt, isDark = isDarkMode, accent = accent,
            textPrimary = textPrimary, textSecondary = textSecondary,
            onSend = { text, reply -> viewModel.sendChatMessage(text, "", reply) },
            onSendImage = { url, reply -> viewModel.sendChatMessage("", url, reply) },
            onEdit = { m, t -> viewModel.editMessage(false, m, t) },
            onDelete = { m -> viewModel.deleteMessage(false, m) },
            onReact = { m, e -> viewModel.reactToMessage(false, m, e) },
            onTyping = { viewModel.notifyTyping() },
            onAvatarClick = { },
            modifier = Modifier.weight(1f)
        )
    }
}

// ───────────────────────────── global room ─────────────────────────────

@Composable
private fun GlobalChatScreen(
    viewModel: StoreViewModel, myUid: String, developers: List<UserEntity>, isDark: Boolean,
    accent: Color, textPrimary: Color, textSecondary: Color, cardBgColor: Color, cardBorderColor: Color,
    onAppClick: ((com.example.data.AppEntity) -> Unit)?
) {
    val messages by viewModel.globalMessages.collectAsStateWithLifecycle()
    val myRole by viewModel.userRole.collectAsStateWithLifecycle()
    var profileUser by remember { mutableStateOf<UserEntity?>(null) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshGlobalChat()
                kotlinx.coroutines.delay(4_000)
            }
        }
    }

    profileUser?.let { u ->
        DeveloperProfileDialog(
            viewModel = viewModel, user = u, accentGreen = accent, textPrimary = textPrimary,
            textSecondary = textSecondary, cardBgColor = cardBgColor, cardBorderColor = cardBorderColor,
            onAppClick = onAppClick, onDismiss = { profileUser = null }
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(isDark, RoundedCornerShape(22.dp), 3.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Public, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(tr("chat_global_title"), color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(tr("chat_global_sub"), color = textSecondary, fontSize = 11.sp)
            }
        }
        ChatConversation(
            messages = messages, myUid = myUid, isGlobal = true, isAdmin = myRole.equals("admin", true),
            peerTyping = false, peerReadAt = 0L, isDark = isDark, accent = accent,
            textPrimary = textPrimary, textSecondary = textSecondary,
            onSend = { text, reply -> viewModel.sendGlobalMessage(text, "", reply) },
            onSendImage = { url, reply -> viewModel.sendGlobalMessage("", url, reply) },
            onEdit = { m, t -> viewModel.editMessage(true, m, t) },
            onDelete = { m -> viewModel.deleteMessage(true, m) },
            onReact = { m, e -> viewModel.reactToMessage(true, m, e) },
            onTyping = { },
            onAvatarClick = { m ->
                profileUser = developers.find { it.uid == m.senderId } ?: UserEntity(
                    uid = m.senderId, email = "", displayName = m.senderName, role = "user",
                    isDeveloper = false, devName = "", profilePhotoUrl = m.senderPhoto
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}

// ───────────────────────────── conversation (shared) ─────────────────────────────

private sealed interface ChatRow {
    data class Day(val label: String, val key: String) : ChatRow
    data class Msg(val m: ChatMessageEntity, val first: Boolean, val last: Boolean) : ChatRow
}

private fun dayKey(ts: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun buildRows(messages: List<ChatMessageEntity>): List<ChatRow> {
    val today = tr("chat_today")
    val yesterday = tr("chat_yesterday")
    return remember(messages, today, yesterday) {
        val out = mutableListOf<ChatRow>()
        val nowKey = dayKey(System.currentTimeMillis())
        val yKey = dayKey(System.currentTimeMillis() - 86_400_000L)
        var lastDay = -1
        messages.forEachIndexed { i, m ->
            val dk = dayKey(m.timestamp)
            if (dk != lastDay) {
                lastDay = dk
                val label = when (dk) {
                    nowKey -> today
                    yKey -> yesterday
                    else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(m.timestamp))
                }
                out.add(ChatRow.Day(label, "day_$dk"))
            }
            val prev = messages.getOrNull(i - 1)
            val next = messages.getOrNull(i + 1)
            fun same(a: ChatMessageEntity?, b: ChatMessageEntity) =
                a != null && a.senderId == b.senderId && dayKey(a.timestamp) == dayKey(b.timestamp) && kotlin.math.abs(a.timestamp - b.timestamp) < 5 * 60_000L
            out.add(ChatRow.Msg(m, first = !same(prev, m), last = !same(next, m)))
        }
        out
    }
}

@Composable
private fun ChatConversation(
    messages: List<ChatMessageEntity>, myUid: String, isGlobal: Boolean, isAdmin: Boolean,
    peerTyping: Boolean, peerReadAt: Long, isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color,
    onSend: (String, ChatMessageEntity?) -> Unit,
    onSendImage: (String, ChatMessageEntity?) -> Unit,
    onEdit: (ChatMessageEntity, String) -> Unit,
    onDelete: (ChatMessageEntity) -> Unit,
    onReact: (ChatMessageEntity, String) -> Unit,
    onTyping: () -> Unit,
    onAvatarClick: (ChatMessageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val rows = buildRows(messages)
    val copiedMsg = tr("chat_copied")

    var draft by remember { mutableStateOf("") }
    var replying by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var editing by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var actionTarget by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var firstScroll by remember { mutableStateOf(true) }

    // The index of my newest message that the other person has seen (1:1 only)
    val lastOwnId = remember(messages, myUid) { messages.lastOrNull { it.senderId == myUid && !it.deleted }?.id }

    LaunchedEffect(rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val nearBottom = (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= rows.size - 3
        val lastIsMine = (rows.last() as? ChatRow.Msg)?.m?.senderId == myUid
        if (firstScroll) { listState.scrollToItem(rows.lastIndex); firstScroll = false }
        else if (nearBottom || lastIsMine) listState.animateScrollToItem(rows.lastIndex)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            val bytes = prepareProfileImageBytes(context, uri, 1280)
            val url = if (bytes == null) null else withContext(Dispatchers.IO) {
                uploadImageToImgBB(context, bytes) { msg -> scope.launch(Dispatchers.Main) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } }
            }
            uploading = false
            if (url != null) { onSendImage(url, replying); replying = null }
            else Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
        }
    }

    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bottomPad = if (imeOpen) 0.dp else LocalNavBarInset.current

    Column(modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.glass(isDark, RoundedCornerShape(24.dp), 3.dp).padding(horizontal = 26.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (isGlobal) "🌍" else "👋", fontSize = 34.sp)
                    Text(if (isGlobal) tr("chat_empty_global") else tr("chat_empty_thread"), color = textSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            LazyColumn(
                state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(rows, key = { r -> when (r) { is ChatRow.Day -> r.key; is ChatRow.Msg -> r.m.id } }) { row ->
                    when (row) {
                        is ChatRow.Day -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(row.label, color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.glass(isDark, RoundedCornerShape(12.dp), 1.dp).padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                        is ChatRow.Msg -> MessageBubble(
                            m = row.m, first = row.first, last = row.last, mine = row.m.senderId == myUid, myUid = myUid,
                            isGlobal = isGlobal, isDark = isDark, accent = accent, textPrimary = textPrimary, textSecondary = textSecondary,
                            showTick = !isGlobal && row.m.senderId == myUid && row.m.id == lastOwnId, seen = row.m.timestamp <= peerReadAt,
                            onLongPress = { if (!row.m.deleted) actionTarget = row.m },
                            onDoubleTap = { if (!row.m.deleted) onReact(row.m, "❤️") },
                            onReactClick = { e -> onReact(row.m, e) },
                            onAvatar = { onAvatarClick(row.m) }
                        )
                    }
                }
            }
        }

        if (peerTyping) TypingPill(isDark, accent)

        // reply / edit banner
        val bannerMsg = editing ?: replying
        AnimatedVisibility(visible = bannerMsg != null) {
            val m = bannerMsg
            if (m != null) {
                Row(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().glass(isDark, RoundedCornerShape(18.dp), 2.dp).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (editing != null) tr("chat_editing") else tr("chat_replying", if (m.senderId == myUid) tr("chat_you") else m.senderName.ifBlank { tr("chat_them") }),
                            color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Text(m.text.ifBlank { "📷 Photo" }, color = textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.Close, null, tint = textSecondary, modifier = Modifier.size(20.dp).clip(CircleShape).clickable {
                        if (editing != null) { editing = null; draft = "" } else replying = null
                    })
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        // composer
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp + bottomPad).fillMaxWidth()
                .glass(isDark, RoundedCornerShape(30.dp), 5.dp).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = !uploading && editing == null) { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                else Icon(Icons.Default.Image, null, tint = if (editing == null) accent else textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(if (isGlobal) 600 else 2000); if (editing == null) onTyping() },
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 8.dp),
                maxLines = 4,
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(accent),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) Text(tr("chat_hint"), color = textSecondary, fontSize = 15.sp)
                        inner()
                    }
                }
            )
            val canSend = draft.isNotBlank()
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.7f))))
                    .alpha(if (canSend) 1f else 0.45f)
                    .clickable(enabled = canSend) {
                        val t = draft.trim()
                        val e = editing
                        if (e != null) { onEdit(e, t); editing = null }
                        else { onSend(t, replying); replying = null }
                        draft = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (editing != null) Icons.Default.Check else Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }

    actionTarget?.let { m ->
        val mine = m.senderId == myUid
        MessageActionsDialog(
            m = m, canEdit = mine && m.text.isNotBlank(), canDelete = mine || (isGlobal && isAdmin), isDark = isDark,
            accent = accent, textPrimary = textPrimary, textSecondary = textSecondary,
            onDismiss = { actionTarget = null },
            onReact = { e -> onReact(m, e); actionTarget = null },
            onReply = { replying = m; editing = null; actionTarget = null },
            onCopy = {
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("message", m.text))
                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                actionTarget = null
            },
            onEdit = { editing = m; replying = null; draft = m.text; actionTarget = null },
            onDelete = { deleteTarget = m; actionTarget = null }
        )
    }
    deleteTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("chat_delete_title")) },
            text = { Text(tr("chat_delete_body")) },
            confirmButton = {
                TextButton(onClick = { onDelete(m); deleteTarget = null }) {
                    Text(tr("chat_act_delete"), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("vid_close")) } }
        )
    }
}

// ───────────────────────────── message bubble ─────────────────────────────

@Composable
private fun MessageBubble(
    m: ChatMessageEntity, first: Boolean, last: Boolean, mine: Boolean, myUid: String, isGlobal: Boolean, isDark: Boolean,
    accent: Color, textPrimary: Color, textSecondary: Color, showTick: Boolean, seen: Boolean,
    onLongPress: () -> Unit, onDoubleTap: () -> Unit, onReactClick: (String) -> Unit, onAvatar: () -> Unit
) {
    val shape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (!mine && last) 6.dp else 20.dp,
        bottomEnd = if (mine && last) 6.dp else 20.dp
    )
    Row(
        Modifier.fillMaxWidth().padding(top = if (first) 6.dp else 0.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine && isGlobal) {
            if (last) Box(Modifier.clip(CircleShape).clickable(onClick = onAvatar)) { AvatarBubble(m.senderPhoto, m.senderName, accent, isDark, 30.dp) }
            else Spacer(Modifier.width(30.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine && isGlobal && first) {
                Text(m.senderName.ifBlank { "User" }, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp))
            }
            val bubbleMod = Modifier
                .widthIn(max = 290.dp)
                .then(
                    if (m.deleted) Modifier.clip(shape).border(1.dp, textSecondary.copy(alpha = 0.3f), shape)
                    else if (mine) Modifier.clip(shape)
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.72f))))
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.24f), Color.Transparent), end = androidx.compose.ui.geometry.Offset(420f, 300f)))
                        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))), shape)
                    else Modifier.glass(isDark, shape, 2.dp)
                )
                .pointerInput(m.id, m.deleted) {
                    detectTapGestures(onLongPress = { onLongPress() }, onDoubleTap = { onDoubleTap() })
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
            val ink = if (mine && !m.deleted) Color.White else textPrimary
            Column(bubbleMod) {
                if (m.deleted) {
                    Text(tr("chat_deleted"), color = textSecondary, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                } else {
                    if (m.replyToId.isNotBlank()) {
                        Row(
                            Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (mine) Color.Black.copy(alpha = 0.18f) else accent.copy(alpha = 0.10f)).padding(8.dp)
                        ) {
                            Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(if (mine) Color.White else accent))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(m.replyToName.ifBlank { "…" }, color = if (mine) Color.White else accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(m.replyToText.ifBlank { "…" }, color = ink.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (m.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = m.imageUrl, contentDescription = "Photo", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(14.dp))
                        )
                        if (m.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }
                    if (m.text.isNotBlank()) Text(m.text, color = ink, fontSize = 15.sp, lineHeight = 20.sp)
                }
                Row(Modifier.align(Alignment.End).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (m.edited && !m.deleted) Text(tr("chat_edited") + " · ", color = ink.copy(alpha = 0.65f), fontSize = 10.sp)
                    Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timestamp)), color = ink.copy(alpha = 0.65f), fontSize = 10.sp)
                    if (showTick) {
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            if (seen) Icons.Default.DoneAll else Icons.Default.Done, null,
                            tint = if (seen) Color(0xFF9BE7FF) else Color.White.copy(alpha = 0.75f), modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            if (m.reactions.isNotEmpty() && !m.deleted) {
                val grouped = m.reactions.values.groupingBy { it }.eachCount()
                Row(Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    grouped.forEach { (emoji, n) ->
                        val iReacted = m.reactions[myUid] == emoji
                        Row(
                            Modifier.glass(isDark, RoundedCornerShape(12.dp), 1.dp)
                                .then(if (iReacted) Modifier.border(1.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { onReactClick(emoji) }.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 13.sp)
                            if (n > 1) Text(" $n", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingPill(isDark: Boolean, accent: Color) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.padding(start = 16.dp, bottom = 4.dp).glass(isDark, RoundedCornerShape(16.dp), 1.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse), label = "dot$i"
            )
            Box(Modifier.padding(horizontal = 2.dp).size(7.dp).clip(CircleShape).background(accent.copy(alpha = a)))
        }
    }
}

// ───────────────────────────── long-press sheet ─────────────────────────────

@Composable
private fun MessageActionsDialog(
    m: ChatMessageEntity, canEdit: Boolean, canDelete: Boolean, isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color,
    onDismiss: () -> Unit, onReact: (String) -> Unit, onReply: () -> Unit, onCopy: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val sheetBg = if (isDark) Color(0xFF1B1F27) else Color.White
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDismiss), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(28.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(sheetBg)
                    .border(1.dp, glassEdge(isDark), RoundedCornerShape(28.dp)).clickable(enabled = false) { }.padding(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QUICK_REACTIONS.forEach { e ->
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)).clickable { onReact(e) }, contentAlignment = Alignment.Center) {
                            Text(e, fontSize = 22.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ActionRow(Icons.Default.Reply, tr("chat_act_reply"), textPrimary, textPrimary, onReply)
                if (m.text.isNotBlank()) ActionRow(Icons.Default.ContentCopy, tr("chat_act_copy"), textPrimary, textPrimary, onCopy)
                if (canEdit) ActionRow(Icons.Default.Edit, tr("chat_act_edit"), textPrimary, textPrimary, onEdit)
                if (canDelete) ActionRow(Icons.Default.Delete, tr("chat_act_delete"), Color(0xFFEF4444), Color(0xFFEF4444), onDelete)
            }
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, textColor: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun shortAgo(ts: Long): String {
    if (ts <= 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}
