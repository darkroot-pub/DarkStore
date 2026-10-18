package com.darkstore.view

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkstore.utils.UnityAdsManager

/** True only while a Unity banner is loaded AND the admin switch is on. */
@Composable
fun rememberUnityAdReady(): Boolean {
    val ready by UnityAdsManager.adReady.collectAsStateWithLifecycle()
    return ready
}

/**
 * Replaces the promo banner in the SAME slot (same height / corner radius / glass card
 * as the banner it stands in for, so nothing around it moves). The ad keeps its native
 * 320x50 size, centred — it is never stretched or cropped.
 */
@Composable
fun UnityAdBannerSlot(isDark: Boolean, height: Dp, shape: Shape) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height).glass(isDark, shape, elevation = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx -> FrameLayout(ctx) },
            update = { container ->
                val ad = UnityAdsManager.bannerView()
                if (ad != null && ad.parent !== container) {
                    (ad.parent as? ViewGroup)?.removeView(ad)
                    container.removeAllViews()
                    container.addView(
                        ad,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER
                        )
                    )
                }
            },
            onRelease = { it.removeAllViews() },
            modifier = Modifier.wrapContentSize()
        )
        Text(
            "Ad",
            color = Color.Gray.copy(alpha = 0.8f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 8.dp)
        )
    }
}
