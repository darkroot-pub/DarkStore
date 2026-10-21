@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.statusBarsPadding
import android.app.Application
import android.os.Bundle
import com.example.view.FullScreenPhotoViewer
import com.example.view.ambientGlow
import com.example.view.glass
import com.example.view.predictiveBackTransform
import com.example.view.glassEdge
import com.example.view.glassSheen
import com.example.view.ProfilePhotoEditor
import com.example.view.UserIdentityCard
import android.widget.Toast
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitFirstDown
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.R
import com.example.data.AppEntity
import com.example.data.DownloadEntity
import com.example.data.SubmissionEntity
import com.example.data.UserEntity
import com.example.utils.ApkInstaller
import com.example.utils.StorageManager
import com.example.utils.NotificationHelper
import com.example.viewmodel.StoreViewModel
import java.io.File
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

// ============================================================================
// ADMIN-ONLY CODE (compiled into the DarkStore Admin app, NOT into the user app).
// Moved out of MainActivity.kt when the app was split into two apps.
// ============================================================================

// ========================================================
// 8. DEVELOPER PORTAL (Pin protected sandbox)
// ========================================================
@Composable
fun ConsoleTabContent(
    viewModel: StoreViewModel,
    apps: List<AppEntity>,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    userEmail: String,
    onShowAppDetails: (com.example.data.AppEntity) -> Unit = {}
) {
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val userUid by viewModel.userUid.collectAsStateWithLifecycle()
    val submissions by viewModel.submissions.collectAsStateWithLifecycle()
    val developers by viewModel.developers.collectAsStateWithLifecycle()
    val termsAgreements by viewModel.termsAgreements.collectAsStateWithLifecycle()
    val devName by viewModel.devName.collectAsStateWithLifecycle()
    val auditEntries by viewModel.auditLog.collectAsStateWithLifecycle()
    val maintenanceCfg by viewModel.maintenanceConfig.collectAsStateWithLifecycle()
    val unityAdsAdminCfg by viewModel.unityAdsConfig.collectAsStateWithLifecycle()
    
    val isAdmin = userRole.equals("admin", ignoreCase = true)
    
    if (!isAdmin) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text("Access Denied", fontWeight = FontWeight.ExtraBold, color = textPrimary, fontSize = 22.sp)
                    Text(
                        "The Admin Console is for administrators only. Register as a developer in Profile to submit your own apps for review.",
                        color = textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        return
    }

    val displayedApps = remember(isAdmin, apps, userEmail) {
        if (isAdmin) apps else apps.filter { it.submittedBy.equals(userEmail, ignoreCase = true) }
    }

    var pinValue by remember { mutableStateOf("") }
    // Secure default access authorization: normal developers bypass the PIN; admins verify the Firebase-stored PIN once
    var isAuthorized by remember(userEmail, userRole) { mutableStateOf(!isAdmin) }
    // PIN now lives in Firebase (adminSecurity, hashed, admin-only). States: loading | missing | ready | error
    var pinConfirm by remember { mutableStateOf("") }
    var pinState by remember { mutableStateOf("loading") }
    var pinRecord by remember { mutableStateOf<com.example.utils.AdminPin.Record?>(null) }
    var pinBusy by remember { mutableStateOf(false) }
    var pinFails by remember { mutableStateOf(0) }
    var pinLockUntil by remember { mutableStateOf(0L) }
    var pinReload by remember { mutableStateOf(0) }
    val gateScope = rememberCoroutineScope()
    LaunchedEffect(isAdmin, isAuthorized, pinReload) {
        if (isAdmin && !isAuthorized) {
            pinState = "loading"
            val (status, rec) = viewModel.loadAdminPin()
            pinRecord = rec
            pinState = when (status) { "ok" -> "ready"; "missing" -> "missing"; else -> "error" }
        }
    }
    
    var userToEdit by remember { mutableStateOf<com.example.data.UserEntity?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var showSendNoticeForm by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<AppEntity?>(null) }
    var editingSubmission by remember { mutableStateOf<SubmissionEntity?>(null) }
    var appSubmittingUpdateFor by remember { mutableStateOf<SubmissionEntity?>(null) }
    var showPushUpdateFormFor by remember { mutableStateOf<SubmissionEntity?>(null) }
    var showUpdateScreenshotsFormFor by remember { mutableStateOf<SubmissionEntity?>(null) }
    val isDarkMode = androidx.compose.foundation.isSystemInDarkTheme()
    var submissionToReject by remember { mutableStateOf<SubmissionEntity?>(null) }
    var rejectionReason by remember { mutableStateOf("") }
    var submissionToApprove by remember { mutableStateOf<SubmissionEntity?>(null) }
    var approvalFeedback by remember { mutableStateOf("") }
    
    // Advanced powerful audit states
    val expandedSubmissions = remember { mutableStateMapOf<String, Boolean>() }
    val submissionChecks = remember { mutableStateMapOf<String, Set<String>>() }
    var screenshotDialogUrl by remember { mutableStateOf<String?>(null) }
    
    // Admin Subviews Segment selection: "SUBMISSIONS" vs "CATALOG"
    var adminSegmentIndex by remember { mutableStateOf(0) }
    var notifyPresetUid by remember { mutableStateOf("") }
    var adminSearchQuery by remember { mutableStateOf("") }
    var adminUserFilter by remember { mutableStateOf("All") }
    val selectedSubmissionIds = remember { mutableStateListOf<String>() }

    val filteredSubmissions = remember(submissions, adminSearchQuery) {
        if (adminSearchQuery.isBlank()) {
            submissions
        } else {
            submissions.filter {
                it.name.contains(adminSearchQuery, ignoreCase = true) ||
                        it.packageName.contains(adminSearchQuery, ignoreCase = true) ||
                        it.developer.contains(adminSearchQuery, ignoreCase = true) ||
                        it.submittedBy.contains(adminSearchQuery, ignoreCase = true)
            }
        }
    }

    val filteredApps = remember(displayedApps, adminSearchQuery) {
        if (adminSearchQuery.isBlank()) {
            displayedApps
        } else {
            displayedApps.filter {
                it.name.contains(adminSearchQuery, ignoreCase = true) ||
                        it.packageName.contains(adminSearchQuery, ignoreCase = true) ||
                        it.developer.contains(adminSearchQuery, ignoreCase = true) ||
                        it.submittedBy.contains(adminSearchQuery, ignoreCase = true)
            }
        }
    }

    var appToManageSuspension by remember { mutableStateOf<AppEntity?>(null) }
    var suspensionDialogReason by remember { mutableStateOf("") }
    var isSuspensionDialogActive by remember { mutableStateOf(false) }

    // Update config admin fields
    val currentUpdateConfig by viewModel.updateConfig.collectAsStateWithLifecycle()
    var uVersionCode by remember { mutableStateOf("") }
    var uVersionName by remember { mutableStateOf("") }
    var uApkUrl by remember { mutableStateOf("") }
    var uTitle by remember { mutableStateOf("") }
    var uMessage by remember { mutableStateOf("") }
    var uForceUpdate by remember { mutableStateOf(false) }
    var uHasInitialized by remember { mutableStateOf(false) }
    var uPushStatusMessage by remember { mutableStateOf("") }
    var uPushIsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentUpdateConfig) {
        currentUpdateConfig?.let { cfg ->
            if (!uHasInitialized) {
                uVersionCode = cfg.latestVersionCode.toString()
                uVersionName = cfg.latestVersionName
                uApkUrl = cfg.apkDownloadUrl
                uTitle = cfg.updateTitle
                uMessage = cfg.updateMessage
                uForceUpdate = cfg.forceUpdate
                uHasInitialized = true
            }
        }
    }
    
    val context = LocalContext.current

    // Trigger submissions refresh whenever the screen becomes visible or authorization changes
    LaunchedEffect(userRole, userEmail) {
        viewModel.refreshSubmissions()
        viewModel.refreshMarketplace()
        viewModel.refreshTermsAgreements()
    }

    if (!isAuthorized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(accentGreen.copy(alpha = 0.12f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        "Admin Console",
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary,
                        fontSize = 22.sp
                    )

                    val pinFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = accentGreen,
                        unfocusedBorderColor = cardBorderColor,
                        focusedContainerColor = textSecondary.copy(alpha = 0.04f),
                        unfocusedContainerColor = textSecondary.copy(alpha = 0.04f),
                        cursorColor = accentGreen
                    )
                    when (pinState) {
                        "loading" -> {
                            CircularProgressIndicator(color = accentGreen, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text("Checking security…", color = textSecondary, fontSize = 13.sp)
                        }
                        "error" -> {
                            Text(
                                "Couldn't load the admin PIN. Check your connection and that you're signed in as an admin.",
                                color = textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp
                            )
                            Button(
                                onClick = { pinReload++ },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Retry", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        else -> {
                            val setup = pinState == "missing"
                            Text(
                                if (setup) "Create a PIN (4–12 digits) to protect the Admin Console. It is stored in Firebase as a secure hash that only admins can access."
                                else "Enter your security PIN to manage submissions, catalog, and store settings.",
                                color = textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            OutlinedTextField(
                                value = pinValue,
                                onValueChange = { pinValue = it.filter(Char::isDigit).take(12) },
                                modifier = Modifier.fillMaxWidth().testTag("admin_password_field"),
                                placeholder = { Text(if (setup) "New PIN" else "Security PIN", color = textSecondary.copy(alpha = 0.5f)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                leadingIcon = {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = textSecondary, modifier = Modifier.size(20.dp))
                                },
                                colors = pinFieldColors
                            )
                            if (setup) {
                                OutlinedTextField(
                                    value = pinConfirm,
                                    onValueChange = { pinConfirm = it.filter(Char::isDigit).take(12) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Confirm PIN", color = textSecondary.copy(alpha = 0.5f)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    colors = pinFieldColors
                                )
                            }
                            Button(
                                onClick = {
                                    if (pinBusy) return@Button
                                    val now = System.currentTimeMillis()
                                    if (setup) {
                                        if (!com.example.utils.AdminPin.isValidFormat(pinValue)) {
                                            Toast.makeText(context, "PIN must be 4–12 digits", Toast.LENGTH_SHORT).show()
                                        } else if (pinValue != pinConfirm) {
                                            Toast.makeText(context, "PINs don't match", Toast.LENGTH_SHORT).show()
                                        } else {
                                            pinBusy = true
                                            gateScope.launch {
                                                val ok = viewModel.saveAdminPin(pinValue)
                                                pinBusy = false
                                                if (ok) {
                                                    pinValue = ""; pinConfirm = ""
                                                    isAuthorized = true
                                                } else {
                                                    Toast.makeText(context, "Couldn't save the PIN. Are you signed in as admin?", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    } else if (now < pinLockUntil) {
                                        Toast.makeText(context, "Too many attempts — wait ${(pinLockUntil - now) / 1000 + 1}s", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val rec = pinRecord ?: return@Button
                                        pinBusy = true
                                        gateScope.launch {
                                            val ok = viewModel.verifyAdminPin(pinValue, rec)
                                            pinBusy = false
                                            if (ok) {
                                                pinValue = ""; pinFails = 0
                                                isAuthorized = true
                                            } else {
                                                pinFails++
                                                if (pinFails >= 5) { pinLockUntil = System.currentTimeMillis() + 60_000L; pinFails = 0 }
                                                Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !pinBusy && pinValue.isNotEmpty(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("admin_auth_submit_button")
                            ) {
                                Text(
                                    if (pinBusy) "Please wait…" else if (setup) "Create PIN" else "Unlock",
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                                )
                            }
                            if (!setup) {
                                Text(
                                    "Forgot your PIN? Delete the “adminSecurity” node in Firebase Console to set a new one.",
                                    color = textSecondary.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

    } else {
        // ─── NEW REDESIGNED ADMIN PANEL ───────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp + com.example.view.LocalNavBarInset.current)
        ) {
            // ── HEADER ─────────────────────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isAdmin) Icons.Default.Shield else Icons.Default.Build,
                                contentDescription = null,
                                tint = accentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isAdmin) "ADMIN CONTROL CENTER" else "DEVELOPER PORTAL",
                                color = accentGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = if (isAdmin) "Manage catalog, submissions & deployments" else "Submit and track your app reviews",
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (isAdmin) {
                        OutlinedButton(
                            onClick = { isAuthorized = false; pinValue = "" },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, cardBorderColor),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(13.dp), tint = textSecondary)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Lock", fontSize = 11.sp, color = textSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── ADMIN IDENTITY CARD ────────────────────────────────────────────────
            if (isAdmin) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = accentGreen.copy(alpha = 0.07f)),
                        border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(accentGreen.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userEmail.ifBlank { "A" }.take(1).uppercase(),
                                    color = accentGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(userEmail.ifBlank { "davidstha900@gmail.com" }, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .background(accentGreen.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text("ADMINISTRATOR", color = accentGreen, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), RoundedCornerShape(50)))
                                        Text("Active", color = Color(0xFF10B981), fontSize = 10.sp)
                                    }
                                }
                            }
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = accentGreen.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // ── ANALYTICS DASHBOARD ─────────────────────────────────────────────────
            if (isAdmin) {
                item {
                    val liveCount = apps.size
                    val pendingCount = submissions.count { it.status == "Pending" }
                    val approvedCount = submissions.count { it.status == "Approved" }
                    val rejectedCount = submissions.count { it.status == "Rejected" }
                    val totalSubmissions = submissions.size
                    val complianceRate = if (totalSubmissions == 0) 100 else (approvedCount * 100) / totalSubmissions
                    val userCount = developers.size
                    val reportedAppsCount = apps.count { it.reportsJson.isNotBlank() }
                    val suspendedUsersCount = developers.count { it.isSuspended }
                    val categoriesList = remember { listOf("Utilities", "Games", "Tools", "Entertainment") }
                    val categoryCounts = remember(apps) {
                        categoriesList.associateWith { cat -> apps.count { it.category.equals(cat, ignoreCase = true) } }
                    }
                    val maxCat = remember(categoryCounts) { (categoryCounts.values.maxOrNull() ?: 1).coerceAtLeast(1) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Analytics, contentDescription = null, tint = accentGreen, modifier = Modifier.size(18.dp))
                                    Text("TELEMETRY", color = accentGreen, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("● LIVE", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            // Overview stat tiles
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val stats = listOf(
                                    Triple("Users", userCount, Color(0xFFA78BFA)),
                                    Triple("Live Apps", liveCount, Color(0xFF38BDF8)),
                                    Triple("Pending", pendingCount, Color(0xFFFBBF24)),
                                    Triple("Reports", reportedAppsCount, Color(0xFFF59E0B))
                                )
                                stats.forEach { (label, value, color) ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(color.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                                            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                        Text(label, color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val stats2 = listOf(
                                    Triple("Approved", approvedCount, Color(0xFF34D399)),
                                    Triple("Rejected", rejectedCount, Color(0xFFF87171)),
                                    Triple("Suspended", suspendedUsersCount, Color(0xFFEF4444))
                                )
                                stats2.forEach { (label, value, color) ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(color.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                                            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                        Text(label, color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                    }
                                }
                            }

                            HorizontalDivider(color = cardBorderColor)

                            // Compliance bar
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Compliance Rate", fontSize = 12.sp, color = textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Approved vs total submissions", fontSize = 10.sp, color = textSecondary)
                                }
                                Text(
                                    "$complianceRate%",
                                    color = if (complianceRate >= 75) Color(0xFF10B981) else if (complianceRate >= 50) Color(0xFFF59E0B) else Color(0xFFEF4444),
                                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold
                                )
                            }
                            val compColor = if (complianceRate >= 75) Color(0xFF10B981) else if (complianceRate >= 50) Color(0xFFF59E0B) else Color(0xFFEF4444)
                            Box(modifier = Modifier.fillMaxWidth().height(7.dp).background(cardBorderColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))) {
                                Box(modifier = Modifier.fillMaxWidth(complianceRate / 100f).height(7.dp).background(compColor, RoundedCornerShape(4.dp)))
                            }

                            HorizontalDivider(color = cardBorderColor)

                            // Category distribution
                            Text("CATALOG DISTRIBUTION", fontSize = 10.sp, color = textSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            categoryCounts.forEach { (cat, count) ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(cat, color = textPrimary, fontSize = 11.sp, modifier = Modifier.width(85.dp))
                                    Box(modifier = Modifier.weight(1f).height(8.dp).background(textSecondary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))) {
                                        val frac = if (maxCat > 0) count.toFloat() / maxCat else 0f
                                        Box(modifier = Modifier.fillMaxWidth(frac).height(8.dp).background(accentGreen, RoundedCornerShape(4.dp)))
                                    }
                                    Text("$count", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            }

            // ── QUICK ACTION BUTTONS ───────────────────────────────────────────────
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Add App
                    Button(
                        onClick = { showAddForm = true },
                        modifier = Modifier.weight(1f).height(50.dp).testTag("admin_add_app_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Text("ADD APP", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp)
                        }
                    }
                    // Send Notice
                    Button(
                        onClick = { showSendNoticeForm = true },
                        modifier = Modifier.weight(1f).height(50.dp).testTag("admin_send_notice_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Text("NOTICE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp)
                        }
                    }
                    // Sync
                    Button(
                        onClick = {
                            viewModel.refreshMarketplace(force = true)
                            viewModel.refreshSubmissions()
                            viewModel.refreshTermsAgreements()
                            viewModel.refreshUpdateConfig()
                            Toast.makeText(context, "Syncing cloud nodes...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cardBgColor, contentColor = textPrimary),
                        border = BorderStroke(1.dp, cardBorderColor),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp), tint = textSecondary)
                            Text("SYNC", color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp)
                        }
                    }
                }
            }

            // ── SEGMENTED TAB BAR ─────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val tabs = listOf(
                            Pair("Submissions", Icons.Default.Inbox),
                            Pair("Live Catalog", Icons.Default.Store),
                            Pair("Push Update", Icons.Default.SystemUpdate),
                            Pair("Users", Icons.Default.People),
                            Pair("Audit", Icons.Default.History),
                            Pair("Server", Icons.Default.Cloud),
                            Pair("Notify", Icons.Default.Notifications),
                            Pair("Collections", Icons.Default.Collections),
                            Pair("Banners", Icons.Default.ViewCarousel),
                            Pair("Ads", Icons.Default.Campaign),
                            Pair("Feedback", Icons.Default.Flag)
                        )
                        tabs.forEachIndexed { idx, (label, icon) ->
                            val selected = adminSegmentIndex == idx
                            Box(
                                modifier = Modifier
                                    .width(74.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) accentGreen else Color.Transparent)
                                    .clickable {
                                        adminSegmentIndex = idx
                                        notifyPresetUid = ""
                                        adminSearchQuery = ""
                                        if (idx == 2) viewModel.refreshUpdateConfig()
                                        if (idx == 3) viewModel.refreshDevelopers()
                                        if (idx == 4) viewModel.refreshAuditLog()
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (selected) Color.White else textSecondary)
                                    Text(label, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color.White else textSecondary, letterSpacing = 0.2.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── SEARCH BAR ────────────────────────────────────────────────────────
            if (adminSegmentIndex == 0 || adminSegmentIndex == 1 || adminSegmentIndex == 3) {
                item {
                    OutlinedTextField(
                        value = adminSearchQuery,
                        onValueChange = { adminSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (adminSegmentIndex == 3) "Search users by name, email, or role..." else "Search by name, package, or developer…", fontSize = 12.sp, color = textSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = accentGreen, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (adminSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { adminSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = cardBgColor,
                            unfocusedContainerColor = cardBgColor,
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = cardBorderColor
                        )
                    )
                }
            }

            // ── SUBMISSIONS TAB ──────────────────────────────────────────────────
            if (adminSegmentIndex == 0) {
                if (filteredSubmissions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Inbox, contentDescription = null, tint = textSecondary, modifier = Modifier.size(48.dp))
                                Text("No submissions found", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(if (adminSearchQuery.isBlank()) "No apps have been submitted yet." else "No results for \"$adminSearchQuery\"", color = textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    // Group by status
                    val pendingSubs = filteredSubmissions.filter { it.status == "Pending" }
                    val approvedSubs = filteredSubmissions.filter { it.status == "Approved" }
                    val rejectedSubs = filteredSubmissions.filter { it.status == "Rejected" }

                    fun statusColor(status: String) = when (status) {
                        "Approved" -> Color(0xFF10B981)
                        "Rejected" -> Color(0xFFEF4444)
                        else -> Color(0xFFF59E0B)
                    }
                    fun statusIcon(status: String) = when (status) {
                        "Approved" -> Icons.Default.CheckCircle
                        "Rejected" -> Icons.Default.Cancel
                        else -> Icons.Default.HourglassEmpty
                    }

                    if (pendingSubs.isNotEmpty()) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                Text("PENDING REVIEW (${pendingSubs.size})", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    if (selectedSubmissionIds.size == pendingSubs.size) {
                                        selectedSubmissionIds.clear()
                                    } else {
                                        selectedSubmissionIds.clear()
                                        selectedSubmissionIds.addAll(pendingSubs.map { it.id })
                                    }
                                }) {
                                    Text(
                                        if (selectedSubmissionIds.size == pendingSubs.size) "Deselect all" else "Select all",
                                        color = accentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (selectedSubmissionIds.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val ids = selectedSubmissionIds.toList()
                                            viewModel.bulkApproveSubmissions(ids) { ok, fail ->
                                                Toast.makeText(context, "Approved $ok, failed $fail", Toast.LENGTH_LONG).show()
                                                selectedSubmissionIds.clear()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    ) {
                                        Text("Approve ${selectedSubmissionIds.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            val ids = selectedSubmissionIds.toList()
                                            viewModel.bulkRejectSubmissions(ids) { ok, fail ->
                                                Toast.makeText(context, "Rejected $ok, failed $fail", Toast.LENGTH_LONG).show()
                                                selectedSubmissionIds.clear()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                                    ) {
                                        Text("Reject ${selectedSubmissionIds.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        items(pendingSubs, key = { it.id }) { sub ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = sub.id in selectedSubmissionIds,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedSubmissionIds.add(sub.id)
                                        else selectedSubmissionIds.remove(sub.id)
                                    },
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = accentGreen)
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    AdminSubmissionCard(
                                        sub = sub,
                                        isAdmin = isAdmin,
                                        accentGreen = accentGreen,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        cardBgColor = cardBgColor,
                                        cardBorderColor = cardBorderColor,
                                        statusColor = statusColor(sub.status),
                                        statusIcon = statusIcon(sub.status),
                                        onApprove = { submissionToApprove = sub; approvalFeedback = "" },
                                        onReject = { submissionToReject = sub; rejectionReason = "" },
                                        onEdit = { editingSubmission = sub },
                                        onCardClick = { onShowAppDetails(sub.toAppEntity(apps.find { it.packageName == sub.packageName }?.versionHistoryJson ?: "")) }
                                    )
                                }
                            }
                        }
                    }
                    if (approvedSubs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                Text("APPROVED (${approvedSubs.size})", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                            }
                        }
                        items(approvedSubs, key = { it.id }) { sub ->
                            AdminSubmissionCard(
                                sub = sub,
                                isAdmin = isAdmin,
                                accentGreen = accentGreen,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                cardBgColor = cardBgColor,
                                cardBorderColor = cardBorderColor,
                                statusColor = statusColor(sub.status),
                                statusIcon = statusIcon(sub.status),
                                onApprove = { submissionToApprove = sub; approvalFeedback = "" },
                                onReject = { submissionToReject = sub; rejectionReason = "" },
                                onEdit = { editingSubmission = sub },
                                onCardClick = { onShowAppDetails(sub.toAppEntity(apps.find { it.packageName == sub.packageName }?.versionHistoryJson ?: "")) }
                            )
                        }
                    }
                    if (rejectedSubs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                Text("REJECTED (${rejectedSubs.size})", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                            }
                        }
                        items(rejectedSubs, key = { it.id }) { sub ->
                            AdminSubmissionCard(
                                sub = sub,
                                isAdmin = isAdmin,
                                accentGreen = accentGreen,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                cardBgColor = cardBgColor,
                                cardBorderColor = cardBorderColor,
                                statusColor = statusColor(sub.status),
                                statusIcon = statusIcon(sub.status),
                                onApprove = { submissionToApprove = sub; approvalFeedback = "" },
                                onReject = { submissionToReject = sub; rejectionReason = "" },
                                onEdit = { editingSubmission = sub },
                                onCardClick = { onShowAppDetails(sub.toAppEntity(apps.find { it.packageName == sub.packageName }?.versionHistoryJson ?: "")) }
                            )
                        }
                    }
                }
            }

            // ── LIVE CATALOG TAB ─────────────────────────────────────────────────
            if (adminSegmentIndex == 1) {
                if (filteredApps.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Store, contentDescription = null, tint = textSecondary, modifier = Modifier.size(48.dp))
                                Text("Catalog is empty", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Add your first app using the ADD APP button above.", color = textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(filteredApps, key = { it.id }) { app ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, if (app.isSuspended) Color(0xFFEF4444).copy(alpha = 0.4f) else cardBorderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // App icon
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accentGreen.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (app.logo.isNotBlank()) {
                                        AsyncImage(model = app.logo, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                    } else {
                                        Text(app.name.take(1).uppercase(), color = accentGreen, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(app.name, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                        if (app.isSuspended) {
                                            Box(modifier = Modifier.background(Color(0xFFEF4444).copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                                Text("SUSPENDED", color = Color(0xFFEF4444), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                        if (app.reportsJson.isNotBlank()) {
                                            val reportCount = app.reportsJson.split("||").filter { it.isNotBlank() }.size
                                            Box(modifier = Modifier.background(Color(0xFFF59E0B).copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                                Text("$reportCount REPORT${if (reportCount == 1) "" else "S"}", color = Color(0xFFF59E0B), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    }
                                    Text("${app.developer}  •  v${app.version}  •  ${app.category}", color = textSecondary, fontSize = 11.sp, maxLines = 1)
                                    Text(app.packageName, color = textSecondary.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
                                    if (app.reportsJson.isNotBlank()) {
                                        val reports = app.reportsJson.split("||").filter { it.isNotBlank() }
                                        reports.take(3).forEach { r ->
                                            Text("• $r", color = Color(0xFFF59E0B), fontSize = 10.sp, maxLines = 2)
                                        }
                                        if (reports.size > 3) {
                                            Text("+${reports.size - 3} more…", color = textSecondary, fontSize = 10.sp)
                                        }
                                        TextButton(onClick = {
                                            viewModel.clearAppReports(app.id) { success, msg ->
                                                Toast.makeText(context, msg ?: if (success) "Cleared" else "Failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Text("Clear reports", color = accentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            // Action row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { editingApp = app },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = accentGreen)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", fontSize = 11.sp, color = accentGreen, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showPushUpdateFormFor = app.toSubmissionEntity(); },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF6366F1))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Update", fontSize = 11.sp, color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { appToManageSuspension = app; suspensionDialogReason = ""; isSuspensionDialogActive = true },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, (if (app.isSuspended) Color(0xFF10B981) else Color(0xFFEF4444)).copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    val suspendColor = if (app.isSuspended) Color(0xFF10B981) else Color(0xFFEF4444)
                                    Icon(if (app.isSuspended) Icons.Default.PlayArrow else Icons.Default.Block, contentDescription = null, modifier = Modifier.size(13.dp), tint = suspendColor)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (app.isSuspended) "Resume" else "Suspend", fontSize = 11.sp, color = suspendColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ── PUSH UPDATE TAB ───────────────────────────────────────────────────
            if (isAdmin && adminSegmentIndex == 2) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(36.dp).background(Color(0xFF6366F1).copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("In-App Update Control", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = textPrimary)
                                    Text("Push mandatory or optional APK updates to all users", color = textSecondary, fontSize = 11.sp)
                                }
                            }

                            HorizontalDivider(color = cardBorderColor)

                            // Live Preview Banner
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = accentGreen.copy(alpha = 0.05f)),
                                border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Icon(Icons.Default.Preview, contentDescription = null, tint = accentGreen, modifier = Modifier.size(14.dp))
                                            Text("LIVE DIALOG PREVIEW", color = accentGreen, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background((if (uForceUpdate) Color(0xFFEF4444) else Color(0xFF10B981)).copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (uForceUpdate) Icons.Default.Warning else Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = if (uForceUpdate) Color(0xFFEF4444) else Color(0xFF10B981),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = if (uForceUpdate) "MANDATORY" else "OPTIONAL",
                                                    color = if (uForceUpdate) Color(0xFFEF4444) else Color(0xFF10B981),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cardBgColor), border = BorderStroke(1.dp, cardBorderColor.copy(alpha = 0.5f))) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(uTitle.ifBlank { "New Update Available!" }, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Version: ${uVersionName.ifBlank { "—" }}  (Build ${uVersionCode.ifBlank { "0" }})", color = textSecondary, fontSize = 11.sp)
                                            Text(uMessage.ifBlank { "Update details will appear here…" }, color = textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = cardBorderColor)

                            // Form fields
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = uVersionCode,
                                    onValueChange = { uVersionCode = it },
                                    label = { Text("Version Code *", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. 12", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accentGreen, unfocusedBorderColor = cardBorderColor)
                                )
                                OutlinedTextField(
                                    value = uVersionName,
                                    onValueChange = { uVersionName = it },
                                    label = { Text("Version Name *", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. 1.2.0", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accentGreen, unfocusedBorderColor = cardBorderColor)
                                )
                            }
                            OutlinedTextField(
                                value = uApkUrl,
                                onValueChange = { uApkUrl = it },
                                label = { Text("APK Download URL *", fontSize = 11.sp) },
                                placeholder = { Text("https://…", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accentGreen, unfocusedBorderColor = cardBorderColor)
                            )
                            OutlinedTextField(
                                value = uTitle,
                                onValueChange = { uTitle = it },
                                label = { Text("Update Dialog Title *", fontSize = 11.sp) },
                                placeholder = { Text("e.g. Dark Store v1.2 is here!", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accentGreen, unfocusedBorderColor = cardBorderColor)
                            )
                            OutlinedTextField(
                                value = uMessage,
                                onValueChange = { uMessage = it },
                                label = { Text("Update Message / Changelog *", fontSize = 11.sp) },
                                placeholder = { Text("What's new in this version…", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary, focusedBorderColor = accentGreen, unfocusedBorderColor = cardBorderColor)
                            )

                            // Force update toggle
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (uForceUpdate) Color(0xFFEF4444).copy(alpha = 0.05f) else cardBgColor),
                                border = BorderStroke(1.dp, if (uForceUpdate) Color(0xFFEF4444).copy(alpha = 0.3f) else cardBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Force Update", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Users cannot dismiss this update dialog", color = textSecondary, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = uForceUpdate,
                                        onCheckedChange = { uForceUpdate = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFEF4444), uncheckedTrackColor = cardBorderColor)
                                    )
                                }
                            }

                            // Status message
                            if (uPushStatusMessage.isNotBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (uPushStatusMessage.contains("successfully", ignoreCase = true) || uPushStatusMessage.contains("Pulled", ignoreCase = true))
                                            Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFEF4444).copy(alpha = 0.08f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val isSuccess = uPushStatusMessage.contains("successfully", ignoreCase = true) || uPushStatusMessage.contains("Pulled", ignoreCase = true)
                                        Icon(if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null, tint = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        Text(uPushStatusMessage, color = textPrimary, fontSize = 11.sp, lineHeight = 15.sp)
                                    }
                                }
                            }

                            // Push button
                            Button(
                                onClick = {
                                    val vCode = uVersionCode.trim().toIntOrNull()
                                    if (vCode == null || uVersionName.isBlank() || uApkUrl.isBlank() || uTitle.isBlank() || uMessage.isBlank()) {
                                        uPushStatusMessage = "All fields are required. Version Code must be a number."
                                        return@Button
                                    }
                                    uPushIsLoading = true
                                    uPushStatusMessage = ""
                                    val cfg = com.example.data.UpdateConfigEntity(
                                        latestVersionCode = vCode,
                                        latestVersionName = uVersionName.trim(),
                                        apkDownloadUrl = uApkUrl.trim(),
                                        updateTitle = uTitle.trim(),
                                        updateMessage = uMessage.trim(),
                                        forceUpdate = uForceUpdate
                                    )
                                    viewModel.saveUpdateConfig(cfg) { success, errorMsg ->
                                        uPushIsLoading = false
                                        uPushStatusMessage = if (success) {
                                            "Update config pushed to Firebase RTDB successfully!"
                                        } else {
                                            "Push failed: ${errorMsg ?: "Unknown error. Check Firebase rules."}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (uForceUpdate) Color(0xFFEF4444) else Color(0xFF6366F1)),
                                enabled = !uPushIsLoading
                            ) {
                                if (uPushIsLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Publish,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    if (uPushIsLoading) "PUSHING TO FIREBASE…" else "PUSH ${if (uForceUpdate) "MANDATORY" else "OPTIONAL"} UPDATE",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.refreshUpdateConfig()
                                        uHasInitialized = false
                                        uPushStatusMessage = "⟳ Pulled live config from Firebase"
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, cardBorderColor)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp), tint = textSecondary)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Pull Live", fontSize = 11.sp, color = textSecondary)
                                }
                                OutlinedButton(
                                    onClick = {
                                        uVersionCode = ""; uVersionName = ""; uApkUrl = ""; uTitle = ""; uMessage = ""; uForceUpdate = false; uPushStatusMessage = ""
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, cardBorderColor)
                                ) {
                                    Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(14.dp), tint = textSecondary)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Clear Form", fontSize = 11.sp, color = textSecondary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Premium is never free: referral reward, admin grant (Users tab) or the admin role itself.
                    var sweeping by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PREMIUM ACCESS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
                            Text("Free Premium is removed. People get Premium by inviting friends, or from you: Users tab > Grant Premium. Admin accounts always have Premium.",
                                color = textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                            OutlinedButton(
                                enabled = !sweeping,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    sweeping = true
                                    viewModel.removeUnearnedPremiumAsAdmin { ok, n ->
                                        sweeping = false
                                        Toast.makeText(context, if (!ok) "Can't reach the DarkStore server." else "Removed old free Premium from $n account(s).", Toast.LENGTH_LONG).show()
                                    }
                                }
                            ) { Text(if (sweeping) "Working..." else "Remove old free Premium from everyone") }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ---------------- REFERRAL REWARDS (admin) ----------------
                    run {
                        val cfgNow by viewModel.referralConfig.collectAsStateWithLifecycle()
                        val topRef by viewModel.adminReferralCounts.collectAsStateWithLifecycle()
                        val allUsers by viewModel.developers.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { viewModel.loadReferralData(); viewModel.refreshAdminReferralCounts() }
                        var enabledS by remember(cfgNow) { mutableStateOf(cfgNow.enabled) }
                        var requiredS by remember(cfgNow) { mutableStateOf(cfgNow.required) }
                        var daysS by remember(cfgNow) { mutableStateOf(cfgNow.rewardDays) }
                        var windowS by remember(cfgNow) { mutableStateOf(cfgNow.accountWindowDays) }
                        var savingRef by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, cardBorderColor)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("REFERRAL REWARDS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (enabledS) "Referrals are ON" else "Referrals are OFF", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Users unlock Premium by inviting friends with their code.",
                                            color = textSecondary, fontSize = 11.sp, lineHeight = 15.sp
                                        )
                                    }
                                    Switch(checked = enabledS, onCheckedChange = { enabledS = it })
                                }

                                Text("Friends needed per reward", color = textSecondary, fontSize = 11.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = { if (requiredS > 1) requiredS-- }) { Text("-") }
                                    Text("$requiredS", color = textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    OutlinedButton(onClick = { if (requiredS < 50) requiredS++ }) { Text("+") }
                                }

                                Text("Premium length per reward", color = textSecondary, fontSize = 11.sp)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0 to "Lifetime", 7 to "7 days", 30 to "30 days", 90 to "90 days", 365 to "1 year").forEach { (d, label) ->
                                        FilterChip(
                                            selected = daysS == d, onClick = { daysS = d }, label = { Text(label, fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentGreen, selectedLabelColor = Color.White)
                                        )
                                    }
                                }

                                Text("A friend can use a code only within this many days of creating their account", color = textSecondary, fontSize = 11.sp)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 3, 7, 14, 30).forEach { d ->
                                        FilterChip(
                                            selected = windowS == d, onClick = { windowS = d }, label = { Text("$d day${if (d > 1) "s" else ""}", fontSize = 12.sp) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentGreen, selectedLabelColor = Color.White)
                                        )
                                    }
                                }

                                Button(
                                    enabled = !savingRef,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    onClick = {
                                        savingRef = true
                                        viewModel.saveReferralConfigAsAdmin(
                                            com.example.data.FirebaseAuthService.ReferralConfig(enabledS, requiredS, daysS, windowS)
                                        ) { ok ->
                                            savingRef = false
                                            Toast.makeText(context, if (ok) "Referral settings saved." else "Couldn't save (admin only).", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) { Text(if (savingRef) "Saving..." else "Save referral settings", color = Color.White, fontWeight = FontWeight.Bold) }

                                if (topRef.isNotEmpty()) {
                                    Divider(color = cardBorderColor.copy(alpha = 0.5f))
                                    Text("Top referrers", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    topRef.take(8).forEach { (uid, n) ->
                                        val who = allUsers.firstOrNull { it.uid == uid }
                                        Row(Modifier.fillMaxWidth()) {
                                            Text(
                                                who?.displayName?.ifBlank { null } ?: who?.email ?: uid.take(8),
                                                color = textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1
                                            )
                                            Text("$n invited", color = accentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // ---------------- APK STORAGE (admin) ----------------
                    com.example.view.AdminApkStorageCard(
                        viewModel = viewModel, accent = accentGreen, cardBg = cardBgColor, cardBorder = cardBorderColor,
                        textPrimary = textPrimary, textSecondary = textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // ---------------- ANTI-CHEAT & FAKE ACCOUNTS (admin) ----------------
                    run {
                        val fcfg by viewModel.fraudConfig.collectAsStateWithLifecycle()
                        val alerts by viewModel.fraudAlerts.collectAsStateWithLifecycle()
                        val scanning by viewModel.fraudScanBusy.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { viewModel.refreshFraudAlerts() }
                        var autoS by remember(fcfg) { mutableStateOf(fcfg.autoSuspend) }
                        var devS by remember(fcfg) { mutableStateOf(fcfg.maxAccountsPerDevice) }
                        var burstS by remember(fcfg) { mutableStateOf(fcfg.burstLimit) }
                        var strikeS by remember(fcfg) { mutableStateOf(fcfg.strikeLimit) }
                        var verifiedS by remember(fcfg) { mutableStateOf(fcfg.requireVerifiedEmail) }
                        val openAlerts = alerts.filter { it.status == "open" }
                        val danger = Color(0xFFEF4444)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = if (openAlerts.isNotEmpty()) danger.copy(alpha = 0.05f).compositeOver(cardBgColor) else cardBgColor),
                            border = BorderStroke(if (openAlerts.isNotEmpty()) 1.5.dp else 1.dp, if (openAlerts.isNotEmpty()) danger.copy(alpha = 0.6f) else cardBorderColor)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("ANTI-CHEAT & FAKE ACCOUNTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                                    if (openAlerts.isNotEmpty()) {
                                        Text("${openAlerts.size} open", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.background(danger, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                }
                                Text(
                                    "Detects extra accounts on the same phone (suspended and signed out automatically), same-phone invites, referral bursts and temporary emails. The DarkStore server scans automatically every 2 minutes, even when you are offline.",
                                    color = textSecondary, fontSize = 11.sp, lineHeight = 15.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Suspend cheaters automatically", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Off = only alert me and let me decide.", color = textSecondary, fontSize = 11.sp)
                                    }
                                    Switch(checked = autoS, onCheckedChange = { autoS = it })
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Require a verified email for referrals", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Blocks throw-away accounts. Google sign-in counts as verified.", color = textSecondary, fontSize = 11.sp)
                                    }
                                    Switch(checked = verifiedS, onCheckedChange = { verifiedS = it })
                                }
                                AdminStepper("Accounts allowed per phone (every extra account is suspended)", devS, 1..10, textPrimary, textSecondary) { devS = it }
                                AdminStepper("Referrals per hour before it looks like a burst", burstS, 2..20, textPrimary, textSecondary) { burstS = it }
                                AdminStepper("Blocked cheating attempts before self-suspension", strikeS, 1..5, textPrimary, textSecondary) { strikeS = it }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                        onClick = {
                                            viewModel.saveFraudConfigAsAdmin(com.example.data.FirebaseAuthService.FraudConfig(autoS, devS, burstS, strikeS, verifiedS)) { ok ->
                                                Toast.makeText(context, if (ok) "Anti-cheat settings saved." else "Couldn't save (admin only).", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        enabled = !scanning,
                                        onClick = {
                                            viewModel.runFraudScan(manual = true) { n, s ->
                                                Toast.makeText(context, if (n < 0) "Can't reach the DarkStore server." else if (n == 0) "Scan finished: nothing suspicious." else "Scan finished: $n new alert(s), $s suspended.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    ) { Text(if (scanning) "Scanning..." else "Scan now") }
                                }

                                if (openAlerts.isEmpty()) {
                                    Text("No open alerts.", color = textSecondary, fontSize = 12.sp)
                                }
                                openAlerts.take(10).forEach { a ->
                                    val sev = if (a.severity == "HIGH") danger else Color(0xFFF59E0B)
                                    val label = when (a.type) {
                                        "SAME_DEVICE_REFERRAL" -> "Same phone referral"
                                        "DEVICE_FARM" -> "Too many accounts on one phone"
                                        "REFERRAL_BURST" -> "Referral burst"
                                        "DISPOSABLE_EMAIL" -> "Temporary email"
                                        "REFERRAL_FARMING" -> "Referral farming"
                                        else -> a.type
                                    }
                                    Column(
                                        Modifier.fillMaxWidth().background(sev.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                            .border(1.dp, sev.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(label.uppercase(), color = sev, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                            Text(
                                                when (a.autoAction) { "suspended" -> "AUTO-SUSPENDED"; "strike" -> "STRIKE"; else -> "FLAGGED" },
                                                color = sev, fontSize = 9.sp, fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text("${a.name.ifBlank { "User" }} • ${a.email}", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(a.details, color = textSecondary, fontSize = 11.sp)
                                        Text(java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(a.createdAt)), color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val suspendedAlready = a.autoAction == "suspended"
                                            TextButton(onClick = {
                                                viewModel.setSuspectSuspended(a, !suspendedAlready) { ok ->
                                                    Toast.makeText(context, if (ok) (if (suspendedAlready) "Account restored." else "Account suspended.") else "Couldn't update the account.", Toast.LENGTH_SHORT).show()
                                                }
                                            }) { Text(if (suspendedAlready) "Restore account" else "Suspend", color = if (suspendedAlready) Color(0xFF10B981) else danger, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                            TextButton(onClick = { viewModel.dismissFraudAlert(a.id) }) { Text("Dismiss", color = textSecondary, fontSize = 12.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Maintenance mode — take the whole store offline for users
                    // (admins can still reach the console).
                    var isSavingMaintenance by remember { mutableStateOf(false) }
                    var maintenanceMessage by remember(maintenanceCfg.message) { mutableStateOf(maintenanceCfg.message) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, if (maintenanceCfg.isEnabled) Color(0xFFEF4444).copy(alpha = 0.4f) else cardBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("MAINTENANCE MODE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (maintenanceCfg.isEnabled) "Store is OFFLINE for users" else "Store is online",
                                        color = if (maintenanceCfg.isEnabled) Color(0xFFEF4444) else textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "When enabled, non-admin users see a maintenance screen instead of the catalog.",
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = maintenanceCfg.isEnabled,
                                    enabled = !isSavingMaintenance,
                                    onCheckedChange = { enabled ->
                                        isSavingMaintenance = true
                                        viewModel.setMaintenanceModeAsAdmin(enabled, maintenanceMessage) { success ->
                                            isSavingMaintenance = false
                                            Toast.makeText(
                                                context,
                                                if (success) (if (enabled) "Maintenance mode ON" else "Maintenance mode OFF") else "Couldn't save — try again.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = maintenanceMessage,
                                onValueChange = { maintenanceMessage = it },
                                label = { Text("Message shown to users", color = textSecondary) },
                                singleLine = false,
                                maxLines = 3,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = accentGreen,
                                    unfocusedIndicatorColor = cardBorderColor,
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── ADS TAB (Unity Ads ON/OFF) ────────────────────────────────────────
            if (isAdmin && adminSegmentIndex == 9) {
                item {
                    // Unity Ads — remote ON/OFF for the home-screen banner slot.
                    // Writes are enforced server-side by the `unityAdsConfig` Firebase rule (admins only).
                    var isSavingUnityAds by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, if (unityAdsAdminCfg.enabled) accentGreen.copy(alpha = 0.4f) else cardBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("UNITY ADS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (unityAdsAdminCfg.enabled) "Unity Ads: Enabled" else "Unity Ads: Disabled",
                                        color = if (unityAdsAdminCfg.enabled) accentGreen else textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "When enabled, a Unity banner replaces the home promo banner if one loads. If it fails, the normal promo shows.",
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = unityAdsAdminCfg.enabled,
                                    enabled = !isSavingUnityAds,
                                    onCheckedChange = { on ->
                                        isSavingUnityAds = true
                                        viewModel.setUnityAdsAsAdmin(on, unityAdsAdminCfg.testMode) { success ->
                                            isSavingUnityAds = false
                                            Toast.makeText(
                                                context,
                                                if (success) "Unity Ads ${if (on) "enabled" else "disabled"}." else "Couldn't save — admin permission or network problem.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (unityAdsAdminCfg.testMode) "Test ads (safe)" else "Production ads (live)",
                                        color = if (unityAdsAdminCfg.testMode) textPrimary else Color(0xFFEF4444),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Turn test mode off only when DarkStore is approved for live ads. Takes effect the next time the app starts.",
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = unityAdsAdminCfg.testMode,
                                    enabled = !isSavingUnityAds,
                                    onCheckedChange = { test ->
                                        isSavingUnityAds = true
                                        viewModel.setUnityAdsAsAdmin(unityAdsAdminCfg.enabled, test) { success ->
                                            isSavingUnityAds = false
                                            Toast.makeText(
                                                context,
                                                if (success) "Unity Ads ${if (test) "test" else "production"} mode saved." else "Couldn't save — admin permission or network problem.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }

                }
            }

            // ── DARKSTORE REPORTS (user feedback) TAB ──────────────────────────────
            if (isAdmin && adminSegmentIndex == 10) {
                item {
                    val dsReports by viewModel.darkstoreReports.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { viewModel.refreshDarkstoreReports() }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // ── Signing fingerprint + share-link verification (for assetlinks.json) ──
                        val fpCtx = LocalContext.current
                        val fp = remember { com.example.utils.SigningInfo.fingerprints(fpCtx) }
                        val linkStatus = remember { com.example.utils.SigningInfo.linkState(fpCtx, "darkrootstore.vercel.app") }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.4f))
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Share link check", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Text("Link status: $linkStatus", color = textSecondary, fontSize = 12.sp)
                                Text("This build is signed with (SHA-256):", color = textSecondary, fontSize = 11.sp)
                                Text(fp?.sha256 ?: "unavailable", color = textPrimary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                Text("SHA-1: ${fp?.sha1 ?: "unavailable"}", color = textSecondary, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                Text("Put the SHA-256 in website/.well-known/assetlinks.json, redeploy the site, then reinstall the app.", color = textSecondary, fontSize = 11.sp)
                                TextButton(onClick = {
                                    val cm = fpCtx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", fp?.sha256 ?: ""))
                                    Toast.makeText(fpCtx, "SHA-256 copied", Toast.LENGTH_SHORT).show()
                                }) { Text("Copy SHA-256", color = accentGreen, fontSize = 12.sp) }
                            }
                        }
                        Text("DarkStore reports (${dsReports.size})", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        if (dsReports.isEmpty()) {
                            Text("No reports from users yet.", color = textSecondary, fontSize = 12.sp)
                        }
                        dsReports.forEach { r ->
                            val catColor = when (r.category) {
                                "Security" -> Color(0xFFEF4444); "Bug" -> Color(0xFFF59E0B)
                                "Abuse / Content" -> Color(0xFFA855F7); "Suggestion" -> Color(0xFF3B82F6)
                                else -> Color(0xFF64748B)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = catColor.copy(alpha = 0.06f).compositeOver(cardBgColor)),
                                border = BorderStroke(1.dp, catColor.copy(alpha = 0.45f))
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(r.category.uppercase(), color = catColor, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                                            modifier = Modifier.background(catColor.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                                        Spacer(Modifier.weight(1f))
                                        Text(java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(r.createdAt)), color = textSecondary, fontSize = 10.sp)
                                    }
                                    Text(r.message, color = textPrimary, fontSize = 13.sp)
                                    Text("${r.reporterName.ifBlank { "User" }} • ${r.reporterEmail}", color = textSecondary, fontSize = 10.sp)
                                    Text("v${r.appVersion} • ${r.device}", color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp)
                                    TextButton(onClick = { viewModel.resolveDarkstoreReport(r.id) }, modifier = Modifier.align(Alignment.End)) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Mark resolved", color = Color(0xFF10B981), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── SERVER TAB ────────────────────────────────────────────────────────
            if (isAdmin && adminSegmentIndex == 5) {
                item {
                    com.example.view.ServerStatusPanel(
                        accentGreen = accentGreen,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
            }

            // ── NOTIFY TAB ────────────────────────────────────────────────────────
            if (isAdmin && adminSegmentIndex == 6) {
                item {
                    com.example.view.AdminNotifyPanel(
                        viewModel = viewModel,
                        users = developers,
                        presetUid = notifyPresetUid,
                        accentGreen = accentGreen,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
            }

            // ── COLLECTIONS TAB ───────────────────────────────────────────────────
            if (isAdmin && adminSegmentIndex == 7) {
                item {
                    com.example.view.CollectionsAdminPanel(
                        viewModel = viewModel,
                        collections = viewModel.collections.collectAsStateWithLifecycle().value,
                        apps = apps,
                        accentGreen = accentGreen,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
            }

            // ── BANNERS TAB ───────────────────────────────────────────────────────
            if (isAdmin && adminSegmentIndex == 8) {
                item {
                    com.example.view.BannersAdminPanel(
                        viewModel = viewModel,
                        banners = viewModel.banners.collectAsStateWithLifecycle().value,
                        apps = apps,
                        collections = viewModel.collections.collectAsStateWithLifecycle().value,
                        accentGreen = accentGreen,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
            }

            // ── AUDIT LOG TAB ─────────────────────────────────────────────────────
            if (isAdmin && adminSegmentIndex == 4) {
                if (auditEntries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.History, contentDescription = null, tint = textSecondary, modifier = Modifier.size(48.dp))
                                Text("No audit events yet", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Admin actions (approve, reject, suspend, rollback…) will appear here.", color = textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                                TextButton(onClick = { viewModel.refreshAuditLog() }) {
                                    Text("Refresh", color = accentGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${auditEntries.size} events", color = textSecondary, fontSize = 12.sp)
                            TextButton(onClick = { viewModel.refreshAuditLog() }) {
                                Text("Refresh", color = accentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(auditEntries, key = { it.id }) { entry ->
                        val timeFmt = remember { java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()) }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, cardBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(entry.action, color = accentGreen, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                    Text(timeFmt.format(java.util.Date(entry.timestamp)), color = textSecondary, fontSize = 10.sp)
                                }
                                Text(
                                    "${entry.targetType}: ${entry.targetName.ifBlank { entry.targetId }}",
                                    color = textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (entry.details.isNotBlank()) {
                                    Text(entry.details, color = textSecondary, fontSize = 11.sp, maxLines = 3)
                                }
                                Text("by ${entry.adminEmail.ifBlank { entry.adminUid }}", color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // ── USERS TAB ────────────────────────────────────────────────────────
            if (isAdmin && adminSegmentIndex == 3) {
                val sectionUsers = when (adminUserFilter) {
                    "Developers" -> developers.filter { it.isDeveloper }
                    "Users" -> developers.filter { !it.isDeveloper && it.role != "admin" }
                    "Admins" -> developers.filter { it.role == "admin" }
                    "Suspended" -> developers.filter { it.isSuspended }
                    "No token" -> developers.filter { it.fcmToken.isBlank() }
                    else -> developers
                }
                val filteredUsers = if (adminSearchQuery.isBlank()) {
                    sectionUsers
                } else {
                    sectionUsers.filter {
                        it.displayName.contains(adminSearchQuery, ignoreCase = true) ||
                        it.email.contains(adminSearchQuery, ignoreCase = true) ||
                        it.role.contains(adminSearchQuery, ignoreCase = true)
                    }
                }
                item {
                    val sections = listOf(
                        "All" to developers.size,
                        "Users" to developers.count { !it.isDeveloper && it.role != "admin" },
                        "Developers" to developers.count { it.isDeveloper },
                        "Admins" to developers.count { it.role == "admin" },
                        "Suspended" to developers.count { it.isSuspended },
                        "No token" to developers.count { it.fcmToken.isBlank() }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sections.forEach { (label, count) ->
                            val selected = adminUserFilter == label
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) accentGreen.copy(alpha = 0.18f) else cardBgColor)
                                    .border(1.dp, if (selected) accentGreen else cardBorderColor, RoundedCornerShape(20.dp))
                                    .clickable { adminUserFilter = label }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    "$label · $count",
                                    color = if (selected) accentGreen else textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                if (filteredUsers.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.People, contentDescription = null, tint = textSecondary, modifier = Modifier.size(48.dp))
                                Text("No users found in \"$adminUserFilter\"", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    items(filteredUsers, key = { it.uid }) { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            border = BorderStroke(1.dp, cardBorderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = user.displayName.ifBlank { "Unknown User" }, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(text = user.email, color = textSecondary, fontSize = 11.sp)
                                    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.background(if (user.role == "admin") Color(0xFFEF4444).copy(alpha = 0.1f) else Color(0xFF3B82F6).copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text(user.role.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = if (user.role == "admin") Color(0xFFEF4444) else Color(0xFF3B82F6))
                                        }
                                        if (user.isDeveloper) {
                                            Box(modifier = Modifier.background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text("DEVELOPER", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
                                            }
                                        }
                                        if (user.isSuspended) {
                                            Box(modifier = Modifier.background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text("SUSPENDED", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF4444))
                                            }
                                        }
                                    }
                                    if (user.isSuspended && user.suspensionReason.isNotBlank()) {
                                        Text(user.suspensionReason, color = Color(0xFFEF4444), fontSize = 10.sp, maxLines = 2)
                                    }

                                    // Push (FCM) token — admin-only view
                                    var tokenExpanded by remember(user.uid) { mutableStateOf(false) }
                                    val hasToken = user.fcmToken.isNotBlank()
                                    val tokenColor = if (hasToken) Color(0xFF10B981) else Color(0xFFF59E0B)
                                    Row(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = hasToken) { tokenExpanded = !tokenExpanded }
                                            .background(tokenColor.copy(alpha = 0.10f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (hasToken) "PUSH TOKEN · ACTIVE · tap to ${if (tokenExpanded) "hide" else "view"}"
                                            else "NO PUSH TOKEN · can't receive direct pushes",
                                            fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = tokenColor
                                        )
                                    }
                                    Text(
                                        "Send notification →",
                                        color = accentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .clickable {
                                                notifyPresetUid = user.uid
                                                adminSegmentIndex = 6
                                            }
                                    )
                                    Text(
                                        if (user.isPremiumMember) "Remove Premium" else "Grant Premium",
                                        color = if (user.isPremiumMember) Color(0xFFEF4444) else Color(0xFFD97706),
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .clickable {
                                                viewModel.setUserPremiumAsAdmin(user, !user.isPremiumMember, 0) { _, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    )
                                    if (hasToken && tokenExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .fillMaxWidth()
                                                .background(textSecondary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                                .padding(8.dp)
                                        ) {
                                            androidx.compose.foundation.text.selection.SelectionContainer {
                                                Text(user.fcmToken, color = textPrimary, fontSize = 10.sp, lineHeight = 13.sp)
                                            }
                                            Text(
                                                "Copy token",
                                                color = accentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                                    .clickable {
                                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("fcm_token", user.fcmToken))
                                                        Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
                                                    }
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    val newState = !user.isSuspended
                                    viewModel.suspendUser(user, newState, if (newState) "Suspended by admin" else "") { success ->
                                        Toast.makeText(context, if (success) (if (newState) "User suspended" else "User unsuspended") else "Failed", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        if (user.isSuspended) Icons.Default.PlayArrow else Icons.Default.Block,
                                        contentDescription = if (user.isSuspended) "Unsuspend" else "Suspend",
                                        tint = if (user.isSuspended) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { userToEdit = user }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit User", tint = textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

        } // end LazyColumn

        // ── OVERLAY FORMS (outside LazyColumn, drawn on top) ──────────────────
        if (showAddForm || editingApp != null) {
            val appToEdit = editingApp
            AddNewAppForm(
            accentColor = accentGreen,
            viewModel = viewModel,
                existingApp = appToEdit,
                isForAdmin = isAdmin,
                userEmail = userEmail,
                defaultDeveloperName = devName,
                onDismiss = { showAddForm = false; editingApp = null },
                onSubmit = { appData ->
                    if (isAdmin) {
                        viewModel.addOrUpdateAppInCatalog(appData) { success ->
                            Toast.makeText(context, if (success) "App saved to catalog!" else "Save failed.", Toast.LENGTH_SHORT).show()
                            if (success) { showAddForm = false; editingApp = null }
                        }
                    } else {
                        viewModel.submitAppForReview(
                            name = appData.name, packageName = appData.packageName, description = appData.description,
                            apkUrl = appData.apkUrl, screenshots = appData.screenshots, logo = appData.logo,
                            category = appData.category, version = appData.version, hasAds = appData.hasAds,
                            versionCode = appData.versionCode, changelog = appData.changelog, videoUrl = appData.videoUrl
                        ) { success, msg ->
                            Toast.makeText(context, msg ?: if (success) "Submitted!" else "Failed", Toast.LENGTH_SHORT).show()
                            if (success) { showAddForm = false; editingApp = null }
                        }
                    }
                }
            )
        }

        if (editingSubmission != null) {
            val sub = editingSubmission!!
            AddNewAppForm(
            accentColor = accentGreen,
            viewModel = viewModel,
                existingApp = AppEntity(
                    id = sub.id, name = sub.name, developer = sub.developer, version = sub.version,
                    size = "18 MB", category = sub.category, rating = "0.0", description = sub.description,
                    logo = sub.logo, screenshots = sub.screenshots, apkUrl = sub.apkUrl,
                    packageName = sub.packageName, isFeatured = false, isPopular = true,
                    isRecent = true, versionCode = 1, isApproved = true, submittedBy = sub.submittedBy, hasAds = sub.hasAds
                ),
                isForAdmin = isAdmin,
                userEmail = userEmail,
                defaultDeveloperName = devName,
                onDismiss = { editingSubmission = null },
                onSubmit = { appData ->
                    val updatedSub = sub.copy(
                        name = appData.name, packageName = appData.packageName, version = appData.version,
                        description = appData.description, apkUrl = appData.apkUrl, screenshots = appData.screenshots,
                        logo = appData.logo, category = appData.category, developer = appData.developer, hasAds = appData.hasAds
                    )
                    viewModel.editSubmissionDetails(updatedSub) { success ->
                        Toast.makeText(context, if (success) "Submission updated!" else "Failed to save changes.", Toast.LENGTH_SHORT).show()
                        if (success) editingSubmission = null
                    }
                }
            )
        }

        if (appSubmittingUpdateFor != null) {
            val originalSub = appSubmittingUpdateFor!!
            val mappedSubApp = AppEntity(
                id = originalSub.id,
                name = originalSub.name,
                developer = originalSub.developer,
                version = originalSub.version,
                size = "18 MB",
                category = originalSub.category,
                rating = "0.0",
                description = originalSub.description,
                logo = originalSub.logo,
                screenshots = originalSub.screenshots,
                apkUrl = originalSub.apkUrl,
                packageName = originalSub.packageName,
                isFeatured = false,
                isPopular = true,
                isRecent = true,
                versionCode = originalSub.versionCode,
                changelog = originalSub.changelog,
                videoUrl = originalSub.videoUrl,
                isApproved = true,
                submittedBy = originalSub.submittedBy,
                hasAds = originalSub.hasAds
            )
            AddNewAppForm(
            accentColor = accentGreen,
            viewModel = viewModel,
                existingApp = mappedSubApp,
                isForAdmin = isAdmin,
                userEmail = userEmail,
                defaultDeveloperName = devName,
                onDismiss = { appSubmittingUpdateFor = null },
                onSubmit = { appData ->
                    viewModel.submitAppForReview(
                        name = appData.name,
                        packageName = appData.packageName,
                        description = appData.description,
                        apkUrl = appData.apkUrl,
                        screenshots = appData.screenshots,
                        logo = appData.logo,
                        category = appData.category,
                        version = appData.version,
                        hasAds = appData.hasAds,
                        versionCode = appData.versionCode,
                        changelog = appData.changelog,
                        videoUrl = appData.videoUrl,
                        isUpdateSubmission = true
                    ) { success, msg ->
                        Toast.makeText(context, msg ?: "Update request dispatched", Toast.LENGTH_SHORT).show()
                        if (success) appSubmittingUpdateFor = null
                    }
                }
            )
        }

        if (showSendNoticeForm) {
            SendNoticeFormDialog(
                apps = apps,
                onDismiss = { showSendNoticeForm = false },
                onSubmit = { notice, fcmServerKey ->
                    viewModel.sendNotice(notice, fcmServerKey) { success, msg ->
                        Toast.makeText(context, msg ?: "Announcement published", Toast.LENGTH_LONG).show()
                        if (success) {
                            showSendNoticeForm = false
                        }
                    }
                }
            )
        }

        if (submissionToApprove != null) {
            val sub = submissionToApprove!!
            Dialog(
                onDismissRequest = { submissionToApprove = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        // Header with icon
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(accentGreen.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentGreen, modifier = Modifier.size(28.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Approve App", fontWeight = FontWeight.ExtraBold, color = textPrimary, fontSize = 20.sp)
                                Text(sub.name, color = textSecondary, fontSize = 13.sp, maxLines = 1)
                            }
                            IconButton(onClick = { submissionToApprove = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(color = cardBorderColor.copy(alpha = 0.6f))

                        Text(
                            text = "Optional feedback is saved and shown to the developer after approval.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = approvalFeedback,
                            onValueChange = { approvalFeedback = it },
                            placeholder = { Text("e.g. Looks great — welcome to the catalog!", color = textSecondary.copy(alpha = 0.5f), fontSize = 13.sp) },
                            singleLine = false,
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                unfocusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                focusedIndicatorColor = accentGreen,
                                unfocusedIndicatorColor = cardBorderColor,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                cursorColor = accentGreen
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("approval_feedback_input")
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { submissionToApprove = null },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("cancel_approve_btn"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Text("Cancel", color = textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    viewModel.approveSubmission(sub, feedback = approvalFeedback.ifBlank { "Approved and published inside Dark Store catalog." }) { success, msg ->
                                        Toast.makeText(context, msg ?: "Submission Approved", Toast.LENGTH_SHORT).show()
                                        submissionToApprove = null
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("confirm_approve_btn"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        if (submissionToReject != null) {
            val sub = submissionToReject!!
            Dialog(
                onDismissRequest = { submissionToReject = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Reject App", fontWeight = FontWeight.ExtraBold, color = textPrimary, fontSize = 20.sp)
                                Text(sub.name, color = textSecondary, fontSize = 13.sp, maxLines = 1)
                            }
                            IconButton(onClick = { submissionToReject = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(color = cardBorderColor.copy(alpha = 0.6f))

                        Text(
                            text = "Tell the developer why this submission was declined. They will see this message.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        OutlinedTextField(
                            value = rejectionReason,
                            onValueChange = { rejectionReason = it },
                            placeholder = { Text("e.g. Missing privacy policy / malware scan failed…", color = textSecondary.copy(alpha = 0.5f), fontSize = 13.sp) },
                            singleLine = false,
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                unfocusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                focusedIndicatorColor = Color(0xFFEF4444),
                                unfocusedIndicatorColor = cardBorderColor,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                cursorColor = Color(0xFFEF4444)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("rejection_reason_input")
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { submissionToReject = null },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("cancel_reject_btn"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Text("Cancel", color = textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    viewModel.rejectSubmission(sub, reason = rejectionReason.ifBlank { "Submission did not satisfy safety regulations." }) { success, msg ->
                                        Toast.makeText(context, msg ?: "Submission Rejected", Toast.LENGTH_SHORT).show()
                                        submissionToReject = null
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("confirm_reject_btn"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reject", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        if (isSuspensionDialogActive && appToManageSuspension != null) {
            val app = appToManageSuspension!!
            val isCurrentlySuspended = app.isSuspended
            val accent = if (isCurrentlySuspended) Color(0xFF10B981) else Color(0xFFEF4444)
            Dialog(
                onDismissRequest = { isSuspensionDialogActive = false; appToManageSuspension = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isCurrentlySuspended) Icons.Default.PlayArrow else Icons.Default.Block,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isCurrentlySuspended) "Resume App" else "Suspend App",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary,
                                    fontSize = 20.sp
                                )
                                Text(app.name, color = textSecondary, fontSize = 13.sp, maxLines = 1)
                            }
                            IconButton(
                                onClick = { isSuspensionDialogActive = false; appToManageSuspension = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(color = cardBorderColor.copy(alpha = 0.6f))

                        Text(
                            text = if (isCurrentlySuspended)
                                "This will make the app visible and downloadable in the store again."
                            else
                                "Suspended apps stay in the catalog but are hidden from normal browsing and downloads.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        if (!isCurrentlySuspended) {
                            OutlinedTextField(
                                value = suspensionDialogReason,
                                onValueChange = { suspensionDialogReason = it },
                                placeholder = { Text("Reason (optional)", color = textSecondary.copy(alpha = 0.5f), fontSize = 13.sp) },
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                    unfocusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                    focusedIndicatorColor = accent,
                                    unfocusedIndicatorColor = cardBorderColor,
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    cursorColor = accent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { isSuspensionDialogActive = false; appToManageSuspension = null },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Text("Cancel", color = textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    val targetState = !isCurrentlySuspended
                                    viewModel.suspendApp(app.id, targetState, suspensionDialogReason) { success, msg ->
                                        Toast.makeText(context, msg ?: if (success) "Updated" else "Failed", Toast.LENGTH_SHORT).show()
                                        isSuspensionDialogActive = false
                                        appToManageSuspension = null
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Text(
                                    if (isCurrentlySuspended) "Resume" else "Suspend",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showPushUpdateFormFor != null) {
            val app = showPushUpdateFormFor!!
            var verInput by remember { mutableStateOf(app.version) }
            var apkInput by remember { mutableStateOf(app.apkUrl) }
            var descInput by remember { mutableStateOf(app.description) }

            Dialog(onDismissRequest = { showPushUpdateFormFor = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Push App Version Update",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = textPrimary
                        )
                        Text(
                            text = "Update the software version name, release logs (changelog details), and APK links. This will immediately update the live application in the store catalog.",
                            fontSize = 11.sp,
                            color = textSecondary,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = cardBorderColor)

                        OutlinedTextField(
                            value = verInput,
                            onValueChange = { verInput = it },
                            label = { Text("Version Name *") },
                            placeholder = { Text("e.g. v2.1.0") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                focusedBorderColor = accentGreen,
                                unfocusedBorderColor = cardBorderColor
                            )
                        )

                        OutlinedTextField(
                            value = apkInput,
                            onValueChange = { apkInput = it },
                            label = { Text("APK Download URL *") },
                            placeholder = { Text("e.g. https://...") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                focusedBorderColor = accentGreen,
                                unfocusedBorderColor = cardBorderColor
                            )
                        )

                        OutlinedTextField(
                            value = descInput,
                            onValueChange = { descInput = it },
                            label = { Text("Changelog / Description *") },
                            placeholder = { Text("What's new in this release...") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                focusedBorderColor = accentGreen,
                                unfocusedBorderColor = cardBorderColor
                            )
                        )

                        HorizontalDivider(color = cardBorderColor)

                        Button(
                            onClick = {
                                if (verInput.isNotBlank() && apkInput.isNotBlank() && descInput.isNotBlank()) {
                                    val originalApp = apps.find { it.id == app.id }
                                    if (originalApp != null) {
                                        val updatedApp = originalApp.copy(
                                            version = verInput.trim(),
                                            apkUrl = apkInput.trim(),
                                            description = descInput.trim()
                                        )
                                        viewModel.addOrUpdateAppInCatalog(updatedApp) { success ->
                                            if (success) {
                                                Toast.makeText(context, "Version update successfully deployed!", Toast.LENGTH_SHORT).show()
                                                viewModel.refreshMarketplace(force = true)
                                                showPushUpdateFormFor = null
                                            } else {
                                                Toast.makeText(context, "Failed to deploy version update.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Error: App entity not found.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "All fields are required.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("DEPLOY UPDATE INSTANTLY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = { showPushUpdateFormFor = null },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Cancel", color = textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (showUpdateScreenshotsFormFor != null) {
            val app = showUpdateScreenshotsFormFor!!
            val existingScreens = remember(app) {
                val list = app.screenshots.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                List(6) { index -> if (index < list.size) list[index] else "" }
            }

            var s1 by remember { mutableStateOf(existingScreens[0]) }
            var s2 by remember { mutableStateOf(existingScreens[1]) }
            var s3 by remember { mutableStateOf(existingScreens[2]) }
            var s4 by remember { mutableStateOf(existingScreens[3]) }
            var s5 by remember { mutableStateOf(existingScreens[4]) }
            var s6 by remember { mutableStateOf(existingScreens[5]) }

            val ssUploadingStates = remember { mutableStateListOf(false, false, false, false, false, false) }
            var targetSlot by remember { mutableStateOf(-1) }
            val coroutineScope = rememberCoroutineScope()

            val pickScreenshotLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null && targetSlot in 0..5) {
                    val slot = targetSlot
                    ssUploadingStates[slot] = true
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()
                            if (bytes != null) {
                                val url = uploadImageToImgBB(context, bytes) { errorMsg ->
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                    }
                                }
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    ssUploadingStates[slot] = false
                                    if (url != null) {
                                        when (slot) {
                                            0 -> s1 = url
                                            1 -> s2 = url
                                            2 -> s3 = url
                                            3 -> s4 = url
                                            4 -> s5 = url
                                            5 -> s6 = url
                                        }
                                        Toast.makeText(context, "Screenshot ${slot + 1} uploaded successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    ssUploadingStates[slot] = false
                                }
                            }
                        } catch (ex: Exception) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                ssUploadingStates[slot] = false
                                Toast.makeText(context, "Failure: ${ex.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            Dialog(onDismissRequest = { showUpdateScreenshotsFormFor = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Update Application Screenshots",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = textPrimary
                        )
                        Text(
                            text = "Upload up to 6 screenshots (minimum 3 required) to show off your application on the storefront catalog detail panels.",
                            fontSize = 11.sp,
                            color = textSecondary,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = cardBorderColor)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val slots = listOf(s1, s2, s3, s4, s5, s6)
                            for (i in 0 until 6 step 3) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (j in i until i + 3) {
                                        val slotUrl = slots[j]
                                        val isUploading = ssUploadingStates[j]

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.6f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(textSecondary.copy(alpha = 0.08f))
                                                .border(1.dp, cardBorderColor, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    targetSlot = j
                                                    pickScreenshotLauncher.launch("image/*")
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isUploading) {
                                                CircularProgressIndicator(color = accentGreen, modifier = Modifier.size(24.dp))
                                            } else if (slotUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = slotUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(18.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                                                        .clickable {
                                                            when (j) {
                                                                0 -> s1 = ""
                                                                1 -> s2 = ""
                                                                2 -> s3 = ""
                                                                3 -> s4 = ""
                                                                4 -> s5 = ""
                                                                5 -> s6 = ""
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                                }
                                            } else {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text("Slot ${j + 1}", fontSize = 9.sp, color = textSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = cardBorderColor)

                        Button(
                            onClick = {
                                val screenshotUrls = listOf(s1, s2, s3, s4, s5, s6).map { it.trim() }.filter { it.isNotEmpty() }
                                if (screenshotUrls.size < 3) {
                                    Toast.makeText(context, "Please provide at least 3 screenshots.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val finalScreenshotsStr = screenshotUrls.joinToString(",")

                                val originalApp = apps.find { it.id == app.id }
                                if (originalApp != null) {
                                    val updatedApp = originalApp.copy(screenshots = finalScreenshotsStr)
                                    viewModel.addOrUpdateAppInCatalog(updatedApp) { success ->
                                        if (success) {
                                            Toast.makeText(context, "Screenshots successfully updated!", Toast.LENGTH_SHORT).show()
                                            viewModel.refreshMarketplace(force = true)
                                            showUpdateScreenshotsFormFor = null
                                        } else {
                                            Toast.makeText(context, "Failed to update screenshots.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Error: App entity not found.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("SAVE SCREENSHOTS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = { showUpdateScreenshotsFormFor = null },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Cancel", color = textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (userToEdit != null) {
            val user = userToEdit!!
            var role by remember { mutableStateOf(user.role) }
            var isDev by remember { mutableStateOf(user.isDeveloper) }
            var devNameLocal by remember { mutableStateOf(user.devName) }
            var displayNameLocal by remember { mutableStateOf(user.displayName) }
            var isSaving by remember { mutableStateOf(false) }

            Dialog(
                onDismissRequest = { userToEdit = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(accentGreen.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    user.displayName.ifBlank { user.email }.take(1).uppercase(),
                                    color = accentGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Edit User", fontWeight = FontWeight.ExtraBold, color = textPrimary, fontSize = 20.sp)
                                Text(user.email, color = textSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { userToEdit = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }

                        HorizontalDivider(color = cardBorderColor.copy(alpha = 0.6f))

                        OutlinedTextField(
                            value = displayNameLocal,
                            onValueChange = { displayNameLocal = it },
                            label = { Text("Display name", color = textSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                unfocusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                focusedIndicatorColor = accentGreen,
                                unfocusedIndicatorColor = cardBorderColor,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = devNameLocal,
                            onValueChange = { devNameLocal = it },
                            label = { Text("Developer name", color = textSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                unfocusedContainerColor = textSecondary.copy(alpha = 0.04f),
                                focusedIndicatorColor = accentGreen,
                                unfocusedIndicatorColor = cardBorderColor,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Role chips
                        Text("Role", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("user", "admin").forEach { r ->
                                val selected = role == r
                                val chipColor = if (r == "admin") Color(0xFFEF4444) else Color(0xFF3B82F6)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) chipColor.copy(alpha = 0.15f) else textSecondary.copy(alpha = 0.06f))
                                        .border(1.dp, if (selected) chipColor.copy(alpha = 0.5f) else cardBorderColor, RoundedCornerShape(12.dp))
                                        .clickable { role = r }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        r.uppercase(),
                                        color = if (selected) chipColor else textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Developer toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(textSecondary.copy(alpha = 0.04f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Developer access", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Can submit apps for review", color = textSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isDev,
                                onCheckedChange = { isDev = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = accentGreen)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { userToEdit = null },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, cardBorderColor)
                            ) {
                                Text("Cancel", color = textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    isSaving = true
                                    val updatedUser = user.copy(
                                        displayName = displayNameLocal,
                                        devName = devNameLocal,
                                        isDeveloper = isDev,
                                        role = role
                                    )
                                    viewModel.updateUserAdmin(updatedUser) { success ->
                                        isSaving = false
                                        Toast.makeText(context, if (success) "User updated" else "Update failed", Toast.LENGTH_SHORT).show()
                                        if (success) userToEdit = null
                                    }
                                },
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

// ========================================================
// ========================================================
// ADMIN SUBMISSION CARD — Compact, grouped, action-ready
// ========================================================
@Composable
fun AdminSubmissionCard(
    sub: SubmissionEntity,
    isAdmin: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    statusColor: Color,
    statusIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onCardClick: () -> Unit = {}
) {
    // Distinct look per request type so the admin can tell them apart at a glance:
    // new-app request / update request / approved app / approved update / rejected.
    val isUpd = sub.isUpdateSubmission
    val stLow = sub.status.lowercase()
    val kindColor: Color; val kindIcon: androidx.compose.ui.graphics.vector.ImageVector; val kindLabel: String
    when {
        stLow == "rejected" -> { kindColor = Color(0xFFEF4444); kindIcon = Icons.Default.Cancel; kindLabel = if (isUpd) "REJECTED UPDATE" else "REJECTED APP" }
        stLow == "approved" || stLow == "live" -> if (isUpd) { kindColor = Color(0xFF06B6D4); kindIcon = Icons.Default.ArrowUpward; kindLabel = "APPROVED UPDATE" } else { kindColor = Color(0xFF10B981); kindIcon = Icons.Default.CheckCircle; kindLabel = "APPROVED APP" }
        isUpd -> { kindColor = Color(0xFFF59E0B); kindIcon = Icons.Default.Refresh; kindLabel = "UPDATE REQUEST" }
        else -> { kindColor = Color(0xFF3B82F6); kindIcon = Icons.Default.AddCircle; kindLabel = "NEW APP REQUEST" }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kindColor.copy(alpha = 0.07f).compositeOver(cardBgColor)),
        border = BorderStroke(1.5.dp, kindColor.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Coloured request-type ribbon
            Row(
                modifier = Modifier.fillMaxWidth().background(kindColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(kindIcon, contentDescription = null, tint = kindColor, modifier = Modifier.size(14.dp))
                Text(kindLabel, color = kindColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
            }
            // Top row: icon + info + status badge
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(accentGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (sub.logo.isNotBlank()) {
                        AsyncImage(model = sub.logo, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        Text(sub.name.take(1).uppercase(), color = accentGreen, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(sub.name, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text("v${sub.version}  •  ${sub.category}  •  ${sub.developer}", color = textSecondary, fontSize = 10.sp, maxLines = 1)
                    Text(sub.submittedBy, color = textSecondary.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
                }
                // Status badge
                Box(
                    modifier = Modifier
                        .background(kindColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(kindIcon, contentDescription = null, tint = kindColor, modifier = Modifier.size(12.dp))
                        Text(kindLabel, color = kindColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // Package name
            Text(sub.packageName, color = textSecondary.copy(alpha = 0.5f), fontSize = 10.sp)

            // Feedback if rejected
            if (sub.status == "Rejected" && sub.feedback.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.07f))
                ) {
                    Text(
                        text = "Reason: ${sub.feedback}",
                        modifier = Modifier.padding(8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            if (sub.status == "Approved" && sub.feedback.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.07f))
                ) {
                    Text(
                        text = "Feedback: ${sub.feedback}",
                        modifier = Modifier.padding(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Admin action buttons
            if (isAdmin) {
                HorizontalDivider(color = cardBorderColor.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Approve
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        enabled = sub.status != "Approved"
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    // Reject
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        enabled = sub.status != "Rejected"
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", fontSize = 11.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                    // Edit
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, cardBorderColor),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = accentGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 11.sp, color = accentGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SendNoticeFormDialog(
    apps: List<AppEntity>,
    onDismiss: () -> Unit,
    onSubmit: (com.example.data.NoticeEntity, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var targetAppId by remember { mutableStateOf("all") }
    var bannerColor by remember { mutableStateOf("#C62828") }
    var bannerFont by remember { mutableStateOf("default") }
    var isUploading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val sharedPrefs = remember { context.getSharedPreferences("dark_store_fcm_prefs", android.content.Context.MODE_PRIVATE) }
    // SECURITY: no hard-coded key and nothing persisted - held in memory only while typing.
    var fcmServerKey by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes == null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isUploading = false
                            Toast.makeText(context, "Failed to read image bytes.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val contentType = "image/jpeg"
                    val fileName = "notice_${System.currentTimeMillis()}.jpg"
                    val uploadedUrl = uploadImageToImgBB(context, bytes) { errorMsg ->
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isUploading = false
                        if (uploadedUrl != null) {
                            imageUrl = uploadedUrl
                            Toast.makeText(context, "Notice photo uploaded successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isUploading = false
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151922)),
            border = BorderStroke(1.dp, Color(0xFF283141))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Broadcast System Announcement",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color(0xFF00AAFF),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Send a localized push notification & announcement notice to client environments.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth().testTag("notice_title_field"),
                        label = { Text("Announcement Title", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00AAFF),
                            unfocusedBorderColor = Color(0xFF283141)
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("notice_message_field"),
                        label = { Text("Announcement Message", color = Color.Gray) },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00AAFF),
                            unfocusedBorderColor = Color(0xFF283141)
                        )
                    )
                }

                item {
                    Text("Target Audience Segment", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { targetAppId = "all" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetAppId == "all") Color(0xFF00AAFF) else Color(0xFF1E2633)
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("All Users", fontSize = 10.sp, color = Color.White)
                        }
                        
                        Button(
                            onClick = { 
                                if (apps.isNotEmpty()) {
                                    targetAppId = apps.first().packageName
                                } else {
                                    targetAppId = "all"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetAppId != "all" && targetAppId != "critical_announcement") Color(0xFF00AAFF) else Color(0xFF1E2633)
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Target App", fontSize = 10.sp, color = Color.White)
                        }

                        Button(
                            onClick = { targetAppId = "critical_announcement" },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetAppId == "critical_announcement") Color(0xFFEF5350) else Color(0xFF1E2633)
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Alert", tint = Color.White, modifier = Modifier.size(10.dp))
                                Text("Alert Banner", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }

                if (targetAppId != "all" && targetAppId != "critical_announcement" && apps.isNotEmpty()) {
                    item {
                        var expandedDropDown by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val selectedAppName = apps.find { it.packageName == targetAppId }?.name ?: targetAppId
                            Button(
                                onClick = { expandedDropDown = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2633))
                            ) {
                                Text("Selected: $selectedAppName", color = Color.White, fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = expandedDropDown,
                                onDismissRequest = { expandedDropDown = false },
                                modifier = Modifier.background(Color(0xFF151922))
                            ) {
                                apps.forEach { app ->
                                    DropdownMenuItem(
                                        text = { Text(app.name, color = Color.White) },
                                        onClick = {
                                            targetAppId = app.packageName
                                            expandedDropDown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (targetAppId == "critical_announcement") {
                    item {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Alert banner style", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                com.example.view.BannerStyles.colors.forEach { (_, hexCode) ->
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(com.example.view.BannerStyles.color(hexCode))
                                            .border(if (bannerColor.equals(hexCode, true)) 3.dp else 0.dp, Color.White, CircleShape)
                                            .clickable { bannerColor = hexCode }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.example.view.BannerStyles.fonts.forEach { (key, label) ->
                                    val on = bannerFont == key
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (on) Color(0xFF00AAFF) else Color(0xFF1E2633))
                                            .clickable { bannerFont = key }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(label, color = Color.White, fontSize = 12.sp,
                                            fontFamily = com.example.view.BannerStyles.font(key),
                                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                            // Live preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(com.example.view.BannerStyles.color(bannerColor))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(
                                        (title.ifBlank { "Alert title" }).uppercase(), color = Color.White, fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = com.example.view.BannerStyles.font(bannerFont)
                                    )
                                    Text(
                                        message.ifBlank { "This is how the banner will look at the top of the app." },
                                        color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp,
                                        fontFamily = com.example.view.BannerStyles.font(bannerFont)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add Notice Photo Link / Uploaded Media", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            modifier = Modifier.fillMaxWidth().testTag("notice_image_field"),
                            placeholder = { Text("https://image-link-url.com/photo.jpg", color = Color.DarkGray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00AAFF),
                                unfocusedBorderColor = Color(0xFF283141)
                            )
                        )
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isUploading,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF283141), contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Upload Photo", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUploading) " uploading photo..." else "Choose Photo Uploader", fontSize = 11.sp)
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Firebase Cloud Messaging Options", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = fcmServerKey,
                            onValueChange = { fcmServerKey = it },
                            modifier = Modifier.fillMaxWidth().testTag("notice_fcm_key_field"),
                            label = { Text("FCM Legacy Server Key", color = Color.Gray) },
                            placeholder = { Text("Enter Server Key from Firebase settings", color = Color.DarkGray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00AAFF),
                                unfocusedBorderColor = Color(0xFF283141)
                            )
                        )
                        Text(
                            text = "Enable Legacy Cloud Messaging API in Google Firebase Console. If configured, a push alert will be broadcasted to all terminals.",
                            fontSize = 10.sp,
                            color = Color.LightGray.copy(alpha = 0.6f)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.Gray),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                        ) {
                            Text("CANCEL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        
                        Button(
                            onClick = {
                                if (title.isBlank() || message.isBlank()) {
                                    Toast.makeText(context, "Fields cannot be blank!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val newNotice = com.example.data.NoticeEntity(
                                    id = "ntc_" + System.currentTimeMillis(),
                                    title = title,
                                    message = message,
                                    imageUrl = imageUrl,
                                    timestamp = System.currentTimeMillis(),
                                    targetAppId = targetAppId,
                                    bannerColor = if (targetAppId == "critical_announcement") bannerColor else "",
                                    bannerFont = if (targetAppId == "critical_announcement") bannerFont else ""
                                )
                                onSubmit(newNotice, fcmServerKey)
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AAFF))
                        ) {
                            Text("PUBLISH & PUSH", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun com.example.data.AppEntity.toSubmissionEntity() = com.example.data.SubmissionEntity(
    id = id,
    name = name,
    packageName = packageName,
    description = description,
    apkUrl = apkUrl,
    screenshots = screenshots,
    category = category,
    version = version,
    logo = logo,
    developer = developer,
    status = if (isApproved) "Approved" else "Pending",
    submittedBy = submittedBy,
    hasAds = hasAds
)

@Composable
private fun AdminStepper(label: String, value: Int, range: IntRange, textPrimary: Color, textSecondary: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = textSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("-") }
        Text("$value", color = textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 10.dp))
        OutlinedButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("+") }
    }
}
