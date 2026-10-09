package com.example.view

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

/** 11-character YouTube id from watch / youtu.be / shorts / embed links, or null. */
fun youTubeId(url: String): String? =
    Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?(?:.*&)?v=|embed/|shorts/|v/))([A-Za-z0-9_-]{11})""")
        .find(url)?.groupValues?.getOrNull(1)

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

/**
 * Inline promo video player for the app-details banner (16:9).
 * Plays in place on the detail page — does NOT open a fullscreen dialog.
 *
 * YouTube Error 153 fix: HTTP Referer via loadUrl(headers) using the app ID as HTTPS
 * origin (YouTube API Services Required Minimum Functionality).
 * Black-screen / audio-only fix: hardware layer, wide viewport, MATCH_PARENT layout.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PromoVideoPlayer(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ytId = remember(url) { youTubeId(url) }
    val appOrigin = "https://com.darkstore.darkroot"

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
    ) {
        if (ytId != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    // Outer FrameLayout so WebView always gets MATCH_PARENT size before load.
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val webView = WebView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            // Hardware layer avoids black-screen + audio-only on many devices.
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                // Do not weaken security (no allowFileAccessFromFileURLs etc.).
                            }
                            setBackgroundColor(android.graphics.Color.BLACK)
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                @Deprecated("Deprecated in Java")
                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    Toast.makeText(
                                        ctx,
                                        "Couldn't play this video here — try Open externally",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            // Official embed URL. origin= helps client identity.
                            val embedUrl =
                                "https://www.youtube.com/embed/$ytId?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=0&origin=${Uri.encode(appOrigin)}"
                            // Primary path from YouTube docs (direct embed, no local HTML): Referer header.
                            val headers = mapOf("Referer" to appOrigin)
                            loadUrl(embedUrl, headers)
                        }
                        addView(webView)
                        tag = webView // for onRelease cleanup
                    }
                },
                onRelease = { container ->
                    val wv = container.tag as? WebView
                    wv?.stopLoading()
                    wv?.loadUrl("about:blank")
                    wv?.destroy()
                    container.removeAllViews()
                }
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val controller = MediaController(ctx).also { it.setAnchorView(this) }
                        setMediaController(controller)
                        setVideoURI(Uri.parse(url))
                        setOnPreparedListener { start() }
                        setOnErrorListener { _, _, _ ->
                            Toast.makeText(ctx, "Couldn't play this video here — try Open externally", Toast.LENGTH_LONG).show()
                            true
                        }
                    }
                },
                onRelease = { it.stopPlayback() }
            )
        }

        // Close — stays on the banner, not system status bars.
        Icon(
            Icons.Default.Close, "Close", tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClose)
                .padding(6.dp)
        )
        // Open externally
        Text(
            tr("det_open_ext"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

/**
 * Backwards-compatible entry used by MainActivity. Plays inline in the detail banner
 * (same 16:9 slot) — no fullscreen dialog.
 */
@Composable
fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    PromoVideoPlayer(url = url, onClose = onDismiss)
}
