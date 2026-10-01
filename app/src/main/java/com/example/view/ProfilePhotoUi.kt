package com.example.view

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.uploadImageToImgBB
import com.example.viewmodel.StoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Instagram/Facebook-style full-screen photo viewer: fades/scales in, pinch to
 * zoom, double-tap to toggle zoom, tap (when not zoomed) or ✕ to close.
 */
@Composable
fun FullScreenPhotoViewer(photoUrl: String, title: String = "", onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.01f) onDismiss() },
                        onDoubleTap = {
                            if (scale > 1.01f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2.5f
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1.01f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f; offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut()
            ) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = title.ifBlank { "Profile photo" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                if (title.isNotBlank()) {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Downscales (max 1024px), applies EXIF rotation and re-encodes as JPEG so uploads are fast on slow links. */
suspend fun prepareProfileImageBytes(context: android.content.Context, uri: Uri, maxSide: Int = 1024): ByteArray? =
    withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null

            val rotation = try {
                context.contentResolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) { 0f }

            val ratio = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                if (ratio < 1f) postScale(ratio, ratio)
                if (rotation != 0f) postRotate(rotation)
            }
            val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val stream = java.io.ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

/**
 * Avatar with a camera badge. Tapping it opens the gallery, uploads the chosen
 * image and saves it to the user's profile (users/{uid}/profilePhotoUrl).
 */
@Composable
fun ProfilePhotoEditor(
    viewModel: StoreViewModel,
    photoUrl: String,
    initial: String,
    accent: Color,
    textSecondary: Color,
    surfaceColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            val bytes = prepareProfileImageBytes(context, uri)
            if (bytes == null) {
                uploading = false
                Toast.makeText(context, "Could not read that image", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val url = withContext(Dispatchers.IO) {
                uploadImageToImgBB(context, bytes) { msg ->
                    scope.launch(Dispatchers.Main) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                }
            }
            uploading = false
            if (url != null) {
                viewModel.updateProfilePhoto(url) { ok ->
                    Toast.makeText(
                        context,
                        if (ok) "Profile photo updated" else "Saved on this device — will sync when online",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(96.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .clickable(enabled = !uploading) { picker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(initial.take(1).uppercase(), color = accent, fontWeight = FontWeight.Bold, fontSize = 36.sp)
                }
                if (uploading) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp)) }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .border(2.dp, surfaceColor, CircleShape)
                    .clickable(enabled = !uploading) { picker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (uploading) "Uploading…" else "Tap to change photo",
                color = textSecondary,
                fontSize = 12.sp
            )
            if (photoUrl.isNotBlank() && !uploading) {
                Text("  •  ", color = textSecondary, fontSize = 12.sp)
                Text(
                    "Remove",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        viewModel.updateProfilePhoto("") { ok ->
                            Toast.makeText(context, if (ok) "Photo removed" else "Removed on this device", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

/** Identity card for regular (non-developer) accounts: avatar (tap → full screen) + name + Edit. */
@Composable
fun UserIdentityCard(
    viewModel: StoreViewModel,
    userName: String,
    userEmail: String,
    photoUrl: String,
    surfaceCol: Color,
    borderCol: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color
) {
    var showViewer by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceCol),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .clickable(enabled = photoUrl.isNotBlank()) { showViewer = true },
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(userName.take(1).uppercase().ifBlank { "?" }, color = accent, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(userName.ifBlank { "Your account" }, color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(userEmail, color = textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { showEdit = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) { Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }

    if (showViewer && photoUrl.isNotBlank()) {
        FullScreenPhotoViewer(photoUrl = photoUrl, title = userName, onDismiss = { showViewer = false })
    }
    if (showEdit) {
        EditBasicProfileDialog(
            viewModel = viewModel,
            currentName = userName,
            photoUrl = photoUrl,
            surfaceCol = surfaceCol,
            borderCol = borderCol,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accent = accent,
            onDismiss = { showEdit = false }
        )
    }
}

@Composable
private fun EditBasicProfileDialog(
    viewModel: StoreViewModel,
    currentName: String,
    photoUrl: String,
    surfaceCol: Color,
    borderCol: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentName) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceCol),
            border = BorderStroke(1.dp, borderCol)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Edit profile", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = textSecondary) }
                }
                Spacer(Modifier.height(12.dp))
                ProfilePhotoEditor(
                    viewModel = viewModel,
                    photoUrl = photoUrl,
                    initial = name.ifBlank { currentName },
                    accent = accent,
                    textSecondary = textSecondary,
                    surfaceColor = surfaceCol
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (name.trim() != currentName) {
                            val (ok, msg) = viewModel.updateUserDisplayName(name)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (!ok) return@Button
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) { Text("Done", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
