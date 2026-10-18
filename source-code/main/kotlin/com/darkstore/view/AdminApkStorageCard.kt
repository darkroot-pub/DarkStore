package com.darkstore.view

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkstore.viewmodel.StoreViewModel

/** Admin: control the APK storage API, limits, and see/delete every uploaded file. */
@Composable
fun AdminApkStorageCard(
    viewModel: StoreViewModel,
    accent: Color,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val context = LocalContext.current
    val st by viewModel.apkAdminStatus.collectAsStateWithLifecycle()
    val loadError by viewModel.apkAdminError.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshApkAdmin() }

    val s = st
    var enabled by remember(s) { mutableStateOf(s?.enabled ?: false) }
    var maxMB by remember(s) { mutableStateOf(s?.maxFileMB ?: 100) }
    var perFiles by remember(s) { mutableStateOf(s?.perUserFiles ?: 5) }
    var perMB by remember(s) { mutableStateOf(s?.perUserMB ?: 300) }
    var baseUrl by remember(s) { mutableStateOf(s?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("APK STORAGE (PREMIUM UPLOADS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)

            if (s == null) {
                if (loadError != null) {
                    Text(loadError.orEmpty(), color = Color(0xFFEF4444), fontSize = 12.sp, lineHeight = 16.sp)
                    OutlinedButton(onClick = { viewModel.refreshApkAdmin() }) { Text("Retry") }
                } else {
                    Text("Loading storage status...", color = textSecondary, fontSize = 12.sp)
                }
            } else {
                Text(
                    "${s.totalFiles} files - ${fmtSize(s.totalBytes)} - ${s.users} uploader(s)",
                    color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black
                )
                Text(
                    if (s.configured) "API key set (${s.keyHint}) - ${s.baseUrl}" else "No API key set yet - uploads can't work until you add one below.",
                    color = if (s.configured) textSecondary else Color(0xFFEF4444), fontSize = 11.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (enabled) "Premium users can upload APKs" else "Direct upload is OFF", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Only Premium members (referral reward, admin grant, admins).", color = textSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                StepRow("Max size per file (MB)", maxMB, 10, 1..500, textPrimary, textSecondary) { maxMB = it }
                StepRow("Files per user", perFiles, 1, 1..50, textPrimary, textSecondary) { perFiles = it }
                StepRow("Storage per user (MB)", perMB, 50, 10..5000, textPrimary, textSecondary) { perMB = it }

                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it }, singleLine = true,
                    label = { Text("Storage API address") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, cursorColor = accent, focusedLabelColor = accent)
                )
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it }, singleLine = true,
                    label = { Text("API key (leave empty to keep the current key)") },
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text("Write-only: the key is stored on the server and never comes back to any phone.", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, cursorColor = accent, focusedLabelColor = accent)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f), enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        onClick = {
                            busy = true
                            viewModel.saveApkConfigAsAdmin(enabled, maxMB, perFiles, perMB, baseUrl, apiKey) { ok, msg ->
                                busy = false
                                if (ok) apiKey = ""
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f), enabled = !busy,
                        onClick = {
                            busy = true; testResult = "Testing..."
                            viewModel.testApkApi { text -> busy = false; testResult = text }
                        }
                    ) { Text("Test connection") }
                }
                if (testResult.isNotBlank()) Text(testResult, color = textPrimary, fontSize = 12.sp)

                if (s.files.isNotEmpty()) {
                    Divider(color = cardBorder.copy(alpha = 0.5f))
                    Text("Uploaded files", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    s.files.take(25).forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    "${f.email} - ${fmtSize(f.size)} - ${java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(f.createdAt))} - ${f.downloads} downloads" +
                                        if (f.inUse) " - IN USE" else " - unused",
                                    color = textSecondary, fontSize = 10.sp
                                )
                            }
                            IconButton(onClick = {
                                viewModel.deleteApkAsAdmin(f.id) { _, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                            }) { Icon(Icons.Default.Delete, contentDescription = "Delete file", tint = Color(0xFFEF4444)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, value: Int, step: Int, range: IntRange, textPrimary: Color, textSecondary: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = textSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(range.first)) }) { Text("-") }
        Text("$value", color = textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 10.dp))
        OutlinedButton(onClick = { onChange((value + step).coerceAtMost(range.last)) }) { Text("+") }
    }
}
