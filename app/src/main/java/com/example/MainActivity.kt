@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example

import android.app.Application
import android.os.Bundle
import com.example.view.FullScreenPhotoViewer
import com.example.view.ambientGlow
import com.example.view.glass
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

val LocalUninstallEnabled = compositionLocalOf { false }

class MainActivity : ComponentActivity() {
    private var packageReceiver: android.content.BroadcastReceiver? = null
    private var lastInstalledAppsRefresh = 0L

    private val viewModel: StoreViewModel by viewModels {
        StoreViewModel.Factory(applicationContext as Application)
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (now - lastInstalledAppsRefresh > 30_000L) {
            viewModel.refreshInstalledApps()
            lastInstalledAppsRefresh = now
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission enabled for Dark Store!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notifications disabled. Standard tray updates will be hidden.", Toast.LENGTH_LONG).show()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (readGranted) {
            Toast.makeText(this, "System storage access granted!", Toast.LENGTH_SHORT).show()
        }
        com.example.widget.DarkStoreWidget.updateAllWidgets(this)
    }

    override fun onStart() {
        super.onStart()
        com.example.utils.ChatPushState.appInForeground = true
    }

    override fun onStop() {
        com.example.utils.ChatPushState.appInForeground = false
        super.onStop()
    }

    override fun onDestroy() {
        packageReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Optimize Coil Image Loading cache to maximize interface smoothness
        try {
            val imageLoader = ImageLoader.Builder(applicationContext)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25) // use up to 25% of available RAM 
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.04) // reasonable portion of disk cache
                        .build()
                }
                .crossfade(true)
                .respectCacheHeaders(false) // reuse local cache ignoring remote HTTP override headers
                .build()
            coil.Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to configure custom Coil cache: ${e.message}")
        }
        
        NotificationHelper.createNotificationChannel(applicationContext)

        // NOTE: previously started a permanently-running foreground "sync" service
        // here that polled every 60s forever just to check for new notices, with
        // its own persistent notification icon. That's the exact kind of always-on
        // background CPU/battery use that's uncomfortable on mid-range devices
        // (Redmi/Samsung etc, which are also aggressive about killing background
        // processes anyway). Removed — notices now arrive via FCM push instead,
        // and app downloads keep running in the background via their own
        // short-lived DownloadForegroundService (only alive while a download is
        // actually in progress).

        // Request modern POST_NOTIFICATIONS permission gracefully for Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request storage system permissions for accurate storage capacity reading
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            val storageNeeded = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storageNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storageNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (storageNeeded.isNotEmpty()) {
                requestStoragePermissionLauncher.launch(storageNeeded.toTypedArray())
            }
        }

        enableEdgeToEdge()
        
        // Register dynamic package installation BroadcastReceiver
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent?) {
                val packageName = intent?.data?.schemeSpecificPart
                if (packageName != null) {
                    android.util.Log.d("MainActivity", "App update detected: ${intent.action} for $packageName")
                    viewModel.refreshMarketplace()
                    viewModel.refreshInstalledApps()
                    com.example.widget.DarkStoreWidget.updateAllWidgets(context)

                    if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                        val appsList = viewModel.apps.value
                        val matchedApp = appsList.find { it.packageName == packageName }
                        val appName = matchedApp?.name ?: "Application"

                        // Clear download record from the download queue so it doesn't linger
                        lifecycleScope.launch {
                            matchedApp?.let {
                                viewModel.cancelDownload(it.id)
                            }
                        }

                        // Send high-priority installation success notification!
                        NotificationHelper.showInstallSuccess(
                            context,
                            appName,
                            packageName.hashCode()
                        )
                    }
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            packageReceiver = receiver
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to register package dynamic receiver: ${e.message}", e)
        }

        // Force widget update with real storage figures on app startup
        com.example.widget.DarkStoreWidget.updateAllWidgets(applicationContext)

        setContent {
            val context = LocalContext.current
            var showSplash by remember { mutableStateOf(true) }
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val isAmoledMode by viewModel.isAmoledMode.collectAsStateWithLifecycle()
            val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
            val isTermsAccepted by viewModel.isTermsAccepted.collectAsStateWithLifecycle()
            val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
            val userName by viewModel.userName.collectAsStateWithLifecycle()
            val showEmailVerificationPrompt by viewModel.showEmailVerificationPrompt.collectAsStateWithLifecycle()

            // State for update checks
            var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }

            // Automatic background update checking on launch
            LaunchedEffect(Unit) {
                val prefs = context.getSharedPreferences("dark_store_pref", android.content.Context.MODE_PRIVATE)
                val cachedCode = prefs.getInt("cached_latest_version_code", 0)
                val cachedName = prefs.getString("cached_latest_version_name", "") ?: ""
                val cachedUrl = prefs.getString("cached_apk_download_url", "") ?: ""
                val cachedTitle = prefs.getString("cached_update_title", "") ?: ""
                val cachedMessage = prefs.getString("cached_update_message", "") ?: ""
                val cachedForce = prefs.getBoolean("cached_force_update", false)
                
                val currentCode = getInstalledVersionCode(context)
                val currentName = getInstalledVersionName(context)
                
                // If a cached forced update is already known, immediately pre-set state
                val isCachedUpdateNeeded = (cachedCode > currentCode) || 
                        (cachedName.isNotBlank() && cachedName != currentName)
                if (isCachedUpdateNeeded && cachedForce) {
                    updateState = UpdateState.UpdateRequired(
                        latestVersionCode = cachedCode,
                        latestVersionName = cachedName,
                        apkDownloadUrl = cachedUrl,
                        updateTitle = cachedTitle,
                        updateMessage = cachedMessage,
                        forceUpdate = cachedForce,
                        offlineMode = !isNetworkAvailable(context)
                    )
                }
                
                try {
                    val config = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.data.FirebaseService.fetchUpdateConfig()
                    }
                    if (config != null) {
                        // Update cache
                        prefs.edit().apply {
                            putInt("cached_latest_version_code", config.latestVersionCode)
                            putString("cached_latest_version_name", config.latestVersionName)
                            putString("cached_apk_download_url", config.apkDownloadUrl)
                            putString("cached_update_title", config.updateTitle)
                            putString("cached_update_message", config.updateMessage)
                            putBoolean("cached_force_update", config.forceUpdate)
                            apply()
                        }
                        
                        val isUpdateNeeded = (config.latestVersionCode > currentCode) || 
                                (config.latestVersionName.isNotBlank() && config.latestVersionName != currentName)
                        if (isUpdateNeeded) {
                            updateState = UpdateState.UpdateRequired(
                                latestVersionCode = config.latestVersionCode,
                                latestVersionName = config.latestVersionName,
                                apkDownloadUrl = config.apkDownloadUrl,
                                updateTitle = config.updateTitle,
                                updateMessage = config.updateMessage,
                                forceUpdate = config.forceUpdate,
                                offlineMode = false
                            )
                        } else {
                            updateState = UpdateState.NoUpdateNeeded
                        }
                    } else {
                        // Offline or error loading update config
                        if (isCachedUpdateNeeded) {
                            if (cachedForce) {
                                updateState = UpdateState.UpdateRequired(
                                    latestVersionCode = cachedCode,
                                    latestVersionName = cachedName,
                                    apkDownloadUrl = cachedUrl,
                                    updateTitle = cachedTitle,
                                    updateMessage = cachedMessage,
                                    forceUpdate = cachedForce,
                                    offlineMode = true
                                )
                            } else {
                                updateState = UpdateState.NoUpdateNeeded
                            }
                        } else {
                            updateState = UpdateState.NoUpdateNeeded
                        }
                    }
                } catch (e: Exception) {
                    if (isCachedUpdateNeeded && cachedForce) {
                        updateState = UpdateState.UpdateRequired(
                            latestVersionCode = cachedCode,
                            latestVersionName = cachedName,
                            apkDownloadUrl = cachedUrl,
                            updateTitle = cachedTitle,
                            updateMessage = cachedMessage,
                            forceUpdate = cachedForce,
                            offlineMode = true
                        )
                    } else {
                        updateState = UpdateState.NoUpdateNeeded
                    }
                }
            }

            // Fade out splash shortly
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2200)
                showSplash = false
            }

            // Centralized Google Play theme controller
            MaterialTheme(
                colorScheme = if (isDarkMode) darkColorScheme(
                    primary = Color(0xFF34D399),
                    background = if (isAmoledMode) Color.Black else Color(0xFF111111),
                    surface = if (isAmoledMode) Color.Black else Color(0xFF1F1F1F)
                ) else lightColorScheme(
                    primary = Color(0xFF01875F),
                    background = Color(0xFFF8F9FA),
                    surface = Color(0xFFFFFFFF)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkMode) (if (isAmoledMode) Color.Black else Color(0xFF111111)) else Color(0xFFF8F9FA)
                ) {
                    AnimatedContent(
                        targetState = showSplash,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith
                            fadeOut(animationSpec = tween(400))
                        },
                        label = "SplashToDashboard"
                    ) { isSplash ->
                        if (isSplash) {
                            PlayStoreSplashScreen(onSkip = { showSplash = false })
                        } else {
                            when {
                                updateState is UpdateState.Checking -> {
                                    // Lightweight loading screen
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF0B0D12)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = Color(0xFF34D399))
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Checking for updates...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                // Forced updates still block the whole app full-screen — there's
                                // no way to use the app on an unsupported version.
                                updateState is UpdateState.UpdateRequired && (updateState as UpdateState.UpdateRequired).forceUpdate -> {
                                    UpdateRequiredScreen(
                                        update = updateState as UpdateState.UpdateRequired,
                                        context = context,
                                        onSkip = {
                                            updateState = UpdateState.NoUpdateNeeded
                                        }
                                    )
                                }
                                // Everything else (no update, OR an optional/non-forced update):
                                // the app itself renders normally and stays fully usable.
                                else -> {
                                    if (!isLoggedIn) {
                                        com.example.view.AuthScreen(
                                            viewModel = viewModel
                                        )
                                    } else if (showEmailVerificationPrompt) {
                                        com.example.view.EmailVerificationScreen(
                                            viewModel = viewModel,
                                            userEmail = userEmail,
                                            onDismiss = { viewModel.dismissEmailVerificationPrompt() }
                                        )
                                    } else if (!isTermsAccepted && userEmail.isNotBlank() && userEmail != "guest@darkroot.io") {
                                        com.example.view.TermsAgreementDialog(
                                            viewModel = viewModel,
                                            onAccept = {
                                                viewModel.markTermsAcceptedForEmail(userEmail, userName)
                                            }
                                        )
                                    } else {
                                        PlayStoreMainDashboard(
                                            viewModel = viewModel,
                                            isDarkMode = isDarkMode,
                                            onThemeToggle = { viewModel.setDarkMode(!isDarkMode) }
                                        )
                                    }

                                    // A non-forced update is available: show it as a dismissible
                                    // popup on top of the (still fully usable) app, instead of
                                    // blocking the whole screen like a forced update does.
                                    val optionalUpdate = updateState as? UpdateState.UpdateRequired
                                    if (optionalUpdate != null && !optionalUpdate.forceUpdate) {
                                        OptionalUpdateDialog(
                                            update = optionalUpdate,
                                            context = context,
                                            onDismiss = {
                                                updateState = UpdateState.NoUpdateNeeded
                                            }
                                        )
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

// ========================================================
// 1. CHIC GOOGLE PLAY SPLASH SCREEN
// ========================================================
@Composable
fun PlayStoreSplashScreen(onSkip: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D12)) // Matrix dark slate void
            .clickable { onSkip() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                // Outer Alarm Ring 1: Soft radial glow background Aura
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(1.0f)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF00AAFF).copy(alpha = 0.5f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                        .alpha(glowAlpha)
                )

                // Outer Alarm Ring 2: Broadcast/pulse wave circle (Thin tech cyan)
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .scale(1.05f)
                        .border(
                            width = 1.dp,
                            color = Color(0xFF00AAFF).copy(alpha = glowAlpha),
                            shape = CircleShape
                        )
                )

                // Outer Alarm Ring 3: Broadcast wave inner circle
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .scale(0.9f)
                        .border(
                            width = 1.5.dp,
                            color = Color(0xFF00D2FF).copy(alpha = glowAlpha * 1.3f),
                            shape = CircleShape
                        )
                )

                // Rotating Alarm Clock/Timer dial ticks (with absolutely no solid card/box background)
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .rotate(rotationAngle)
                        .drawBehind {
                            val strokeWidth = 3f
                            val color = Color(0xFF00AAFF).copy(alpha = 0.85f)
                            val numTicks = 32
                            val radius = size.minDimension / 2
                            val tickLength = 12f
                            for (i in 0 until numTicks) {
                                val angleDeg = (i * 360f / numTicks)
                                val angleRad = Math.toRadians(angleDeg.toDouble())
                                val startX = (center.x + (radius - tickLength) * Math.cos(angleRad)).toFloat()
                                val startY = (center.y + (radius - tickLength) * Math.sin(angleRad)).toFloat()
                                val endX = (center.x + radius * Math.cos(angleRad)).toFloat()
                                val endY = (center.y + radius * Math.sin(angleRad)).toFloat()
                                drawLine(
                                    color = color,
                                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                                    strokeWidth = strokeWidth
                                )
                            }
                        }
                )

                // Beautiful brand logo centered with transparent surrounding background
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(listOf(Color(0xFF007FFF), Color(0xFF00E5FF))),
                            shape = CircleShape
                        )
                        .padding(4.dp), // Space inside the neon glowing ring
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo_new),
                        contentDescription = "Dark Store App Logo Symbol",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape), // Perfect circular crop
                        contentScale = ContentScale.Fit // Prevent stretching/distortion of aspect ratio
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Premium DarkRoot Store main title
            Text(
                text = "DarkRoot Store",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.2.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Premium edition description
            Text(
                text = "MODULAR DISCOVERY & ARCHIVE ACCESS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.8.sp,
                    color = Color(0xFF00D2FF)
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color(0xFF00AAFF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = "DARKROOT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray.copy(alpha = 0.8f),
                letterSpacing = 1.5.sp
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        )
    }
}

// ========================================================
// 2. SHIMMER & ELEGANT IMAGE LOADER (NO SPINNER SYSTEM)
// ========================================================
@Composable
fun ShimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            Color.LightGray.copy(alpha = 0.6f),
            Color.LightGray.copy(alpha = 0.2f),
            Color.LightGray.copy(alpha = 0.6f),
        )

        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
        val translateAnim = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(durationMillis = 1000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "shimmer_anim"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(x = translateAnim.value, y = translateAnim.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset.Zero
        )
    }
}

@Composable
fun ElegantImageLoader(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // PERF: SubcomposeAsyncImage forces an extra subcomposition pass per image
    // (it composes separate loading/success/error slots), which is noticeably
    // more expensive than plain AsyncImage when it's repeated across every row
    // of a scrolling list — this was a real contributor to scroll jank since
    // every app logo on screen paid that cost. Both callers already build their
    // Coil ImageRequest with .placeholder()/.error() drawables set, so AsyncImage
    // shows the same fallback art for free, without subcomposing.
    coil.compose.AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

// ========================================================
// 3. STUNNING DYNAMIC LOGO HANDLER
// ========================================================
@Composable
fun AppLogo(
    logoUrl: String,
    appName: String,
    packageName: String,
    modifier: Modifier = Modifier
) {
    // Override general duplicate placeholders
    val actualUrl = remember(logoUrl, packageName) {
        val hasGenericPlaceholder = logoUrl.isBlank() || 
                                    logoUrl.contains("game-logo.png") || 
                                    logoUrl.contains("img_app_icon") ||
                                    logoUrl.contains("img_app_logo_new")
        
        when {
            hasGenericPlaceholder -> {
                when {
                    packageName.contains("brave", ignoreCase = true) -> 
                        "https://raw.githubusercontent.com/brave/brave-browser/master/assets/brave-logo-color.png"
                    packageName.contains("emulator", ignoreCase = true) || packageName.contains("retro", ignoreCase = true) -> 
                        "https://cdn-icons-png.flaticon.com/512/566/566373.png"
                    packageName.contains("aero", ignoreCase = true) || packageName.contains("music", ignoreCase = true) -> 
                        "https://cdn-icons-png.flaticon.com/512/4612/4612571.png"
                    else -> ""
                }
            }
            else -> logoUrl
        }
    }

    if (actualUrl.isNotEmpty()) {
        val context = LocalContext.current
        val imageRequest = remember(actualUrl, context) {
            coil.request.ImageRequest.Builder(context)
                .data(actualUrl)
                // PERF: crossfade is a ~300ms animated transition that keeps
                // ticking/recomposing for every image while it plays. Slow
                // scrolling only ever has one or two icons loading at a time,
                // so it's invisible — but a fast fling brings a dozen+ new,
                // never-before-seen icons on screen within the same second,
                // each starting its own crossfade animation simultaneously.
                // That's extra concurrent per-frame work stacked exactly when
                // frame budget is already tightest. These are small 54dp list
                // thumbnails, so the fade adds little and costs more than it's
                // worth here.
                .crossfade(false)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .placeholder(R.drawable.img_app_logo_new)
                .error(R.drawable.img_app_logo_new)
                .fallback(R.drawable.img_app_logo_new)
                .build()
        }
        ElegantImageLoader(
            model = imageRequest,
            contentDescription = "$appName Logo",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // Procedurally render an distinct eye-catching logo based on name hashing
        val logoInfo = remember(appName) {
            val nameHash = Math.abs(appName.hashCode())
            val firstChar = appName.trim().take(1).uppercase()
            val gradient = when (nameHash % 5) {
                0 -> Brush.linearGradient(listOf(Color(0xFF0F9D58), Color(0xFF34D399))) // Green
                1 -> Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF64B5F6))) // Blue
                2 -> Brush.linearGradient(listOf(Color(0xFFEA4335), Color(0xFFFF8A80))) // Red
                3 -> Brush.linearGradient(listOf(Color(0xFFFBBC05), Color(0xFFFFD54F))) // Yellow
                else -> Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFBA68C8))) // Purple
            }
            Pair(firstChar, gradient)
        }
        val firstChar = logoInfo.first
        val gradient = logoInfo.second

        Box(
            modifier = modifier
                .background(gradient)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstChar,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ========================================================
// 3. MAIN STORE DASHBOARD LAYOUT
// ========================================================
@Composable
fun PlayStoreMainDashboard(
    viewModel: StoreViewModel,
    isDarkMode: Boolean,
    onThemeToggle: () -> Unit
) {
    // Premium "ad-light catalog": hide the in-store AD badge chrome for
    // members. Details dialog still shows honest "Contains ads" safety text
    // when opening an app so users aren't misled about the APK itself.
    val appsRaw by viewModel.apps.collectAsStateWithLifecycle()
    val unfilteredApps by viewModel.unfilteredApps.collectAsStateWithLifecycle()
    val isPremiumMember by viewModel.isPremiumMember.collectAsStateWithLifecycle()
    val apps = remember(appsRaw, isPremiumMember) {
        if (isPremiumMember) appsRaw.map { a -> if (a.hasAds) a.copy(hasAds = false) else a } else appsRaw
    }
    
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val installedPackages by viewModel.installedPackages.collectAsStateWithLifecycle()
    val installedAppsInfo by viewModel.installedAppsInfo.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val isAmoledMode by viewModel.isAmoledMode.collectAsStateWithLifecycle()
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val userUid by viewModel.userUid.collectAsStateWithLifecycle()
    val submissions by viewModel.submissions.collectAsStateWithLifecycle()
    val isTermsAccepted by viewModel.isTermsAccepted.collectAsStateWithLifecycle()
    val isEcosystemPolicyAccepted by viewModel.isEcosystemPolicyAccepted.collectAsStateWithLifecycle()
    val devName by viewModel.devName.collectAsStateWithLifecycle()
    val isDeveloper by viewModel.isDeveloper.collectAsStateWithLifecycle()
    val premiumTheme by viewModel.premiumTheme.collectAsStateWithLifecycle()
    val developers by viewModel.developers.collectAsStateWithLifecycle()
    val appReviews by viewModel.appReviews.collectAsStateWithLifecycle()
    val isReviewsLoading by viewModel.isReviewsLoading.collectAsStateWithLifecycle()
    val isAdmin = userRole.equals("admin", ignoreCase = true)
    val context = LocalContext.current
    val maintenanceConfig by viewModel.maintenanceConfig.collectAsStateWithLifecycle()

    // Non-admins see a full-screen maintenance message when the store is offline.
    if (maintenanceConfig.isEnabled && !isAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color(0xFF0F0F0F) else Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(64.dp))
                Text(
                    "Under Maintenance",
                    color = if (isDarkMode) Color.White else Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    maintenanceConfig.message,
                    color = if (isDarkMode) Color(0xFFAAAAAA) else Color(0xFF666666),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
        return
    }

    var showOfflineDialog by remember { mutableStateOf(false) }

    var isUninstallEnabled by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isUninstallEnabled = com.example.utils.ApkInstaller.isDeviceAdminActive(context)

                // Nothing in the app previously refreshed data the moment it
                // came back to the foreground — the only thing keeping data
                // "live" was a 12-second background loop tied to the
                // ViewModel's own coroutine scope. That loop can stall or get
                // frozen by the OS while the app is backgrounded (common on
                // battery-aggressive OEM skins), so reopening the app could
                // show stale data for however long is left of whatever cycle
                // it was mid-way through — sometimes close to the full
                // interval. force=true bypasses the normal 5-minute
                // foreground-refresh throttle, so every time the app is
                // brought back to the front, it's guaranteed a fresh fetch
                // right away instead of waiting on the background loop.
                viewModel.refreshMarketplace(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Live chat badge: poll the inbox every few seconds, but ONLY while the app is on
    // screen (STARTED) and signed in — no background polling.
    val chatUnread by viewModel.totalUnread.collectAsStateWithLifecycle()
    val allCollections by viewModel.collections.collectAsStateWithLifecycle()
    val visibleCollections = remember(allCollections) { allCollections.filter { it.isActive && it.appIds.isNotEmpty() } }
    var openCollection by remember { mutableStateOf<com.example.data.CollectionEntity?>(null) }
    LaunchedEffect(isLoggedIn, lifecycleOwner) {
        if (!isLoggedIn) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                viewModel.pollChats()
                kotlinx.coroutines.delay(8_000)
            }
        }
    }

    var activeTab by remember { mutableStateOf("Apps") } // Apps, Games, Library, Console
    var showDetailsApp by remember { mutableStateOf<AppEntity?>(null) }
    val headerPhotoUrl by viewModel.profilePhotoUrl.collectAsStateWithLifecycle()
    val readNoticeIds by viewModel.readNoticeIds.collectAsStateWithLifecycle()
    val allBanners by viewModel.banners.collectAsStateWithLifecycle()
    val visibleBanners = remember(allBanners) { allBanners.filter { it.isActive && it.imageUrl.isNotBlank() } }
    val onBannerClick: (com.example.data.BannerEntity) -> Unit = remember(apps, allCollections) {
        { b ->
            when (b.targetType) {
                "app" -> apps.find { it.id == b.targetValue }?.let { showDetailsApp = it }
                "collection" -> allCollections.find { it.id == b.targetValue }?.let { openCollection = it }
                "link" -> try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(b.targetValue)))
                } catch (e: Exception) { }
            }
        }
    }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showUserAppSubmissionForm by remember { mutableStateOf(false) }
    var appSubmittingUpdateFor by remember { mutableStateOf<com.example.data.SubmissionEntity?>(null) }

    // Premium & Upcoming states
    val purchasedAppIds by viewModel.purchasedAppIds.collectAsStateWithLifecycle()
    val preRegisteredAppIds by viewModel.preRegisteredAppIds.collectAsStateWithLifecycle()
    var purchaseAppTarget by remember { mutableStateOf<AppEntity?>(null) }

    val notices by viewModel.notices.collectAsStateWithLifecycle()
    var activeNoticeToShow by remember { mutableStateOf<com.example.data.NoticeEntity?>(null) }

    val currentActivity = context as? android.app.Activity
    LaunchedEffect(notices, currentActivity?.intent) {
        val currentUnfilteredApps = viewModel.unfilteredApps.value
        val viewNoticeId = currentActivity?.intent?.getStringExtra("view_notice_id")
        if (viewNoticeId != null) {
            val foundNotice = notices.find { it.id == viewNoticeId }
            if (foundNotice != null) {
                activeNoticeToShow = foundNotice
                currentActivity.intent?.removeExtra("view_notice_id")
            }
        }

        val openScreen = currentActivity?.intent?.getStringExtra("open_screen")
        val appId = currentActivity?.intent?.getStringExtra("app_id")
        
        if (openScreen != null) {
            when (openScreen) {
                "submissions" -> {
                    if (isLoggedIn && isAdmin) {
                        activeTab = "Console"
                    }
                }
                "app_details", "updates" -> {
                    if (appId != null) {
                        // On a cold start (e.g. tapped from the home-screen widget) the catalog may
                        // not be loaded yet — wait briefly instead of silently dropping the request.
                        val foundApp = currentUnfilteredApps.find { it.id == appId || it.packageName == appId }
                            ?: kotlinx.coroutines.withTimeoutOrNull(5_000) {
                                viewModel.unfilteredApps.first { list -> list.any { it.id == appId || it.packageName == appId } }
                            }?.find { it.id == appId || it.packageName == appId }
                        if (foundApp != null) {
                            showDetailsApp = foundApp
                        }
                    }
                }
                "announcements" -> {
                    activeTab = "Notifications"
                }
                "chat" -> {
                    if (isLoggedIn) {
                        activeTab = "Chat"
                        val peerUid = currentActivity.intent?.getStringExtra("chat_uid").orEmpty()
                        if (peerUid.isNotBlank()) {
                            val peerName = currentActivity.intent?.getStringExtra("chat_name").orEmpty()
                            val peerPhoto = currentActivity.intent?.getStringExtra("chat_photo").orEmpty()
                            val known = viewModel.developers.value.find { it.uid == peerUid }
                            viewModel.openChatWith(
                                known ?: com.example.data.UserEntity(
                                    uid = peerUid,
                                    email = "",
                                    displayName = peerName,
                                    role = "user",
                                    isDeveloper = false,
                                    devName = peerName,
                                    profilePhotoUrl = peerPhoto
                                )
                            )
                        }
                        currentActivity.intent?.removeExtra("chat_uid")
                    }
                }
            }
            currentActivity.intent?.removeExtra("open_screen")
            currentActivity.intent?.removeExtra("app_id")
        }
    }

    // Refresh installed app info from PackageManager when details dialog is shown
    LaunchedEffect(showDetailsApp) {
        if (showDetailsApp != null) {
            viewModel.refreshInstalledApps()
            viewModel.loadReviewsForApp(showDetailsApp!!.id)
        }
    }

    // Automatically switch target if logged out of console session or not an admin
    LaunchedEffect(isLoggedIn, isAdmin) {
        if (!isLoggedIn && activeTab == "Chat") {
            activeTab = "Apps"
        }
        if ((!isLoggedIn || !isAdmin) && activeTab == "Console") {
            activeTab = "Apps"
        }
    }

    // Dynamic color assignments
    val backgroundColor = if (isDarkMode) (if (isAmoledMode) Color.Black else Color(0xFF111111)) else Color(0xFFF8F9FA)
    val cardBgColor = if (isDarkMode) (if (isAmoledMode) Color.Black else Color(0xFF1E1E1E)) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDarkMode) (if (isAmoledMode) Color(0xFF1A1A1A) else Color(0xFF2A2A2A)) else Color(0xFFE0E0E0)
    val textPrimary = if (isDarkMode) Color(0xFFE8EAED) else Color(0xFF202124)
    // Translucent "glass" card colours (home + library). Dialogs keep the opaque cardBgColor.
    val glassCardBg = if (isDarkMode) Color.White.copy(alpha = if (isAmoledMode) 0.07f else 0.10f) else Color.White.copy(alpha = 0.78f)
    val glassCardBorder = if (isDarkMode) Color.White.copy(alpha = 0.17f) else Color(0xFF334155).copy(alpha = 0.13f)
    val textSecondary = if (isDarkMode) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val premiumGold = if (isDarkMode) {
        when (premiumTheme) {
            "purple" -> Color(0xFFA78BFA)
            "cyan" -> Color(0xFF22D3EE)
            "green" -> Color(0xFF34D399)
            "rose" -> Color(0xFFF472B6)
            else -> Color(0xFFFBBF24) // gold
        }
    } else {
        when (premiumTheme) {
            "purple" -> Color(0xFF7C3AED)
            "cyan" -> Color(0xFF0891B2)
            "green" -> Color(0xFF059669)
            "rose" -> Color(0xFFDB2777)
            else -> Color(0xFFD97706) // gold
        }
    }
    val accentGreen = if (isPremiumMember) premiumGold else (if (isDarkMode) Color(0xFF34D399) else Color(0xFF01875F))

    if (showOfflineDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOfflineDialog = false },
            title = { Text("No internet connection") },
            text = { Text("An active internet connection is required to download or update apps.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showOfflineDialog = false
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                // Ignore
                            }
                        }
                    }
                ) { Text("Join Internet", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showOfflineDialog = false }) { Text("Cancel") }
            },
            containerColor = if (isDarkMode) Color(0xFF2A2A2A) else Color.White,
            titleContentColor = if (isDarkMode) Color.White else Color.Black,
            textContentColor = if (isDarkMode) Color.Gray else Color.DarkGray
        )
    }

    // Store review ratings override map locally to provide real updates
    var localAppRatings by remember { mutableStateOf(mapOf<String, Pair<String, Int>>()) }

    val onAppClick = remember {
        { app: AppEntity -> showDetailsApp = app }
    }
    
    val onActionClick = remember(viewModel, context) {
        { app: AppEntity ->
            val isCurrentlyInstalled = viewModel.installedAppsInfo.value.containsKey(app.packageName)
            handleAppActionButton(app, isCurrentlyInstalled, viewModel, context) {
                showOfflineDialog = true
            }
        }
    }

    val onCancelClick = remember(viewModel) {
        { id: String -> viewModel.cancelDownload(id) }
    }

    val installCoroutineScope = rememberCoroutineScope()
    val onInstallClick = remember(context) {
        { dl: DownloadEntity ->
            if (dl.localFilePath != null) {
                // BUG FIX: ApkInstaller.installApk() first attempts a silent install
                // via Runtime.exec(...) + process.waitFor(), which blocks the calling
                // thread for however long the shell command takes (potentially
                // several seconds, tried up to 3 times). This was called directly
                // from a click handler, i.e. on the main thread — freezing the whole
                // UI every time "Install" was tapped. Dispatch to IO instead.
                installCoroutineScope.launch(Dispatchers.IO) {
                    ApkInstaller.installApk(context, File(dl.localFilePath))
                }
            }
        }
    }

    val tabs = remember(isLoggedIn, isAdmin) {
        mutableListOf(
            Triple("Games", "Games", Icons.Default.PlayArrow),
            Triple("Apps", "Apps", Icons.Default.Home),
            Triple("Library", "My Library", Icons.Default.List)
        ).apply {
            if (isLoggedIn) {
                add(Triple("Chat", "Chat", Icons.Default.Chat))
            }
            if (isLoggedIn && isAdmin) {
                add(Triple("Console", "Admin", Icons.Default.Build))
            }
            add(Triple("Profile", "Profile", Icons.Default.AccountCircle))
            // Settings now lives inside Profile (gear button next to refresh).
        }
    }
    androidx.activity.compose.BackHandler(enabled = activeTab == "Settings") { activeTab = "Profile" }
    androidx.activity.compose.BackHandler(enabled = activeTab == "Notifications") { activeTab = "Apps" }
    val tabsList = remember(tabs) { tabs.map { it.first } }


    val appLang by viewModel.appLanguage.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalUninstallEnabled provides isUninstallEnabled,
        com.example.view.LocalLang provides appLang,
        com.example.view.LocalNavBarInset provides 96.dp
    ) {
        Box(Modifier.fillMaxSize().background(backgroundColor).ambientGlow(isDarkMode, isAmoledMode, accentGreen)) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            containerColor = Color.Transparent,
) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Check internet status and active announcements
            val isOnline by viewModel.isInternetAvailable.collectAsStateWithLifecycle()
            val announcements = notices.filter { it.targetAppId == "critical_announcement" }

            AnimatedVisibility(
                visible = !isOnline,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEF5350))
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Offline icon",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "No internet connection. Viewing offline database.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Top alert announcements in red color and danger symbol
            announcements.forEach { announcement ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(com.example.view.BannerStyles.color(announcement.bannerColor))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)))
                        .padding(vertical = 4.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val urlPattern = """https?://[^\s]+""".toRegex()
                    val detectedUrl = urlPattern.find(announcement.message)?.value 
                        ?: urlPattern.find(announcement.title)?.value
 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Critical announcement banner icon",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = announcement.title.uppercase(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            ,
                                fontFamily = com.example.view.BannerStyles.font(announcement.bannerFont)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = announcement.message,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 8.5.sp,
                                lineHeight = 11.sp
                            ,
                                fontFamily = com.example.view.BannerStyles.font(announcement.bannerFont)
                            )
                        }

                        if (detectedUrl != null) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Card(
                                onClick = {
                                    try {
                                        val uri = android.net.Uri.parse(detectedUrl)
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            setPackage("com.android.chrome")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val uri = android.net.Uri.parse(detectedUrl)
                                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (ex: Exception) {
                                            android.widget.Toast.makeText(context, "No web browser found.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFFC62828)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Redirect icon",
                                        modifier = Modifier.size(11.dp),
                                        tint = Color(0xFFC62828)
                                    )
                                    Text(
                                        text = "CHROME",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. Brand header + notifications / settings / profile, and the big search bar
            com.example.view.DarkStoreHeader(
                isDark = isDarkMode,
                accent = accentGreen,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                userName = userName,
                photoUrl = headerPhotoUrl,
                isPremium = isPremiumMember,
                premiumGold = premiumGold,
                unreadNotices = notices.count { !it.isRead && it.id !in readNoticeIds },
                showSearch = activeTab == "Apps" || activeTab == "Games" || activeTab == "Library",
                searchQuery = searchQuery,
                onSearchChange = { viewModel.updateSearchQuery(it) },
                onBell = { activeTab = "Notifications" },
                onSettings = { activeTab = "Settings" },
                onProfile = { activeTab = "Profile" }
            )

            // 2. Discover Content Board based on selected Navigation Tab with smooth GPU-accelerated animated transitions to prevent lagging
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    val initialIdx = tabsList.indexOf(initialState)
                    val targetIdx = tabsList.indexOf(targetState)
                    val direction = if (targetIdx >= initialIdx) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    slideIntoContainer(direction, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(150)) togetherWith
                    slideOutOfContainer(direction, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(150))
                },
                label = "dashboard_tab_transition_animation",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // PERF: this used to be a hand-rolled awaitPointerEventScope loop
                    // that called awaitPointerEvent() on every single touch-move
                    // sample of ANY gesture anywhere in this subtree — including
                    // every frame of every vertical scroll on every tab (Apps, Games,
                    // Library, Console, Profile, Settings all share this wrapper).
                    // Once a child like the feed's LazyColumn started consuming a
                    // vertical scroll, the loop's outer `while(true)` kept
                    // re-suspending and resuming on every remaining move sample of
                    // that same gesture instead of stepping aside, adding real
                    // continuous per-frame overhead for the whole scroll — exactly
                    // coincident with the reported lag.
                    // detectHorizontalDragGestures is Compose Foundation's own
                    // built-in utility for this exact job: it uses an internal
                    // touch-slop check that recognizes non-horizontal gestures
                    // quickly and steps aside for the child to handle them, instead
                    // of continuing to poll every subsequent move sample by hand.
                    // Same threshold-based swipe-to-change-tab behavior as before.
                    .pointerInput(tabsList, activeTab, selectedCategory) {
                        var dragAmountAccumulated = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val threshold = 180f
                                if (kotlin.math.abs(dragAmountAccumulated) > threshold) {
                                    val currentIdx = tabsList.indexOf(activeTab)
                                    if (dragAmountAccumulated < 0) {
                                        // Swipe Left -> Next tab
                                        if (currentIdx != -1 && currentIdx < tabsList.size - 1) {
                                            val nextTab = tabsList[currentIdx + 1]
                                            activeTab = nextTab
                                            if (nextTab == "Games") {
                                                viewModel.selectCategory("Games")
                                            } else if (nextTab == "Apps" && selectedCategory == "Games") {
                                                viewModel.selectCategory("All")
                                            }
                                        }
                                    } else {
                                        // Swipe Right -> Previous tab
                                        if (currentIdx > 0) {
                                            val prevTab = tabsList[currentIdx - 1]
                                            activeTab = prevTab
                                            if (prevTab == "Games") {
                                                viewModel.selectCategory("Games")
                                            } else if (prevTab == "Apps" && selectedCategory == "Games") {
                                                viewModel.selectCategory("All")
                                            }
                                        }
                                    }
                                }
                                dragAmountAccumulated = 0f
                            },
                            onDragCancel = { dragAmountAccumulated = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAmountAccumulated += dragAmount
                        }
                    }
            ) { targetTab ->
                // PERF: build once per actual data change (see DiscoveryFeedState doc),
                // reused by both tabs below so DiscoveryTabContent stays skippable.
                val discoveryFeedState = remember(apps, downloads, installedAppsInfo, installedPackages, localAppRatings, purchasedAppIds, preRegisteredAppIds, visibleCollections, visibleBanners) {
                    DiscoveryFeedState(
                        apps = apps,
                        downloads = downloads,
                        installedAppsInfo = installedAppsInfo,
                        installedPackages = installedPackages,
                        localAppRatings = localAppRatings,
                        purchasedAppIds = purchasedAppIds,
                        preRegisteredAppIds = preRegisteredAppIds,
                        collections = visibleCollections,
                        banners = visibleBanners
                    )
                }
                when (targetTab) {
                    "Games" -> {
                        DiscoveryTabContent(
                            isDarkMode = isDarkMode,
                            category = "Games",
                            searchQuery = searchQuery,
                            feedState = discoveryFeedState,
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refreshMarketplace(force = true) },
                            accentGreen = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            cardBgColor = glassCardBg,
                            onCollectionClick = { openCollection = it },
                            onBannerClick = onBannerClick,
                            cardBorderColor = glassCardBorder,
                            onAppClick = onAppClick,
                            onBuyPremiumClick = { purchaseAppTarget = it },
                            onPreRegisterClick = { app ->
                                viewModel.preRegisterApp(app.id)
                                Toast.makeText(context, "Successfully Pre-registered for ${app.name}! You will be notified when this upcoming software goes live.", Toast.LENGTH_LONG).show()
                            },
                            onActionClick = onActionClick
                        )
                    }
                    "Apps" -> {
                        // Apps board with category pills overlay
                        DiscoveryTabContent(
                            isDarkMode = isDarkMode,
                            category = selectedCategory,
                            searchQuery = searchQuery,
                            feedState = discoveryFeedState,
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refreshMarketplace(force = true) },
                            accentGreen = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            cardBgColor = glassCardBg,
                            onBannerClick = onBannerClick,
                            cardBorderColor = glassCardBorder,
                            onAppClick = onAppClick,
                            showPillNavbar = true,
                            selectedPill = selectedCategory,
                            onPillSelect = { viewModel.selectCategory(it) },
                            onCollectionClick = { openCollection = it },
                            onBuyPremiumClick = { purchaseAppTarget = it },
                            onPreRegisterClick = { app ->
                                viewModel.preRegisterApp(app.id)
                                Toast.makeText(context, "Successfully Pre-registered for ${app.name}! You will be notified when this upcoming software goes live.", Toast.LENGTH_LONG).show()
                            },
                            onActionClick = onActionClick
                        )
                    }
                    "Library" -> {
                        LibraryTabContent(
                            apps = apps,
                            downloads = downloads,
                            installedAppsInfo = installedAppsInfo,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentGreen = accentGreen,
                            cardBgColor = glassCardBg,
                            cardBorderColor = glassCardBorder,
                            onAppClick = onAppClick,
                            onCancelClick = onCancelClick,
                            onInstallClick = onInstallClick,
                            onActionClick = onActionClick
                        )
                    }
                    "Console" -> {
                        if (isAdmin) {
                            ConsoleTabContent(
                                viewModel = viewModel,
                                apps = unfilteredApps,
                                accentGreen = accentGreen,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                cardBgColor = cardBgColor,
                                cardBorderColor = cardBorderColor,
                                userEmail = userEmail,
                                onShowAppDetails = { app -> showDetailsApp = app }
                            )
                        }
                    }
                    "Chat" -> {
                        com.example.view.ChatTabContent(
                            viewModel = viewModel,
                            isLoggedIn = isLoggedIn,
                            isDarkMode = isDarkMode,
                            accentGreen = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            cardBgColor = cardBgColor,
                            cardBorderColor = cardBorderColor,
                            onAppClick = { chatApp -> showDetailsApp = chatApp }
                        )
                    }
                    "Profile" -> {
                        ProfileTabContent(
                            viewModel = viewModel,
                            isDarkMode = isDarkMode,
                            isLoggedIn = isLoggedIn,
                            userName = userName,
                            userEmail = userEmail,
                            submissions = remember(submissions, userEmail) {
                                submissions.filter { it.submittedBy.equals(userEmail, ignoreCase = true) }
                            },
                            onTriggerSubmitForm = { showUserAppSubmissionForm = true },
                            onRefreshSubmissions = { viewModel.refreshSubmissions() },
                            onOpenSettings = { activeTab = "Settings" },
                            onLogin = { email, name -> viewModel.loginWithGoogle(email, name) },
                            onLogout = { viewModel.logout() },
                            onThemeToggle = onThemeToggle,
                            onUpdateDeveloperName = { viewModel.updateDeveloperName(it) },
                            onRequestUpdate = { appSubmittingUpdateFor = it },
                            onShowAppDetails = { app -> showDetailsApp = app },
                            accentGreen = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            cardBgColor = cardBgColor,
                            cardBorderColor = cardBorderColor
                        )
                    }
                    "Notifications" -> {
                        com.example.view.NotificationsScreen(
                            viewModel = viewModel,
                            notices = notices,
                            readIds = readNoticeIds,
                            isAdmin = isAdmin,
                            isDark = isDarkMode,
                            accent = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onBack = { activeTab = "Apps" }
                        )
                    }
                    "Settings" -> {
                        SettingsTabContent(
                            isDarkMode = isDarkMode,
                            onThemeToggle = onThemeToggle,
                            viewModel = viewModel,
                            accentGreen = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            cardBgColor = cardBgColor,
                            cardBorderColor = cardBorderColor,
                            onBack = { activeTab = "Profile" }
                        )
                    }
                }
            }
        }
    }
        // Floating nav bar — overlays the content (no reserved strip behind it).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            val navShape = RoundedCornerShape(32.dp)
            val navTop = if (isDarkMode) Color(0xFF262B33).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.90f)
            val navBottom = if (isDarkMode) Color(0xFF14171C).copy(alpha = 0.95f) else Color(0xFFF1F4F7).copy(alpha = 0.94f)
            val navEdge = if (isDarkMode) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.95f)
            val navEdgeLow = if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color(0xFF8FA0B3).copy(alpha = 0.30f)
            val rawSelIdx = tabs.indexOfFirst { it.first == activeTab || (it.first == "Profile" && activeTab == "Settings") }
            val selIdx = rawSelIdx.coerceAtLeast(0)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp)
                    .shadow(18.dp, navShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.32f))
                    .clip(navShape)
                    .background(Brush.verticalGradient(listOf(navTop, navBottom)))
                    .border(1.dp, Brush.verticalGradient(listOf(navEdge, navEdgeLow)), navShape)
            ) {
                val itemW = (maxWidth - 12.dp) / tabs.size
                // "Liquid" bubble that slides (with a little overshoot) to the selected tab
                val bubbleX by animateDpAsState(
                    targetValue = itemW * selIdx,
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessLow),
                    label = "nav_liquid_x"
                )
                Box(modifier = Modifier.padding(6.dp)) {
                    Box(
                        modifier = Modifier
                            .offset(x = bubbleX)
                            .alpha(if (rawSelIdx >= 0) 1f else 0f)
                            .width(itemW)
                            .height(58.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.verticalGradient(listOf(accentGreen.copy(alpha = 0.36f), accentGreen.copy(alpha = 0.12f))))
                            .border(
                                1.dp,
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), accentGreen.copy(alpha = 0.25f))),
                                RoundedCornerShape(26.dp)
                            )
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        tabs.forEach { (tabId, label, icon) ->
                            val isSelected = activeTab == tabId || (tabId == "Profile" && activeTab == "Settings")
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.14f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "nav_icon_scale"
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        activeTab = tabId
                                        if (tabId == "Games") {
                                            viewModel.selectCategory("Games")
                                        } else if (tabId == "Apps" && selectedCategory == "Games") {
                                            viewModel.selectCategory("All")
                                        }
                                        // Always re-fetch live data when switching tabs while online
                                        viewModel.refreshMarketplace(force = true)
                                        if (tabId == "Chat") viewModel.refreshChatThreads()
                                        if (tabId == "Profile" || tabId == "Console") viewModel.refreshSubmissions()
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val tint = if (isSelected) accentGreen else textSecondary
                                val navIcon: @Composable () -> Unit = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = tint,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                    )
                                }
                                if (tabId == "Chat" && chatUnread > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) {
                                                Text(
                                                    text = if (chatUnread > 99) "99+" else chatUnread.toString(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    ) { navIcon() }
                                } else {
                                    navIcon()
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    com.example.view.tr("nav_$tabId"),
                                    color = tint,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    openCollection?.let { col ->
            com.example.view.CollectionDetailDialog(
                collection = col,
                apps = apps,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                pageBg = backgroundColor,
                onAppClick = onAppClick,
                onDismiss = { openCollection = null }
            )
        }

        }


    // 3. Dynamic Account Dialog Trigger (replaced by full-screen Profile tab panel)

    if (showUserAppSubmissionForm) {
        AddNewAppForm(
            isForAdmin = isAdmin,
            userEmail = userEmail,
            defaultDeveloperName = devName,
            onDismiss = { showUserAppSubmissionForm = false },
            onSubmit = { appData ->
                if (isAdmin) {
                    viewModel.addOrUpdateAppInCatalog(appData) { success ->
                        if (success) {
                            Toast.makeText(context, "Direct deployment committed!", Toast.LENGTH_SHORT).show()
                            showUserAppSubmissionForm = false
                            viewModel.refreshMarketplace(force = true)
                        } else {
                            Toast.makeText(context, "Transmission error.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
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
                        videoUrl = appData.videoUrl
                    ) { success, msg ->
                        Toast.makeText(context, msg ?: "Submission dispatched", Toast.LENGTH_SHORT).show()
                        if (success) showUserAppSubmissionForm = false
                    }
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

    // 4. Highly detailed specifications dialog overlay
    showDetailsApp?.let { app ->
        val activeDl = downloads.find { it.id == app.id }
        val installedInfo = installedAppsInfo[app.packageName]
        val dynamicRatingInfo = localAppRatings[app.id] ?: Pair(app.rating, 120 + Math.abs(app.id.hashCode() % 350))

        AppDetailsDialog(
            app = app,
            viewModel = viewModel,
            downloadState = activeDl,
            installedInfo = installedInfo,
            currentRating = dynamicRatingInfo.first,
            currentReviewsCount = dynamicRatingInfo.second,
            accentGreen = accentGreen,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            cardBgColor = cardBgColor,
            isDarkMode = isDarkMode,
            isPurchased = purchasedAppIds.contains(app.id),
            isRegistered = preRegisteredAppIds.contains(app.id),
            isAdmin = isAdmin,
            developers = developers,
            allApps = apps,
            appReviews = appReviews,
            isReviewsLoading = isReviewsLoading,
            onAppClick = { selectedApp -> showDetailsApp = selectedApp },
            onBuyClick = { purchaseAppTarget = app; showDetailsApp = null },
            onRegisterClick = {
                viewModel.preRegisterApp(app.id)
                Toast.makeText(context, "Successfully Pre-registered for ${app.name}! You will be notified when this software goes live.", Toast.LENGTH_LONG).show()
            },
            onMessageDeveloper = { peer ->
                showDetailsApp = null
                viewModel.openChatWith(peer)
                activeTab = "Chat"
            },
            onDismiss = { showDetailsApp = null },
            onAction = {
                val isCurrentlyInstalled = installedInfo != null
                handleAppActionButton(app, isCurrentlyInstalled, viewModel, context) {
                    showOfflineDialog = true
                }
            },
            onDeleteDl = {
                viewModel.cancelDownload(app.id)
            },
            onReviewSubmit = { stars, comment ->
                viewModel.submitReview(app.id, comment, stars) { success ->
                    if (success) {
                        viewModel.loadReviewsForApp(app.id)
                    }
                }
                
                // Calculate dynamic rated average locally on screen!
                val totalSReviews = dynamicRatingInfo.second + 1
                val oldAverage = dynamicRatingInfo.first.toFloatOrNull() ?: 4.5f
                val newAverage = ((oldAverage * dynamicRatingInfo.second) + stars) / totalSReviews
                val formattedStr = String.format("%.1f", newAverage)
                localAppRatings = localAppRatings + (app.id to Pair(formattedStr, totalSReviews))
                
                Toast.makeText(context, "Review posted! Global community rating computed.", Toast.LENGTH_LONG).show()
            },
            onReportSubmit = { reason ->
                viewModel.reportApp(app.id, userEmail, reason) { success, msg ->
                    Toast.makeText(context, msg ?: "Failed to submit report", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // The old fake payment checkout (hardcoded "Play Balance $25.00", a made-up
    // Visa card, a delay() pretending to authorize a real payment) has been
    // removed entirely — DarkStore has no real payment processor behind it,
    // so pretending a purchase succeeded was actively dishonest. Any app
    // still flagged premium (from before this change) now honestly says
    // "Coming Soon" instead of faking a checkout.
    purchaseAppTarget?.let { app ->
        ComingSoonDialog(
            title = "Paid Apps — Coming Soon",
            message = "Purchasing premium apps isn't available yet. We're building real payment support and will let you know the moment it's ready.",
            accentColor = Color(0xFFFF9800),
            onDismiss = { purchaseAppTarget = null }
        )
    }

    if (activeNoticeToShow != null) {
        NoticeDetailsDialog(
            notice = activeNoticeToShow!!,
            onDismiss = { activeNoticeToShow = null }
        )
    }
    }
}

// ========================================================
// 4. DISCOVERY TABS CONTENT VIEWS (APPS & GAMES)
// ========================================================
@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    textPrimary: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = title,
            color = textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp
        )
    }
}

// ========================================================
/**
 * PERF: bundles every List/Map/Set the home feed needs into one object marked
 * @Immutable. Compose treats bare List/Map/Set parameters as unconditionally
 * unstable (they're just interfaces — could be backed by a mutable
 * implementation), which forces the ENTIRE composable that receives them to
 * be non-skippable: it will fully re-execute every time anything in its
 * parent recomposes, regardless of whether these values actually changed.
 * That was true of DiscoveryTabContent, so literally any unrelated state
 * change elsewhere on the home screen (a dialog opening, a toast, a snackbar,
 * search text) was forcing the whole scrollable feed — including rebuilding
 * every row's content — to recompose, competing with active scrolling for
 * frame time. @Immutable tells the compiler to trust structural equality
 * instead, making DiscoveryTabContent skippable again: it now only
 * re-executes when one of these collections has actually changed.
 */
@Immutable
data class DiscoveryFeedState(
    val apps: List<AppEntity>,
    val downloads: List<DownloadEntity>,
    val installedAppsInfo: Map<String, com.example.utils.ApkInstaller.InstalledAppInfo>,
    val installedPackages: Set<String>,
    val localAppRatings: Map<String, Pair<String, Int>>,
    val purchasedAppIds: Set<String>,
    val preRegisteredAppIds: Set<String>,
    val collections: List<com.example.data.CollectionEntity> = emptyList(),
    val banners: List<com.example.data.BannerEntity> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryTabContent(
    isDarkMode: Boolean,
    category: String,
    searchQuery: String = "",
    feedState: DiscoveryFeedState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit = {},
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onAppClick: (AppEntity) -> Unit,
    showPillNavbar: Boolean = false,
    selectedPill: String = "All",
    onPillSelect: (String) -> Unit = {},
    onBuyPremiumClick: (AppEntity) -> Unit = {},
    onPreRegisterClick: (AppEntity) -> Unit = {},
    onCollectionClick: (com.example.data.CollectionEntity) -> Unit = {},
    onBannerClick: (com.example.data.BannerEntity) -> Unit = {},
    onActionClick: (AppEntity) -> Unit
) {
    // Unpack into the original local names — zero changes needed below this line.
    val (apps, downloads, installedAppsInfo, installedPackages, localAppRatings, purchasedAppIds, preRegisteredAppIds) = feedState

    val categories = remember { listOf("All", "Utilities", "Games", "Tools", "Entertainment") }

    // PERF: hold the latest callback lambdas via rememberUpdatedState so that
    // per-item click handlers below can have a STABLE identity (keyed only on
    // app.id) without ever calling a stale closure. Passing raw `{ onAppClick(app) }`
    // inline creates a brand-new lambda every recomposition, which defeats Compose's
    // ability to skip recomposing list rows and was the main cause of scroll jank.
    val latestOnAppClick = rememberUpdatedState(onAppClick)
    val latestOnActionClick = rememberUpdatedState(onActionClick)
    val latestOnBuyPremiumClick = rememberUpdatedState(onBuyPremiumClick)
    val latestOnPreRegisterClick = rememberUpdatedState(onPreRegisterClick)

    val downloadsMap = remember(downloads) { downloads.associateBy { it.id } }
    
    // Filter Games out for Games tab specifically
    val appsToRender = remember(apps, category, selectedPill) {
        if (category == "Games") {
            apps.filter { it.category.equals("Games", ignoreCase = true) }
        } else {
            apps
        }
    }
    val featuredList = remember(appsToRender) { appsToRender.filter { it.isFeatured } }
    // Home sections: horizontally scrolling rows of square glass tiles
    val recommendedApps = remember(appsToRender) {
        appsToRender.filter { !it.isUpcoming }.let { std -> std.filter { it.isFeatured }.ifEmpty { std } }
    }
    val popularApps = remember(appsToRender) {
        val std = appsToRender.filter { !it.isUpcoming }
        std.filter { it.isPopular }.ifEmpty { std }.sortedByDescending { it.rating.toFloatOrNull() ?: 0f }
    }
    val topFreeApps = remember(appsToRender) {
        appsToRender.filter { !it.isUpcoming && !it.isPremium }.sortedByDescending { it.rating.toFloatOrNull() ?: 0f }
    }
    fun moreOf(title: String, list: List<AppEntity>) = com.example.data.CollectionEntity(
        id = "more_$title", title = title, color = "#10B981", appIds = list.map { it.id }
    )
    val premiumApps = remember(appsToRender) { appsToRender.filter { it.isPremium } }
    val upcomingApps = remember(appsToRender) { appsToRender.filter { it.isUpcoming } }
    val standardApps = remember(appsToRender) { appsToRender.filter { !it.isUpcoming } }

    val context = LocalContext.current
    val cacheKey = remember(category, selectedPill, searchQuery) { "${category}_${selectedPill}_${searchQuery}" }
    
    val loadedApps = remember { mutableStateListOf<AppEntity?>() }
    var isPageLoading by remember { mutableStateOf(false) }

    var isLoadingDiscovery by remember { mutableStateOf(true) }
    
    // PERF: This was the main cause of the scroll lag. rememberInfiniteTransition +
    // animateFloat produces a State<Float> that ticks on every animation frame
    // (60fps+) FOR AS LONG AS IT IS READ, forever, not just while loading. Reading
    // it unconditionally here (via `by`) meant this entire DiscoveryTabContent
    // function — including rebuilding the whole LazyColumn's item content — was
    // recomposing every single frame in the background, even long after the
    // loading skeleton was gone, permanently competing with scroll for the frame
    // budget. Only create/read the animation while a skeleton is actually on
    // screen; the rest of the time no infinite transition exists at all.
    val showSkeleton = isLoadingDiscovery && appsToRender.isEmpty() && loadedApps.isEmpty()
    val activeShimmerTranslate = if (showSkeleton) {
        val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerTranslate by shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer_offset"
        )
        shimmerTranslate
    } else {
        0f
    }
    // Retained for the (currently unused in practice) null-placeholder pagination
    // row, kept equal to the gated value above so it never runs standalone.
    val shimmerTranslate = activeShimmerTranslate

    val listState = rememberLazyListState()

    // Sync non-null loadedApps with standardApps when background updates occur (e.g., status/ratings changes)
    LaunchedEffect(standardApps) {
        for (i in 0 until loadedApps.size) {
            val loadedItem = loadedApps[i]
            if (loadedItem != null) {
                val updatedItem = standardApps.firstOrNull { it.id == loadedItem.id }
                if (updatedItem != null && updatedItem != loadedItem) {
                    loadedApps[i] = updatedItem
                }
            }
        }
    }

    // Always sync the visible list from live standardApps (RTDB).
    // Old behavior restored PaginationCache first — so when a NEW app was
    // approved online, the UI kept showing the stale cached page and
    // isPageLoading / hasMore looked like "forever loading" until the user
    // scrolled enough to page in the rest. Live data wins; cache is only a
    // cold-start hint when standardApps is still empty.
    LaunchedEffect(cacheKey, standardApps) {
        if (standardApps.isEmpty()) {
            // Still waiting for first network (or truly empty store).
            // Keep skeleton only if we have nothing to show yet.
            if (loadedApps.isEmpty()) {
                isLoadingDiscovery = true
                // Optional cold-start: show disk cache while network loads
                if (searchQuery.isEmpty()) {
                    val cached = withContext(Dispatchers.IO) {
                        com.example.data.PaginationCache.getApps(context, cacheKey)
                    }
                    if (!cached.isNullOrEmpty()) {
                        loadedApps.clear()
                        loadedApps.addAll(cached)
                        isLoadingDiscovery = false
                        isPageLoading = false
                    }
                }
            }
            return@LaunchedEffect
        }

        // We have live apps — never leave discovery in a loading state.
        isLoadingDiscovery = false
        isPageLoading = false

        val liveById = standardApps.associateBy { it.id }
        val previousIds = loadedApps.mapNotNull { it?.id }

        if (loadedApps.isEmpty()) {
            // First paint from live data (show more on first page so new apps
            // near the top are visible without scrolling).
            val initialSize = minOf(40, standardApps.size)
            loadedApps.addAll(standardApps.take(initialSize))
        } else {
            // 1) Update rows that already exist
            for (i in loadedApps.indices) {
                val cur = loadedApps[i] ?: continue
                val fresh = liveById[cur.id]
                if (fresh != null && fresh != cur) {
                    loadedApps[i] = fresh
                }
            }
            // 2) Drop rows removed from the catalog
            loadedApps.removeAll { it == null || it.id !in liveById }
            // 3) Prepend brand-new apps so they appear immediately at the top
            val existing = loadedApps.mapNotNull { it?.id }.toSet()
            val brandNew = standardApps.filter { it.id !in existing }
            if (brandNew.isNotEmpty()) {
                // Insert at front of the paged window
                brandNew.asReversed().forEach { loadedApps.add(0, it) }
            }
            // Cap memory: keep a reasonable window (scroll loads more via hasMore)
            val maxWindow = maxOf(40, previousIds.size + brandNew.size).coerceAtMost(standardApps.size)
            while (loadedApps.size > maxWindow && loadedApps.size > 40) {
                // Prefer trimming from the end (older page) not the new head
                loadedApps.removeAt(loadedApps.lastIndex)
            }
        }

        if (searchQuery.isEmpty()) {
            val snapshot = loadedApps.filterNotNull()
            withContext(Dispatchers.IO) {
                com.example.data.PaginationCache.saveApps(context, cacheKey, snapshot)
            }
        }
    }

    val hasMore = remember(loadedApps.size, standardApps.size) {
        loadedApps.size < standardApps.size
    }

    // Auto-load next page when reaching ~85% of loaded list
    LaunchedEffect(listState, loadedApps.size, hasMore, isPageLoading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                // Also cache the scroll position as the user scrolls
                if (searchQuery.isEmpty()) {
                    val firstIndex = listState.firstVisibleItemIndex
                    val firstOffset = listState.firstVisibleItemScrollOffset
                    com.example.data.PaginationCache.saveScrollPosition(cacheKey, firstIndex, firstOffset)
                }
                
                val totalLoaded = loadedApps.size
                if (hasMore && !isPageLoading && totalLoaded > 0 && lastVisibleIndex >= (totalLoaded * 0.85).toInt()) {
                    isPageLoading = true
                    val nextPageSize = minOf(20, standardApps.size - totalLoaded)
                    if (nextPageSize > 0) {
                        // High-performance strategy: Load the entire next chunk in a single frame,
                        // avoiding index-by-index recomposition lag!
                        kotlinx.coroutines.delay(80)
                        val nextChunk = standardApps.subList(totalLoaded, totalLoaded + nextPageSize)
                        loadedApps.addAll(nextChunk)
                    }
                    isPageLoading = false
                    if (searchQuery.isEmpty()) {
                        // PERF/BUG: this was the big one — this fires roughly every ~17
                        // rows scrolled (each time pagination triggers) for as long as
                        // the user keeps scrolling down, re-serializing the ENTIRE
                        // (ever-growing) loaded list to JSON and writing it to disk,
                        // synchronously, directly on the main thread — a real freeze
                        // landing exactly mid-scroll, repeatedly, for long scroll
                        // sessions. Snapshot the list before hopping off the main
                        // thread (SnapshotStateList isn't safe to read from a
                        // background thread while still attached to composition).
                        val snapshot = loadedApps.filterNotNull()
                        withContext(Dispatchers.IO) {
                            com.example.data.PaginationCache.saveApps(context, cacheKey, snapshot)
                        }
                    }
                }
            }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            onRefresh()
            // Fail-safe: if the external refreshing state doesn't activate (e.g. cached/offline)
            // or ends extremely quickly, we force stop the pull-to-refresh animation after 1.2 seconds.
            kotlinx.coroutines.delay(1200)
            if (!isRefreshing) {
                pullToRefreshState.endRefresh()
            }
        }
    }
    
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = com.example.view.LocalNavBarInset.current)
        ) {
        if (isLoadingDiscovery && loadedApps.isEmpty() && appsToRender.isEmpty()) {
            // ========================================================
            // ZERO-SPINNER ANIMATED SHIMMER SKELETON LOADERS
            // (only when the catalog is empty — never replace a live list
            // with skeletons on background/tab refresh)
            // ========================================================
            
            // 1. Shimmering Promo Banner Skeleton
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(134.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shimmerBrush(isDarkMode, activeShimmerTranslate))
                )
            }

            // 2. Shimmering Category Filter Pills Skeletons
            if (showPillNavbar && category != "Games") {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(5) {
                            Box(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(shimmerBrush(isDarkMode, activeShimmerTranslate))
                            )
                        }
                    }
                }
            }

            // 3. Status indication text line
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush(isDarkMode, activeShimmerTranslate))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRefreshing) "Syncing Dark Store databases..." else "Aligning system packages...",
                        color = textSecondary.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 4. Featured Recommendations Header
            item {
                SectionHeader(
                    title = "Featured recommendations",
                    icon = Icons.Default.Star,
                    iconColor = Color(0xFFF1A80A),
                    textPrimary = textPrimary
                )
            }

            // 5. Featured recommendations row skeletons
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(3) {
                        FeaturedAppCardSkeleton(
                            isDarkMode = isDarkMode,
                            cardBgColor = cardBgColor,
                            cardBorderColor = cardBorderColor,
                            shimmerTranslate = activeShimmerTranslate
                        )
                    }
                }
            }

            // 6. Recommended for you header skeleton
            item {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader(
                    title = "Recommended for you",
                    icon = Icons.Default.ThumbUp,
                    iconColor = accentGreen,
                    textPrimary = textPrimary
                )
            }

            // 7. Recommended for you vertical list skeletons
            items(4) {
                AppItemCardSkeleton(
                    isDarkMode = isDarkMode,
                    cardBgColor = cardBgColor,
                    cardBorderColor = cardBorderColor,
                    shimmerTranslate = activeShimmerTranslate
                )
            }
        } else {
            // ========================================================
            // REAL PRODUCTIVE CONTENT LAYOUT (FADE-IN SHOWN VIA ENGINES)
            // ========================================================
            
            // App Store Play Carousel
            item {
                Spacer(modifier = Modifier.height(6.dp))
                val bannerAlpha = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    bannerAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)
                    )
                }
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = bannerAlpha.value
                        translationY = (1f - bannerAlpha.value) * -10f
                    }
                ) {
                    if (feedState.banners.isNotEmpty() && category != "Games") {
                        com.example.view.BannerCarousel(
                            banners = feedState.banners,
                            isDark = isDarkMode,
                            accent = accentGreen,
                            onBannerClick = onBannerClick
                        )
                    } else {
                        PromotedAppsCarousel(
                            featuredList = featuredList,
                            allApps = appsToRender,
                            isDarkMode = isDarkMode,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentColor = accentGreen,
                            onAppClick = onAppClick
                        )
                    }
                }
            }

            // Horizontal Category Filter Pills
            if (showPillNavbar && category != "Games") {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories, key = { it }) { cat ->
                            val isSelected = selectedPill == cat
                            Button(
                                onClick = { onPillSelect(cat) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) accentGreen else cardBgColor,
                                    contentColor = if (isSelected) Color.White else textSecondary
                                ),
                                shape = RoundedCornerShape(50.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                border = if (!isSelected) BorderStroke(1.dp, cardBorderColor) else null,
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Admin-curated collections (Apps tab, "All" pill, not while searching)
            if (showPillNavbar && category == "All" && searchQuery.isBlank() && feedState.collections.isNotEmpty()) {
                item(key = "collections_row") {
                    com.example.view.CollectionsRow(
                        collections = feedState.collections,
                        apps = apps,
                        textPrimary = textPrimary,
                        onClick = onCollectionClick
                    )
                }
            }

            if (appsToRender.isEmpty()) {
                item {
                    EmptyCatalogStateCard(
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentGreen = accentGreen,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
            } else {
                // ── Recommended For You (square tiles) ──
                if (recommendedApps.isNotEmpty()) {
                    item(key = "sec_recommended") {
                        com.example.view.SectionTitleRow(
                            title = com.example.view.tr("sec_recommended"), accent = accentGreen, textPrimary = textPrimary,
                            onMore = { onCollectionClick(moreOf("Recommended For You", recommendedApps)) }
                        )
                    }
                    item(key = "row_recommended") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(recommendedApps.take(12), key = { "rec_" + it.id }) { app ->
                                val onClick = remember(app.id) { { latestOnAppClick.value(app) } }
                                com.example.view.SquareAppTile(app, isDarkMode, accentGreen, textPrimary, textSecondary, onClick)
                            }
                        }
                    }
                }

                // ── Most Popular ──
                if (popularApps.isNotEmpty()) {
                    item(key = "sec_popular") {
                        com.example.view.SectionTitleRow(
                            title = com.example.view.tr("sec_popular"), accent = accentGreen, textPrimary = textPrimary,
                            onMore = { onCollectionClick(moreOf("Most Popular", popularApps)) }
                        )
                    }
                    item(key = "row_popular") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(popularApps.take(12), key = { "pop_" + it.id }) { app ->
                                val onClick = remember(app.id) { { latestOnAppClick.value(app) } }
                                com.example.view.PopularAppCard(app, localAppRatings[app.id]?.first ?: app.rating, isDarkMode, accentGreen, textPrimary, textSecondary, onClick)
                            }
                        }
                    }
                }

                // ── Top Free ──
                if (topFreeApps.isNotEmpty()) {
                    item(key = "sec_free") {
                        com.example.view.SectionTitleRow(
                            title = com.example.view.tr("sec_free"), accent = accentGreen, textPrimary = textPrimary,
                            onMore = { onCollectionClick(moreOf("Top Free", topFreeApps)) }
                        )
                    }
                    item(key = "row_free") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(topFreeApps.take(12), key = { "free_" + it.id }) { app ->
                                val onClick = remember(app.id) { { latestOnAppClick.value(app) } }
                                com.example.view.CompactAppCard(app, localAppRatings[app.id]?.first ?: app.rating, isDarkMode, accentGreen, textPrimary, textSecondary, onClick)
                            }
                        }
                    }
                }

            if (premiumApps.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Premium Highlights",
                        icon = Icons.Default.Favorite,
                        iconColor = Color(0xFFFF9800),
                        textPrimary = textPrimary
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        items(premiumApps, key = { it.id }) { app ->
                            val isPurchased = purchasedAppIds.contains(app.id)
                            val onClick = remember(app.id) { { latestOnAppClick.value(app) } }
                            val onBuyClick = remember(app.id) { { latestOnBuyPremiumClick.value(app) } }
                            Box(modifier = Modifier.animateItemPlacement(tween(280, easing = FastOutSlowInEasing))) {
                                PremiumAppCardView(
                                    app = app,
                                    purchased = isPurchased,
                                    cardBgColor = cardBgColor,
                                    cardBorderColor = cardBorderColor,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    accentGreen = accentGreen,
                                    onClick = onClick,
                                    onBuyClick = onBuyClick
                                )
                            }
                        }
                    }
                }
            }

            if (upcomingApps.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Upcoming Releases (Pre-register)",
                        icon = Icons.Default.NewReleases,
                        iconColor = Color(0xFF9C27B0),
                        textPrimary = textPrimary
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        items(upcomingApps, key = { it.id }) { app ->
                            val isRegistered = preRegisteredAppIds.contains(app.id)
                            val onClick = remember(app.id) { { latestOnAppClick.value(app) } }
                            val onRegisterClick = remember(app.id) { { latestOnPreRegisterClick.value(app) } }
                            Box(modifier = Modifier.animateItemPlacement(tween(280, easing = FastOutSlowInEasing))) {
                                UpcomingAppCardView(
                                    app = app,
                                    registered = isRegistered,
                                    cardBgColor = cardBgColor,
                                    cardBorderColor = cardBorderColor,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    accentGreen = accentGreen,
                                    onClick = onClick,
                                    onRegisterClick = onRegisterClick
                                )
                            }
                        }
                    }
                }
            }

            // Standard Packages item list header
            item {
                SectionHeader(
                    title = com.example.view.tr("sec_all"),
                    icon = Icons.Default.ThumbUp,
                    iconColor = accentGreen,
                    textPrimary = textPrimary
                )
            }

            // Packages list
            itemsIndexed(
                loadedApps,
                key = { index, app -> app?.id ?: "shimmer_$index" },
                contentType = { _, app -> if (app == null) "skeleton" else "app_row" }
            ) { index, app ->
                if (app == null) {
                    AppItemCardSkeleton(
                        isDarkMode = isDarkMode,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor,
                        shimmerTranslate = shimmerTranslate
                    )
                } else {
                    val dlState = downloadsMap[app.id]
                    val installedInfo = installedAppsInfo[app.packageName]
                    val overriddenRating = localAppRatings[app.id]?.first ?: app.rating

                    val isPurchased = purchasedAppIds.contains(app.id)
                    val isRegistered = preRegisteredAppIds.contains(app.id)

                    // PERF: stable per-row lambda identity (keyed only on app.id) so
                    // AppItemCardView can be skipped by Compose when nothing about
                    // this specific row changed, instead of recreating a new lambda
                    // (and forcing a recompose) on every parent recomposition.
                    val onBuyClick = remember(app.id) { { latestOnBuyPremiumClick.value(app) } }
                    val onRegisterClick = remember(app.id) { { latestOnPreRegisterClick.value(app) } }
                    val onClick = remember(app.id) { { latestOnAppClick.value(app) } }
                    val onActionClick = remember(app.id) { { latestOnActionClick.value(app) } }

                    // Smooth cross-fade + slide whenever a row's position shifts
                    // (new items inserted above it, a filtered item removed,
                    // etc.) instead of it just snapping to its new spot.
                    // animateItemPlacement() is Compose Foundation's own
                    // built-in, low-cost placement-animation primitive — safe
                    // to add without reopening any of the earlier scroll-perf
                    // fixes to this same row.
                    Box(modifier = Modifier.animateItemPlacement(tween(280, easing = FastOutSlowInEasing))) {
                        AppItemCardView(
                            app = app,
                            downloadState = dlState,
                            installedInfo = installedInfo,
                            overriddenRating = overriddenRating,
                            accentGreen = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            cardBgColor = cardBgColor,
                            cardBorderColor = cardBorderColor,
                            purchased = isPurchased,
                            registered = isRegistered,
                            onBuyClick = onBuyClick,
                            onRegisterClick = onRegisterClick,
                            onClick = onClick,
                            onActionClick = onActionClick
                        )
                    }
                }
            }

            if (isPageLoading && hasMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = accentGreen,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else if (!hasMore && loadedApps.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "You're all caught up.",
                            color = textSecondary.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (pullToRefreshState.verticalOffset > 0f || pullToRefreshState.isRefreshing) {
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = cardBgColor,
            contentColor = accentGreen
        )
    }
}
}

// ========================================================
// 4.B PREMIUM AND UPCOMING ITEM CARD VIEWS
// ========================================================
@Composable
fun PremiumAppCardView(
    app: AppEntity,
    purchased: Boolean,
    cardBgColor: Color,
    cardBorderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onClick: () -> Unit,
    onBuyClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(152.dp)
            .glass(textPrimary.luminance() > 0.5f, RoundedCornerShape(22.dp), elevation = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                AppLogo(
                    logoUrl = app.logo,
                    appName = app.name,
                    packageName = app.packageName,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.developer,
                color = textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            if (purchased) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentGreen.copy(alpha = 0.15f), RoundedCornerShape(15.dp))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Purchased",
                        color = accentGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onBuyClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    Text(
                        text = app.price.ifEmpty { "$1.99" },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun UpcomingAppCardView(
    app: AppEntity,
    registered: Boolean,
    cardBgColor: Color,
    cardBorderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(152.dp)
            .glass(textPrimary.luminance() > 0.5f, RoundedCornerShape(22.dp), elevation = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                AppLogo(
                    logoUrl = app.logo,
                    appName = app.name,
                    packageName = app.packageName,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.category,
                color = textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            if (registered) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentGreen.copy(alpha = 0.15f), RoundedCornerShape(15.dp))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = accentGreen, modifier = Modifier.size(10.dp))
                        Text(
                            text = "Registered",
                            color = accentGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Button(
                    onClick = onRegisterClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    Text(
                        text = "Pre-register",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ========================================================
// 5. SEVERAL PLAY-STORE-LIKE AUXILIARY CARDS
// ========================================================

@Composable
fun PlayStoreBannerHero(accentColor: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(accentColor, accentColor.copy(alpha = 0.5f))
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.65f),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DARK STORE EXCLUSIVE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "High-Speed Access",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Secure local package deployment & fast Firebase catalog synchronizer.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Overlay controller icon illustrative graphic
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Graphic indicator",
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.CenterEnd)
                    .alpha(0.15f),
                tint = Color.White
            )
        }
    }
}

fun shimmerBrush(isDarkMode: Boolean, translateAnim: Float): Brush {
    val shimmerColors = if (isDarkMode) {
        listOf(
            Color(0xFF2A2A2A),
            Color(0xFF1F1F1F),
            Color(0xFF2A2A2A)
        )
    } else {
        listOf(
            Color(0xFFE5E7EB),
            Color(0xFFFFFFFF),
            Color(0xFFE5E7EB)
        )
    }

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
}

@Composable
fun FeaturedAppCardSkeleton(
    isDarkMode: Boolean,
    cardBgColor: Color,
    cardBorderColor: Color,
    shimmerTranslate: Float
) {
    val brush = shimmerBrush(isDarkMode, shimmerTranslate)
    Card(
        modifier = Modifier
            .width(160.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Logo skeleton
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Title skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Developer skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Rating / Size row skeleton
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Star skeleton
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                // Rating val skeleton
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.width(4.dp))
                // Size skeleton
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun AppItemCardSkeleton(
    isDarkMode: Boolean,
    cardBgColor: Color,
    cardBorderColor: Color,
    shimmerTranslate: Float
) {
    val brush = shimmerBrush(isDarkMode, shimmerTranslate)
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo skeleton
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                // Title
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Developer & Category
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Rating/size row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(35.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(brush)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Install/Update action button skeleton
            Box(
                modifier = Modifier
                    .size(70.dp, 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun FeaturedAppCardView(
    app: AppEntity,
    activeRating: String,
    cardBgColor: Color,
    cardBorderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onClick: () -> Unit
) {
    // NOTE: previously this showed a skeleton for 180ms via remember(app.id) every
    // time the card entered composition. Since LazyRow/LazyColumn dispose items
    // that scroll out of view and recompose them fresh when they scroll back in,
    // this fake delay was retriggering on every single scroll-up/scroll-down pass
    // over this row — a visible flash/stutter, not a real loading state (the app
    // data is already in memory, nothing is actually being fetched). Removed so
    // the card renders immediately, every time.
    run {
        Card(
            modifier = Modifier
                .width(160.dp)
                .glass(textPrimary.luminance() > 0.5f, RoundedCornerShape(22.dp), elevation = 3.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = null
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppLogo(
                        logoUrl = app.logo,
                        appName = app.name,
                        packageName = app.packageName,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.name,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = if (app.hasAds) 55.dp else 0.dp) // Leave safety room for top-right Ad badge if name is long
                        )
                    }
                    
                    Text(
                        text = app.developer,
                        color = textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeRating,
                            color = textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "stars rating",
                            tint = Color(0xFFF1A80A),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = app.size,
                            color = textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                if (app.hasAds) {
                    AdBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AdBadge(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.img_ad_badge),
        contentDescription = "Contains Ads",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
    )
}

@Composable
fun AppItemCardView(
    app: AppEntity,
    downloadState: DownloadEntity?,
    installedInfo: com.example.utils.ApkInstaller.InstalledAppInfo?,
    overriddenRating: String,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    purchased: Boolean = false,
    registered: Boolean = false,
    onBuyClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glass(textPrimary.luminance() > 0.5f, RoundedCornerShape(22.dp), elevation = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        // PERF: Material3's default Card elevation renders a soft shadow via
        // an extra graphics layer per card. With a visible border already
        // providing separation between rows, that shadow adds nothing here
        // but costs a compositing pass on every row, every frame it's on
        // screen — multiplied by however many rows a fast fling brings on
        // screen at once. Pinning it to 0 removes that cost outright.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Logo bounding box representing percentage ring on download trigger
                    Box(contentAlignment = Alignment.Center) {
                        AppLogo(
                            logoUrl = app.logo,
                            appName = app.name,
                            packageName = app.packageName,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        // Draw thin percentage progress circle directly around logo as specified
                        if (downloadState?.status == "DOWNLOADING") {
                            CircularProgressIndicator(
                                progress = downloadState.progress / 100f,
                                color = accentGreen,
                                trackColor = Color.Transparent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(58.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (app.hasAds) 55.dp else 0.dp) // Leave safety room for top-right Ad badge if name is long
                    ) {
                        Text(
                            text = app.name,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = app.developer,
                                color = textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Box(
                                modifier = Modifier
                                    .background(accentGreen.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = app.category.uppercase(),
                                    color = accentGreen,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating Star",
                                    tint = Color(0xFFF1A80A),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = overriddenRating,
                                    fontSize = 11.sp,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Divider(
                                modifier = Modifier
                                    .height(10.dp)
                                    .width(1.dp),
                                color = textSecondary.copy(alpha = 0.3f)
                            )

                            Text(
                                text = app.size,
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Contextual action button layout as per user version matching rules
                    Box(contentAlignment = Alignment.Center) {
                        val context = LocalContext.current
                        if (downloadState?.status == "DOWNLOADING") {
                            IconButton(
                                onClick = onActionClick,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel download",
                                    tint = textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            val isInstalled = remember(installedInfo) { installedInfo != null }
                            val hasUpdate = remember(isInstalled, app.versionCode, app.version, installedInfo) {
                                isInstalled && (
                                    app.versionCode > (installedInfo?.versionCode ?: 0L) ||
                                    (installedInfo?.versionName != null && !app.version.trim().equals(installedInfo.versionName.trim(), ignoreCase = true))
                                )
                            }

                            when {
                                app.isUpcoming -> {
                                    if (registered) {
                                        OutlinedButton(
                                            onClick = {},
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, accentGreen),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Text("Registered", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = onRegisterClick,
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF9C27B0),
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Pre-register", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                app.isPremium && !purchased && !isInstalled -> {
                                    Button(
                                        onClick = onBuyClick,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF9800),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        modifier = Modifier.height(32.dp).testTag("premium_buy_button_${app.id}")
                                    ) {
                                        Text(app.price.ifEmpty { "$1.99" }, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                downloadState?.status == "DOWNLOADED" -> {
                                    Button(
                                        onClick = onActionClick,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accentGreen,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("install_action_button_${app.id}")
                                    ) {
                                        Text("Install", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                isInstalled && hasUpdate -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Update Button
                                        Button(
                                            onClick = onActionClick,
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentGreen,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("update_action_button_${app.id}")
                                        ) {
                                            Text("Update", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Open Button
                                        OutlinedButton(
                                            onClick = { ApkInstaller.launchApp(context, app.packageName) },
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, accentGreen),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("open_action_button_${app.id}")
                                        ) {
                                            Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                isInstalled -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Open Button
                                        Button(
                                            onClick = { ApkInstaller.launchApp(context, app.packageName) },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFE6F4EA),
                                                contentColor = Color(0xFF01875F)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("open_action_button_${app.id}")
                                        ) {
                                            Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Uninstall Button
                                        if (LocalUninstallEnabled.current) {
                                            OutlinedButton(
                                                onClick = { ApkInstaller.uninstallApp(context, app.packageName) },
                                                shape = RoundedCornerShape(20.dp),
                                                border = BorderStroke(1.dp, Color(0xFFEF5350)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                                modifier = Modifier
                                                    .height(32.dp)
                                                    .testTag("uninstall_action_button_${app.id}")
                                            ) {
                                                Text("Uninstall", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = onActionClick,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accentGreen,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("install_action_button_${app.id}")
                                    ) {
                                        Text("Install", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Real-time progress bar description inside card
                if (downloadState?.status == "DOWNLOADING") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(textSecondary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Downloading: ",
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                // Show real-time downloading percentage
                                Text(
                                    text = "${downloadState.progress}%",
                                    color = accentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = downloadState.downloadSpeed,
                                color = accentGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = downloadState.progress / 100f,
                            color = accentGreen,
                            trackColor = textSecondary.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
            if (app.hasAds) {
                AdBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                )
            }
        }
    }
}

// ========================================================
// 6. MY LIBRARY STATEBOARD (Streamlined Play Manager)
// ========================================================
@Composable
fun LibraryTabContent(
    apps: List<AppEntity>,
    downloads: List<DownloadEntity>,
    installedAppsInfo: Map<String, com.example.utils.ApkInstaller.InstalledAppInfo>,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onAppClick: (AppEntity) -> Unit,
    onCancelClick: (String) -> Unit,
    onInstallClick: (DownloadEntity) -> Unit,
    onActionClick: (AppEntity) -> Unit
) {
    val context = LocalContext.current
    val installedAppsInCatalog = remember(apps, installedAppsInfo) {
        apps.filter { installedAppsInfo.containsKey(it.packageName) }
    }
    val activeDownloads = remember(downloads, installedAppsInfo) {
        downloads.filter { 
            (it.status == "DOWNLOADING" || it.status == "DOWNLOADED") && !installedAppsInfo.containsKey(it.packageName)
        }
    }
    val appsMap = remember(apps) { apps.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = com.example.view.LocalNavBarInset.current)
    ) {
        // Active downloads list with speed
        if (activeDownloads.isNotEmpty()) {
            item {
                Text(
                    text = "Active & In Progress (${activeDownloads.size})",
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            itemsIndexed(activeDownloads, key = { _, dl -> dl.id }) { index, dl ->
                val fullApp = appsMap[dl.id]
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppLogo(
                                logoUrl = fullApp?.logo ?: "",
                                appName = dl.name,
                                packageName = dl.packageName,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    dl.name,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    dl.packageName,
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            if (dl.status == "DOWNLOADING") {
                                OutlinedButton(
                                    onClick = { onCancelClick(dl.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(28.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                                ) {
                                    Text("Cancel", fontSize = 11.sp)
                                }
                            } else if (dl.status == "DOWNLOADED") {
                                Button(
                                    onClick = { onInstallClick(dl) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Install", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }

                        if (dl.status == "DOWNLOADING") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Progress: ${dl.progress}% (Speed: ${dl.downloadSpeed})",
                                    fontSize = 10.sp,
                                    color = accentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = dl.progress / 100f,
                                color = accentGreen,
                                trackColor = textSecondary.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }

        // Active Devices Installed packages list
        item {
            Text(
                text = "Installed Apps (${installedAppsInCatalog.size})",
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (installedAppsInCatalog.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No installed packages detected from our catalog index.",
                            color = textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(installedAppsInCatalog, key = { _, app -> app.id }) { index, app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppLogo(
                            logoUrl = app.logo,
                            appName = app.name,
                            packageName = app.packageName,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Developer: ${app.developer}",
                                color = textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Play Store Double Button layout UX
                        val installedInfo = remember(installedAppsInfo.keys, app.packageName) { installedAppsInfo[app.packageName] }
                        val hasUpdate = remember(installedInfo, app.versionCode, app.version) {
                            installedInfo != null && (
                                app.versionCode > installedInfo.versionCode ||
                                (!app.version.trim().equals(installedInfo.versionName.trim(), ignoreCase = true))
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (hasUpdate) {
                                // Real-time Store Version is newer -> Show Update and Open!
                                // Update Button
                                Button(
                                    onClick = { onActionClick(app) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("UPDATE", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                // Open Button
                                OutlinedButton(
                                    onClick = { ApkInstaller.launchApp(context, app.packageName) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                    border = BorderStroke(1.dp, accentGreen.copy(0.3f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("OPEN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                // Installed version is up-to-date -> Show Open and Uninstall!
                                // Uninstall
                                if (LocalUninstallEnabled.current) {
                                    OutlinedButton(
                                        onClick = { ApkInstaller.uninstallApp(context, app.packageName) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(0.3f)),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("UNINSTALL", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                }

                                // Open
                                Button(
                                    onClick = { ApkInstaller.launchApp(context, app.packageName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("OPEN", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

// ========================================================
// REUSABLE SUBMISSION CARD COMPOSABLE
// ========================================================
@Composable
fun DeveloperSubmissionCard(
    sub: com.example.data.SubmissionEntity,
    surfaceCol: Color,
    borderCol: Color,
    textPrimaryCol: Color,
    textSecondaryCol: Color,
    accentGreen: Color,
    isDarkMode: Boolean,
    onRequestUpdate: (com.example.data.SubmissionEntity) -> Unit,
    onEditOptions: (com.example.data.SubmissionEntity) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceCol),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sub.name.trim().take(1).uppercase(),
                        color = accentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sub.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimaryCol
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "v${sub.version}",
                            color = textSecondaryCol,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "•",
                            color = textSecondaryCol.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = sub.category,
                            color = textSecondaryCol,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val (statusLabel, statusColor, statusBg) = when {
                    sub.status.equals("approved", ignoreCase = true) || sub.status.equals("live", ignoreCase = true) -> {
                        Triple("Approved", Color(0xFF10B981), Color(0xFFE6F4EA))
                    }
                    sub.status.equals("rejected", ignoreCase = true) -> {
                        Triple("Rejected", Color(0xFFEF4444), Color(0xFFFEF2F2))
                    }
                    else -> {
                        Triple("In Review", Color(0xFFF59E0B), Color(0xFFFFFBEB))
                    }
                }

                val finalStatusBg = if (isDarkMode) statusColor.copy(alpha = 0.15f) else statusBg

                Box(
                    modifier = Modifier
                        .background(finalStatusBg, RoundedCornerShape(50))
                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            if (sub.status.equals("rejected", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF2C1616) else Color(0xFFFEF2F2)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "REJECTION FEEDBACK",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color(0xFFEF4444),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sub.feedback.ifBlank { "No feedback details provided by reviewer." },
                            fontSize = 11.sp,
                            color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onRequestUpdate(sub) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fix & Re-submit app details", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (sub.status.equals("approved", ignoreCase = true) || sub.status.equals("live", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onEditOptions(sub) },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit / Update App", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { onRequestUpdate(sub) },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, borderCol),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = accentGreen, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Modify Metadata", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textPrimaryCol)
                    }
                }
            }
        }
    }
}

// ========================================================
// 7. DEVELOPER PROFILE & REAL-TIME CONSOLE PANEL
// ========================================================
@Composable
fun ProfileTabContent(
    viewModel: com.example.viewmodel.StoreViewModel,
    isDarkMode: Boolean,
    isLoggedIn: Boolean,
    userName: String,
    userEmail: String,
    submissions: List<com.example.data.SubmissionEntity> = emptyList(),
    onTriggerSubmitForm: () -> Unit = {},
    onRefreshSubmissions: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onThemeToggle: () -> Unit,
    onUpdateDeveloperName: (String) -> Pair<Boolean, String> = { Pair(true, "") },
    onRequestUpdate: (com.example.data.SubmissionEntity) -> Unit = {},
    onShowAppDetails: (com.example.data.AppEntity) -> Unit = {},
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isAuthenticating by remember { mutableStateOf(false) }

    val sharedPrefs = remember(context) { context.getSharedPreferences("dark_store_prefs", android.content.Context.MODE_PRIVATE) }
    val devBioState by viewModel.devBio.collectAsStateWithLifecycle()
    val devBio = devBioState.ifBlank { "Independent developer building professional tools for the community." }
    var isEditingBio by remember { mutableStateOf(false) }
    var editedBio by remember(devBio) { mutableStateOf(devBio) }

    var selectedSubTab by remember { mutableStateOf("Live") }
    var activeDetailSubmission by remember { mutableStateOf<com.example.data.SubmissionEntity?>(null) }
    var editOptionsForApp by remember { mutableStateOf<com.example.data.SubmissionEntity?>(null) }
    var showUpdateScreenshotsFormFor by remember { mutableStateOf<com.example.data.SubmissionEntity?>(null) }
    var showPushUpdateFormFor by remember { mutableStateOf<com.example.data.SubmissionEntity?>(null) }

    LaunchedEffect(isLoggedIn, Unit) {
        if (isLoggedIn) {
            onRefreshSubmissions()
            viewModel.refreshDevelopers()
        }
    }

    val bgCol = if (isDarkMode) Color(0xFF13151C) else Color(0xFFF8FAFC)
    // Liquid-glass palette (translucent fill + light-catching edge) — dialogs keep an opaque surface
    val surfaceCol = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.70f)
    val borderCol = if (isDarkMode) Color.White.copy(alpha = 0.16f) else Color(0xFF334155).copy(alpha = 0.14f)
    val dialogCol = if (isDarkMode) Color(0xFF1B1F27) else Color.White
    val textPrimaryCol = textPrimary
    val textSecondaryCol = textSecondary

    Box(
        modifier = Modifier
            .fillMaxSize()
            
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = com.example.view.LocalNavBarInset.current)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Screen Title Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = com.example.view.tr("dev_console"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimaryCol,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLoggedIn) com.example.view.tr("dev_console_sub") else "Sandbox environment gateway",
                        fontSize = 11.sp,
                        color = textSecondaryCol,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoggedIn) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(surfaceCol)
                                .border(1.dp, borderCol, CircleShape)
                                .clickable {
                                    onRefreshSubmissions()
                                    Toast.makeText(context, "Syncing console databases...", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh data",
                                tint = accentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(surfaceCol)
                            .border(1.dp, borderCol, CircleShape)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = textSecondaryCol,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (isAuthenticating) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = accentGreen,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Connecting secure developer workspace...",
                        fontSize = 14.sp,
                        color = textSecondaryCol,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (isLoggedIn) {
                val isDeveloper by viewModel.isDeveloper.collectAsStateWithLifecycle()
                val devWebsite by viewModel.devWebsite.collectAsStateWithLifecycle()
                val devGithub by viewModel.devGithub.collectAsStateWithLifecycle()
                val devName by viewModel.devName.collectAsStateWithLifecycle()
                val isEmailVerified by viewModel.isEmailVerified.collectAsStateWithLifecycle()
                var isResendingVerification by remember { mutableStateOf(false) }

                if (!isEmailVerified) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = BorderStroke(1.dp, Color(0xFFFBBF24))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFF92400E),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Email not verified",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = "Verify $userEmail to secure your account.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    if (!isResendingVerification) {
                                        isResendingVerification = true
                                        viewModel.sendVerificationEmail { _, message ->
                                            isResendingVerification = false
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isResendingVerification
                            ) {
                                Text(
                                    text = if (isResendingVerification) "Sending..." else "Resend",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }

                if (!isDeveloper && userEmail != "guest@darkroot.io") {
                    val ownPhotoUrl by viewModel.profilePhotoUrl.collectAsStateWithLifecycle()
                    UserIdentityCard(
                        viewModel = viewModel,
                        userName = userName,
                        userEmail = userEmail,
                        photoUrl = ownPhotoUrl,
                        surfaceCol = surfaceCol,
                        borderCol = borderCol,
                        textPrimary = textPrimaryCol,
                        textSecondary = textSecondaryCol,
                        accent = accentGreen
                    )
                }

                // Following/Followers for non-developers only (developers get this
                // inside the Option 2 profile card below).
                if (!isDeveloper) {
                    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
                    val followerIds by viewModel.followerIds.collectAsStateWithLifecycle()
                    val allKnownUsers by viewModel.developers.collectAsStateWithLifecycle()
                    var showFollowersFollowingDialog by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .background(surfaceCol, RoundedCornerShape(14.dp))
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .clickable { showFollowersFollowingDialog = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${followingIds.size}", color = textPrimaryCol, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text(text = "Following", color = textSecondaryCol, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(borderCol)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${followerIds.size}", color = textPrimaryCol, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text(text = "Followers", color = textSecondaryCol, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (showFollowersFollowingDialog) {
                        FollowersFollowingDialog(
                            viewModel = viewModel,
                            isDarkMode = isDarkMode,
                            developers = allKnownUsers,
                            onDismiss = { showFollowersFollowingDialog = false }
                        )
                    }
                }

                if (!isDeveloper) {
                    if (userEmail == "guest@darkroot.io") {
                        Card(
                            modifier = Modifier.fillMaxWidth().glass(isDarkMode, RoundedCornerShape(24.dp), 3.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(accentGreen.copy(alpha = 0.25f), Color(0xFF2563EB).copy(alpha = 0.1f))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Guest user icon",
                                        tint = accentGreen,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Guest Mode Active",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimaryCol
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "You are currently browsing the DarkRoot Store as a guest. To submit applications, register as a publisher, and manage software releases, please sign out of guest mode and create a secure developer account.",
                                    fontSize = 13.sp,
                                    color = textSecondaryCol,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        onLogout()
                                        Toast.makeText(context, "Welcome back to login! Please sign up or sign in to configure your profile.", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("guest_upgrade_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentGreen,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Sign In or Register Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        var websiteInput by remember { mutableStateOf("") }
                        var githubInput by remember { mutableStateOf("") }
                        var bioInput by remember { mutableStateOf("") }
                        var registering by remember { mutableStateOf(false) }
                        val resolvedDevName = remember(userName, devName, userEmail) {
                            devName.ifBlank {
                                userName.ifBlank {
                                    userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                                }
                            }
                        }
                        var devNameInput by remember(resolvedDevName) { mutableStateOf(resolvedDevName) }

                        Card(
                            modifier = Modifier.fillMaxWidth().glass(isDarkMode, RoundedCornerShape(24.dp), 3.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(accentGreen.copy(alpha = 0.25f), Color(0xFF2563EB).copy(alpha = 0.1f))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = "Become Developer",
                                        tint = accentGreen,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Become a Verified Developer",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimaryCol
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Submit your developer profile to unlock application submissions, update releases, and real-time App Store play console monitoring.",
                                    fontSize = 12.sp,
                                    color = textSecondaryCol,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                OutlinedTextField(
                                    value = devNameInput,
                                    onValueChange = { devNameInput = it },
                                    label = { Text("Developer Name", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. Acme Software") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dev_registration_name_input"),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = "Developer Identity icon",
                                            tint = accentGreen
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = textPrimaryCol,
                                        unfocusedTextColor = textPrimaryCol,
                                        focusedBorderColor = accentGreen,
                                        unfocusedBorderColor = borderCol,
                                        focusedLabelColor = accentGreen,
                                        unfocusedLabelColor = textSecondaryCol
                                    )
                                )

                                if (devNameInput.contains("DarkRoot", ignoreCase = true) || resolvedDevName.contains("DarkRoot", ignoreCase = true)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .background(Color(0xFFFEF3C7).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0xFFD97706).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Warning",
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Accidentally registered as 'DarkRoot'? Edit the Developer Name above to enter your correct name!",
                                                color = textPrimaryCol,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = websiteInput,
                                    onValueChange = { websiteInput = it },
                                    label = { Text("Website (Optional)", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. https://mycoolapps.com") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dev_registration_website_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = githubInput,
                                    onValueChange = { githubInput = it },
                                    label = { Text("GitHub Username (Optional)", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. github_dev_user") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dev_registration_github_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = bioInput,
                                    onValueChange = { if (it.length <= 160) bioInput = it },
                                    label = { Text("Developer Bio / Headline (Optional)", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. Building open-source utilities and tools...") },
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dev_registration_bio_input")
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                Button(
                                    onClick = {
                                        if (devNameInput.isBlank()) {
                                            Toast.makeText(context, "Please enter a valid developer name.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        registering = true
                                        viewModel.registerDeveloper(
                                            devName = devNameInput.trim(),
                                            website = websiteInput.trim(),
                                            github = githubInput.trim(),
                                            bio = bioInput.trim()
                                        ) { success, msg ->
                                            registering = false
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    enabled = !registering,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("dev_registration_submit_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentGreen,
                                        contentColor = Color.White
                                    )
                                ) {
                                    if (registering) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Register Developer Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF2C1616) else Color(0xFFFEF2F2)
                        ),
                        border = BorderStroke(
                            1.dp, 
                            if (isDarkMode) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFFFCA5A5).copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLogout()
                                    Toast.makeText(context, if (userEmail == "guest@darkroot.io") "Exited guest session." else "Logged out of safe developer session.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFB91C1C), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (userEmail == "guest@darkroot.io") "Exit Guest Session" else "Sign Out Developer Session",
                                color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // Option 2 — Cover banner profile + Links + About + Edit
                    val profilePhotoUrl by viewModel.profilePhotoUrl.collectAsStateWithLifecycle()
                    val devLocationVal by viewModel.devLocation.collectAsStateWithLifecycle()
                    val liveDevBio by viewModel.devBio.collectAsStateWithLifecycle()
                    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
                    val followerIds by viewModel.followerIds.collectAsStateWithLifecycle()
                    val allKnownUsers by viewModel.developers.collectAsStateWithLifecycle()
                    var showEditProfile by remember { mutableStateOf(false) }
                    var showOwnPhotoViewer by remember { mutableStateOf(false) }
                    var showFollowersFollowingDialog by remember { mutableStateOf(false) }

                    // Cover + identity card
                    Card(
                        modifier = Modifier.fillMaxWidth().glass(isDarkMode, RoundedCornerShape(20.dp), 3.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = null
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Gradient cover banner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                accentGreen,
                                                Color(0xFF6366F1),
                                                Color(0xFF06B6D4).copy(alpha = 0.7f)
                                            )
                                        )
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 20.dp)
                                        .size(64.dp)
                                )
                                // Edit chip on banner
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .clickable { showEditProfile = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Avatar overlapping banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .offset(y = (-28).dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(surfaceCol)
                                        .border(3.dp, surfaceCol, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(accentGreen.copy(alpha = 0.15f))
                                            .clickable(enabled = profilePhotoUrl.isNotBlank()) { showOwnPhotoViewer = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (profilePhotoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = profilePhotoUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = (devName.ifBlank { userName }).take(1).uppercase(),
                                                color = accentGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 28.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = devName.ifBlank { userName },
                                            color = textPrimaryCol,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = accentGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = if (devGithub.isNotBlank()) "@${devGithub.trim().removePrefix("@")}" else userEmail.substringBefore("@").let { "@$it" },
                                        color = textSecondaryCol,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Following / Followers under avatar row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = (-12).dp)
                                    .padding(horizontal = 16.dp)
                                    .clickable { showFollowersFollowingDialog = true },
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "${followingIds.size}",
                                        color = accentGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text("Following", color = textSecondaryCol, fontSize = 11.sp)
                                }
                                Column {
                                    Text(
                                        text = "${followerIds.size}",
                                        color = accentGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text("Followers", color = textSecondaryCol, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    if (showFollowersFollowingDialog) {
                        FollowersFollowingDialog(
                            viewModel = viewModel,
                            isDarkMode = isDarkMode,
                            developers = allKnownUsers,
                            onDismiss = { showFollowersFollowingDialog = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Links card
                    Card(
                        modifier = Modifier.fillMaxWidth().glass(isDarkMode, RoundedCornerShape(16.dp), 3.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = null
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Link, null, tint = accentGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Links", color = textPrimaryCol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Website
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val url = devWebsite.trim()
                                        if (url.isNotBlank()) {
                                            try {
                                                val full = if (url.startsWith("http")) url else "https://$url"
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(full)))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Cannot open website", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            showEditProfile = true
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Language, null, tint = accentGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Website", color = textPrimaryCol, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = devWebsite.ifBlank { "Not set — tap Edit" },
                                        color = textSecondaryCol,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                                Icon(Icons.Default.OpenInNew, null, tint = textSecondaryCol, modifier = Modifier.size(14.dp))
                            }

                            HorizontalDivider(color = borderCol.copy(alpha = 0.7f))

                            // GitHub
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val gh = devGithub.trim().removePrefix("@")
                                        if (gh.isNotBlank()) {
                                            try {
                                                val full = if (gh.startsWith("http")) gh else "https://github.com/$gh"
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(full)))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Cannot open GitHub", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            showEditProfile = true
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Code, null, tint = accentGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("GitHub", color = textPrimaryCol, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (devGithub.isNotBlank()) {
                                            val g = devGithub.trim().removePrefix("@")
                                            if (g.startsWith("http")) g else "github.com/$g"
                                        } else "Not set — tap Edit",
                                        color = textSecondaryCol,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                                Icon(Icons.Default.OpenInNew, null, tint = textSecondaryCol, modifier = Modifier.size(14.dp))
                            }

                            HorizontalDivider(color = borderCol.copy(alpha = 0.7f))

                            // Email
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Email, null, tint = accentGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Email", color = textPrimaryCol, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(userEmail, color = textSecondaryCol, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // About card
                    Card(
                        modifier = Modifier.fillMaxWidth().glass(isDarkMode, RoundedCornerShape(16.dp), 3.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = null
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = accentGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("About", color = textPrimaryCol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = liveDevBio.ifBlank { "Add a short bio so users know who you are." },
                                color = if (liveDevBio.isBlank()) textSecondaryCol.copy(alpha = 0.7f) else textSecondaryCol,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            if (devLocationVal.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = textSecondaryCol, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(devLocationVal, color = textSecondaryCol, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Full Edit Profile dialog
                    if (showEditProfile) {
                        var editName by remember { mutableStateOf(devName.ifBlank { userName }) }
                        var editBio by remember { mutableStateOf(liveDevBio) }
                        var editWebsite by remember { mutableStateOf(devWebsite) }
                        var editGithub by remember { mutableStateOf(devGithub) }
                        var editLocation by remember { mutableStateOf(devLocationVal) }
                        var isSaving by remember { mutableStateOf(false) }

                        Dialog(
                            onDismissRequest = { if (!isSaving) showEditProfile = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 620.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = dialogCol),
                                border = BorderStroke(1.dp, borderCol)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Edit profile", color = textPrimaryCol, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        IconButton(onClick = { showEditProfile = false }, enabled = !isSaving) {
                                            Icon(Icons.Default.Close, null, tint = textSecondaryCol)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val fieldColors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accentGreen,
                                            unfocusedBorderColor = borderCol,
                                            focusedTextColor = textPrimaryCol,
                                            unfocusedTextColor = textPrimaryCol,
                                            cursorColor = accentGreen,
                                            focusedLabelColor = accentGreen,
                                            unfocusedLabelColor = textSecondaryCol
                                        )
                                        ProfilePhotoEditor(
                                            viewModel = viewModel,
                                            photoUrl = profilePhotoUrl,
                                            initial = devName.ifBlank { userName },
                                            accent = accentGreen,
                                            textSecondary = textSecondaryCol,
                                            dialogColor = dialogCol
                                        )
                                        OutlinedTextField(
                                            value = editName,
                                            onValueChange = { editName = it },
                                            label = { Text("Display name") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = fieldColors
                                        )
                                        OutlinedTextField(
                                            value = editBio,
                                            onValueChange = { if (it.length <= 200) editBio = it },
                                            label = { Text("Bio") },
                                            minLines = 3,
                                            maxLines = 4,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = fieldColors,
                                            supportingText = { Text("${editBio.length}/200", color = textSecondaryCol, fontSize = 11.sp) }
                                        )
                                        OutlinedTextField(
                                            value = editLocation,
                                            onValueChange = { editLocation = it },
                                            label = { Text("Location") },
                                            singleLine = true,
                                            placeholder = { Text("e.g. Kathmandu, Nepal") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = fieldColors
                                        )
                                        OutlinedTextField(
                                            value = editWebsite,
                                            onValueChange = { editWebsite = it },
                                            label = { Text("Website URL") },
                                            singleLine = true,
                                            placeholder = { Text("https://yoursite.com") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = fieldColors
                                        )
                                        OutlinedTextField(
                                            value = editGithub,
                                            onValueChange = { editGithub = it },
                                            label = { Text("GitHub username or URL") },
                                            singleLine = true,
                                            placeholder = { Text("username") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = fieldColors
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            isSaving = true
                                            viewModel.updateDeveloperField("name", editName) { okName, msgName ->
                                                viewModel.updateDeveloperField("bio", editBio) { _, _ ->
                                                    viewModel.updateDeveloperField("website", editWebsite) { _, _ ->
                                                        viewModel.updateDeveloperField("github", editGithub) { _, _ ->
                                                            viewModel.updateDeveloperField("location", editLocation) { ok, msg ->
                                                                isSaving = false
                                                                showEditProfile = false
                                                                Toast.makeText(
                                                                    context,
                                                                    if (ok || okName) "Profile updated" else (msg.ifBlank { msgName }),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isSaving,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                                    ) {
                                        Text(if (isSaving) "Saving…" else "Save changes", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (showOwnPhotoViewer && profilePhotoUrl.isNotBlank()) {
                        FullScreenPhotoViewer(
                            photoUrl = profilePhotoUrl,
                            title = devName.ifBlank { userName },
                            onDismiss = { showOwnPhotoViewer = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Add New App
                    Button(
                        onClick = { onTriggerSubmitForm() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_trigger_user_submission_form"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add New App", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                Spacer(modifier = Modifier.height(24.dp))
                var selectedSubTab by remember { mutableStateOf("Approved") }
                val devAppGroups = remember(submissions) { buildDeveloperAppGroups(submissions) }
                val approvedGroups = devAppGroups.filter { it.liveSub != null }
                val pendingGroups = devAppGroups.filter { it.pendingSub != null }
                val rejectedGroups = devAppGroups.filter { it.rejectedSub != null }

                // My Work: three clear status buckets — Approved / Pending / Rejected —
                // each showing only the submissions actually in that state. (Previously
                // this was "My Work" / "Followers" / "Post" — leftover labels from a
                // social-profile template, where "Followers" and "My Work" both showed
                // the same approved-apps list, and "Post" was actually the rejected list.)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceCol, RoundedCornerShape(12.dp))
                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    // Tab 1: Approved
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSubTab == "Approved") accentGreen else Color.Transparent)
                            .clickable { selectedSubTab = "Approved" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (selectedSubTab == "Approved") Color.White else accentGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Approved",
                                color = if (selectedSubTab == "Approved") Color.White else textPrimaryCol,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Tab 2: Pending
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSubTab == "Pending") accentGreen else Color.Transparent)
                            .clickable { selectedSubTab = "Pending" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = if (selectedSubTab == "Pending") Color.White else accentGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Pending",
                                color = if (selectedSubTab == "Pending") Color.White else textPrimaryCol,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Tab 3: Rejected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSubTab == "Rejected") accentGreen else Color.Transparent)
                            .clickable { selectedSubTab = "Rejected" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = if (selectedSubTab == "Rejected") Color.White else accentGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Rejected",
                                color = if (selectedSubTab == "Rejected") Color.White else textPrimaryCol,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                val activeList = when (selectedSubTab) {
                    "Approved" -> approvedGroups
                    "Pending" -> pendingGroups
                    else -> rejectedGroups
                }

                if (activeList.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp).glass(isDarkMode, RoundedCornerShape(20.dp), 3.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = when (selectedSubTab) {
                                    "Approved" -> Icons.Default.CheckCircle
                                    "Pending" -> Icons.Default.Build
                                    else -> Icons.Default.Close
                                },
                                contentDescription = "Empty submissions",
                                tint = textSecondaryCol.copy(alpha = 0.5f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = when (selectedSubTab) {
                                    "Approved" -> "No approved applications yet"
                                    "Pending" -> "No pending submissions"
                                    else -> "No rejected submissions"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryCol
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (selectedSubTab) {
                                    "Approved" -> "Apps you submit will appear here once approved and live."
                                    "Pending" -> "Register a new software catalog item above to initialize tracking logs."
                                    else -> "Apps that were not approved will appear here with admin feedback."
                                },
                                fontSize = 11.sp,
                                color = textSecondaryCol,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when (selectedSubTab) {
                                    "Approved" -> "Approved"
                                    "Pending" -> "Pending"
                                    else -> "Rejected"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryCol
                            )
                            Text(
                                text = when (selectedSubTab) {
                                    "Approved" -> "Apps published and live on DarkRoot Store"
                                    "Pending" -> "Apps awaiting administrator review"
                                    else -> "Apps that were not approved"
                                },
                                fontSize = 11.sp,
                                color = textSecondaryCol
                            )
                        }
                    }
                    activeList.chunked(2).forEach { rowGroups ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowGroups.forEach { group ->
                                Box(modifier = Modifier.weight(1f)) {
                                    DeveloperAppGroupCard(
                                        group = group,
                                        surfaceCol = surfaceCol,
                                        borderCol = borderCol,
                                        textPrimaryCol = textPrimaryCol,
                                        textSecondaryCol = textSecondaryCol,
                                        accentGreen = accentGreen,
                                        isDarkMode = isDarkMode,
                                        onRequestUpdate = onRequestUpdate,
                                        onEditOptions = { editOptionsForApp = it },
                                        onClick = { activeDetailSubmission = group.latestSub }
                                    )
                                }
                            }
                            if (rowGroups.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ========================================================
                // POPUP 1: SUBMISSION DETAILS VIEW OVERLAY (when clicked)
                // ========================================================
                if (activeDetailSubmission != null) {
                    val sub = activeDetailSubmission!!
                    Dialog(
                        onDismissRequest = { activeDetailSubmission = null },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = true
                        )
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = dialogCol
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(dialogCol)
                            ) {
                                // Dynamic Navigation Header / App Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { activeDetailSubmission = null },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Go back",
                                            tint = textPrimaryCol
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Conformity Audit Center",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = accentGreen,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "Submission Report",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = textPrimaryCol
                                        )
                                    }
                                    
                                    val statusText = sub.status.uppercase()
                                    val statusColor = when (sub.status.lowercase()) {
                                        "approved", "live" -> Color(0xFF10B981)
                                        "rejected" -> Color(0xFFEF4444)
                                        else -> Color(0xFFFFB300)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(statusColor, CircleShape)
                                            )
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    // 1. Hero / App Identity Panel
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, borderCol)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            // App Icon Placeholder with dynamic gradient
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(accentGreen, Color(0xFF2563EB))
                                                        ),
                                                        RoundedCornerShape(16.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (sub.logo.isNotBlank()) "" else sub.name.take(1).uppercase(),
                                                    color = Color.White,
                                                    fontSize = 32.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                if (sub.logo.isNotBlank()) {
                                                    AsyncImage(
                                                        model = sub.logo,
                                                        contentDescription = "App Logo",
                                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sub.name,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = textPrimaryCol
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = accentGreen,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "By ${sub.developer}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = textPrimaryCol
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Sender: ${sub.submittedBy}",
                                                    fontSize = 11.sp,
                                                    color = textSecondaryCol
                                                )
                                            }
                                        }
                                    }

                                    // 2. Timeline Pipeline Tracker Panel
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "VERIFICATION PIPELINE PROGRESS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = textSecondaryCol,
                                            letterSpacing = 0.6.sp
                                        )
                                        
                                        val isApproved = sub.status.equals("approved", ignoreCase = true) || sub.status.equals("live", ignoreCase = true)
                                        val isRejected = sub.status.equals("rejected", ignoreCase = true)
                                        val isPending = !isApproved && !isRejected

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.4f) else Color(0xFFF1F5F9).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                                                .padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Step 1: Dispatched
                                            TimelineStepRow(
                                                title = "Application Dispatched",
                                                subtitle = "Software package registered & indexed into evaluation queue.",
                                                statusIcon = Icons.Default.CheckCircle,
                                                statusColor = accentGreen,
                                                isLast = false,
                                                textPrimaryCol = textPrimaryCol,
                                                textSecondaryCol = textSecondaryCol,
                                                borderCol = borderCol
                                            )

                                            // Step 2: Evaluation
                                            val step2Icon = if (isApproved || isRejected) Icons.Default.CheckCircle else Icons.Default.Refresh
                                            val step2Color = if (isApproved || isRejected) accentGreen else Color(0xFFFFB300)
                                            val step2Desc = if (isApproved || isRejected) "Console administrative clearance completed successfully." else "Auditors are evaluating targets, build parameters, and policy constraints."
                                            TimelineStepRow(
                                                title = "Evaluation Review",
                                                subtitle = step2Desc,
                                                statusIcon = step2Icon,
                                                statusColor = step2Color,
                                                isLast = false,
                                                textPrimaryCol = textPrimaryCol,
                                                textSecondaryCol = textSecondaryCol,
                                                borderCol = borderCol
                                            )

                                            // Step 3: Deployment Result
                                            val step3Icon = when {
                                                isApproved -> Icons.Default.CheckCircle
                                                isRejected -> Icons.Default.Warning
                                                else -> Icons.Default.Info
                                            }
                                            val step3Color = when {
                                                isApproved -> Color(0xFF10B981)
                                                isRejected -> Color(0xFFEF4444)
                                                else -> textSecondaryCol.copy(alpha = 0.5f)
                                            }
                                            val step3Desc = when {
                                                isApproved -> "Live on marketplace storefront for catalog distributions."
                                                isRejected -> "Audit declined. Refusal log issued with details below."
                                                else -> "Pending deployment and final storefront compilation."
                                            }
                                            TimelineStepRow(
                                                title = "Deployment Stage",
                                                subtitle = step3Desc,
                                                statusIcon = step3Icon,
                                                statusColor = step3Color,
                                                isLast = true,
                                                textPrimaryCol = textPrimaryCol,
                                                textSecondaryCol = textSecondaryCol,
                                                borderCol = borderCol
                                            )
                                        }
                                    }

                                    // 3. Decline/Feedback alert box (if rejected)
                                    if (sub.status.equals("rejected", ignoreCase = true)) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isDarkMode) Color(0xFF3B1E1E) else Color(0xFFFFF1F1)
                                            ),
                                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                                        ) {
                                            Column(modifier = Modifier.padding(18.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        "DECLINATION CONSTRAINTS FEEDBACK",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFEF4444),
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = sub.feedback.ifBlank { "No detailed critique logged by administrator." },
                                                    fontSize = 13.sp,
                                                    color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B),
                                                    lineHeight = 18.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // 4. Technical specifications Grid
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "TECHNICAL SPECIFICATIONS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = textSecondaryCol,
                                            letterSpacing = 0.6.sp
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    MetadataBadge(
                                                        icon = Icons.Default.Info,
                                                        label = "PACKAGE IDENTIFIER",
                                                        value = sub.packageName,
                                                        accentColor = accentGreen,
                                                        dialogCol = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF1F5F9).copy(alpha = 0.6f),
                                                        borderCol = borderCol,
                                                        textPrimaryCol = textPrimaryCol,
                                                        textSecondaryCol = textSecondaryCol
                                                    )
                                                }
                                                Box(modifier = Modifier.weight(1f)) {
                                                    MetadataBadge(
                                                        icon = Icons.Default.Build,
                                                        label = "BUILD VERSION",
                                                        value = "v${sub.version}",
                                                        accentColor = accentGreen,
                                                        dialogCol = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF1F5F9).copy(alpha = 0.6f),
                                                        borderCol = borderCol,
                                                        textPrimaryCol = textPrimaryCol,
                                                        textSecondaryCol = textSecondaryCol
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    MetadataBadge(
                                                        icon = Icons.Default.Menu,
                                                        label = "STORE CATEGORY",
                                                        value = sub.category,
                                                        accentColor = accentGreen,
                                                        dialogCol = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF1F5F9).copy(alpha = 0.6f),
                                                        borderCol = borderCol,
                                                        textPrimaryCol = textPrimaryCol,
                                                        textSecondaryCol = textSecondaryCol
                                                    )
                                                }
                                                Box(modifier = Modifier.weight(1f)) {
                                                    MetadataBadge(
                                                        icon = Icons.Default.Check,
                                                        label = "MONETIZATION ADS",
                                                        value = if (sub.hasAds) "Contains Advertisements" else "Clean Build / No Ads",
                                                        accentColor = accentGreen,
                                                        dialogCol = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF1F5F9).copy(alpha = 0.6f),
                                                        borderCol = borderCol,
                                                        textPrimaryCol = textPrimaryCol,
                                                        textSecondaryCol = textSecondaryCol
                                                    )
                                                }
                                            }
                                            if (sub.apkUrl.isNotBlank()) {
                                                MetadataBadge(
                                                    icon = Icons.Default.Share,
                                                    label = "DISTRIBUTION SOURCE URL (APK / TARGET)",
                                                    value = sub.apkUrl,
                                                    accentColor = Color(0xFF2563EB),
                                                    dialogCol = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFF1F5F9).copy(alpha = 0.6f),
                                                    borderCol = borderCol,
                                                    textPrimaryCol = textPrimaryCol,
                                                    textSecondaryCol = textSecondaryCol
                                                )
                                            }
                                        }
                                    }

                                    // 5. App Logs & Description
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "MANIFEST SUMMARY & RELEASE NOTES",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = textSecondaryCol,
                                            letterSpacing = 0.6.sp
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.4f) else Color(0xFFF1F5F9).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = sub.description.ifBlank { "No detailed release notes or catalogued description provided with this submission build." },
                                                fontSize = 13.sp,
                                                color = textPrimaryCol,
                                                lineHeight = 19.sp
                                            )
                                        }
                                    }

                                    // 6. Graphical Screenshots Carousel
                                    if (sub.screenshots.isNotBlank()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = "SUBMITTED GRAPHICAL SCREENSHOTS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = textSecondaryCol,
                                                letterSpacing = 0.6.sp
                                            )

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                sub.screenshots.split(",").forEach { url ->
                                                    if (url.isNotBlank()) {
                                                        Card(
                                                            shape = RoundedCornerShape(16.dp),
                                                            modifier = Modifier.size(width = 160.dp, height = 260.dp),
                                                            border = BorderStroke(1.dp, borderCol)
                                                        ) {
                                                            AsyncImage(
                                                                model = url,
                                                                contentDescription = "screenshot",
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Bottom Footer Area with close button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                        .padding(16.dp)
                                ) {
                                    Button(
                                        onClick = { activeDetailSubmission = null },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Text(
                                            text = "DISMISS REPORT AUDIT",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ========================================================
                // POPUP 2: EDIT OPTIONS POPUP (edited screenshot & push update)
                // ========================================================
                if (editOptionsForApp != null) {
                    val app = editOptionsForApp!!
                    Dialog(onDismissRequest = { editOptionsForApp = null }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = dialogCol),
                            border = BorderStroke(1.dp, borderCol)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Update Application Profile",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = accentGreen
                                )
                                Text(
                                    text = "Configure adjustments or push software update for ${app.name}.",
                                    fontSize = 14.sp,
                                    color = textPrimaryCol,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Button 1: Edited Screenshot
                                Button(
                                    onClick = {
                                        showUpdateScreenshotsFormFor = app
                                        editOptionsForApp = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Update Screenshots", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                // Button 2: Push Update
                                Button(
                                    onClick = {
                                        showPushUpdateFormFor = app
                                        editOptionsForApp = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Push Version Update", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                TextButton(onClick = { editOptionsForApp = null }) {
                                    Text("Cancel", color = textSecondaryCol, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ========================================================
                // POPUP 3: SCREENSHOTS UPDATE FORM DIALOG
                // ========================================================
                // ========================================================
                // POPUP 3: SCREENSHOTS UPDATE FORM DIALOG
                // ========================================================
                if (showUpdateScreenshotsFormFor != null) {
                    val app = showUpdateScreenshotsFormFor!!
                    
                    // Use a state list of screenshots initialized with the current screenshots of the app
                    val screenshotsList = remember(app) {
                        val list = app.screenshots.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        mutableStateListOf<String>().apply {
                            if (list.isEmpty()) {
                                add("") // Ensure at least one empty slot is shown if list is empty
                            } else {
                                addAll(list)
                            }
                        }
                    }

                    // A map to track the upload state of each index
                    val ssUploadingStates = remember { mutableStateMapOf<Int, Boolean>() }
                    var targetSlot by remember { mutableStateOf(-1) }

                    val pickScreenshotLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uri ->
                        if (uri != null && targetSlot >= 0 && targetSlot < screenshotsList.size) {
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
                                                screenshotsList[slot] = url
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
                            colors = CardDefaults.cardColors(containerColor = dialogCol),
                            border = BorderStroke(1.dp, borderCol)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Manage & Update Screenshots",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = textPrimaryCol,
                                            letterSpacing = (-0.3).sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Play Console Asset Management",
                                            fontSize = 11.sp,
                                            color = accentGreen,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { showUpdateScreenshotsFormFor = null },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(borderCol.copy(alpha = 0.3f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textPrimaryCol, modifier = Modifier.size(16.dp))
                                    }
                                }
                                
                                Text(
                                    text = "Configure high-fidelity image slots below. You can either select files from your device to upload to safe cloud-hosted storage or paste direct web-accessible URL addresses directly. Replaces current assets.",
                                    fontSize = 11.sp,
                                    color = textSecondaryCol,
                                    lineHeight = 16.sp
                                )

                                Divider(color = borderCol.copy(alpha = 0.5f))

                                // Dynamic list of screenshots
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    screenshotsList.forEachIndexed { index, value ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = borderCol.copy(alpha = 0.12f)),
                                            border = BorderStroke(1.dp, borderCol.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                // Preview Box
                                                Card(
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clickable {
                                                            targetSlot = index
                                                            pickScreenshotLauncher.launch("image/*")
                                                        },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = CardDefaults.cardColors(containerColor = borderCol.copy(alpha = 0.2f)),
                                                    border = BorderStroke(1.dp, borderCol.copy(alpha = 0.4f))
                                                ) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        if (ssUploadingStates[index] == true) {
                                                            CircularProgressIndicator(color = accentGreen, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                        } else if (value.isNotBlank()) {
                                                            AsyncImage(
                                                                model = value,
                                                                contentDescription = "screenshot preview",
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = accentGreen, modifier = Modifier.size(20.dp))
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text("Upload Image", fontSize = 9.sp, color = textSecondaryCol, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }

                                                // URL Input + Actions
                                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Screenshot #${index + 1}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = textPrimaryCol
                                                        )
                                                        // Only allow deletion if there's at least one screenshot slot remaining
                                                        if (screenshotsList.size > 1) {
                                                            IconButton(
                                                                onClick = {
                                                                    screenshotsList.removeAt(index)
                                                                    ssUploadingStates.remove(index)
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = "Delete slot", tint = Color.Red, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                    OutlinedTextField(
                                                        value = value,
                                                        onValueChange = { newValue ->
                                                            screenshotsList[index] = newValue
                                                        },
                                                        placeholder = { Text("Or paste image/screenshot URL directly...", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = accentGreen,
                                                            unfocusedBorderColor = borderCol
                                                        ),
                                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Add New Screenshot Button
                                    Button(
                                        onClick = {
                                            screenshotsList.add("")
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accentGreen.copy(alpha = 0.12f),
                                            contentColor = accentGreen
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.3f))
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add New Screenshot Slot", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Divider(color = borderCol.copy(alpha = 0.5f))

                                Button(
                                    onClick = {
                                        val combined = screenshotsList.map { it.trim() }.filter { it.isNotBlank() }.joinToString(",")
                                        if (combined.isBlank()) {
                                            Toast.makeText(context, "At least one screenshot is required to update.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        viewModel.submitAppForReview(
                                            name = app.name,
                                            packageName = app.packageName,
                                            description = app.description,
                                            apkUrl = app.apkUrl,
                                            screenshots = combined,
                                            logo = app.logo,
                                            category = app.category,
                                            version = app.version,
                                            hasAds = app.hasAds,
                                            versionCode = app.versionCode,
                                            changelog = app.changelog,
                                            videoUrl = app.videoUrl
                                        ) { success, msg ->
                                            Toast.makeText(context, msg ?: "Screenshots update submitted for review", Toast.LENGTH_SHORT).show()
                                            if (success) {
                                                showUpdateScreenshotsFormFor = null
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text("SUBMIT FOR AUDIT REVIEW", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }

                                TextButton(
                                    onClick = { showUpdateScreenshotsFormFor = null },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Cancel", color = textSecondaryCol, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ========================================================
                // POPUP 4: PUSH VERSION UPDATE FORM DIALOG
                // ========================================================
                if (showPushUpdateFormFor != null) {
                    val app = showPushUpdateFormFor!!
                    var verInput by remember { mutableStateOf(app.version) }
                    var apkInput by remember { mutableStateOf(app.apkUrl) }
                    var descInput by remember { mutableStateOf(app.description) }
                    var catInput by remember { mutableStateOf(app.category) }
                    var adsInput by remember { mutableStateOf(app.hasAds) }

                    Dialog(onDismissRequest = { showPushUpdateFormFor = null }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = dialogCol),
                            border = BorderStroke(1.dp, borderCol)
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
                                    color = textPrimaryCol
                                )
                                Text(
                                    text = "Update the software version name, release logs (changelog details), and APK links. This triggers immediate admin queue auditing verification.",
                                    fontSize = 11.sp,
                                    color = textSecondaryCol,
                                    lineHeight = 16.sp
                                )

                                Divider(color = borderCol.copy(alpha = 0.5f))

                                // Inputs
                                OutlinedTextField(
                                    value = verInput,
                                    onValueChange = { verInput = it },
                                    label = { Text("Version Name *") },
                                    placeholder = { Text("e.g. v2.1.0") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accentGreen,
                                        unfocusedBorderColor = borderCol
                                    )
                                )

                                OutlinedTextField(
                                    value = apkInput,
                                    onValueChange = { apkInput = it },
                                    label = { Text("APK Executable Link *") },
                                    placeholder = { Text("e.g. https://...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accentGreen,
                                        unfocusedBorderColor = borderCol
                                    )
                                )
                                if (apkInput.contains("drive.google.com", ignoreCase = true) || apkInput.contains("docs.google.com", ignoreCase = true)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE0F2FE), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Drive Support",
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Google Drive link detected! Clean direct downloads and virus scans confirmation bypass are fully supported and automated.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF0369A1),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = catInput,
                                    onValueChange = { catInput = it },
                                    label = { Text("App Category") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accentGreen,
                                        unfocusedBorderColor = borderCol
                                    )
                                )

                                OutlinedTextField(
                                    value = descInput,
                                    onValueChange = { descInput = it },
                                    label = { Text("Release Notes / Changelog *") },
                                    placeholder = { Text("e.g. Fixed core bugs and updated UI components.") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accentGreen,
                                        unfocusedBorderColor = borderCol
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Contains Advertisements", fontSize = 12.sp, color = textPrimaryCol)
                                    Switch(
                                        checked = adsInput,
                                        onCheckedChange = { adsInput = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = accentGreen)
                                    )
                                }

                                Divider(color = borderCol.copy(alpha = 0.5f))

                                Button(
                                    onClick = {
                                        if (verInput.isNotBlank() && apkInput.isNotBlank() && descInput.isNotBlank()) {
                                            viewModel.submitAppForReview(
                                                name = app.name,
                                                packageName = app.packageName,
                                                description = descInput,
                                                apkUrl = apkInput,
                                                screenshots = app.screenshots,
                                                logo = app.logo,
                                                category = catInput,
                                                version = verInput,
                                                hasAds = adsInput,
                                                versionCode = app.versionCode,
                                                changelog = app.changelog,
                                                videoUrl = app.videoUrl
                                            ) { success, msg ->
                                                Toast.makeText(context, msg ?: "Version update queued for Admin audit", Toast.LENGTH_SHORT).show()
                                                if (success) {
                                                    showPushUpdateFormFor = null
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "All marked fields (*) are required.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(44.dp)
                                ) {
                                    Text("SUBMIT FOR AUDIT REVIEW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                TextButton(
                                    onClick = { showPushUpdateFormFor = null },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Cancel", color = textSecondaryCol, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Sign Out Button Card Row
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkMode) Color(0xFF2C1616) else Color(0xFFFEF2F2)
                    ),
                    border = BorderStroke(
                        1.dp, 
                        if (isDarkMode) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFFFCA5A5).copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLogout()
                                Toast.makeText(context, "Logged out of safe developer session.", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFB91C1C), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sign Out Developer Session",
                            color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                }

                Spacer(modifier = Modifier.height(32.dp))

            } else {
                // ================= Anonymous Guest Frame =================
                var customEmail by remember { mutableStateOf("guest.dev@gmail.com") }
                var customName by remember { mutableStateOf("External Developer") }
                var showCustomFields by remember { mutableStateOf(false) }

                if (!showCustomFields) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Guest",
                                tint = textSecondaryCol.copy(alpha = 0.7f),
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Guest Developer Workspace",
                            fontWeight = FontWeight.Bold,
                            color = textPrimaryCol,
                            fontSize = 18.sp
                        )

                        Text(
                            text = "Unauthenticated sandbox environment",
                            color = textSecondaryCol,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Connect an active Developer Profile to submit your custom apps, deploy updates, configure build attributes, and monitor your submissions live.",
                            fontSize = 12.sp,
                            color = textSecondaryCol,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = { showCustomFields = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Connect Google Account",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { onLogin("guest@darkroot.io", "Guest Developer") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, borderCol),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = textPrimaryCol
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Guest",
                                    modifier = Modifier.size(18.dp),
                                    tint = textSecondaryCol
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Login as Guest",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    // Google Authentication Form Style
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surfaceCol, RoundedCornerShape(24.dp))
                            .border(1.dp, borderCol, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Google Developer Identity",
                            color = textPrimaryCol,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Configure your authorized play console registry profile",
                            color = textSecondaryCol,
                            fontSize = 12.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Display Name", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_auth_display_name")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = customEmail,
                            onValueChange = { customEmail = it },
                            label = { Text("Google Account Email Address", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_auth_email")
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (customEmail.isNotBlank() && customName.isNotBlank()) {
                                    isAuthenticating = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(1200)
                                        onLogin(customEmail.trim(), customName.trim())
                                        isAuthenticating = false
                                        Toast.makeText(context, "Successfully authorized as: $customName", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Name and Email are required.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("custom_auth_submit_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Authorize Sandbox Session", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = { showCustomFields = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Back to Selection", color = accentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ========================================================
// 7. GOOGLE PLAY ACCOUNT PROFILE WINDOW
// ========================================================
@Composable
fun GooglePlayAccountDialog(
    isDarkMode: Boolean,
    isLoggedIn: Boolean,
    userName: String,
    userEmail: String,
    submissions: List<SubmissionEntity> = emptyList(),
    existingApps: List<com.example.data.AppEntity> = emptyList(),
    onTriggerSubmitForm: () -> Unit = {},
    onRefreshSubmissions: () -> Unit = {},
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onThemeToggle: () -> Unit,
    onUpdateDeveloperName: (String) -> Pair<Boolean, String> = { Pair(true, "") },
    onRequestUpdate: (SubmissionEntity) -> Unit = {},
    onShowAppDetails: (com.example.data.AppEntity) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isAuthenticating by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn, Unit) {
        if (isLoggedIn) {
            onRefreshSubmissions()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        val bgCol = if (isDarkMode) Color(0xFF13151C) else Color(0xFFFFFFFF)
        val surfaceCol = if (isDarkMode) Color(0xFF1D202B) else Color(0xFFF8FAFC)
        val borderCol = if (isDarkMode) Color(0xFF292E3D) else Color(0xFFE2E8F0)
        val textPrimaryCol = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF1E293B)
        val textSecondaryCol = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = bgCol),
            border = BorderStroke(1.dp, borderCol)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header bar with G-Logo or Shield icon, and a Close button.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            tint = if (isDarkMode) Color(0xFF60A5FA) else Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "DarkRoot Developer Account",
                            fontWeight = FontWeight.Bold,
                            color = textPrimaryCol,
                            fontSize = 14.sp,
                            letterSpacing = 0.2.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .background(if (isDarkMode) Color(0xFF292E3D) else Color(0xFFF1F5F9), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textPrimaryCol,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isAuthenticating) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = if (isDarkMode) Color(0xFF34D399) else Color(0xFF01875F),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Securing developer authentication token...",
                            fontSize = 13.sp,
                            color = textSecondaryCol,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (isLoggedIn) {
                    // Profile Info Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = surfaceCol),
                        border = BorderStroke(1.dp, borderCol)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val initialLetter = userName.trim().take(1).uppercase()
                            
                            // Beautiful user avatar circular image/letter with deep rich colors
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF60A5FA))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initialLetter,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            var isEditingName by remember { mutableStateOf(false) }
                            var editedName by remember(userName) { mutableStateOf(userName) }

                            if (isEditingName) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    OutlinedTextField(
                                        value = editedName,
                                        onValueChange = { editedName = it },
                                        label = { Text("Developer Name", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).heightIn(max = 56.dp).testTag("edit_dev_name_input"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            if (editedName.isNotBlank()) {
                                                val (success, msg) = onUpdateDeveloperName(editedName)
                                                if (success) {
                                                    isEditingName = false
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp).testTag("save_dev_name_button")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF10B981))
                                    }
                                    IconButton(
                                        onClick = {
                                            isEditingName = false
                                            editedName = userName
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Red)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .clickable { isEditingName = true }
                                        .clip(RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = userName,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimaryCol,
                                        fontSize = 16.sp,
                                        modifier = Modifier.testTag("dev_name_text")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Developer Name",
                                        tint = textSecondaryCol,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            Text(
                                text = userEmail,
                                color = textSecondaryCol,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Verified Tag
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(
                                        if (isDarkMode) Color(0xFF0F172A) else Color(0xFFF1F5F9), 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                                Text(
                                    text = "VERIFIED PUBLISHING PARTNER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) Color(0xFF6EE7B7) else Color(0xFF047857),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ACTION MENU LIST
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = bgCol),
                        border = BorderStroke(1.dp, borderCol)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Publish Application option row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onTriggerSubmitForm()
                                    }
                                    .testTag("trigger_user_submission_form")
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (isDarkMode) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Publish New Application",
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimaryCol,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Upload premium APKs or game builds",
                                        color = textSecondaryCol,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = textSecondaryCol,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Divider(color = borderCol.copy(alpha = 0.5f))

                            // Submissions option row
                            var showMySubmissionsDialog by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showMySubmissionsDialog = true }
                                    .testTag("view_submitted_apps_button")
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFECFDF5), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = if (isDarkMode) Color(0xFF34D399) else Color(0xFF059669),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "My Submissions Tracker",
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimaryCol,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "View live statuses & reviewer feedback",
                                        color = textSecondaryCol,
                                        fontSize = 11.sp
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isDarkMode) Color(0xFF0F172A) else Color(0xFFECFDF5), 
                                                RoundedCornerShape(50)
                                            )
                                            .border(1.dp, if (isDarkMode) Color(0xFF34D399).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(50))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = submissions.size.toString(),
                                            color = if (isDarkMode) Color(0xFF34D399) else Color(0xFF047857),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = textSecondaryCol,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (showMySubmissionsDialog) {
                                MySubmissionsStatusDialog(
                                    isDarkMode = isDarkMode,
                                    submissions = submissions,
                                    existingApps = existingApps,
                                    onDismiss = { showMySubmissionsDialog = false },
                                    onRequestUpdate = { sub ->
                                        showMySubmissionsDialog = false
                                        onRequestUpdate(sub)
                                    },
                                    onShowAppDetails = { app ->
                                        onShowAppDetails(app)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sign Out Button Card Row
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkMode) Color(0xFF2C1616) else Color(0xFFFEF2F2)
                        ),
                        border = BorderStroke(
                            1.dp, 
                            if (isDarkMode) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFFFCA5A5).copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLogout()
                                    Toast.makeText(context, "Logged out of safe session.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Sign Out from Console",
                                color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // ================= Anonymous Guest Frame =================
                    var customEmail by remember { mutableStateOf("guest.dev@gmail.com") }
                    var customName by remember { mutableStateOf("External Developer") }
                    var showCustomFields by remember { mutableStateOf(false) }

                    if (!showCustomFields) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Guest",
                                    tint = textSecondaryCol.copy(alpha = 0.7f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Guest Developer Workspace",
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryCol,
                                fontSize = 16.sp
                            )

                            Text(
                                text = "Unauthenticated sandbox environment",
                                color = textSecondaryCol,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Connect a Developer Profile to submit applications, deploy custom revisions, and query real-time metadata.",
                                fontSize = 11.sp,
                                color = textSecondaryCol,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { showCustomFields = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFFFFFFF),
                                    contentColor = textPrimaryCol
                                ),
                                border = BorderStroke(1.dp, borderCol),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Sign-In with Google Account",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = textPrimaryCol
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onLogin("guest@darkroot.io", "Guest Developer") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFFFFFFF),
                                    contentColor = textPrimaryCol
                                ),
                                border = BorderStroke(1.dp, borderCol),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Guest",
                                        modifier = Modifier.size(16.dp),
                                        tint = textSecondaryCol
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Login as Guest",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = textPrimaryCol
                                    )
                                }
                            }
                        }
                    } else {
                        // Google Authentication Form Style
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Google Developer Identity",
                                color = textPrimaryCol,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Text(
                                text = "Fill your preferred credentials to authorize a developer console account.",
                                color = textSecondaryCol,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.Start).padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Developer Display Name", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimaryCol,
                                    unfocusedTextColor = textPrimaryCol,
                                    focusedBorderColor = if (isDarkMode) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                    unfocusedBorderColor = borderCol
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = customEmail,
                                onValueChange = { customEmail = it },
                                label = { Text("Developer Contact Email", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimaryCol,
                                    unfocusedTextColor = textPrimaryCol,
                                    focusedBorderColor = if (isDarkMode) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                    unfocusedBorderColor = borderCol
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    if (customEmail.isBlank() || !customEmail.contains("@")) {
                                        Toast.makeText(context, "Please enter a valid Google Account email", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isAuthenticating = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(1000)
                                        onLogin(customEmail, customName)
                                        isAuthenticating = false
                                        showCustomFields = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkMode) Color(0xFF3B82F6) else Color(0xFF2563EB)
                                )
                            ) {
                                Text("AUTHENTICATE DEV ID", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = { showCustomFields = false },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Abandon Sign-In", fontSize = 12.sp, color = textSecondaryCol)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = borderCol.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(16.dp))

                // Beautiful standard option row for Dark Mode Theme Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfaceCol)
                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Theme Icon",
                            tint = if (isDarkMode) Color(0xFFFFB300) else Color(0xFF01875F),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Dark Mode Configuration",
                                color = textPrimaryCol,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Adjust app theme dynamically",
                                color = textSecondaryCol,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onThemeToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF34D399),
                            checkedTrackColor = Color(0xFF102A24),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.LightGray.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Dark Store Client Developer Services. Sandbox operations, session credentials and application uploads remain locally encrypted.",
                    fontSize = 10.sp,
                    color = textSecondaryCol.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// ========================================================
// MY SUBMISSIONS STATUS DIALOG WITH ADMIN PANEL STYLE
// ========================================================
@Composable
fun MySubmissionsStatusDialog(
    isDarkMode: Boolean,
    submissions: List<SubmissionEntity>,
    existingApps: List<com.example.data.AppEntity> = emptyList(),
    onDismiss: () -> Unit,
    onRequestUpdate: (SubmissionEntity) -> Unit,
    onShowAppDetails: (com.example.data.AppEntity) -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        val bgCol = if (isDarkMode) Color(0xFF13151C) else Color(0xFFFFFFFF)
        val surfaceCol = if (isDarkMode) Color(0xFF1D202B) else Color(0xFFF8FAFC)
        val borderCol = if (isDarkMode) Color(0xFF292E3D) else Color(0xFFE2E8F0)
        val textPrimaryColor = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF1E293B)
        val textSecondaryColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = bgCol),
            border = BorderStroke(1.dp, borderCol)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.List, 
                                contentDescription = "Submissions Icon",
                                tint = if (isDarkMode) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Developer Console",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkMode) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "My Applications Status",
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimaryColor,
                                fontSize = 16.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss, 
                        modifier = Modifier
                            .size(28.dp)
                            .background(if (isDarkMode) Color(0xFF292E3D) else Color(0xFFF1F5F9), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = "Close", 
                            tint = textPrimaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = borderCol.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(16.dp))

                if (submissions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No submissions",
                                tint = textSecondaryColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No sandboxed app submissions detected.",
                                color = textSecondaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(submissions, key = { it.id }) { sub ->
                            val isApprovedState = sub.status == "Approved"
                            val isRejectedState = sub.status == "Rejected"
                            val (statusBg, statusFg, statusTxt) = when {
                                isApprovedState -> Triple(
                                    if (isDarkMode) Color(0xFF064E3B) else Color(0xFFD1FAE5),
                                    if (isDarkMode) Color(0xFF34D399) else Color(0xFF065F46),
                                    "Approved"
                                )
                                isRejectedState -> Triple(
                                    if (isDarkMode) Color(0xFF7F1D1D) else Color(0xFFFEE2E2),
                                    if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B),
                                    "Rejected"
                                )
                                else -> Triple(
                                    if (isDarkMode) Color(0xFF78350F) else Color(0xFFFEF3C7),
                                    if (isDarkMode) Color(0xFFFBBF24) else Color(0xFF92400E),
                                    "Pending Review"
                                )
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("user_sub_card_" + sub.id)
                                    .clickable {
                                        onDismiss()
                                        onShowAppDetails(sub.toAppEntity(existingApps.find { it.packageName == sub.packageName }?.versionHistoryJson ?: ""))
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = surfaceCol),
                                border = BorderStroke(1.dp, borderCol)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // AppLogo matching professional guidelines
                                        AppLogo(
                                            logoUrl = sub.logo.ifBlank { if (sub.screenshots.contains(",")) sub.screenshots.substringBefore(",") else sub.screenshots },
                                            appName = sub.name,
                                            packageName = sub.packageName,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sub.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = textPrimaryColor
                                            )
                                            Text(
                                                text = sub.packageName,
                                                fontSize = 11.sp,
                                                color = textSecondaryColor,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "v${sub.version} • ${sub.category}",
                                                fontSize = 11.sp,
                                                color = textSecondaryColor.copy(alpha = 0.8f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        
                                        // Status Badge
                                        Surface(
                                            color = statusBg,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, statusFg.copy(alpha = 0.2f)),
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text(
                                                text = statusTxt,
                                                color = statusFg,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = sub.description,
                                        fontSize = 12.sp,
                                        color = textPrimaryColor.copy(alpha = 0.9f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )

                                    // Feedback Block
                                    if (sub.feedback.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val feedbackBoxBg = if (isApprovedState) {
                                            if (isDarkMode) Color(0xFF0F1C15) else Color(0xFFECFDF5)
                                        } else if (isRejectedState) {
                                            if (isDarkMode) Color(0xFF2D1414) else Color(0xFFFEF2F2)
                                        } else {
                                            if (isDarkMode) Color(0xFF241C0F) else Color(0xFFFFFBEB)
                                        }
                                        val feedbackTxtColor = if (isApprovedState) {
                                            if (isDarkMode) Color(0xFF6EE7B7) else Color(0xFF065F46)
                                        } else if (isRejectedState) {
                                            if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                                        } else {
                                            if (isDarkMode) Color(0xFFFCD34D) else Color(0xFF92400E)
                                        }

                                        Surface(
                                            color = feedbackBoxBg,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, feedbackTxtColor.copy(alpha = 0.15f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Reviewer Feedback",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.sp,
                                                    color = feedbackTxtColor,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = sub.feedback,
                                                    fontSize = 11.sp,
                                                    color = textPrimaryColor.copy(alpha = 0.9f),
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }

                                    if (isApprovedState) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                onDismiss() // Dismiss the submissions dialog
                                                onRequestUpdate(sub) // Trigger update action
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .testTag("my_submissions_request_update_" + sub.id),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDarkMode) Color(0xFF3B82F6) else Color(0xFF2563EB)
                                            ),
                                            contentPadding = PaddingValues(vertical = 0.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh, 
                                                contentDescription = "Refresh Icon", 
                                                modifier = Modifier.size(14.dp), 
                                                tint = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Request App Update", 
                                                color = Color.White, 
                                                fontSize = 11.sp, 
                                                fontWeight = FontWeight.Bold
                                            )
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
}

// ========================================================
// 8a. SETTINGS & SYSTEM CONTROL PANEL
// ========================================================
@Composable
fun SettingsTabContent(
    isDarkMode: Boolean,
    onThemeToggle: () -> Unit,
    viewModel: StoreViewModel,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sty = com.example.view.SettingsStyle(accentGreen, textPrimary, textSecondary, cardBgColor, cardBorderColor)
    val isAmoledMode by viewModel.isAmoledMode.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val autoInstall by viewModel.autoInstall.collectAsStateWithLifecycle()
    val notifyNewApps by viewModel.notifyNewApps.collectAsStateWithLifecycle()
    val notifyUpdates by viewModel.notifyUpdates.collectAsStateWithLifecycle()
    val notifyAnnouncements by viewModel.notifyAnnouncements.collectAsStateWithLifecycle()
    val notifySubmissions by viewModel.notifySubmissions.collectAsStateWithLifecycle()
    val notifyMessages by viewModel.notifyMessages.collectAsStateWithLifecycle()
    var apkCacheSize by remember { mutableStateOf(StorageManager.getApkCacheSize(context)) }

    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val isAdmin = userRole == "admin"
    var showPoliciesDialog by remember { mutableStateOf(false) }

    if (showPoliciesDialog) {
        com.example.view.EcosystemPolicyDialog(
            viewModel = viewModel,
            isMandatoryAccept = false,
            onDismiss = { showPoliciesDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = com.example.view.LocalNavBarInset.current)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(cardBgColor)
                        .border(1.dp, cardBorderColor, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(com.example.view.tr("set_title"), color = textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(com.example.view.tr("set_sub"), color = textSecondary, fontSize = 12.sp)
                }
            }
        }

        // Account summary
        item {
            val accName by viewModel.userName.collectAsStateWithLifecycle()
            val accEmail by viewModel.userEmail.collectAsStateWithLifecycle()
            val accPhoto by viewModel.profilePhotoUrl.collectAsStateWithLifecycle()
            val accLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onBack),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(54.dp).clip(CircleShape).background(accentGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (accPhoto.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = accPhoto, contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                (accName.ifBlank { "G" }).take(1).uppercase(),
                                color = accentGreen, fontWeight = FontWeight.Bold, fontSize = 22.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (accLoggedIn) accName.ifBlank { com.example.view.tr("set_account") } else com.example.view.tr("set_guest"),
                            color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1
                        )
                        Text(
                            if (accLoggedIn) accEmail else com.example.view.tr("set_signin_hint"),
                            color = textSecondary, fontSize = 12.sp, maxLines = 1
                        )
                        if (isAdmin) {
                            Spacer(Modifier.height(4.dp))
                            com.example.view.SettingsChip("ADMIN", accentGreen)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSecondary)
                }
            }
        }

        // ----------------------------------------------------
        // DARKSTORE PREMIUM SUBSCRIPTION CARD
        // ----------------------------------------------------
        item {
            val isPremiumMember by viewModel.isPremiumMember.collectAsStateWithLifecycle()
            val isPremiumFreeMode by viewModel.isPremiumFreeMode.collectAsStateWithLifecycle()
            var showPremiumComingSoonDialog by remember { mutableStateOf(false) }
            val premiumGold = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706)

            if (showPremiumComingSoonDialog) {
                ComingSoonDialog(
                    title = "Paid Premium — Coming Soon",
                    message = "DarkStore Premium now requires a subscription. We're building real payment support and will let you know the moment it's ready.",
                    accentColor = premiumGold,
                    onDismiss = { showPremiumComingSoonDialog = false }
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("darkstore_premium_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPremiumMember) {
                        if (isDarkMode) Color(0xFF261C02) else Color(0xFFFEFBF0)
                    } else {
                        cardBgColor
                    }
                ),
                border = BorderStroke(
                    1.5.dp,
                    if (isPremiumMember) premiumGold else cardBorderColor
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Premium icon",
                                tint = premiumGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "DARKSTORE PREMIUM",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isPremiumMember) premiumGold else textPrimary,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isPremiumMember) premiumGold.copy(alpha = 0.15f) else textSecondary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isPremiumMember) "ACTIVE MEMBER" else "STANDARD TIER",
                                color = if (isPremiumMember) premiumGold else textSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isPremiumMember) {
                            "Thank you for being a Premium member! Your badge and highlighted reviews are now visible to everyone."
                        } else if (isPremiumFreeMode) {
                            "Free while we build this out — enable Premium to unlock a custom theme, a badge on your name, and highlighted reviews."
                        } else {
                            "DarkStore Premium now requires a subscription. Real payment support is coming soon."
                        },
                        fontSize = 12.sp,
                        color = textPrimary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumBenefitItem("Custom Accent Theme", "Transforms your catalog interface with one of five signature color looks.", isPremiumMember, premiumGold, textSecondary)
                        PremiumBenefitItem("Premium Badge", "A gold badge next to your name on your reviews and profile, visible to everyone.", isPremiumMember, premiumGold, textSecondary)
                        PremiumBenefitItem("Highlighted Reviews", "Your reviews stand out with a subtle gold accent in every app's review list.", isPremiumMember, premiumGold, textSecondary)
                        PremiumBenefitItem("Ad-light catalog", "Hide promotional ad badges on app cards while browsing the store.", isPremiumMember, premiumGold, textSecondary)
                        PremiumBenefitItem("Priority support chat", "Message developers with a premium indicator so they can prioritize replies.", isPremiumMember, premiumGold, textSecondary)
                        PremiumBenefitItem("Early feature access", "Try new DarkStore tools (collections, update center) as they roll out.", isPremiumMember, premiumGold, textSecondary)
                        PremiumBenefitItem("Custom accent themes", "Unlock extra theme accents for your store appearance.", isPremiumMember, premiumGold, textSecondary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = cardBorderColor.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(premiumGold.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .border(1.dp, premiumGold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Premium Membership Status",
                                color = textPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    isPremiumMember -> "Premium features active (free for now)"
                                    isPremiumFreeMode -> "Enable to unlock premium features instantly — free for now"
                                    else -> "Requires a subscription — tap to learn more"
                                },
                                color = textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = isPremiumMember,
                            onCheckedChange = { isChecked ->
                                val allowed = viewModel.setPremiumMember(isChecked)
                                if (!allowed) {
                                    showPremiumComingSoonDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        if (isChecked) "DarkStore Premium activated — free for now!" else "Premium features deactivated.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = premiumGold,
                                checkedTrackColor = premiumGold.copy(alpha = 0.4f),
                                uncheckedThumbColor = textSecondary,
                                uncheckedTrackColor = textSecondary.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag("premium_status_toggle_switch")
                        )
                    }

                    if (isPremiumMember) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = cardBorderColor.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Text(
                            text = "PREMIUM CUSTOM STYLING ENGINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = premiumGold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "Dynamically alter the store's accents and primary highlight colors globally.",
                            fontSize = 11.sp,
                            color = textSecondary,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        val themes = listOf(
                            Triple("gold", "Amber Gold", Color(0xFFFBBF24)),
                            Triple("purple", "Cosmic Purple", Color(0xFFA78BFA)),
                            Triple("cyan", "Holo Cyan", Color(0xFF22D3EE)),
                            Triple("green", "Matrix Green", Color(0xFF34D399)),
                            Triple("rose", "Ruby Rose", Color(0xFFF472B6))
                        )
                        
                        val selectedTheme by viewModel.premiumTheme.collectAsStateWithLifecycle()
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            themes.forEach { (themeId, themeName, themeColor) ->
                                val isSelected = selectedTheme == themeId
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(themeColor)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) (if (isDarkMode) Color.White else Color.Black) else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            viewModel.setPremiumTheme(themeId)
                                            Toast.makeText(context, "$themeName Theme Selected!", Toast.LENGTH_SHORT).show()
                                        }
                                        .testTag("premium_theme_select_$themeId"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (themeId == "cyan" || themeId == "gold") Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }


                    }
                }
            }
        }

        // Keep Android Open Campaign Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keep_android_open_banner_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMode) Color(0xFF131A26) else Color(0xFFF1F5F9)
                ),
                border = BorderStroke(1.5.dp, accentGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(accentGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Keep Android Open Logo",
                            tint = accentGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "KEEP ANDROID OPEN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentGreen,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Keep Android an open ecosystem. Competing app stores and creators deserve fair access and zero artificial restrictions. Support freedom and choice on your device.",
                            fontSize = 12.sp,
                            color = textPrimary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                try {
                                    val uri = android.net.Uri.parse("https://keepandroidopen.org")
                                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                        setPackage("com.android.chrome")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val uri = android.net.Uri.parse("https://keepandroidopen.org")
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "No web browser found.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentGreen,
                                contentColor = if (isDarkMode) Color.Black else Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                "TAKE ACTION IN CHROME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── LANGUAGE ─────────────────────────────────────────────────
        item {
            val currentLang by viewModel.appLanguage.collectAsStateWithLifecycle()
            com.example.view.SettingsGroup(sty, com.example.view.tr("g_language")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(textSecondary.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    com.example.view.Languages.all.forEach { (code, native, _) ->
                        val on = currentLang == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (on) accentGreen else Color.Transparent)
                                .clickable { viewModel.setAppLanguage(code) }
                                .padding(vertical = 11.dp)
                                .testTag("lang_$code"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(native, color = if (on) Color.White else textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    com.example.view.tr("lang_note"),
                    color = textSecondary, fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
                )
            }
        }

        // ── APPEARANCE ───────────────────────────────────────────────
        item {
            com.example.view.SettingsGroup(sty, com.example.view.tr("g_appearance")) {
                // Segmented Light / Dark control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(textSecondary.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(false to com.example.view.tr("light"), true to com.example.view.tr("dark")).forEach { (dark, label) ->
                        val on = isDarkMode == dark
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (on) accentGreen else Color.Transparent)
                                .clickable { if (!on) onThemeToggle() }
                                .padding(vertical = 10.dp)
                                .testTag(if (dark) "dark_mode_selector_button" else "light_mode_selector_button"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = if (on) Color.White else textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(label, color = if (on) Color.White else textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsSwitchRow(
                    sty, Icons.Default.Brightness4, Color(0xFF8B5CF6),
                    com.example.view.tr("oled_t"), com.example.view.tr("oled_s"),
                    checked = isAmoledMode,
                    onChecked = {
                        viewModel.setAmoledMode(it)
                        Toast.makeText(context, if (it) "Pure OLED black on" else "Pure OLED black off", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ── DOWNLOADS & STORAGE ──────────────────────────────────────
        item {
            val msgCleared = com.example.view.tr("cleared")
            val msgNothing = com.example.view.tr("nothing_clear")
            com.example.view.SettingsGroup(sty, com.example.view.tr("g_downloads")) {
                com.example.view.SettingsSwitchRow(
                    sty, Icons.Default.Wifi, Color(0xFF3B82F6),
                    com.example.view.tr("wifi_t"), com.example.view.tr("wifi_s"),
                    checked = wifiOnly, onChecked = { viewModel.setWifiOnly(it) }
                )
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsSwitchRow(
                    sty, Icons.Default.InstallMobile, Color(0xFF10B981),
                    com.example.view.tr("auto_t"), com.example.view.tr("auto_s"),
                    checked = autoInstall, onChecked = { viewModel.setAutoInstall(it) }
                )
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsRow(
                    sty, Icons.Default.DeleteSweep, Color(0xFFEF4444),
                    com.example.view.tr("cache_t"),
                    com.example.view.tr("cache_s", StorageManager.formatSizePublic(apkCacheSize))
                ) {
                    com.example.view.SettingsPillButton(
                        com.example.view.tr("clear"), if (apkCacheSize > 0) Color(0xFFEF4444) else textSecondary
                    ) {
                        val deleted = StorageManager.clearApkCache(context)
                        apkCacheSize = StorageManager.getApkCacheSize(context)
                        Toast.makeText(
                            context,
                            if (deleted) msgCleared else msgNothing,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // ── NOTIFICATIONS ────────────────────────────────────────────
        item {
            // System-level switch: if it is off, none of the toggles below can show anything.
            var systemAllowed by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
            val settingsLifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(settingsLifecycle) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        systemAllowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                    }
                }
                settingsLifecycle.lifecycle.addObserver(obs)
                onDispose { settingsLifecycle.lifecycle.removeObserver(obs) }
            }
            com.example.view.SettingsGroup(sty, com.example.view.tr("g_notifications")) {
                if (!systemAllowed) {
                    com.example.view.SettingsRow(
                        sty, Icons.Default.NotificationsOff, Color(0xFFF59E0B),
                        com.example.view.tr("blocked_t"), com.example.view.tr("blocked_s"),
                        onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    ) { com.example.view.SettingsPillButton(com.example.view.tr("open"), Color(0xFFF59E0B)) {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    } }
                    com.example.view.SettingsDivider(sty)
                }
                com.example.view.SettingsSwitchRow(
                    sty, Icons.Default.NewReleases, Color(0xFF10B981),
                    com.example.view.tr("n_new"), com.example.view.tr("n_new_s"),
                    checked = notifyNewApps, onChecked = { viewModel.setNotifyNewApps(it) },
                    modifier = Modifier.testTag("toggle_new_apps_alerts")
                )
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsSwitchRow(
                    sty, Icons.Default.SystemUpdate, Color(0xFFF59E0B),
                    com.example.view.tr("n_upd"), com.example.view.tr("n_upd_s"),
                    checked = notifyUpdates, onChecked = { viewModel.setNotifyUpdates(it) },
                    modifier = Modifier.testTag("toggle_update_alerts")
                )
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsSwitchRow(
                    sty, Icons.Default.Campaign, Color(0xFF8B5CF6),
                    com.example.view.tr("n_ann"), com.example.view.tr("n_ann_s"),
                    checked = notifyAnnouncements, onChecked = { viewModel.setNotifyAnnouncements(it) },
                    modifier = Modifier.testTag("toggle_announcements_alerts")
                )
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsSwitchRow(
                    sty, Icons.Default.Inbox, Color(0xFF3B82F6),
                    com.example.view.tr("n_sub"), com.example.view.tr("n_sub_s"),
                    checked = notifySubmissions, onChecked = { viewModel.setNotifySubmissions(it) },
                    modifier = Modifier.testTag("toggle_submissions_alerts")
                )
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsSwitchRow(
                    sty, Icons.Default.Chat, Color(0xFFEC4899),
                    com.example.view.tr("n_chat"), com.example.view.tr("n_chat_s"),
                    checked = notifyMessages, onChecked = { viewModel.setNotifyMessages(it) },
                    modifier = Modifier.testTag("toggle_chat_alerts")
                )
            }
        }

        // ── SYSTEM ───────────────────────────────────────────────────
        item {
            var isDeviceAdminActive by remember { mutableStateOf(false) }
            val lifecycleOwnerForAdmin = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwnerForAdmin, context) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isDeviceAdminActive = com.example.utils.ApkInstaller.isDeviceAdminActive(context)
                    }
                }
                lifecycleOwnerForAdmin.lifecycle.addObserver(observer)
                onDispose { lifecycleOwnerForAdmin.lifecycle.removeObserver(observer) }
            }
            com.example.view.SettingsGroup(sty, com.example.view.tr("g_system")) {
                com.example.view.SettingsRow(
                    sty, Icons.Default.Sync, Color(0xFF10B981),
                    com.example.view.tr("sys_bg"), com.example.view.tr("sys_bg_s")
                ) { com.example.view.SettingsChip(com.example.view.tr("enabled"), Color(0xFF10B981)) }
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsRow(
                    sty, Icons.Default.AdminPanelSettings, if (isDeviceAdminActive) Color(0xFF10B981) else Color(0xFFEF4444),
                    com.example.view.tr("sys_admin"), com.example.view.tr("sys_admin_s")
                ) {
                    com.example.view.SettingsPillButton(
                        if (isDeviceAdminActive) com.example.view.tr("active") else com.example.view.tr("activate"),
                        if (isDeviceAdminActive) Color(0xFF10B981) else Color(0xFFEF4444)
                    ) {
                        if (isDeviceAdminActive) {
                            com.example.utils.ApkInstaller.removeDeviceAdmin(context)
                            isDeviceAdminActive = false
                            Toast.makeText(context, "Device admin deactivated", Toast.LENGTH_SHORT).show()
                        } else {
                            com.example.utils.ApkInstaller.requestDeviceAdmin(context)
                        }
                    }
                }
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsRow(
                    sty, Icons.Default.Storage, Color(0xFF3B82F6),
                    com.example.view.tr("sys_db"), com.example.view.tr("sys_db_s")
                ) { com.example.view.SettingsChip(com.example.view.tr("active"), Color(0xFF10B981)) }
            }
        }

        // ── ADMIN SECURITY (admins only) ─────────────────────────────
        if (isAdmin) {
            item {
                var showPinDialog by remember { mutableStateOf(false) }
                com.example.view.SettingsGroup(sty, "Admin security") {
                    com.example.view.SettingsRow(
                        sty, Icons.Default.VpnKey, Color(0xFFEF4444),
                        "Admin console PIN", "Stored in Firebase as a hash — only admins can change it",
                        onClick = { showPinDialog = true }
                    ) { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSecondary) }
                }
                if (showPinDialog) {
                    com.example.view.ChangeAdminPinDialog(
                        viewModel = viewModel,
                        accent = accentGreen, textPrimary = textPrimary, textSecondary = textSecondary,
                        cardBg = cardBgColor, cardBorder = cardBorderColor,
                        onDismiss = { showPinDialog = false }
                    )
                }
            }
        }

        // ── ABOUT & LEGAL ────────────────────────────────────────────
        item {
            com.example.view.SettingsGroup(sty, com.example.view.tr("g_about")) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_icon),
                        contentDescription = "Dark Store",
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Dark Store", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(
                        "${com.example.view.tr("version")} ${getInstalledVersionName(context)} (${getInstalledVersionCode(context)})",
                        color = accentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        com.example.view.tr("about_desc"),
                        color = textSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 22.dp)
                    )
                }
                com.example.view.SettingsDivider(sty)
                com.example.view.SettingsRow(
                    sty, Icons.Default.Gavel, Color(0xFF8B5CF6),
                    com.example.view.tr("legal_t"), com.example.view.tr("legal_s"),
                    onClick = { showPoliciesDialog = true }
                ) { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textSecondary) }
                Text(
                    "© 2026 DarkRoot · Dark Store. All rights reserved.",
                    color = textSecondary.copy(alpha = 0.7f), fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

}

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
    val isPremiumFreeModeAdmin by viewModel.isPremiumFreeMode.collectAsStateWithLifecycle()
    
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
                            Pair("Banners", Icons.Default.ViewCarousel)
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

                    // Premium is free-to-enable for now (no real payment
                    // processor exists yet) — this is the one switch that
                    // controls that for every user in the app, without
                    // needing a new build. Flipping it off doesn't touch
                    // anyone who's already enabled Premium; it only stops
                    // NEW activations, showing a Coming Soon message instead.
                    var isSavingPremiumConfig by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("PREMIUM MEMBERSHIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPremiumFreeModeAdmin) "Free to enable (no payment yet)" else "Requires subscription",
                                        color = textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isPremiumFreeModeAdmin) "Any user can turn Premium on for free right now." else "New activations are blocked with a Coming Soon message.",
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = isPremiumFreeModeAdmin,
                                    enabled = !isSavingPremiumConfig,
                                    onCheckedChange = { newIsFree ->
                                        isSavingPremiumConfig = true
                                        viewModel.setPremiumFreeModeAsAdmin(newIsFree) { success ->
                                            isSavingPremiumConfig = false
                                            Toast.makeText(
                                                context,
                                                if (success) "Premium is now ${if (newIsFree) "free" else "paid"} for all users." else "Couldn't save — try again.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
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
                    items(filteredUsers) { user ->
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                        Text(sub.status.uppercase(), color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
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

// ========================================================
// 9. HIGH FIDELITY SPECIFICATIONS OVERLAY SHEET (PLAY STORE TYPE)
// ========================================================
// ========================================================
@Composable
fun AppDetailsSkeleton(
    isDarkMode: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    shimmerTranslate: Float
) {
    val brush = shimmerBrush(isDarkMode, shimmerTranslate)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
    ) {
        // Core App Specs Row skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Title
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Developer
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Package
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Numerical statistics summary bar skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Detailed descriptive scroll field skeleton
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Description lines
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Screenshot previews header
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Screenshot galleries skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(195.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Community reviews header
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Review list skeleton
            repeat(1) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(brush)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Bottom sticky download execution controller bar skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(textSecondary.copy(alpha = 0.05f))
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun AppDetailsDialog(
    app: AppEntity,
    viewModel: com.example.viewmodel.StoreViewModel,
    downloadState: DownloadEntity?,
    installedInfo: com.example.utils.ApkInstaller.InstalledAppInfo?,
    currentRating: String,
    currentReviewsCount: Int,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    isDarkMode: Boolean,
    isPurchased: Boolean = false,
    isRegistered: Boolean = false,
    isAdmin: Boolean = false,
    developers: List<UserEntity> = emptyList(),
    allApps: List<AppEntity> = emptyList(),
    appReviews: List<com.example.data.ReviewEntity> = emptyList(),
    isReviewsLoading: Boolean = false,
    onAppClick: (AppEntity) -> Unit = {},
    onBuyClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onMessageDeveloper: (UserEntity) -> Unit = {},
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onDeleteDl: () -> Unit,
    onReviewSubmit: (Int, String) -> Unit,
    onReportSubmit: (String) -> Unit
) {
    val context = LocalContext.current
    var showDevProfileDialog by remember { mutableStateOf(false) }
    val cardBorderColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)
    val isInstalled = installedInfo != null
    var isWritingReview by remember { mutableStateOf(false) }
    var inputRatingStars by remember { mutableStateOf(5) }
    var inputReviewText by remember { mutableStateOf("") }
    var activeLightboxImageIndex by remember { mutableStateOf<Int?>(null) }

    var isLoadingDetails by remember { mutableStateOf(true) }

    // PERF: same fix as the home feed — only run the infinite shimmer animation
    // (and only pay its every-frame recomposition cost) while the details
    // skeleton is actually showing, not for the entire time this dialog is open.
    val shimmerTranslate = if (isLoadingDetails) {
        val shimmerTransition = rememberInfiniteTransition(label = "details_shimmer")
        val translate by shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "details_shimmer_translate"
        )
        translate
    } else {
        0f
    }

    LaunchedEffect(app.id) {
        isLoadingDetails = true
        kotlinx.coroutines.delay(650) // Simulate elegant live spec fetching with a 650ms shimmer to avoid lag & look premium
        isLoadingDetails = false
    }

    val screenshotList = remember(app.screenshots) {
        app.screenshots.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) Color(0xFF0E1013) else Color(0xFFF3F6F9))
                .ambientGlow(isDarkMode, false, accentGreen),
            color = Color.Transparent
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // ── Top bar: glass back button + category chip (no share button) ──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).glass(isDarkMode, CircleShape, 3.dp).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimary, modifier = Modifier.size(21.dp)) }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(accentGreen.copy(alpha = 0.14f))
                            .border(1.dp, accentGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(app.category.uppercase(), color = accentGreen, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp) }
                }

                if (isLoadingDetails) {
                    AppDetailsSkeleton(
                        isDarkMode = isDarkMode,
                        accentGreen = accentGreen,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        shimmerTranslate = shimmerTranslate
                    )
                } else {
                Box(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // ── Banner: promo video (play button) or the first screenshot ──
                        var showVideo by remember { mutableStateOf(false) }
                        val ytId = remember(app.videoUrl) { com.example.view.youTubeId(app.videoUrl) }
                        val hasVideo = app.videoUrl.isNotBlank()
                        val bannerModel = remember(app.videoUrl, screenshotList) {
                            if (ytId != null) "https://img.youtube.com/vi/$ytId/hqdefault.jpg" else screenshotList.firstOrNull().orEmpty()
                        }
                        if (showVideo && hasVideo) {
                            // Plays right here in the page (no full-screen takeover)
                            com.example.view.InlineVideoPlayer(
                                url = app.videoUrl, isDark = isDarkMode, accent = accentGreen,
                                textPrimary = textPrimary, textSecondary = textSecondary,
                                onClose = { showVideo = false }
                            )
                            Spacer(Modifier.height(16.dp))
                        } else if (bannerModel.isNotBlank() || hasVideo) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                    .glass(isDarkMode, RoundedCornerShape(24.dp), 5.dp)
                                    .clickable {
                                        if (hasVideo) showVideo = true
                                        else if (screenshotList.isNotEmpty()) activeLightboxImageIndex = 0
                                    }
                            ) {
                                if (bannerModel.isNotBlank()) {
                                    AsyncImage(model = bannerModel, contentDescription = app.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accentGreen.copy(alpha = 0.55f), accentGreen.copy(alpha = 0.15f)))))
                                }
                                if (hasVideo) {
                                    Box(
                                        Modifier.align(Alignment.Center).size(66.dp).clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.26f))
                                            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(40.dp)) }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // ── Title block ──
                        Box(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppLogo(
                                        logoUrl = app.logo, appName = app.name, packageName = app.packageName,
                                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(22.dp))
                                    )
                                    if (downloadState?.status == "DOWNLOADING") {
                                        CircularProgressIndicator(
                                            progress = downloadState.progress / 100f, color = accentGreen,
                                            trackColor = Color.Transparent, strokeWidth = 3.3.dp, modifier = Modifier.size(90.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f).padding(end = if (app.hasAds) 64.dp else 0.dp)) {
                                    Text(app.name, color = textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(currentRating.toString(), color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(6.dp))
                                        Text("($currentReviewsCount)", color = textSecondary, fontSize = 13.sp)
                                    }
                                    Row(
                                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { showDevProfileDialog = true },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(app.developer, color = accentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        Text("  •  ${app.category}", color = textSecondary, fontSize = 13.sp, maxLines = 1)
                                    }
                                }
                            }
                            if (app.hasAds) {
                                AdBadge(modifier = Modifier.align(Alignment.TopEnd))
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Info tiles (what we actually know: size, version, ads, reviews) ──
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.example.view.AppInfoTile(com.example.view.tr("det_size"), app.size, isDarkMode, textPrimary, textSecondary, Modifier.weight(1f))
                            com.example.view.AppInfoTile(com.example.view.tr("version"), app.version, isDarkMode, textPrimary, textSecondary, Modifier.weight(1f))
                            com.example.view.AppInfoTile(
                                com.example.view.tr("det_ads"),
                                if (app.hasAds) com.example.view.tr("det_ads_yes") else com.example.view.tr("det_ads_no"),
                                isDarkMode, textPrimary, textSecondary, Modifier.weight(1f)
                            )
                            com.example.view.AppInfoTile(com.example.view.tr("det_reviews"), currentReviewsCount.toString(), isDarkMode, textPrimary, textSecondary, Modifier.weight(1f))
                        }
                        if (installedInfo != null) {
                            val detailsHasUpdate = app.versionCode > installedInfo.versionCode ||
                                !app.version.trim().equals(installedInfo.versionName.trim(), ignoreCase = true)
                            Text(
                                "Installed: ${installedInfo.versionName} (v${installedInfo.versionCode})" + if (detailsHasUpdate) " • Update available" else "",
                                color = if (detailsHasUpdate) accentGreen else textSecondary, fontSize = 11.sp,
                                fontWeight = if (detailsHasUpdate) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Install / update / open / buy (moved here from the old bottom bar) ──
                        Box(modifier = Modifier.fillMaxWidth().glass(isDarkMode, RoundedCornerShape(28.dp), 4.dp).padding(14.dp)) {
                    if (downloadState?.status == "DOWNLOADING") {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fetching package data: ${downloadState.progress}%",
                                    color = accentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = downloadState.downloadSpeed,
                                    color = accentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = downloadState.progress / 100f,
                                modifier = Modifier.fillMaxWidth(),
                                color = accentGreen,
                                trackColor = textSecondary.copy(alpha = 0.15f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = onDeleteDl,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("CANCEL DOWNLOAD", color = Color(0xFFEF5350), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val hasUpdate = isInstalled && (
                            app.versionCode > (installedInfo?.versionCode ?: 0L) ||
                            (installedInfo?.versionName != null && !app.version.trim().equals(installedInfo.versionName.trim(), ignoreCase = true))
                        )
                        if (isInstalled) {
                            if (hasUpdate) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { ApkInstaller.launchApp(context, app.packageName) },
                                        shape = RoundedCornerShape(26.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(52.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                        border = BorderStroke(1.5.dp, accentGreen),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "", tint = accentGreen)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("OPEN", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }

                                    Button(
                                        onClick = onAction,
                                        shape = RoundedCornerShape(26.dp),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                                    ) {
                                        Icon(
                                            imageVector = if (downloadState?.status == "DOWNLOADED") Icons.Default.CheckCircle else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "",
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (downloadState?.status == "DOWNLOADED") "INSTALL" else "UPDATE",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else {
                                // Double Actions UX Replicas
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (LocalUninstallEnabled.current) {
                                        OutlinedButton(
                                            onClick = { ApkInstaller.uninstallApp(context, app.packageName) },
                                            shape = RoundedCornerShape(26.dp),
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .height(52.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                                            border = BorderStroke(1.5.dp, Color(0xFFEF5350)),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "", tint = Color(0xFFEF5350))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("UNINSTALL", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                    }

                                    Button(
                                        onClick = { ApkInstaller.launchApp(context, app.packageName) },
                                        shape = RoundedCornerShape(26.dp),
                                        modifier = Modifier
                                            .weight(if (LocalUninstallEnabled.current) 1.2f else 1.0f)
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "", tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("OPEN", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        } else {
                            if (app.isUpcoming) {
                                if (isRegistered) {
                                    OutlinedButton(
                                        onClick = {},
                                        shape = RoundedCornerShape(26.dp),
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                        border = BorderStroke(1.5.dp, accentGreen)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "", tint = accentGreen)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("PRE-REGISTERED", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = onRegisterClick,
                                        shape = RoundedCornerShape(26.dp),
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "", tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("PRE-REGISTER FOR ITEM", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            } else if (app.isPremium && !isPurchased) {
                                Button(
                                    onClick = onBuyClick,
                                    shape = RoundedCornerShape(26.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = "", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("BUY FOR ${app.price.ifEmpty { "$1.99" }}", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            } else {
                                Button(
                                    onClick = onAction,
                                    shape = RoundedCornerShape(26.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                                ) {
                                    Icon(
                                        imageVector = if (downloadState?.status == "DOWNLOADED") Icons.Default.CheckCircle else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "",
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (downloadState?.status == "DOWNLOADED") "INSTALL" else "INSTALL",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                        Spacer(Modifier.height(16.dp))

                        if (app.isApproved && !app.isUpcoming) {
                            com.example.view.VerifiedCard(isDarkMode, accentGreen, textPrimary, textSecondary)
                            Spacer(Modifier.height(12.dp))
                        }
                        com.example.view.AboutCard(app.description, isDarkMode, textPrimary, textSecondary)
                        if (app.changelog.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            com.example.view.WhatsNewCard(app.version, app.changelog, isDarkMode, accentGreen, textPrimary, textSecondary)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "SCREENSHOT PREVIEWS",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Screenshot galleries
                        if (screenshotList.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(screenshotList, key = { index, sUrl -> "${index}_$sUrl" }) { index, sUrl ->
                                    val screenshotRequest = remember(sUrl, context) {
                                        coil.request.ImageRequest.Builder(context)
                                            .data(sUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .placeholder(R.drawable.img_app_logo_new)
                                            .error(R.drawable.img_app_logo_new)
                                            .fallback(R.drawable.img_app_logo_new)
                                            .build()
                                    }
                                    ElegantImageLoader(
                                        model = screenshotRequest,
                                        contentDescription = "App Preview Frame",
                                        modifier = Modifier
                                            .width(135.dp)
                                            .height(240.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(textSecondary.copy(alpha = 0.1f))
                                            .border(1.dp, glassEdge(isDarkMode), RoundedCornerShape(18.dp))
                                            .clickable {
                                                activeLightboxImageIndex = index
                                            },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .background(textSecondary.copy(0.05f), RoundedCornerShape(10.dp))
                                    .border(1.dp, textSecondary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No screenshots available.", color = textSecondary, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Version history was already being recorded into
                        // app.versionHistoryJson on every update (submission
                        // approval, and now the admin's direct edit/push-update
                        // path too) but had no UI anywhere showing it — stored
                        // and immediately invisible. Surfaced here as a
                        // collapsible list, newest-first, right below the
                        // description.
                        val versionHistory = remember(app.versionHistoryJson) {
                            if (app.versionHistoryJson.isBlank()) {
                                emptyList()
                            } else {
                                try {
                                    // BUG FIX: no KotlinJsonAdapterFactory registered here
                                    // either — fromJson() on a Kotlin data class list
                                    // threw every time, silently caught below, so this
                                    // always showed "no history" even for apps that
                                    // genuinely had some.
                                    val moshi = com.squareup.moshi.Moshi.Builder()
                                        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                        .build()
                                    val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.AppVersionHistoryEntry::class.java)
                                    moshi.adapter<List<com.example.data.AppVersionHistoryEntry>>(listType)
                                        .fromJson(app.versionHistoryJson)
                                        ?.sortedByDescending { it.publishedAt }
                                        ?: emptyList()
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        if (versionHistory.isNotEmpty()) {
                            var isHistoryExpanded by remember(app.id) { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isHistoryExpanded = !isHistoryExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "VERSION HISTORY (${versionHistory.size})",
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isHistoryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            AnimatedVisibility(visible = isHistoryExpanded) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }
                                    versionHistory.forEach { entry ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "v${entry.versionName}",
                                                    color = textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = dateFormat.format(java.util.Date(entry.publishedAt)),
                                                    color = textSecondary,
                                                    fontSize = 11.sp
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                if (isAdmin) {
                                                    TextButton(
                                                        onClick = {
                                                            viewModel.rollbackAppToVersion(app.id, entry) { success, msg ->
                                                                Toast.makeText(context, msg ?: if (success) "Rolled back" else "Failed", Toast.LENGTH_LONG).show()
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                    ) {
                                                        Text("Rollback", color = accentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            if (entry.changelog.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = entry.changelog,
                                                    color = textSecondary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Play Store-like write-review segment
                        Text(
                            text = "RATE THIS APPLICATION",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (!isWritingReview) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { isWritingReview = true }
                                    .background(textSecondary.copy(alpha = 0.05f))
                                    .background(glassSheen(isDarkMode))
                                    .border(1.dp, glassEdge(isDarkMode), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (i in 1..5) {
                                        Icon(
                                            imageVector = Icons.Outlined.Star,
                                            contentDescription = "Unfilled rate star",
                                            tint = textSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Write a public review",
                                    color = accentGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Active interactive stars writing board
                            Card(
                                colors = CardDefaults.cardColors(containerColor = textSecondary.copy(0.04f)),
                                border = BorderStroke(1.dp, textSecondary.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Select rating stars", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        for (i in 1..5) {
                                            val isSelected = i <= inputRatingStars
                                            Icon(
                                                imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.Star,
                                                contentDescription = "Rate app star click",
                                                tint = if (isSelected) Color(0xFFF1A80A) else textSecondary,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clickable { inputRatingStars = i }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = inputReviewText,
                                        onValueChange = { inputReviewText = it },
                                        placeholder = { Text("Tell the community your experience with this package (optional)...", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = textPrimary,
                                            unfocusedTextColor = textPrimary
                                        ),
                                        minLines = 2
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { isWritingReview = false; inputReviewText = "" }) {
                                            Text("CANCEL", color = textSecondary)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                if (inputReviewText.isNotBlank()) {
                                                    onReviewSubmit(inputRatingStars, inputReviewText)
                                                    isWritingReview = false
                                                    inputReviewText = ""
                                                } else {
                                                    Toast.makeText(context, "Please enter some review text!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                                        ) {
                                            Text("SUBMIT", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Render user feedback posts
                        Text(
                            text = "COMMUNITY REVIEWS",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isReviewsLoading) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentGreen)
                            }
                        } else if (appReviews.isEmpty()) {
                            Text("No reviews yet. Be the first to review!", color = textSecondary, fontSize = 12.sp)
                        } else {
                            val reviewerPremiumGold = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706)
                            appReviews.forEach { review ->
                                // Premium badge + highlight: resolved by matching the
                                // review's userId against the already-loaded users
                                // list, same lookup pattern used for the developer
                                // profile popup. A reviewer whose account isn't
                                // found (or isn't Premium) just renders like normal.
                                val reviewerIsPremium = remember(review.userId, developers) {
                                    developers.find { it.uid == review.userId }?.isPremiumMember == true
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (reviewerIsPremium) {
                                                Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(reviewerPremiumGold.copy(alpha = 0.10f))
                                                    .border(1.dp, reviewerPremiumGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                                    .padding(12.dp)
                                            } else {
                                                Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(textSecondary.copy(alpha = 0.05f))
                                                    .background(glassSheen(isDarkMode))
                                                    .border(1.dp, glassEdge(isDarkMode), RoundedCornerShape(16.dp))
                                                    .padding(12.dp)
                                            }
                                        )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(review.userName, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        if (reviewerIsPremium) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Premium member",
                                                tint = reviewerPremiumGold,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row {
                                            for (j in 1..5) {
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = "",
                                                    tint = if (j <= review.stars) Color(0xFFF1A80A) else textSecondary.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(review.msg, color = textPrimary.copy(alpha = 0.8f), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (!reviewerIsPremium) {
                                        Divider(color = textSecondary.copy(alpha = 0.1f))
                                    }
                                }
                            }
                        }

                        // Report App Section
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "SAFETY & POLICIES",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var isReporting by remember { mutableStateOf(false) }
                        var reportReason by remember { mutableStateOf("") }

                        if (!isReporting) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isReporting = true }
                                    .background(textSecondary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Report",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Report application flag / policy violation",
                                    color = Color(0xFFEF5350),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = textSecondary.copy(0.04f)),
                                border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Why are you reporting this application?",
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = reportReason,
                                        onValueChange = { reportReason = it },
                                        placeholder = { Text("E.g. Malware, scam, annoying ads, copyright infringement, etc...", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth().testTag("app_report_reason_field"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = textPrimary,
                                            unfocusedTextColor = textPrimary,
                                            focusedBorderColor = Color(0xFFEF5350),
                                            unfocusedBorderColor = textSecondary.copy(alpha = 0.3f)
                                        ),
                                        minLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { isReporting = false; reportReason = "" }) {
                                            Text("CANCEL", color = textSecondary)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                if (reportReason.isNotBlank()) {
                                                    onReportSubmit(reportReason)
                                                    isReporting = false
                                                    reportReason = ""
                                                } else {
                                                    Toast.makeText(context, "Please enter a report description!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                                        ) {
                                            Text("SUBMIT REPORT", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
                } // closes isLoadingDetails else block
 // closes isLoadingDetails else block
            }
        }
    }

    if (activeLightboxImageIndex != null) {
        ScreenshotLightboxDialog(
            screenshots = screenshotList,
            initialIndex = activeLightboxImageIndex!!,
            appName = app.name,
            onDismiss = { activeLightboxImageIndex = null }
        )
    }

    if (showDevProfileDialog) {
        val devProfile = remember(app.developer, developers) {
            developers.find {
                it.devName.equals(app.developer, ignoreCase = true) ||
                it.displayName.equals(app.developer, ignoreCase = true) ||
                it.email.equals(app.submittedBy, ignoreCase = true)
            } ?: UserEntity(
                uid = "fallback_uid",
                email = "",
                displayName = app.developer,
                role = "user",
                devName = app.developer,
                devBio = "This developer builds apps for Dark Store.",
                isDeveloper = true
            )
        }

        val developerApps = remember(app.developer, allApps) {
            allApps.filter { it.developer.equals(app.developer, ignoreCase = true) }
        }

        Dialog(
            onDismissRequest = { showDevProfileDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 20.dp)
                    .heightIn(max = 620.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Soft header band (login-style visual accent)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        accentGreen.copy(alpha = 0.22f),
                                        accentGreen.copy(alpha = 0.04f)
                                    )
                                )
                            )
                    ) {
                        IconButton(
                            onClick = { showDevProfileDialog = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                            .offset(y = (-36).dp)
                    ) {
                        // Avatar — real photo when available (tap → full screen)
                        var showDevPhotoViewer by remember { mutableStateOf(false) }
                        if (showDevPhotoViewer && devProfile.profilePhotoUrl.isNotBlank()) {
                            FullScreenPhotoViewer(
                                photoUrl = devProfile.profilePhotoUrl,
                                title = devProfile.devName.ifBlank { devProfile.displayName.ifBlank { app.developer } },
                                onDismiss = { showDevPhotoViewer = false }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .align(Alignment.CenterHorizontally)
                                .clip(CircleShape)
                                .background(accentGreen.copy(alpha = 0.12f))
                                .border(3.dp, cardBgColor, CircleShape)
                                .clickable(enabled = devProfile.profilePhotoUrl.isNotBlank()) { showDevPhotoViewer = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (devProfile.profilePhotoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = devProfile.profilePhotoUrl,
                                    contentDescription = "Developer photo",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = (devProfile.devName.ifBlank { devProfile.displayName }.ifBlank { app.developer }).take(1).uppercase(),
                                    color = accentGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = devProfile.devName.ifBlank { devProfile.displayName.ifBlank { app.developer } },
                            color = textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (devProfile.email.isNotBlank() && !devProfile.email.contains("guest", ignoreCase = true)) {
                            Text(
                                text = devProfile.email,
                                color = textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Follow row
                        if (devProfile.uid != "fallback_uid") {
                            val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
                            val isFollowing = followingIds.contains(devProfile.uid)
                            var followerCount by remember(devProfile.uid) { mutableStateOf<Int?>(null) }
                            var isTogglingFollow by remember { mutableStateOf(false) }
                            val isOwnProfile = viewModel.userUid.collectAsStateWithLifecycle().value == devProfile.uid

                            LaunchedEffect(devProfile.uid) {
                                followerCount = viewModel.fetchFollowerCount(devProfile.uid)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = when (val count = followerCount) {
                                        null -> "···"
                                        1 -> "1 follower"
                                        else -> "$count followers"
                                    },
                                    color = textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!isOwnProfile) {
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Button(
                                        onClick = {
                                            if (!isTogglingFollow) {
                                                isTogglingFollow = true
                                                val wasFollowing = isFollowing
                                                viewModel.toggleFollowDeveloper(devProfile.uid) { success ->
                                                    isTogglingFollow = false
                                                    if (success) {
                                                        followerCount = (followerCount ?: 0) + if (wasFollowing) -1 else 1
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isTogglingFollow,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFollowing) textSecondary.copy(alpha = 0.12f) else accentGreen,
                                            contentColor = if (isFollowing) textPrimary else Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                    ) {
                                        Text(
                                            if (isFollowing) "Following" else "Follow",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (devProfile.uid != "fallback_uid") {
                                                showDevProfileDialog = false
                                                onMessageDeveloper(devProfile)
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "This developer is not linked to a chat account yet.",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.55f)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Chat,
                                            contentDescription = "Message",
                                            tint = accentGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Message", color = accentGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // About
                        val bio = devProfile.devBio.ifBlank { "Developer on Dark Store" }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("About", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        Text(
                            text = bio,
                            color = textPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Links
                        val hasLinks = devProfile.devWebsite.isNotBlank() || devProfile.devGithub.isNotBlank()
                        if (hasLinks) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (devProfile.devWebsite.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val url = if (devProfile.devWebsite.startsWith("http")) devProfile.devWebsite else "https://${devProfile.devWebsite}"
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, cardBorderColor),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Language, null, tint = accentGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Website", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (devProfile.devGithub.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val gh = if (devProfile.devGithub.startsWith("http")) devProfile.devGithub else "https://github.com/${devProfile.devGithub}"
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(gh)))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open GitHub", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, cardBorderColor),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Code, null, tint = accentGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("GitHub", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = cardBorderColor.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Published Apps (${developerApps.size})",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (developerApps.isEmpty()) {
                                Text("No published apps yet.", color = textSecondary, fontSize = 12.sp)
                            } else {
                                developerApps.forEach { devApp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(textSecondary.copy(alpha = 0.05f))
                                            .clickable {
                                                showDevProfileDialog = false
                                                onAppClick(devApp)
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(accentGreen.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (devApp.logo.isNotBlank()) {
                                                AsyncImage(
                                                    model = devApp.logo,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(devApp.name.take(1).uppercase(), color = accentGreen, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(devApp.name, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            Text(devApp.category, color = textSecondary, fontSize = 11.sp, maxLines = 1)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(devApp.rating, color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showDevProfileDialog = false },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text("Close", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}


@Composable
fun DetailBadgeRowItem(
    title: String,
    value: String,
    sub: String,
    colorText: Color,
    colorTextSub: Color,
    icon: ImageVector? = null,
    iconTint: Color = colorText,
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(16.dp)
    val tileDark = colorText.luminance() > 0.5f
    Box(
        modifier = modifier
            .clip(tileShape)
            .background(colorTextSub.copy(0.05f))
            .background(glassSheen(tileDark))
            .border(1.dp, glassEdge(tileDark), tileShape)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 8.sp, color = colorTextSub, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    value,
                    fontSize = 12.sp,
                    color = colorText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                sub,
                fontSize = 8.sp,
                color = colorTextSub,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )
        }
    }
}

// ========================================================
// 10. EMPTY CATALOG ARCHIVE WARNING CARD
// ========================================================
@Composable
fun EmptyCatalogStateCard(
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    cardBgColor: Color,
    cardBorderColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Catalog empty info symbol",
                tint = accentGreen,
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "No Apps Published",
                fontSize = 16.sp,
                color = textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Dark Store catalog contains no packages at this time. Authorize within the Developer Console tab to upload and deploy application archives directly.",
                color = textSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}


private fun handleAppActionButton(
    app: AppEntity,
    isInstalled: Boolean,
    viewModel: StoreViewModel,
    context: android.content.Context,
    onOfflineTriggered: () -> Unit = {}
) {
    val downloadState = viewModel.downloads.value.find { it.id == app.id }
    val installedInfo = ApkInstaller.getInstalledAppInfo(context, app.packageName)
    val currentlyInstalled = installedInfo != null
    val hasUpdate = currentlyInstalled && (
        app.versionCode > (installedInfo?.versionCode ?: 0L) ||
        (installedInfo?.versionName != null && !app.version.trim().equals(installedInfo.versionName.trim(), ignoreCase = true))
    )

    if (downloadState?.status == "DOWNLOADED" && downloadState.localFilePath != null) {
        val file = File(downloadState.localFilePath)
        val packageInfo = if (file.exists()) {
            try {
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        if (packageInfo == null) {
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }
            if (!isNetworkAvailable(context)) {
                onOfflineTriggered()
            } else {
                Toast.makeText(context, "Cached APK is corrupt or missing. Restarting download...", Toast.LENGTH_SHORT).show()
                viewModel.downloadAndInstallApp(app)
            }
        } else {
            // BUG FIX: same issue as elsewhere — installApk()'s silent-install
            // attempt blocks on Runtime.exec(...).waitFor() for potentially several
            // seconds. This function has no coroutine scope available (it's a plain
            // function called from a click handler on the main thread), so a
            // lightweight background thread is the simplest way to keep this off
            // the UI thread without restructuring every caller of this function.
            Thread {
                ApkInstaller.installApk(context, file)
            }.start()
        }
    } else if (hasUpdate) {
        if (!isNetworkAvailable(context)) {
            onOfflineTriggered()
        } else {
            viewModel.downloadAndInstallApp(app)
            Toast.makeText(context, "Downloading update for: ${app.name}", Toast.LENGTH_SHORT).show()
        }
    } else if (currentlyInstalled) {
        ApkInstaller.launchApp(context, app.packageName)
    } else {
        if (!isNetworkAvailable(context)) {
            onOfflineTriggered()
        } else {
            viewModel.downloadAndInstallApp(app)
            Toast.makeText(context, "Starting fetch sequence: ${app.name}", Toast.LENGTH_SHORT).show()
        }
    }
}

// ========================================================
// 11. FORM DIALOG TO REGISTER OR APPEND STORE APP TO FIREBASE
// ========================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddNewAppForm(
    existingApp: AppEntity? = null,
    isForAdmin: Boolean = false,
    userEmail: String = "",
    defaultDeveloperName: String = "",
    onDismiss: () -> Unit,
    onSubmit: (AppEntity) -> Unit
) {
    var id by remember { mutableStateOf(existingApp?.id ?: "app_${System.currentTimeMillis()}_${(1000..9999).random()}") }
    var name by remember { mutableStateOf(existingApp?.name ?: "") }
    var developer by remember { mutableStateOf(existingApp?.developer ?: defaultDeveloperName.ifBlank { "Developer" }) }
    var version by remember { mutableStateOf(existingApp?.version ?: "") }
    var changelog by remember { mutableStateOf(existingApp?.changelog ?: "") }
    var videoUrl by remember { mutableStateOf(existingApp?.videoUrl ?: "") }
    var size by remember { mutableStateOf(existingApp?.size ?: "") }
    var category by remember { mutableStateOf(existingApp?.category ?: "Utilities") }
    var rating by remember { mutableStateOf(existingApp?.rating ?: "") }
    var description by remember { mutableStateOf(existingApp?.description ?: "") }
    var logoUrl by remember { mutableStateOf(existingApp?.logo ?: "") }
    
    val initialScreenshots = remember(existingApp) {
        val list = existingApp?.screenshots?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        List(6) { index -> if (index < list.size) list[index] else "" }
    }
    
    var ss1 by remember { mutableStateOf(initialScreenshots[0]) }
    var ss2 by remember { mutableStateOf(initialScreenshots[1]) }
    var ss3 by remember { mutableStateOf(initialScreenshots[2]) }
    var ss4 by remember { mutableStateOf(initialScreenshots[3]) }
    var ss5 by remember { mutableStateOf(initialScreenshots[4]) }
    var ss6 by remember { mutableStateOf(initialScreenshots[5]) }

    val uploadingStates = remember { mutableStateListOf(false, false, false, false, false, false) }
    var activePickingSlotIndex by remember { mutableStateOf(-1) }
    var isUploadingLogo by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun uploadFileFromUri(uri: Uri, isLogo: Boolean, onFinished: (String?) -> Unit) {
        // Simplified per feedback: previously tried Firebase Storage first and only
        // fell back to ImgBB silently on failure — the Firebase step was failing
        // unpredictably and just added an extra point of failure with no benefit.
        // Now uploads directly to ImgBB, with a clear error if ImgBB itself is down.
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read image bytes.", Toast.LENGTH_SHORT).show()
                        onFinished(null)
                    }
                    return@launch
                }

                try {
                    val base64String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val formBody = okhttp3.FormBody.Builder()
                        .add("image", base64String)
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url("https://api.imgbb.com/1/upload?key=a046c848dfa5230136f107106d4bb187")
                        .post(formBody)
                        .build()

                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val match = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(bodyString)
                        val uploadedUrl = match?.groupValues?.get(1)?.replace("\\/", "/")
                        if (uploadedUrl != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                onFinished(uploadedUrl)
                            }
                        } else {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, "Image server returned an unexpected response. Please try again.", Toast.LENGTH_LONG).show()
                                onFinished(null)
                            }
                        }
                    } else {
                        // Clear, specific "server down" messaging instead of a generic failure.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Image server is currently down (error ${response.code}). Please try again later.", Toast.LENGTH_LONG).show()
                            onFinished(null)
                        }
                    }
                } catch (ex: java.io.IOException) {
                    // Network-level failure (no connection, timeout, DNS, etc.) — this is
                    // what "server is down" looks like from the client's perspective.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Image server is currently down or unreachable. Please try again later.", Toast.LENGTH_LONG).show()
                        onFinished(null)
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    onFinished(null)
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        // Snapshot the target slot immediately so a second Pick tap cannot
        // redirect this upload into the wrong field (logo vs screenshot).
        val idx = activePickingSlotIndex
        activePickingSlotIndex = -1
        if (uri == null) return@rememberLauncherForActivityResult
        if (idx == 99) {
            isUploadingLogo = true
            uploadFileFromUri(uri, isLogo = true) { url ->
                isUploadingLogo = false
                if (url != null) {
                    logoUrl = url
                    Toast.makeText(context, "App logo updated successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to upload app logo.", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (idx in 0..5) {
            uploadingStates[idx] = true
            uploadFileFromUri(uri, isLogo = false) { url ->
                uploadingStates[idx] = false
                if (url != null) {
                    Toast.makeText(context, "Screenshot ${idx + 1} uploaded successfully!", Toast.LENGTH_SHORT).show()
                    when (idx) {
                        0 -> ss1 = url
                        1 -> ss2 = url
                        2 -> ss3 = url
                        3 -> ss4 = url
                        4 -> ss5 = url
                        5 -> ss6 = url
                    }
                } else {
                    Toast.makeText(context, "Failed to upload screenshot ${idx + 1}.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "No upload target selected. Tap Logo or Pick again.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Permission denied. Can't select photos from device without media permission.", Toast.LENGTH_LONG).show()
        }
    }

    var apkUrl by remember { mutableStateOf(existingApp?.apkUrl ?: "") }

    var packageName by remember { mutableStateOf(existingApp?.packageName ?: "") }
    var isFeatured by remember { mutableStateOf(existingApp?.isFeatured ?: false) }
    var versionCodeInput by remember { mutableStateOf(existingApp?.versionCode?.toString() ?: "1") }
    var hasAds by remember { mutableStateOf(existingApp?.hasAds ?: false) }
    var isPremium by remember { mutableStateOf(existingApp?.isPremium ?: false) }
    var price by remember { mutableStateOf(existingApp?.price ?: "") }
    var isUpcoming by remember { mutableStateOf(existingApp?.isUpcoming ?: false) }


    val categories = remember { listOf("Utilities", "Games", "Tools", "Entertainment") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF01875F), Color(0xFF0F9D58))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (existingApp == null) "Submit New Package" else "Edit Application Profile",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "DarkStore Developer Console • Workspace Sync",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF01875F), modifier = Modifier.size(20.dp))
                                Text("Application Metadata", fontWeight = FontWeight.Bold, color = Color(0xFF01875F), fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("App Name *") },
                                placeholder = { Text("e.g. Brave Browser Mod") },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF01875F)) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("add_name_field")
                            )
                            
                            if (isForAdmin) {
                                OutlinedTextField(
                                    value = developer,
                                    onValueChange = { developer = it },
                                    label = { Text("Developer Team *") },
                                    leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color(0xFF01875F)) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LaunchedEffect(defaultDeveloperName) {
                                    if (defaultDeveloperName.isNotBlank()) {
                                        developer = defaultDeveloperName
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color(0xFF01875F).copy(alpha = 0.7f))
                                        Column {
                                            Text("Registered Publisher Team", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = developer.ifBlank { "Unverified Developer" },
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            
                            OutlinedTextField(
                                value = version,
                                onValueChange = { version = it },
                                label = { Text("Package Version Name (e.g., 1.5.0)") },
                                placeholder = { Text("1.0.0") },
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF01875F)) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = versionCodeInput,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() }) {
                                        versionCodeInput = newValue
                                    }
                                },
                                label = { Text("Package Version Code * (e.g. 100)") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF01875F)) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )

                            // Changelog: what's new in this version. Most useful when this
                            // form is used to submit an update to an already-published app,
                            // but left available for a first submission too (a launch note).
                            OutlinedTextField(
                                value = changelog,
                                onValueChange = { changelog = it },
                                label = { Text("Changelog / What's New (Optional)") },
                                placeholder = { Text("e.g. Fixed login crash, improved scroll performance") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF01875F)) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                minLines = 2,
                                maxLines = 4
                            )
                            
                            OutlinedTextField(
                                value = videoUrl,
                                onValueChange = { videoUrl = it.trim() },
                                label = { Text("Promo video link (Optional)") },
                                placeholder = { Text("YouTube link or direct .mp4 URL") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF01875F)) },
                                supportingText = { Text("Shown as the playable banner on your app's page") },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = size,
                                onValueChange = { size = it },
                                label = { Text("Binary Size (e.g., 15 MB)") },
                                placeholder = { Text("24 MB") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF01875F)) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = packageName,
                                onValueChange = { packageName = it },
                                label = { Text("Unique Package ID * (e.g., com.brave.mod)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF01875F)) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("App Marketplace Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF01875F))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                categories.forEach { cat ->
                                    val activeCat = category == cat
                                    FilterChip(
                                        selected = activeCat,
                                        onClick = { category = cat },
                                        label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF01875F),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFF01875F), modifier = Modifier.size(20.dp))
                                Text("Creative Assets & Screenshots", fontWeight = FontWeight.Bold, color = Color(0xFF01875F), fontSize = 14.sp)
                            }

                            // ——— App icon / logo ———
                            Text("App icon *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF01875F))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Live logo preview
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF01875F).copy(alpha = 0.08f))
                                        .border(1.dp, Color(0xFF01875F).copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isUploadingLogo -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFF01875F)
                                            )
                                        }
                                        logoUrl.isNotBlank() -> {
                                            AsyncImage(
                                                model = logoUrl.trim(),
                                                contentDescription = "Logo preview",
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = null,
                                                tint = Color(0xFF01875F).copy(alpha = 0.45f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = logoUrl,
                                        onValueChange = { logoUrl = it },
                                        label = { Text("Logo URL") },
                                        placeholder = { Text("https://…/icon.png") },
                                        shape = RoundedCornerShape(12.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            activePickingSlotIndex = 99
                                            val permissionToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                android.Manifest.permission.READ_MEDIA_IMAGES
                                            } else {
                                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                                            }
                                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context, permissionToRequest
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasPermission) {
                                                imagePickerLauncher.launch("image/*")
                                            } else {
                                                permissionLauncher.launch(permissionToRequest)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF01875F).copy(alpha = 0.12f),
                                            contentColor = Color(0xFF01875F)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = android.R.drawable.ic_menu_upload),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isUploadingLogo) "Uploading…" else "Upload logo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                if (rating.isBlank()) rating = "0.0"
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("App screenshots *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF01875F))
                                Text("3–6 screenshots. Paste a URL or upload — preview appears automatically.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            val slots = listOf(
                                Triple("Primary *", ss1, 0),
                                Triple("Screenshot 2 *", ss2, 1),
                                Triple("Screenshot 3 *", ss3, 2),
                                Triple("Screenshot 4", ss4, 3),
                                Triple("Screenshot 5", ss5, 4),
                                Triple("Screenshot 6", ss6, 5)
                            )

                            slots.forEach { (label, ssValue, idx) ->
                                val isRequired = idx < 3
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            if (isRequired && ssValue.isBlank()) Color(0xFF01875F).copy(alpha = 0.35f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Screenshot preview thumbnail
                                    Box(
                                        modifier = Modifier
                                            .width(56.dp)
                                            .height(96.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF01875F).copy(alpha = 0.06f))
                                            .border(1.dp, Color(0xFF01875F).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            idx < uploadingStates.size && uploadingStates[idx] -> {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color(0xFF01875F)
                                                )
                                            }
                                            ssValue.isNotBlank() -> {
                                                AsyncImage(
                                                    model = ssValue.trim(),
                                                    contentDescription = "Screenshot $idx preview",
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            else -> {
                                                Text(
                                                    text = "${idx + 1}",
                                                    color = Color(0xFF01875F).copy(alpha = 0.4f),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF01875F))
                                        OutlinedTextField(
                                            value = ssValue,
                                            onValueChange = { v ->
                                                when (idx) {
                                                    0 -> ss1 = v
                                                    1 -> ss2 = v
                                                    2 -> ss3 = v
                                                    3 -> ss4 = v
                                                    4 -> ss5 = v
                                                    5 -> ss6 = v
                                                }
                                            },
                                            placeholder = { Text("Paste URL or upload", fontSize = 11.sp) },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        )
                                        Button(
                                            onClick = {
                                                activePickingSlotIndex = idx
                                                val permissionToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                    android.Manifest.permission.READ_MEDIA_IMAGES
                                                } else {
                                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                                }
                                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context, permissionToRequest
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) {
                                                    imagePickerLauncher.launch("image/*")
                                                } else {
                                                    permissionLauncher.launch(permissionToRequest)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF01875F).copy(alpha = 0.12f),
                                                contentColor = Color(0xFF01875F)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            if (idx < uploadingStates.size && uploadingStates[idx]) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF01875F))
                                            } else {
                                                Icon(
                                                    painter = painterResource(id = android.R.drawable.ic_menu_upload),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Pick", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF01875F), modifier = Modifier.size(20.dp))
                                Text("Distribution & Monetization", fontWeight = FontWeight.Bold, color = Color(0xFF01875F), fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))

                            OutlinedTextField(
                                value = apkUrl,
                                onValueChange = { apkUrl = it },
                                label = { Text(if (isUpcoming) "Direct Download APK Url Link (Optional)" else "Direct Download APK Url Link *") },
                                placeholder = { Text("https://example.com/app.apk") },
                                leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF01875F)) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("add_apk_url_field")
                            )
                            if (apkUrl.contains("drive.google.com", ignoreCase = true) || apkUrl.contains("docs.google.com", ignoreCase = true)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE0F2FE), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Drive Support",
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Google Drive link detected! Clean direct downloads and virus scans confirmation bypass are fully supported and automated.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF0369A1),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Detailed description of software components") },
                                placeholder = { Text("Write a compelling explanation of features...") },
                                leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF01875F)) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Features & Options", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF01875F))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isFeatured,
                                    onCheckedChange = { isFeatured = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF01875F))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Promote to Featured slide carousel", fontSize = 12.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().testTag("has_ads_checkbox_row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hasAds,
                                    onCheckedChange = { hasAds = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF03A9F4))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Contains advertising (Show 'AD' badge)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Paid apps (real payments) aren't built yet — the old
                            // checkbox here let a developer flag an app "Premium"
                            // and set a price, which fed into a fully fake checkout
                            // dialog on the buyer's side (hardcoded "Play Balance
                            // $25.00", a made-up Visa card, a delay() pretending to
                            // process a payment). Removed that dishonest path
                            // entirely — this row is now disabled and clearly
                            // labeled "Coming Soon" instead of quietly lying to
                            // both developers and buyers.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("is_premium_checkbox_row")
                                    .clickable {
                                        Toast.makeText(
                                            context,
                                            "Paid apps aren't available yet — coming soon!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = null,
                                    enabled = false,
                                    colors = CheckboxDefaults.colors(disabledUncheckedColor = Color(0xFFFF9800).copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Premium App (paid apps)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFF9800).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "COMING SOON",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().testTag("is_upcoming_checkbox_row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isUpcoming,
                                    onCheckedChange = { isUpcoming = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF9C27B0))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upcoming Release (Mark as Pre-register only)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp).testTag("cancel_app_form_button"),
                        border = BorderStroke(1.2.dp, Color.Gray),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val isApkUrlRequired = !isUpcoming
                            if (name.isBlank() || (isApkUrlRequired && apkUrl.isBlank()) || packageName.isBlank()) {
                                val msg = if (isApkUrlRequired) {
                                    "Mandatory requirements: Name, Package ID, and Direct Apk Url must be specified."
                                } else {
                                    "Mandatory requirements: Name and Package ID must be specified."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (logoUrl.trim().isBlank()) {
                                Toast.makeText(context, "App icon / logo URL is required.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val screenshotUrls = listOf(ss1, ss2, ss3, ss4, ss5, ss6).map { it.trim() }.filter { it.isNotEmpty() }
                            // Guard: logo must not accidentally be the only value used as every screenshot
                            if (screenshotUrls.any { it.equals(logoUrl.trim(), ignoreCase = true) } && screenshotUrls.size < 3) {
                                Toast.makeText(context, "Screenshots look incomplete — make sure each slot has its own image, not only the logo.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (screenshotUrls.size < 3 || screenshotUrls.size > 6) {
                                Toast.makeText(context, "Please provide between 3 and 6 screenshots. You currently have ${screenshotUrls.size}.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val finalScreenshotsStr = screenshotUrls.joinToString(",")
                            onSubmit(
                                AppEntity(
                                    id = id.trim(),
                                    name = name.trim(),
                                    developer = if (developer.isBlank()) "Community Dev" else developer.trim(),
                                    version = if (version.isBlank()) "1.0.0" else version.trim(),
                                    size = if (size.isBlank()) "18 MB" else size.trim(),
                                    category = category,
                                    rating = if (rating.isBlank()) "0.0" else rating.trim(),
                                    description = if (description.isBlank()) "Standard safe installation package." else description.trim(),
                                    logo = logoUrl.trim(),
                                    screenshots = finalScreenshotsStr,
                                    apkUrl = apkUrl.trim(),
                                    packageName = packageName.trim(),
                                    isFeatured = isFeatured,
                                    isPremium = isPremium,
                                    price = if (isPremium) (if (price.isBlank()) "$1.99" else price.trim()) else "",
                                    isUpcoming = isUpcoming,
                                    isPopular = true,
                                    isRecent = true,
                                    versionCode = versionCodeInput.trim().toIntOrNull() ?: 1,
                                    changelog = changelog.trim(),
                                    videoUrl = videoUrl.trim(),
                                    isApproved = if (existingApp != null) existingApp.isApproved else isForAdmin,
                                    submittedBy = if (existingApp != null) existingApp.submittedBy else userEmail,
                                    hasAds = hasAds
                                )
                            )
                        },
                        modifier = Modifier.weight(1.5f).height(44.dp).testTag("submit_app_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF01875F)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (existingApp == null) "DEPLOY APKS" else "SAVE CHANGES",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ========================================================
// 11.B SIMULATED PURCHASE / CHECKOUT DIALOG
// ========================================================
@Composable
fun FollowersFollowingDialog(
    viewModel: com.example.viewmodel.StoreViewModel,
    isDarkMode: Boolean,
    developers: List<UserEntity>,
    onDismiss: () -> Unit
) {
    // Option A: dark card, pill tabs, compact rows
    val bgCol = if (isDarkMode) Color(0xFF1A1C22) else Color(0xFFFFFFFF)
    val rowCol = if (isDarkMode) Color(0xFF22252E) else Color(0xFFF8FAFC)
    val borderCol = if (isDarkMode) Color(0xFF2E3340) else Color(0xFFE2E8F0)
    val textPrimaryCol = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondaryCol = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentGreen = Color(0xFF22C55E)

    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val followerIds by viewModel.followerIds.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Following, 1 = Followers

    val followingUsers = remember(followingIds, developers) {
        followingIds.mapNotNull { uid -> developers.find { it.uid == uid } }
    }
    val followerUsers = remember(followerIds, developers) {
        followerIds.mapNotNull { uid -> developers.find { it.uid == uid } }
    }
    val list = if (selectedTab == 0) followingUsers else followerUsers

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.72f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = bgCol),
            border = BorderStroke(1.dp, borderCol),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Following & Followers",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryCol
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textSecondaryCol,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pill tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        0 to "Following (${followingUsers.size})",
                        1 to "Followers (${followerUsers.size})"
                    ).forEach { (index, label) ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (selected) accentGreen else Color.Transparent)
                                .border(
                                    width = if (selected) 0.dp else 1.dp,
                                    color = if (selected) Color.Transparent else borderCol,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else textSecondaryCol,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (list.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Default.PersonAdd else Icons.Default.Group,
                                contentDescription = null,
                                tint = textSecondaryCol.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (selectedTab == 0) "Not following anyone yet" else "No followers yet",
                                color = textSecondaryCol,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.uid }) { person ->
                            val personIsFollowedByMe = followingIds.contains(person.uid)
                            var isToggling by remember(person.uid) { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(rowCol)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(accentGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (person.profilePhotoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = person.profilePhotoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = (person.devName.ifBlank { person.displayName }).take(1).uppercase(),
                                            color = accentGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = person.devName.ifBlank { person.displayName }.ifBlank { "Developer" },
                                        color = textPrimaryCol,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (person.isDeveloper) "Developer" else "User",
                                        color = textSecondaryCol,
                                        fontSize = 12.sp
                                    )
                                }

                                // Follow / Following chip
                                val chipLabel = when {
                                    personIsFollowedByMe -> "Following"
                                    selectedTab == 1 -> "Follow Back"
                                    else -> "Follow"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (personIsFollowedByMe) Color.Transparent
                                            else accentGreen.copy(alpha = 0.15f)
                                        )
                                        .border(
                                            1.dp,
                                            if (personIsFollowedByMe) borderCol else accentGreen.copy(alpha = 0.45f),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .clickable(enabled = !isToggling) {
                                            isToggling = true
                                            viewModel.toggleFollowDeveloper(person.uid) {
                                                isToggling = false
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (personIsFollowedByMe) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = accentGreen,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = chipLabel,
                                            color = if (personIsFollowedByMe) textSecondaryCol else accentGreen,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
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


@Composable
fun ComingSoonDialog(
    title: String,
    message: String,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentSize()
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "coming_soon_pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.12f,
                        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "pulse_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Got it", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ========================================================
// 12. ANNOUNCEMENTS AND BIASED NOTIFICATION DETAILS DIALOG
// ========================================================
@Composable
fun NoticeDetailsDialog(
    notice: com.example.data.NoticeEntity,
    onDismiss: () -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val surface = if (isDark) Color(0xFF141820) else Color(0xFFFFFFFF)
    val onSurface = if (isDark) Color.White else Color(0xFF111827)
    val muted = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val border = if (isDark) Color(0xFF2A3140) else Color(0xFFE5E7EB)
    val accent = Color(0xFF3B82F6)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = surface),
            border = BorderStroke(1.dp, border),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header image / gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (notice.imageUrl.isNotBlank()) 180.dp else 110.dp)
                ) {
                    if (notice.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = notice.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, surface.copy(alpha = 0.85f))
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(accent.copy(alpha = 0.25f), surface)
                                    )
                                )
                        )
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = accent.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                        )
                    }

                    // Badge + close
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .align(Alignment.TopStart),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "SYSTEM NOTICE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(surface.copy(alpha = 0.75f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = onSurface, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = notice.title,
                        color = onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        lineHeight = 26.sp
                    )

                    val timeStr = java.text.SimpleDateFormat("MMM d, yyyy · HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(notice.timestamp))
                    Text(
                        text = timeStr,
                        color = muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    )

                    HorizontalDivider(color = border.copy(alpha = 0.8f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = notice.message,
                            color = onSurface.copy(alpha = 0.92f),
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }

                    val urlPattern = """https?://[^\s]+""".toRegex()
                    val detectedUrl = urlPattern.find(notice.message)?.value
                        ?: urlPattern.find(notice.title)?.value

                    if (detectedUrl != null) {
                        val context = LocalContext.current
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(detectedUrl)
                                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.OpenInNew, null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open link", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text("I understand", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
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
    var fcmServerKey by remember { 
        val stored = sharedPrefs.getString("fcm_server_key", "") ?: ""
        mutableStateOf(stored.ifBlank { "63dH-KuA8Y9q-V4wAZGpf_e6gFxYrhGp_qEu0LWDDik" })
    }

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
                                // Persist server key inside Shared Preferences
                                sharedPrefs.edit().putString("fcm_server_key", fcmServerKey.trim()).apply()

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

// ========================================================
// 12. FULLSCREEN INTERACTIVE LIGHTBOX VIEW FOR SCREENSHOTS
// ========================================================
@Composable
fun ScreenshotLightboxDialog(
    screenshots: List<String>,
    initialIndex: Int,
    appName: String,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex) }
    var isZoomed by remember(currentIndex) { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isZoomed) 1.8f else 1.0f, label = "zoom_state_anim")
    
    // Accumulate drag displacements for swiping gestures
    var dragAmountAccumulated by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF90B0D12)) // Dark sleek immersive cosmic background
        ) {
            // Main image viewer box with swipe detection and double tap / click to toggle zoom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragAmountAccumulated > 140f) {
                                    if (currentIndex > 0) {
                                        currentIndex--
                                        isZoomed = false
                                    }
                                } else if (dragAmountAccumulated < -140f) {
                                    if (currentIndex < screenshots.size - 1) {
                                        currentIndex++
                                        isZoomed = false
                                    }
                                }
                                dragAmountAccumulated = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragAmountAccumulated += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = screenshots[currentIndex],
                    contentDescription = "App High Resolution Screenshot Frame",
                    error = painterResource(id = R.drawable.img_app_logo_new),
                    modifier = Modifier
                        .fillMaxSize(0.85f)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isZoomed = !isZoomed
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Top Toolbar: Status indices and actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = appName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Screenshot ${currentIndex + 1} of ${screenshots.size}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Modern Zoom indicator badge
                    Surface(
                        onClick = { isZoomed = !isZoomed },
                        color = if (isZoomed) Color(0xFF34D399) else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.testTag("lightbox_zoom_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Zoom toggle symbol",
                                tint = if (isZoomed) Color(0xFF0B0D12) else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isZoomed) "1.8x Zoom" else "Fit View",
                                color = if (isZoomed) Color(0xFF0B0D12) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .testTag("lightbox_close_button")
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close full screen screenshot viewer",
                            tint = Color.White
                        )
                    }
                }
            }

            // Left Navigation Overlay Click zones & Arrows
            if (currentIndex > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                ) {
                    IconButton(
                        onClick = {
                            currentIndex--
                            isZoomed = false
                        },
                        modifier = Modifier
                            .testTag("lightbox_prev_button")
                            .size(50.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Load previous screenshot",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Right Navigation Overlay Click zones & Arrows
            if (currentIndex < screenshots.size - 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    IconButton(
                        onClick = {
                            currentIndex++
                            isZoomed = false
                        },
                        modifier = Modifier
                            .testTag("lightbox_next_button")
                            .size(50.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Load next screenshot",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Bottom Navigation & Mini-Thumblist Pager Strip
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(bottom = 24.dp, top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive dot line indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    screenshots.forEachIndexed { i, _ ->
                        val selected = i == currentIndex
                        Box(
                            modifier = Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .background(
                                    color = if (selected) Color(0xFF34D399) else Color.White.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Clicking mini horizontal gallery strips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    itemsIndexed(screenshots, key = { i, thumbUrl -> "$i-$thumbUrl" }) { i, thumbUrl ->
                        val isActive = i == currentIndex
                        val activeBorderColor = if (isActive) Color(0xFF34D399) else Color.White.copy(alpha = 0.4f)
                        
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(
                                    border = BorderStroke(if (isActive) 2.dp else 1.dp, activeBorderColor),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    currentIndex = i
                                    isZoomed = false
                                }
                        ) {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = "Direct switch to screenshot $i",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ========================================================
// 13. PROMOTED APPS INTERACTIVE AUTO-SLIDING CAROUSEL SKELETON & LOADER
// ========================================================
@Composable
fun PromotedAppsCarouselSkeleton(
    isDarkMode: Boolean,
    cardBgColor: Color,
    cardBorderColor: Color,
    shimmerTranslate: Float
) {
    val brush = shimmerBrush(isDarkMode, shimmerTranslate)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(134.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Category badge skeleton
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Title skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description skeleton line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Description skeleton line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                // Logo skeleton
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Button skeleton
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush)
                )
            }
        }
    }
}

// ========================================================
// 13. PROMOTED APPS INTERACTIVE AUTO-SLIDING CAROUSEL
// ========================================================
@Composable
fun PromotedAppsCarousel(
    featuredList: List<AppEntity>,
    allApps: List<AppEntity>,
    isDarkMode: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    onAppClick: (AppEntity) -> Unit
) {
    // Collect promoted apps: either featuredList, or if featuredList is empty, use the first 4 apps in allApps as a fallback!
    val promotedApps = remember(featuredList, allApps) {
        if (featuredList.isNotEmpty()) {
            featuredList
        } else {
            allApps.take(4)
        }
    }

    if (promotedApps.isEmpty()) {
        // Fallback static Hero if absolutely no apps are available
        PlayStoreBannerHero(accentColor)
        return
    }

    // NOTE: this used to hold a 180ms fake "loading" delay + shimmer before showing
    // the banner, re-triggered by `remember` with no keys every time this composable
    // re-entered composition — which happens every time the user scrolls this item
    // (it lives inside the LazyColumn) out of view and back in. That produced a
    // visible flash on nearly every scroll-up. Removed; the banner has all its data
    // already and can render immediately.
    if (false) {
        val shimmerTransition = rememberInfiniteTransition(label = "promoted_shimmer")
        val shimmerTranslate by shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "promoted_shimmer_translate"
        )
        val skeletonBgColor = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        val skeletonBorderColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
        
        PromotedAppsCarouselSkeleton(
            isDarkMode = isDarkMode,
            cardBgColor = skeletonBgColor,
            cardBorderColor = skeletonBorderColor,
            shimmerTranslate = shimmerTranslate
        )
    } else {
        var currentIndex by remember(promotedApps) { mutableStateOf(0) }
        var slideDirectionLeft by remember { mutableStateOf(true) }

        // Auto-slide running inside a LaunchedEffect:
        LaunchedEffect(currentIndex, promotedApps.size) {
            if (promotedApps.size > 1) {
                kotlinx.coroutines.delay(4000L)
                slideDirectionLeft = true
                currentIndex = (currentIndex + 1) % promotedApps.size
            }
        }

        val activeApp = promotedApps[currentIndex]

        // To implement touch swiping gestures easily:
        var dragAmountAccumulated by remember { mutableStateOf(0f) }

        Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(134.dp)
            .pointerInput(currentIndex, promotedApps.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAmountAccumulated > 120f) {
                            // Swipe right (load previous)
                            if (promotedApps.size > 1) {
                                slideDirectionLeft = false
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else promotedApps.size - 1
                            }
                        } else if (dragAmountAccumulated < -120f) {
                            // Swipe left (load next)
                            if (promotedApps.size > 1) {
                                slideDirectionLeft = true
                                currentIndex = (currentIndex + 1) % promotedApps.size
                            }
                        }
                        dragAmountAccumulated = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAmountAccumulated += dragAmount
                    }
                )
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, glassEdge(isDarkMode))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val cardBgGradient = remember(isDarkMode, accentColor) {
                if (isDarkMode) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            accentColor.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.88f),
                            accentColor.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.66f)
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(cardBgGradient)
                    .clickable { onAppClick(activeApp) }
                    .padding(14.dp)
            ) {
                AnimatedContent(
                    targetState = activeApp,
                    transitionSpec = {
                        if (slideDirectionLeft) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }
                    },
                    label = "promoted_app_transition",
                    modifier = Modifier.fillMaxSize()
                ) { app ->
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PROMOTED • ${app.category.uppercase()}",
                                    color = accentColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = app.name,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Text(
                                text = app.description.ifBlank { "Modern app built securely for the catalog store platform." },
                                color = textSecondary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp, end = 8.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            AppLogo(
                                logoUrl = app.logo,
                                appName = app.name,
                                packageName = app.packageName,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Button(
                                onClick = { onAppClick(app) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = "VIEW",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (promotedApps.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        promotedApps.forEachIndexed { i, _ ->
                            val selected = i == currentIndex
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 7.dp else 5.dp)
                                    .background(
                                        color = if (selected) accentColor else textSecondary.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun TimelineStepRow(
    title: String,
    subtitle: String,
    statusIcon: androidx.compose.ui.graphics.vector.ImageVector,
    statusColor: androidx.compose.ui.graphics.Color,
    isLast: Boolean,
    textPrimaryCol: androidx.compose.ui.graphics.Color,
    textSecondaryCol: androidx.compose.ui.graphics.Color,
    borderCol: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            modifier = androidx.compose.ui.Modifier.width(24.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .size(24.dp)
                    .background(statusColor.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape)
                    .border(1.5.dp, statusColor, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = androidx.compose.ui.Modifier.size(12.dp)
                )
            }
            if (!isLast) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(statusColor, borderCol.copy(alpha = 0.4f))
                            )
                        )
                )
            }
        }
        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.padding(bottom = if (isLast) 0.dp else 12.dp)) {
            androidx.compose.material3.Text(
                text = title, 
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, 
                fontSize = 13.sp, 
                color = textPrimaryCol
            )
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(2.dp))
            androidx.compose.material3.Text(
                text = subtitle, 
                fontSize = 11.sp, 
                color = textSecondaryCol, 
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun MetadataBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    surfaceCol: androidx.compose.ui.graphics.Color,
    borderCol: androidx.compose.ui.graphics.Color,
    textPrimaryCol: androidx.compose.ui.graphics.Color,
    textSecondaryCol: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(surfaceCol, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .border(1.dp, borderCol, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .size(28.dp)
                .background(accentColor.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = accentColor, 
                modifier = androidx.compose.ui.Modifier.size(14.dp)
            )
        }
        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                text = label, 
                fontSize = 9.sp, 
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, 
                color = textSecondaryCol, 
                letterSpacing = 0.4.sp
            )
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(1.dp))
            androidx.compose.material3.Text(
                text = value, 
                fontSize = 11.sp, 
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, 
                color = textPrimaryCol, 
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

// ========================================================
// IN-APP UPDATE SYSTEM
// ========================================================
sealed class UpdateState {
    object Checking : UpdateState()
    object NoUpdateNeeded : UpdateState()
    data class UpdateRequired(
        val latestVersionCode: Int,
        val latestVersionName: String,
        val apkDownloadUrl: String,
        val updateTitle: String,
        val updateMessage: String,
        val forceUpdate: Boolean,
        val offlineMode: Boolean = false
    ) : UpdateState()
}

/**
 * Uploads image bytes directly to ImgBB and returns the hosted URL, or null on
 * failure. Centralizes what used to be duplicated (and inconsistent) upload
 * logic across the app logo, screenshots (in two separate forms), and notice
 * image pickers — some of which relied solely on an unreliable Firebase
 * Storage call with no fallback at all. This always goes straight to ImgBB
 * and reports a clear "server is down" message on failure via [onError],
 * rather than the vague "upload failed/skipped" messages that existed before.
 */
internal suspend fun uploadImageToImgBB(
    context: android.content.Context,
    bytes: ByteArray,
    onError: (String) -> Unit
): String? {
    return try {
        val base64String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val formBody = okhttp3.FormBody.Builder()
            .add("image", base64String)
            .build()
        val request = okhttp3.Request.Builder()
            .url("https://api.imgbb.com/1/upload?key=a046c848dfa5230136f107106d4bb187")
            .post(formBody)
            .build()
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val bodyString = response.body?.string() ?: ""
            val match = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(bodyString)
            val uploadedUrl = match?.groupValues?.get(1)?.replace("\\/", "/")
            if (uploadedUrl == null) {
                onError("Image server returned an unexpected response. Please try again.")
            }
            uploadedUrl
        } else {
            onError("Image server is currently down (error ${response.code}). Please try again later.")
            null
        }
    } catch (ex: java.io.IOException) {
        onError("Image server is currently down or unreachable. Please try again later.")
        null
    } catch (ex: Exception) {
        onError("Upload failed: ${ex.message}")
        null
    }
}

private fun getInstalledVersionCode(context: android.content.Context): Int {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    } catch (e: Exception) {
        1
    }
}

private fun getInstalledVersionName(context: android.content.Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

private fun isNetworkAvailable(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
    if (connectivityManager != null) {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    return false
}

@Composable
fun OptionalUpdateDialog(
    update: UpdateState.UpdateRequired,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    val hasInternet = remember { isNetworkAvailable(context) }

    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161B22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Update available",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = update.updateTitle.ifBlank { "Update Available" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = update.updateMessage.ifBlank { "A new version (${update.latestVersionName}) of Dark Store is available." },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = downloadProgress!! / 100f,
                            color = Color(0xFF34D399),
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloading... ${downloadProgress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    } else {
                        CircularProgressIndicator(color = Color(0xFF34D399), modifier = Modifier.size(24.dp))
                    }
                }

                downloadError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Download Failed: $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF5252)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isDownloading,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Later", color = Color.White)
                    }
                    Button(
                        onClick = {
                            if (!hasInternet) {
                                downloadError = "No active internet connection."
                                return@Button
                            }
                            isDownloading = true
                            downloadError = null
                            // PERF/BUG: constructing AppDao does synchronous file I/O in its
                            // init block (reads 3 cached JSON files from disk) — this ran on
                            // the Main dispatcher by default here, a real main-thread stall
                            // every time this button was tapped. Dispatch the whole flow to IO.
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val appDao = com.example.data.AppDao(context.applicationContext)
                                    val repository = com.example.data.AppRepository(appDao)
                                    val downloadManager = com.example.utils.CustomDownloadManager(context, repository)
                                    val downloadedFile = downloadManager.downloadSelfUpdate(update.apkDownloadUrl) { progress ->
                                        downloadProgress = progress
                                    }
                                    isDownloading = false
                                    com.example.utils.ApkInstaller.installApk(context, downloadedFile)
                                } catch (e: Exception) {
                                    isDownloading = false
                                    downloadError = e.message ?: "Unknown error"
                                }
                            }
                        },
                        enabled = !isDownloading && hasInternet,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34D399),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Update Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateRequiredScreen(
    update: UpdateState.UpdateRequired,
    context: android.content.Context,
    onSkip: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    
    val hasInternet = remember { isNetworkAvailable(context) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D12))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Update Icon",
                tint = Color(0xFF34D399),
                modifier = Modifier.size(72.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Dark Store Update Available",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E222B)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Current Version",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = getInstalledVersionName(context),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Arrow",
                        tint = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Latest Version",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = update.latestVersionName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Divider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
                
                Text(
                    text = update.updateTitle.ifBlank { "New features await!" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = update.updateMessage.ifBlank { "Please update to continue using Dark Store with latest premium features." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                if (!hasInternet || update.offlineMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4A1521), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Offline",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "An active internet connection is required to download this update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF8A80)
                        )
                    }
                }
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isDownloading) {
                val progress = downloadProgress ?: 0
                if (progress >= 0) {
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF34D399),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Downloading Update: $progress%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color(0xFF34D399)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Downloading Update...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
            
            downloadError?.let { err ->
                Text(
                    text = "Download Failed: $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5252),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (!hasInternet && !update.offlineMode) {
                        downloadError = "No active internet connection."
                        return@Button
                    }
                    
                    isDownloading = true
                    downloadError = null
                    
                    // PERF/BUG: same main-thread file I/O issue as the optional-update
                    // dialog above — AppDao's constructor synchronously reads 3 cached
                    // JSON files from disk. Dispatch to IO instead of the default Main.
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val appDao = com.example.data.AppDao(context.applicationContext)
                            val repository = com.example.data.AppRepository(appDao)
                            val downloadManager = com.example.utils.CustomDownloadManager(
                                context,
                                repository
                            )
                            val downloadedFile = downloadManager.downloadSelfUpdate(update.apkDownloadUrl) { progress ->
                                downloadProgress = progress
                            }
                            
                            isDownloading = false
                            com.example.utils.ApkInstaller.installApk(context, downloadedFile)
                        } catch (e: Exception) {
                            isDownloading = false
                            downloadError = e.message ?: "Unknown error"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("update_now_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34D399),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(25.dp),
                enabled = !isDownloading && (hasInternet && !update.offlineMode)
            ) {
                Text(
                    text = "Update Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        (context as? android.app.Activity)?.finishAffinity()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("exit_app_button"),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "Exit App",
                        color = Color.White
                    )
                }
                
                if (!update.forceUpdate) {
                    Button(
                        onClick = onSkip,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("skip_update_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "Skip for Now"
                        )
                    }
                }
            }
        }
    }
}

// existingHistoryJson lets a caller that has access to the already-published
// app (by packageName) carry its versionHistoryJson into this synthetic
// AppEntity — without it, viewing a submission's "details" (e.g. from the
// admin review queue, or a developer's own submissions list) would show an
// empty version history even when the app being updated already has a rich
// one, since SubmissionEntity itself has no such field to draw from.
fun com.example.data.SubmissionEntity.toAppEntity(existingHistoryJson: String = "") = com.example.data.AppEntity(
    id = id,
    name = name,
    developer = developer,
    version = version,
    size = "18 MB",
    category = category,
    rating = "0.0",
    description = description,
    logo = logo,
    screenshots = screenshots,
    apkUrl = apkUrl,
    packageName = packageName,
    isFeatured = false,
    isPopular = true,
    isRecent = true,
    versionCode = 1,
    isApproved = status == "Approved",
    submittedBy = submittedBy,
    hasAds = hasAds,
    versionHistoryJson = existingHistoryJson
)

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
fun PremiumBenefitItem(
    title: String,
    desc: String,
    unlocked: Boolean,
    premiumGold: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (unlocked) Icons.Default.CheckCircle else Icons.Default.Star,
            contentDescription = null,
            tint = if (unlocked) premiumGold else textSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(14.dp).padding(top = 1.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) premiumGold else textSecondary
            )
            Text(
                text = desc,
                fontSize = 9.5.sp,
                color = textSecondary.copy(alpha = 0.8f),
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun PaymentSimulationDialog(
    onDismiss: () -> Unit,
    onPaymentSuccess: () -> Unit,
    isDarkMode: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    premiumGold: Color
) {
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isLoading && !isSuccess) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("payment_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSuccess) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = premiumGold,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "WELCOME TO PREMIUM!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = premiumGold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your free sandbox trial has been successfully activated. All premium capabilities are now unlocked.",
                        fontSize = 12.sp,
                        color = textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onPaymentSuccess,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = premiumGold, contentColor = Color.White)
                    ) {
                        Text("LET'S GO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PREMIUM ENABLER",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = textPrimary
                        )
                        IconButton(onClick = onDismiss, enabled = !isLoading) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "DarkStore Premium is currently 100% free for sandbox testing. Enable below to instantly upgrade your account.",
                        fontSize = 12.sp,
                        color = textSecondary,
                        modifier = Modifier.align(Alignment.Start),
                        lineHeight = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                kotlinx.coroutines.delay(1000) // Simulating activation delay
                                isLoading = false
                                isSuccess = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = premiumGold, contentColor = Color.White),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ACTIVATING PREMIUM...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ACTIVATE PREMIUM FOR FREE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}



data class DeveloperAppGroup(
    val packageName: String,
    val liveSub: com.example.data.SubmissionEntity?,
    val pendingSub: com.example.data.SubmissionEntity?,
    val rejectedSub: com.example.data.SubmissionEntity?,
    val latestSub: com.example.data.SubmissionEntity
)

fun buildDeveloperAppGroups(submissions: List<com.example.data.SubmissionEntity>): List<DeveloperAppGroup> {
    return submissions.groupBy { it.packageName }.map { (packageName, list) ->
        val sortedList = list.sortedByDescending { it.createdAt }
        val liveSub = sortedList.firstOrNull { it.status.equals("approved", ignoreCase = true) || it.status.equals("live", ignoreCase = true) }
        val pendingSub = sortedList.firstOrNull { !it.status.equals("approved", ignoreCase = true) && !it.status.equals("live", ignoreCase = true) && !it.status.equals("rejected", ignoreCase = true) }
        val rejectedSub = sortedList.firstOrNull { it.status.equals("rejected", ignoreCase = true) }
        DeveloperAppGroup(
            packageName = packageName,
            liveSub = liveSub,
            pendingSub = pendingSub,
            rejectedSub = rejectedSub,
            latestSub = sortedList.first()
        )
    }.sortedByDescending { it.latestSub.createdAt }
}

@Composable
fun DeveloperAppGroupCard(
    group: DeveloperAppGroup,
    surfaceCol: Color,
    borderCol: Color,
    textPrimaryCol: Color,
    textSecondaryCol: Color,
    accentGreen: Color,
    isDarkMode: Boolean,
    onRequestUpdate: (com.example.data.SubmissionEntity) -> Unit,
    onEditOptions: (com.example.data.SubmissionEntity) -> Unit,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val mainAppSub = group.liveSub ?: group.latestSub

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .testTag("dev_app_group_card_" + group.packageName),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceCol),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // App Logo Placeholder (styled beautifully)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mainAppSub.name.trim().take(1).uppercase(),
                        color = accentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Box {
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = textSecondaryCol,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Update App Form", fontSize = 12.sp) },
                            onClick = {
                                expanded = false
                                onRequestUpdate(mainAppSub)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Submit", fontSize = 12.sp) },
                            onClick = {
                                expanded = false
                                onEditOptions(mainAppSub)
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = mainAppSub.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = textPrimaryCol,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = mainAppSub.category,
                color = textSecondaryCol,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("5.0", fontSize = 12.sp, color = textPrimaryCol, fontWeight = FontWeight.Bold)
                    Icon(imageVector = Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                }
                Text("23M", fontSize = 12.sp, color = textPrimaryCol, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val (statusLabel, statusColor) = when {
                group.liveSub != null -> Pair("Published", Color(0xFF10B981))
                group.pendingSub != null -> Pair("Pending", Color(0xFFF59E0B))
                else -> Pair("Rejected", Color(0xFFEF4444))
            }
            
            Text(
                text = statusLabel,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
