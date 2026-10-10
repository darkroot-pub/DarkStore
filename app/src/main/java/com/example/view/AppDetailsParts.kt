package com.example.view

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
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

/** https://www.youtube.com/embed/VIDEO_ID with the documented player parameters (no fullscreen button). */
private fun embedUrl(videoId: String, appOrigin: String): String =
    "https://www.youtube.com/embed/$videoId?enablejsapi=1&autoplay=1&playsinline=1&rel=0&fs=0" +
        "&origin=" + java.net.URLEncoder.encode(appOrigin, "UTF-8")

/**
 * Player page. It is loaded with loadDataWithBaseURL(baseUrl = https://<applicationId>/), which is the
 * documented way to give an app-bundled player its API-client identity: the iframe request then carries
 * Referer: https://<applicationId>/ . The iframe sets referrerpolicy explicitly (never "no-referrer") and
 * the IFrame API is attached to it only to report errors back to the app.
 */
internal fun youTubePlayerHtml(videoId: String, appOrigin: String): String = """
<!DOCTYPE html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>
</head><body>
<iframe id="yt" src="${embedUrl(videoId, appOrigin)}"
  referrerpolicy="strict-origin-when-cross-origin"
  allow="autoplay; encrypted-media; picture-in-picture"></iframe>
<script>
  var tag = document.createElement('script');
  tag.src = 'https://www.youtube.com/iframe_api';
  tag.onerror = function () { DarkStore.onError(-1); };
  document.head.appendChild(tag);
  function onYouTubeIframeAPIReady() {
    new YT.Player('yt', { events: {
      onReady: function () { DarkStore.onReady(); },
      onError: function (e) { DarkStore.onError(e.data); }
    }});
  }
</script>
</body></html>
""".trimIndent()

/** Receives the player's events. Harmless on purpose: it can only report "ready" or an error code. */
private class YouTubeBridge(private val readyCallback: () -> Unit, private val errorCallback: (Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun onReady() { main.post { readyCallback() } }
    @JavascriptInterface fun onError(code: Int) { main.post { errorCallback(code) } }
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
 * Plays the app's promo video right inside the details page (16:9, no full-screen takeover).
 * Close / "Open in YouTube" sit BELOW the player — YouTube's rules forbid overlays on top of it.
 * YouTube links use the embedded player; any other link is played with the built-in video view.
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
    var ready by remember(url) { mutableStateOf(false) }
    var slow by remember(url) { mutableStateOf(false) }

    // Pre-resolve messages: the player callbacks aren't composable
    val msg153 = tr("vid_err_153")
    val msgBlocked = tr("vid_err_blocked")
    val msgUnavailable = tr("vid_err_unavailable")
    val msgNet = tr("vid_err_net")
    val msgGeneric = tr("vid_err_generic")
    fun messageFor(code: Int) = when (code) {
        153 -> "$msg153 (153)"
        101, 150 -> "$msgBlocked ($code)"
        100 -> "$msgUnavailable (100)"
        -1 -> msgNet
        else -> "$msgGeneric ($code)"
    }

    LaunchedEffect(url) {
        if (ytId != null) { delay(12_000); if (!ready && error == null) slow = true }
    }

    Column(Modifier.fillMaxWidth()) {
        // The glass frame is a SIBLING drawn behind the player. Clipping/shadowing the WebView itself
        // (rounded clip, graphicsLayer) breaks WebView's video surface: audio plays, picture stays black.
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().glass(isDark, RoundedCornerShape(24.dp), 5.dp).background(Color.Black.copy(alpha = 0.92f)))
            Box(Modifier.padding(8.dp).fillMaxWidth().aspectRatio(16f / 9f)) {
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
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            // NOTE: no setLayerType(...) here. Forcing a HARDWARE layer renders the WebView into an
                            // offscreen texture, and on many phones (MIUI/Realme/Samsung) YouTube's video plane is
                            // then left out: audio plays, picture stays black. The window is already GPU-accelerated.
                            settings.javaScriptEnabled = true           // required by the YouTube player
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowFileAccess = false            // hardening: this page needs no local files
                            settings.allowContentAccess = false
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) {
                                    if (request.isForMainFrame) error = messageFor(-1)
                                }
                            }
                            addJavascriptInterface(
                                YouTubeBridge(readyCallback = { ready = true; slow = false }, errorCallback = { code -> error = messageFor(code) }),
                                "DarkStore"
                            )
                            // baseUrl https://<applicationId>/ → Referer for the embedded player (API client identity)
                            loadDataWithBaseURL("$appOrigin/", youTubePlayerHtml(ytId, appOrigin), "text/html", "utf-8", null)
                        }
                    },
                    onRelease = { it.removeJavascriptInterface("DarkStore"); it.stopLoading(); it.loadUrl("about:blank"); it.destroy() }
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            // The video lives on a SurfaceView; inside a Dialog + scrolling Compose parent it can end up
                            // behind the window (audio only). Keep it above the window background, below dialog content.
                            setZOrderMediaOverlay(true)
                            val controller = MediaController(ctx).also { it.setAnchorView(this) }
                            setMediaController(controller)
                            setVideoURI(Uri.parse(url))
                            setOnPreparedListener { ready = true; start() }
                            setOnErrorListener { _, _, _ -> error = msgGeneric; true }
                        }
                    },
                    onRelease = { it.stopPlayback() }
                )
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
        if (slow && error == null) {
            Text(tr("vid_slow"), color = textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        }
    }
}
