package com.darkstore.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** One stat pill in the profile hero ("Following", "Followers", "Apps"). */
data class ProfileStat(val value: String, val label: String, val onClick: (() -> Unit)? = null)

/**
 * Liquid-glass profile header, shared by every account type:
 *  - an aurora banner built from the same three colours as the app's ambient glow (accent · indigo · cyan),
 *  - a large avatar with a gradient ring and soft glow (tap → full screen when there is a photo),
 *  - name, handle and role chips,
 *  - frosted stat pills.
 */
@Composable
fun ProfileHeroCard(
    name: String,
    handle: String,
    photoUrl: String,
    isDark: Boolean,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    roleLabel: String? = null,
    verified: Boolean = false,
    location: String = "",
    stats: List<ProfileStat> = emptyList(),
    onEdit: (() -> Unit)? = null,
    onPhotoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val indigo = Color(0xFF6366F1)
    val cyan = Color(0xFF06B6D4)
    val shape = RoundedCornerShape(28.dp)
    val edge = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.85f)

    Column(modifier.fillMaxWidth().glass(isDark, shape, 4.dp)) {
        // ── aurora banner ──
        Box(Modifier.fillMaxWidth().height(112.dp)) {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), indigo.copy(alpha = 0.85f), cyan.copy(alpha = 0.75f))))
            )
            // soft light blobs → the "liquid" look
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.38f), Color.Transparent), center = Offset(120f, 40f), radius = 420f)
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(cyan.copy(alpha = 0.55f), Color.Transparent), center = Offset(900f, 260f), radius = 520f)
                )
            )
            // fade into the card
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp).background(
                    Brush.verticalGradient(listOf(Color.Transparent, (if (isDark) Color.Black else Color.White).copy(alpha = if (isDark) 0.18f else 0.30f)))
                )
            )
            if (onEdit != null) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── avatar + identity ──
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.pullUp(46.dp), contentAlignment = Alignment.BottomEnd) {
                // gradient ring
                Box(
                    Modifier.size(96.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(accent, indigo, cyan, accent)))
                        .padding(3.5.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1B1F27) else Color.White)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f))
                        .clickable(enabled = onPhotoClick != null && photoUrl.isNotBlank()) { onPhotoClick?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(model = photoUrl, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(name.take(1).uppercase().ifBlank { "?" }, color = accent, fontWeight = FontWeight.Black, fontSize = 34.sp)
                    }
                }
                if (verified) {
                    Box(
                        Modifier.offset(x = (-2).dp, y = (-2).dp).size(26.dp).clip(CircleShape)
                            .background(Color(0xFF3B82F6)).border(2.dp, if (isDark) Color(0xFF1B1F27) else Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    name.ifBlank { "Your account" }, color = textPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, letterSpacing = (-0.3).sp
                )
                if (handle.isNotBlank()) {
                    Text(handle, color = textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (roleLabel != null || location.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (roleLabel != null) {
                            Text(
                                roleLabel.uppercase(), color = accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f))
                                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (location.isNotBlank()) {
                            Row(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(textSecondary.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = textSecondary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(location, color = textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // ── frosted stat pills ──
            if (stats.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 16.dp, bottom = 18.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    stats.forEach { s ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.55f))
                                .border(1.dp, edge, RoundedCornerShape(18.dp))
                                .then(if (s.onClick != null) Modifier.clickable { s.onClick.invoke() } else Modifier)
                                .padding(vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(s.value, color = textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text(s.label, color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** Pulls a child up over the element above it AND gives back the space (unlike offset, which leaves a gap). */
private fun Modifier.pullUp(amount: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val up = amount.roundToPx()
    layout(p.width, (p.height - up).coerceAtLeast(0)) { p.place(0, -up) }
}
