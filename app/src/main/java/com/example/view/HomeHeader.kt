package com.example.view

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R

/**
 * Top of every screen: brand + notification / settings / profile buttons, and (on app-browsing
 * tabs) a larger glass search bar with voice input.
 */
@Composable
fun DarkStoreHeader(
    isDark: Boolean,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    userName: String,
    photoUrl: String,
    isPremium: Boolean,
    premiumGold: Color,
    unreadNotices: Int,
    showSearch: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBell: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val voiceLocale = when (lang) { "ne" -> "ne-NP"; "hi" -> "hi-IN"; else -> "en-US" }
    val prompt = tr("voice_prompt")
    val missing = tr("voice_missing")
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onSearchChange)
        }
    }

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.img_app_icon),
                contentDescription = "Dark Store",
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = textPrimary, fontWeight = FontWeight.ExtraBold)) { append("DARK ") }
                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.ExtraBold)) { append("STORE") }
                    },
                    fontSize = 18.sp, letterSpacing = 1.sp, maxLines = 1
                )
                Text(tr("tagline"), color = textSecondary, fontSize = 10.sp, maxLines = 1)
            }

            GlassCircle(isDark, onBell) {
                Box {
                    Icon(Icons.Default.Notifications, "Notifications", tint = textPrimary, modifier = Modifier.size(19.dp))
                    if (unreadNotices > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(9.dp)
                                .clip(CircleShape).background(Color(0xFFEF4444)).border(1.5.dp, Color.White, CircleShape)
                        )
                    }
                }
            }
            Spacer(Modifier.width(7.dp))
            GlassCircle(isDark, onSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = textPrimary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(8.dp))
            // Profile: the user's current photo when they have one, otherwise their initial
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(
                        if (isPremium) 2.dp else 1.dp,
                        if (isPremium) SolidColor(premiumGold) else glassEdge(isDark),
                        CircleShape
                    )
                    .background(Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF4285F4))))
                    .clickable(onClick = onProfile),
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = photoUrl, contentDescription = "Profile",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(userName.trim().take(1).uppercase().ifBlank { "?" }, color = Color.White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }

        if (showSearch) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .glass(isDark, RoundedCornerShape(26.dp), elevation = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, "Search", tint = textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.weight(1f).testTag("search_field"),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(accent),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) Text(tr("search_hint"), color = textSecondary, fontSize = 14.sp)
                                inner()
                            }
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear, "Clear", tint = textSecondary,
                            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onSearchChange("") }
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Icon(
                        Icons.Default.Mic, "Voice search", tint = accent,
                        modifier = Modifier.size(22.dp).clip(CircleShape).clickable {
                            try {
                                voiceLauncher.launch(
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLocale)
                                        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                                )
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, missing, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCircle(isDark: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape: Shape = CircleShape
    Box(
        modifier = Modifier.size(38.dp).glass(isDark, shape, elevation = 3.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
