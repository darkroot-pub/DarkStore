package com.example.view

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AppEntity
import com.example.data.CollectionEntity
import com.example.viewmodel.StoreViewModel

private fun hex(c: String): Color = try { Color(android.graphics.Color.parseColor(c)) } catch (e: Exception) { Color(0xFF10B981) }

/** Apps of a collection, in the admin's order, skipping anything unpublished or suspended. */
fun collectionApps(c: CollectionEntity, apps: List<AppEntity>): List<AppEntity> {
    val byId = apps.associateBy { it.id }
    return c.appIds.mapNotNull { byId[it] }.filter { it.isApproved && !it.isSuspended }
}

/** Horizontal strip of collection cards for the top of the Apps tab. */
@Composable
fun CollectionsRow(
    collections: List<CollectionEntity>,
    apps: List<AppEntity>,
    textPrimary: Color,
    onClick: (CollectionEntity) -> Unit
) {
    val lang = LocalLang.current
    Column(Modifier.fillMaxWidth()) {
        Text(tr("col_title"), color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(collections, key = { it.id }) { c ->
                val base = hex(c.color)
                val list = collectionApps(c, apps)
                Box(
                    modifier = Modifier
                        .width(260.dp).height(132.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f).compositeOver(Color.Black))))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = 160f))
                        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(22.dp))
                        .clickable { onClick(c) }
                        .padding(16.dp)
                ) {
                    Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.9f)) {
                        Text(c.titleFor(lang), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                        if (c.subtitleFor(lang).isNotBlank()) {
                            Text(c.subtitleFor(lang), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
                        Row {
                            list.take(3).forEachIndexed { i, a ->
                                AsyncImage(
                                    model = a.logo, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .offset(x = (-8 * i).dp).size(30.dp).clip(CircleShape)
                                        .border(2.dp, base, CircleShape).background(Color.White.copy(alpha = 0.2f))
                                )
                            }
                        }
                        Spacer(Modifier.width(if (list.size > 1) 4.dp else 0.dp))
                        Text(tr("col_apps", list.size), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(red * a + background.red * (1 - a), green * a + background.green * (1 - a), blue * a + background.blue * (1 - a), 1f)
}

/** Full-screen page for one collection. */
@Composable
fun CollectionDetailDialog(
    collection: CollectionEntity,
    apps: List<AppEntity>,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    pageBg: Color,
    onAppClick: (AppEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalLang.current
    val base = hex(collection.color)
    val list = collectionApps(collection, apps)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(pageBg)) {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(base, base.copy(alpha = 0.6f).compositeOver(Color.Black))))
                    .statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 22.dp)
            ) {
                Column {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    Text(collection.titleFor(lang), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                        lineHeight = 31.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    if (collection.subtitleFor(lang).isNotBlank()) {
                        Text(collection.subtitleFor(lang), color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    }
                    Text(tr("col_apps", list.size), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 10.dp))
                }
            }
            if (list.isEmpty()) {
                Text(tr("col_empty"), color = textSecondary, modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(list, key = { it.id }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardBgColor)
                                .border(1.dp, cardBorderColor, RoundedCornerShape(18.dp))
                                .clickable { onDismiss(); onAppClick(app) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(app.logo, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(accentGreen.copy(alpha = 0.1f)))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.name, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${app.category} · ${app.developer}", color = textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                            Text(app.rating, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── admin ─────────────────────────

/** Admin ▸ Collections: create, edit, hide and delete curated lists. */
@Composable
fun CollectionsAdminPanel(
    viewModel: StoreViewModel,
    collections: List<CollectionEntity>,
    apps: List<AppEntity>,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<CollectionEntity?>(null) }
    var deleting by remember { mutableStateOf<CollectionEntity?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Collections", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("Curated lists shown on the Apps tab", color = textSecondary, fontSize = 12.sp)
            }
            Button(onClick = { editing = CollectionEntity(order = collections.size) }, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("New", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (collections.isEmpty()) {
            Text("No collections yet. Create one — e.g. “Best Nepali apps” — and it appears as a card on the Apps tab.",
                color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        collections.forEach { c ->
            val n = collectionApps(c, apps).size
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(hex(c.color)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$n apps · ${if (c.isActive) "Visible" else "Hidden"} · order ${c.order}", color = textSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = c.isActive, onCheckedChange = { on ->
                        viewModel.saveCollection(c.copy(isActive = on)) { ok ->
                            if (!ok) Toast.makeText(context, "Couldn't update", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = SwitchDefaults.colors(checkedTrackColor = accentGreen, checkedThumbColor = Color.White))
                    IconButton(onClick = { editing = c }) { Icon(Icons.Default.Edit, "Edit", tint = textSecondary) }
                    IconButton(onClick = { deleting = c }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444)) }
                }
            }
        }
    }

    editing?.let { c ->
        CollectionEditorDialog(c, apps, accentGreen, textPrimary, textSecondary, cardBgColor, cardBorderColor,
            onDismiss = { editing = null },
            onSave = { saved ->
                viewModel.saveCollection(saved) { ok ->
                    Toast.makeText(context, if (ok) "Collection saved" else "Save failed — check admin permission", Toast.LENGTH_SHORT).show()
                    if (ok) editing = null
                }
            })
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${c.title}”?") },
            text = { Text("The collection disappears for everyone. The apps themselves are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.deleteCollection(c.id, c.title) { ok ->
                        Toast.makeText(context, if (ok) "Deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CollectionEditorDialog(
    initial: CollectionEntity,
    apps: List<AppEntity>,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onDismiss: () -> Unit,
    onSave: (CollectionEntity) -> Unit
) {
    var title by remember { mutableStateOf(initial.title) }
    var subtitle by remember { mutableStateOf(initial.subtitle) }
    var titleNe by remember { mutableStateOf(initial.titleNe) }
    var subtitleNe by remember { mutableStateOf(initial.subtitleNe) }
    var titleHi by remember { mutableStateOf(initial.titleHi) }
    var subtitleHi by remember { mutableStateOf(initial.subtitleHi) }
    var color by remember { mutableStateOf(initial.color) }
    var order by remember { mutableStateOf(initial.order.toString()) }
    var selected by remember { mutableStateOf(initial.appIds) }
    var search by remember { mutableStateOf("") }
    var showTranslations by remember { mutableStateOf(titleNe.isNotBlank() || titleHi.isNotBlank()) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
        focusedBorderColor = accentGreen, unfocusedBorderColor = cardBorderColor,
        cursorColor = accentGreen, focusedLabelColor = accentGreen, unfocusedLabelColor = textSecondary
    )
    val swatches = listOf("#10B981", "#3B82F6", "#8B5CF6", "#EC4899", "#F59E0B", "#EF4444", "#14B8A6", "#64748B")
    val published = apps.filter { it.isApproved && !it.isSuspended }
    val shown = published.filter { search.isBlank() || it.name.contains(search, true) || it.developer.contains(search, true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(if (initial.id.isBlank()) "New collection" else "Edit collection", color = textPrimary,
                    fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtitle (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)

                TextButton(onClick = { showTranslations = !showTranslations }) {
                    Text(if (showTranslations) "Hide translations" else "Add Nepali / Hindi text (optional)", color = accentGreen, fontSize = 12.sp)
                }
                if (showTranslations) {
                    listOf(
                        Triple("Title — नेपाली", titleNe, { v: String -> titleNe = v }),
                        Triple("Subtitle — नेपाली", subtitleNe, { v: String -> subtitleNe = v }),
                        Triple("Title — हिन्दी", titleHi, { v: String -> titleHi = v }),
                        Triple("Subtitle — हिन्दी", subtitleHi, { v: String -> subtitleHi = v })
                    ).forEach { (label, value, set) ->
                        OutlinedTextField(value, set, label = { Text(label) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                    }
                }

                Text("Card color", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    swatches.forEach { sw ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(hex(sw))
                                .border(if (color.equals(sw, true)) 3.dp else 0.dp, textPrimary, CircleShape)
                                .clickable { color = sw }
                        )
                    }
                }
                OutlinedTextField(order, { order = it.filter(Char::isDigit).take(3) }, label = { Text("Display order (0 = first)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors)

                Spacer(Modifier.height(12.dp))
                Text("Apps in this collection (${selected.size})", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Tap to add or remove — they appear in the order you add them.", color = textSecondary, fontSize = 11.sp)
                OutlinedTextField(search, { search = it }, placeholder = { Text("Search apps…", fontSize = 12.sp) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    shown.forEach { a ->
                        val pos = selected.indexOf(a.id)
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (pos >= 0) selected - a.id else selected + a.id }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(a.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(a.category, color = textSecondary, fontSize = 11.sp)
                            }
                            if (pos >= 0) {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(accentGreen), contentAlignment = Alignment.Center) {
                                    Text("${pos + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, cardBorderColor)) { Text("Cancel", color = textSecondary) }
                    Button(
                        onClick = {
                            onSave(initial.copy(
                                title = title.trim(), subtitle = subtitle.trim(),
                                titleNe = titleNe.trim(), subtitleNe = subtitleNe.trim(),
                                titleHi = titleHi.trim(), subtitleHi = subtitleHi.trim(),
                                color = color, appIds = selected, order = order.toIntOrNull() ?: 0
                            ))
                        },
                        enabled = title.isNotBlank() && selected.isNotEmpty(),
                        modifier = Modifier.weight(1.3f).height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                    ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
