package com.darkstore.view

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkstore.data.ApkUploadResult
import com.darkstore.viewmodel.StoreViewModel

internal fun fmtSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * Submit form: "Upload APK file" for Premium users. The file goes to the DarkStore server (never straight to the
 * storage service), which returns the download link that fills the APK link field.
 */
@Composable
fun ApkUploadSection(
    viewModel: StoreViewModel,
    accent: Color,
    isDark: Boolean,
    expectedPackage: String,
    onUploaded: (ApkUploadResult) -> Unit
) {
    val context = LocalContext.current
    val info by viewModel.apkUploadInfo.collectAsStateWithLifecycle()
    val progress by viewModel.apkUploadProgress.collectAsStateWithLifecycle()
    var done by remember { mutableStateOf<ApkUploadResult?>(null) }
    var showFiles by remember { mutableStateOf(false) }
    var gaveUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.loadApkUploadInfo()
        kotlinx.coroutines.delay(10_000)
        gaveUp = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.uploadApk(uri, expectedPackage) { res, msg ->
                if (res != null) {
                    done = res
                    onUploaded(res)
                    Toast.makeText(context, "APK uploaded. The download link was added to the form.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val gold = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val i = info
    Card(
        modifier = Modifier.fillMaxWidth().testTag("apk_upload_section"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, (if (i?.premium == true) accent else gold).copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudUpload, null, tint = if (i?.premium == true) accent else gold)
                Text("Upload APK from your phone", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (i?.premium != true) {
                    Text("PREMIUM", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.background(gold, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            when {
                i == null && gaveUp -> {
                    Text("Direct upload isn't available right now (the DarkStore server didn't answer). Paste a download link below instead.", fontSize = 12.sp)
                    TextButton(onClick = { gaveUp = false; viewModel.loadApkUploadInfo() }) { Text("Try again", fontSize = 12.sp) }
                }
                i == null -> Text("Checking upload access...", fontSize = 12.sp)
                !i.premium -> {
                    Text("Premium members can upload the APK file directly, no download link needed.", fontSize = 12.sp)
                    Text("Invite friends in Settings to unlock Premium, or paste a link below.", fontSize = 11.sp)
                }
                !i.enabled -> Text("Direct upload is switched off by the admin right now. Paste a download link below instead.", fontSize = 12.sp)
                else -> {
                    val p = progress
                    if (p != null) {
                        Text(if (p < 0f) "Preparing the file..." else "Uploading ${(p * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = if (p < 0f) 0f else p,
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = accent
                        )
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth().testTag("apk_upload_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) }
                        ) { Text("Choose APK file", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    done?.let {
                        Text("Uploaded: ${it.packageName} v${it.versionName} (${fmtSize(it.size)})", fontSize = 12.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Your storage: ${i.usedFiles}/${i.perUserFiles} files, ${fmtSize(i.usedBytes)} of ${i.perUserMB} MB. Max ${i.maxFileMB} MB per file. " +
                            "Unused uploads are removed after 24 hours.",
                        fontSize = 11.sp
                    )
                    if (i.files.isNotEmpty()) {
                        TextButton(onClick = { showFiles = !showFiles }) { Text(if (showFiles) "Hide my uploads" else "My uploads (${i.files.size})", fontSize = 12.sp) }
                        if (showFiles) {
                            i.files.forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(f.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(fmtSize(f.size) + if (f.inUse) " - used by a submission" else "", fontSize = 10.sp)
                                    }
                                    if (!f.inUse) {
                                        IconButton(onClick = {
                                            viewModel.deleteMyApk(f.id) { _, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete upload", tint = Color(0xFFEF4444)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
