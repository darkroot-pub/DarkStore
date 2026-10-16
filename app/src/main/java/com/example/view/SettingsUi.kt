package com.example.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared look for the redesigned Settings screen. */
class SettingsStyle(
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardBg: Color,
    val cardBorder: Color
)

/** A titled group: small caps label above one rounded card holding the rows. */
@Composable
fun SettingsGroup(style: SettingsStyle, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            color = style.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = style.cardBg),
            border = BorderStroke(1.dp, style.cardBorder)
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** Inset divider between rows (starts after the icon, like system settings). */
@Composable
fun SettingsDivider(style: SettingsStyle) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
        color = style.cardBorder.copy(alpha = 0.7f)
    )
}

/** One row: tinted icon tile · title/subtitle · trailing control (switch, chip, chevron…). */
@Composable
fun SettingsRow(
    style: SettingsStyle,
    icon: ImageVector,
    iconTint: Color = style.accent,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = style.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = style.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
fun SettingsSwitchRow(
    style: SettingsStyle,
    icon: ImageVector,
    iconTint: Color = style.accent,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsRow(
        style = style, icon = icon, iconTint = iconTint, title = title, subtitle = subtitle,
        onClick = { onChecked(!checked) }
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = style.accent,
                uncheckedThumbColor = style.textSecondary,
                uncheckedTrackColor = style.cardBorder.copy(alpha = 0.6f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/** Small pill used for read-only status values ("ACTIVE", "ENABLED"). */
@Composable
fun SettingsChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
    }
}

/** Compact pill button for row actions. */
@Composable
fun SettingsPillButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}
