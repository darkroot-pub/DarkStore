package com.example.view

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AppEntity
import com.example.data.BannerEntity
import com.example.data.CollectionEntity
import com.example.uploadImageToImgBB
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Auto-sliding banner carousel for the top of the home screen. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BannerCarousel(
    banners: List<BannerEntity>,
    isDark: Boolean,
    accent: Color,
    onBannerClick: (BannerEntity) -> Unit
) {
    if (banners.isEmpty()) return
    val pager = rememberPagerState(pageCount = { banners.size })
    LaunchedEffect(banners.size) {
        if (banners.size > 1) {
            while (true) {
                delay(5000)
                if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % banners.size)
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(state = pager, pageSpacing = 12.dp, key = { banners[it].id }) { page ->
            val b = banners[page]
            val shape = RoundedCornerShape(26.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(176.dp)
                    .glass(isDark, shape, elevation = 5.dp)
                    .clickable { onBannerClick(b) }
            ) {
                if (b.imageUrl.isNotBlank()) {
                    AsyncImage(model = b.imageUrl, contentDescription = b.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.4f)))))
                }
                if (b.title.isNotBlank() || b.subtitle.isNotBlank() || b.buttonText.isNotBlank()) {
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
                    Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.68f).padding(20.dp)) {
                        if (b.title.isNotBlank()) {
                            Text(b.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (b.subtitle.isNotBlank()) {
                            Text(b.subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (b.buttonText.isNotBlank()) {
                            Box(
                                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) { Text("${b.buttonText}  →", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        if (banners.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                repeat(banners.size) { i ->
                    val on = pager.currentPage == i
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 20.dp else 6.dp)
                            .clip(CircleShape).background(if (on) accent else accent.copy(alpha = 0.28f))
                    )
                }
            }
        }
    }
}

// ───────────────────────── admin ─────────────────────────

/** Admin ▸ Banners: add any number of banner images, edit, hide, delete. */
@Composable
fun BannersAdminPanel(
    viewModel: StoreViewModel,
    banners: List<BannerEntity>,
    apps: List<AppEntity>,
    collections: List<CollectionEntity>,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<BannerEntity?>(null) }
    var deleting by remember { mutableStateOf<BannerEntity?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Home banners", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("Slide at the top of the Apps tab — add as many as you like", color = textSecondary, fontSize = 12.sp)
            }
            Button(onClick = { editing = BannerEntity(order = banners.size) }, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("New", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (banners.isEmpty()) {
            Text("No banners yet — the home screen shows the promoted-apps carousel until you add one.",
                color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        banners.forEach { b ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = b.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(104.dp).height(58.dp).clip(RoundedCornerShape(10.dp)).background(accentGreen.copy(alpha = 0.1f))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.title.ifBlank { "(no title)" }, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${when (b.targetType) { "app" -> "Opens an app"; "collection" -> "Opens a collection"; "link" -> "Opens a link"; else -> "No action" }} · " +
                                "${if (b.isActive) "Visible" else "Hidden"} · #${b.order}",
                            color = textSecondary, fontSize = 11.sp
                        )
                    }
                    Switch(checked = b.isActive, onCheckedChange = { on ->
                        viewModel.saveBanner(b.copy(isActive = on)) { ok -> if (!ok) Toast.makeText(context, "Couldn't update", Toast.LENGTH_SHORT).show() }
                    }, colors = SwitchDefaults.colors(checkedTrackColor = accentGreen, checkedThumbColor = Color.White))
                    IconButton(onClick = { editing = b }) { Icon(Icons.Default.Edit, "Edit", tint = textSecondary) }
                    IconButton(onClick = { deleting = b }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444)) }
                }
            }
        }
    }

    editing?.let { b ->
        BannerEditorDialog(b, apps, collections, accentGreen, textPrimary, textSecondary, cardBgColor, cardBorderColor,
            onDismiss = { editing = null },
            onSave = { saved ->
                viewModel.saveBanner(saved) { ok ->
                    Toast.makeText(context, if (ok) "Banner saved" else "Save failed — check admin permission", Toast.LENGTH_SHORT).show()
                    if (ok) editing = null
                }
            })
    }
    deleting?.let { b ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this banner?") },
            text = { Text("It disappears from every user's home screen.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.deleteBanner(b.id, b.title) { ok -> Toast.makeText(context, if (ok) "Deleted" else "Delete failed", Toast.LENGTH_SHORT).show() }
                }) { Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BannerEditorDialog(
    initial: BannerEntity,
    apps: List<AppEntity>,
    collections: List<CollectionEntity>,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onDismiss: () -> Unit,
    onSave: (BannerEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageUrl by remember { mutableStateOf(initial.imageUrl) }
    var title by remember { mutableStateOf(initial.title) }
    var subtitle by remember { mutableStateOf(initial.subtitle) }
    var buttonText by remember { mutableStateOf(initial.buttonText) }
    var targetType by remember { mutableStateOf(initial.targetType) }
    var targetValue by remember { mutableStateOf(initial.targetValue) }
    var order by remember { mutableStateOf(initial.order.toString()) }
    var uploading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            val bytes = prepareProfileImageBytes(context, uri, 1600)
            if (bytes == null) {
                Toast.makeText(context, "Could not read that image", Toast.LENGTH_SHORT).show()
            } else {
                val url = withContext(Dispatchers.IO) {
                    uploadImageToImgBB(context, bytes) { msg -> scope.launch(Dispatchers.Main) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } }
                }
                if (url != null) imageUrl = url
            }
            uploading = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
        focusedBorderColor = accentGreen, unfocusedBorderColor = cardBorderColor,
        cursorColor = accentGreen, focusedLabelColor = accentGreen, unfocusedLabelColor = textSecondary
    )
    val published = apps.filter { it.isApproved && !it.isSuspended }

    Dialog(onDismissRequest = { if (!uploading) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 700.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(if (initial.id.isBlank()) "New banner" else "Edit banner", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))

                // image
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 7f).clip(RoundedCornerShape(16.dp))
                        .background(accentGreen.copy(alpha = 0.08f))
                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .clickable(enabled = !uploading) { picker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = accentGreen, modifier = Modifier.size(32.dp))
                            Text("Tap to choose a banner image", color = textSecondary, fontSize = 12.sp)
                        }
                    }
                    if (uploading) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                Text("Best size: 1280 × 560 (wide). Landscape images look best.", color = textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                OutlinedTextField(imageUrl, { imageUrl = it.trim() }, label = { Text("…or paste an image URL") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), colors = fieldColors)

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtitle (optional)") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(buttonText, { buttonText = it }, label = { Text("Button text (e.g. Explore Now)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)

                // action
                Text("When tapped", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("none" to "Nothing", "app" to "Open an app", "collection" to "Open a collection", "link" to "Open a link").forEach { (key, label) ->
                        val on = targetType == key
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (on) accentGreen.copy(alpha = 0.2f) else textSecondary.copy(alpha = 0.08f))
                                .clickable { targetType = key; targetValue = "" }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text(label, color = if (on) accentGreen else textSecondary, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
                    }
                }
                when (targetType) {
                    "app" -> PickList(published.map { it.id to it.name }, targetValue, textPrimary, textSecondary, accentGreen) { targetValue = it }
                    "collection" -> PickList(collections.map { it.id to it.title }, targetValue, textPrimary, textSecondary, accentGreen) { targetValue = it }
                    "link" -> OutlinedTextField(targetValue, { targetValue = it.trim() }, label = { Text("https://…") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                }

                OutlinedTextField(order, { order = it.filter(Char::isDigit).take(3) }, label = { Text("Display order (0 = first)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), colors = fieldColors)

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, enabled = !uploading, modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, cardBorderColor)) { Text("Cancel", color = textSecondary) }
                    Button(
                        onClick = {
                            onSave(initial.copy(
                                imageUrl = imageUrl, title = title.trim(), subtitle = subtitle.trim(), buttonText = buttonText.trim(),
                                targetType = targetType, targetValue = targetValue, order = order.toIntOrNull() ?: 0
                            ))
                        },
                        enabled = imageUrl.isNotBlank() && !uploading && (targetType == "none" || targetValue.isNotBlank()),
                        modifier = Modifier.weight(1.3f).height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                    ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun PickList(
    items: List<Pair<String, String>>, selected: String,
    textPrimary: Color, textSecondary: Color, accent: Color, onPick: (String) -> Unit
) {
    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
        if (items.isEmpty()) Text("Nothing to choose from yet.", color = textSecondary, fontSize = 12.sp)
        items.forEach { (id, label) ->
            Row(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == id, onClick = { onPick(id) }, colors = RadioButtonDefaults.colors(selectedColor = accent))
                Text(label, color = textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
