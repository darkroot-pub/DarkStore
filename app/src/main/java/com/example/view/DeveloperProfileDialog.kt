package com.example.view

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.AppEntity
import com.example.data.UserEntity
import com.example.viewmodel.StoreViewModel

/**
 * "About developer" popup — same content as the one in App Details (photo → full screen,
 * name, followers + Follow, About, links, Published Apps), but usable from anywhere
 * (e.g. tapping the profile picture inside a chat).
 */
@Composable
fun DeveloperProfileDialog(
    viewModel: StoreViewModel,
    user: UserEntity,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onAppClick: ((AppEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allApps by viewModel.unfilteredApps.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val myUid by viewModel.userUid.collectAsStateWithLifecycle()

    val name = user.devName.ifBlank { user.displayName }.ifBlank { user.email.substringBefore("@") }
    val developerApps = remember(allApps, user.uid, user.email, name) {
        if (!user.isDeveloper) emptyList() else allApps.filter {
            it.isApproved && !it.isSuspended && !it.isUpcoming &&
                (it.developer.equals(name, ignoreCase = true) ||
                    (user.email.isNotBlank() && it.submittedBy.equals(user.email, ignoreCase = true)))
        }
    }

    var showPhoto by remember { mutableStateOf(false) }
    var followerCount by remember(user.uid) { mutableStateOf<Int?>(null) }
    LaunchedEffect(user.uid) { followerCount = viewModel.fetchFollowerCount(user.uid) }
    val isFollowing = followingIds.contains(user.uid)
    val isOwn = myUid == user.uid

    if (showPhoto && user.profilePhotoUrl.isNotBlank()) {
        FullScreenPhotoViewer(photoUrl = user.profilePhotoUrl, title = name, onDismiss = { showPhoto = false })
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 20.dp).heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, cardBorderColor.copy(alpha = 0.9f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().height(88.dp).background(
                        Brush.verticalGradient(listOf(accentGreen.copy(alpha = 0.22f), accentGreen.copy(alpha = 0.04f)))
                    )
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                    }
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).offset(y = (-36).dp)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(CircleShape)
                            .background(accentGreen.copy(alpha = 0.12f))
                            .clickable(enabled = user.profilePhotoUrl.isNotBlank()) { showPhoto = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (user.profilePhotoUrl.isNotBlank()) {
                            AsyncImage(
                                model = user.profilePhotoUrl,
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(name.take(1).uppercase(), color = accentGreen, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        name, color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        if (user.isDeveloper) "Verified Developer" else "Dark Store member",
                        color = accentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    if (user.devLocation.isNotBlank()) {
                        Text(
                            user.devLocation, color = textSecondary, fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            when (val c = followerCount) { null -> "···"; 1 -> "1 follower"; else -> "$c followers" },
                            color = textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                        if (!isOwn && user.isDeveloper) {
                            Spacer(Modifier.width(14.dp))
                            Button(
                                onClick = {
                                    viewModel.toggleFollowDeveloper(user.uid) { ok ->
                                        if (!ok) Toast.makeText(context, "Couldn't update follow", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) cardBorderColor else accentGreen,
                                    contentColor = if (isFollowing) textPrimary else Color.White
                                )
                            ) { Text(if (isFollowing) "Following" else "Follow", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("About", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    Text(
                        user.devBio.ifBlank { if (user.isDeveloper) "Developer on Dark Store" else "Member of the Dark Store community" },
                        color = textPrimary, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    if (user.devWebsite.isNotBlank() || user.devGithub.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (user.devWebsite.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val url = if (user.devWebsite.startsWith("http")) user.devWebsite else "https://${user.devWebsite}"
                                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        catch (e: Exception) { Toast.makeText(context, "Cannot open website", Toast.LENGTH_SHORT).show() }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, cardBorderColor),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Language, null, tint = accentGreen, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Website", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (user.devGithub.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val gh = if (user.devGithub.startsWith("http")) user.devGithub else "https://github.com/${user.devGithub}"
                                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gh))) }
                                        catch (e: Exception) { Toast.makeText(context, "Cannot open GitHub", Toast.LENGTH_SHORT).show() }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, cardBorderColor),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Code, null, tint = accentGreen, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("GitHub", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    if (user.isDeveloper) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = cardBorderColor.copy(alpha = 0.7f))
                        Spacer(Modifier.height(12.dp))
                        Text("Published Apps (${developerApps.size})", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        if (developerApps.isEmpty()) {
                            Text("No published apps yet.", color = textSecondary, fontSize = 12.sp)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                developerApps.forEach { devApp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(textSecondary.copy(alpha = 0.05f))
                                            .clickable(enabled = onAppClick != null) {
                                                onDismiss()
                                                onAppClick?.invoke(devApp)
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(accentGreen.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (devApp.logo.isNotBlank()) {
                                                AsyncImage(
                                                    model = devApp.logo, contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(devApp.name.take(1).uppercase(), color = accentGreen, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(devApp.name, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            Text(devApp.category, color = textSecondary, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(13.dp))
                                        Text(devApp.rating, color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Close", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
