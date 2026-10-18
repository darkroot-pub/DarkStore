package com.darkstore.view

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** One of the four info boxes under the app title (Size · Version · Ads · Reviews). */
@Composable
fun AppInfoTile(label: String, value: String, isDark: Boolean, textPrimary: Color, textSecondary: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.glass(isDark, RoundedCornerShape(18.dp), elevation = 2.dp).padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = textSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

/** Shown only for apps an admin has approved — it states exactly that, nothing more. */
@Composable
fun VerifiedCard(isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color) {
    Row(
        Modifier.fillMaxWidth().glass(isDark, RoundedCornerShape(22.dp), elevation = 2.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.VerifiedUser, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("det_verified"), color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Default.Verified, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
            }
            Text(tr("det_verified_sub"), color = textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** "About this app" — tap to expand. */
@Composable
fun AboutCard(text: String, isDark: Boolean, textPrimary: Color, textSecondary: Color) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().glass(isDark, RoundedCornerShape(22.dp), elevation = 2.dp)
            .clickable { open = !open }.animateContentSize().padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("det_about"), color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = textSecondary, modifier = Modifier.size(22.dp).rotate(if (open) 90f else 0f))
        }
        Text(
            text, color = textPrimary, fontSize = 13.sp, lineHeight = 19.sp,
            maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** "What's New — Version x.y   More →" with the changelog. */
@Composable
fun WhatsNewCard(version: String, changelog: String, isDark: Boolean, accent: Color, textPrimary: Color, textSecondary: Color) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().glass(isDark, RoundedCornerShape(22.dp), elevation = 2.dp)
            .clickable { open = !open }.animateContentSize().padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("det_whatsnew"), color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(if (open) tr("det_less") else tr("more"), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Default.ArrowForward, null, tint = accent, modifier = Modifier.size(15.dp).rotate(if (open) 90f else 0f))
        }
        Text("${tr("version")} $version", color = textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text(
            changelog, color = textPrimary, fontSize = 13.sp, lineHeight = 19.sp,
            maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ───────────────────────── Promo video ─────────────────────────

private val YT_ID = Regex("^[A-Za-z0-9_-]{11}$")

private fun queryParam(rawQuery: String?, key: String): String? =
    rawQuery?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it[0] == key }?.getOrNull(1)

/**
 * 11-character video id from youtu.be/ID, youtube.com/watch?v=ID, /embed/ID, /shorts/ID, /live/ID, /v/ID
 * and youtube-nocookie.com/embed/ID links (extra parameters like ?si= or &t= are ignored). Null if it
 * isn't a YouTube link. Pure JVM (java.net.URI) so it can be unit-tested without Android.
 */
fun youTubeId(url: String): String? {
    val raw = url.trim()
    if (raw.isEmpty()) return null
    val uri = try { java.net.URI(if ("://" in raw) raw else "https://$raw") } catch (e: Exception) { return null }
    val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    val segments = (uri.path ?: "").split('/').filter { it.isNotEmpty() }
    val candidate = when (host) {
        "youtu.be" -> segments.firstOrNull()
        "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when (segments.firstOrNull()) {
            "watch" -> queryParam(uri.rawQuery, "v")
            "embed", "shorts", "live", "v" -> segments.getOrNull(1)
            else -> null
        }
        else -> null
    }
    return candidate?.takeIf { YT_ID.matches(it) }
}

private fun openExternally(context: android.content.Context, url: String, videoId: String?) {
    val tries = buildList {
        if (videoId != null) add(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")))   // YouTube app
        add(Intent(Intent.ACTION_VIEW, Uri.parse(if (videoId != null) "https://www.youtube.com/watch?v=$videoId" else url)))
    }
    for (intent in tries) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: ActivityNotFoundException) { }
    }
    Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
}

/**
 * Plays the app's promo video right inside the details page, always in the same 16:9 slot.
 * Playing system: YouTube links load the official embed URL directly (no local HTML page) with the app id as the
 * HTTPS Referer — the YouTube API-services requirement that avoids Error 153 — inside a MATCH_PARENT FrameLayout
 * with a hardware layer (avoids black screen / audio-only). Any other link plays in a VideoView.
 * Close / "Open in YouTube" sit BELOW the player — YouTube's rules forbid overlays on top of it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InlineVideoPlayer(
    url: String,
    isDark: Boolean,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val ytId = remember(url) { youTubeId(url) }
    val appOrigin = remember { "https://" + context.packageName.lowercase() }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var started by remember(url) { mutableStateOf(false) }
    var posterGone by remember(url) { mutableStateOf(false) }
    val msgNet = tr("vid_err_net")
    val msgGeneric = tr("vid_err_generic")

    LaunchedEffect(url) { delay(4_000); posterGone = true }   // never keep the cover over a working player

    Column(Modifier.fillMaxWidth()) {
        // The player fills the whole 16:9 slot edge to edge (no frame / padding around it).
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(24.dp)).background(Color.Black)
            ) {
                val err = error
                if (err != null) {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF111418)).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFFB300), modifier = Modifier.size(30.dp))
                        Text(err, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp))
                        Text(
                            tr("vid_open_yt"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(20.dp))
                                .background(accent).clickable { openExternally(context, url, ytId) }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                } else if (ytId != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // Outer FrameLayout so the WebView always gets MATCH_PARENT size before it loads.
                            FrameLayout(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                val webView = WebView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    // Hardware layer avoids black screen + audio-only on many devices.
                                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        mediaPlaybackRequiresUserGesture = false
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                    }
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    webChromeClient = WebChromeClient()
                                    webViewClient = object : WebViewClient() {
                                        // SECURITY: the player may only ever navigate inside YouTube.
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val h = request?.url?.host?.lowercase() ?: return true
                                            val ok = request.url.scheme == "https" && (h == "youtube.com" || h.endsWith(".youtube.com") || h.endsWith(".youtube-nocookie.com") || h == "youtu.be")
                                            return !ok
                                        }
                                        override fun onPageFinished(view: WebView?, u: String?) { started = true }
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) {
                                            if (request.isForMainFrame) error = msgNet
                                        }
                                    }
                                    // Official embed URL; origin= + Referer header give the player its client identity.
                                    val embed = "https://www.youtube.com/embed/$ytId?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=0&origin=${Uri.encode(appOrigin)}"
                                    loadUrl(embed, mapOf("Referer" to appOrigin))
                                }
                                addView(webView)
                                tag = webView   // for onRelease cleanup
                            }
                        },
                        onRelease = { container ->
                            val wv = container.tag as? WebView
                            wv?.stopLoading(); wv?.loadUrl("about:blank"); wv?.destroy()
                            container.removeAllViews()
                        }
                    )
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                val controller = MediaController(ctx).also { it.setAnchorView(this) }
                                setMediaController(controller)
                                setVideoURI(Uri.parse(url))
                                setOnPreparedListener { started = true; start() }
                                setOnErrorListener { _, _, _ -> error = msgGeneric; true }
                            }
                        },
                        onRelease = { it.stopPlayback() }
                    )
                }

                // Cover the not-yet-loaded player with the video's thumbnail + spinner (no black flash)
                if (error == null && !started && !posterGone) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF111418)), contentAlignment = Alignment.Center) {
                        if (ytId != null) {
                            AsyncImage(
                                model = "https://img.youtube.com/vi/$ytId/hqdefault.jpg", contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }

        // Controls under the player (never on top of it)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).glass(isDark, RoundedCornerShape(20.dp), 2.dp)
                    .clickable(onClick = onClose).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Close, null, tint = textPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("vid_close"), color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).glass(isDark, RoundedCornerShape(20.dp), 2.dp)
                    .clickable { openExternally(context, url, ytId) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.OpenInNew, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("det_open_ext"), color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
