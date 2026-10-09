package com.example.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AppEntity

/** "Recommended For You            More →" */
@Composable
fun SectionTitleRow(title: String, accent: Color, textPrimary: Color, onMore: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
        if (onMore != null) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onMore).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr("more"), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, tint = accent, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppEntity, size: androidx.compose.ui.unit.Dp, accent: Color) {
    AsyncImage(
        model = app.logo, contentDescription = app.name, contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(accent.copy(alpha = 0.08f))
    )
}

@Composable
private fun StarRating(rating: String, textPrimary: Color) {
    val r = rating.toFloatOrNull()
    if (r != null && r > 0f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(String.format("%.1f", r), color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
        }
    }
}

/** Square glass tile: big icon on top, name and size under it ("Recommended For You"). */
@Composable
fun SquareAppTile(app: AppEntity, isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(116.dp).height(136.dp)
            .glass(isDark, RoundedCornerShape(24.dp), elevation = 3.dp)
            .clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AppIcon(app, 62.dp, accent)
        Column {
            Text(app.name, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.size, color = textSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** Wider glass card with name, size and rating ("Most Popular"). */
@Composable
fun PopularAppCard(app: AppEntity, rating: String, isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(158.dp).height(136.dp)
            .glass(isDark, RoundedCornerShape(24.dp), elevation = 3.dp)
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AppIcon(app, 54.dp, accent)
        Column {
            Text(app.name, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.size, color = textSecondary, fontSize = 11.sp, maxLines = 1)
                StarRating(rating, textPrimary)
            }
        }
    }
}

/** Compact glass card for dense rows ("Top Free"). */
@Composable
fun CompactAppCard(app: AppEntity, rating: String, isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(104.dp).height(124.dp)
            .glass(isDark, RoundedCornerShape(22.dp), elevation = 3.dp)
            .clickable(onClick = onClick).padding(11.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AppIcon(app, 50.dp, accent)
        Column {
            Text(app.name, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.size, color = textSecondary, fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                StarRating(rating, textPrimary)
            }
        }
    }
}
