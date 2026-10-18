package com.example.view

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Predictive back (Android 14+ system back gesture) for Compose screens.
 *
 * The SYSTEM drives everything: the edge-swipe (left OR right edge) produces progress events
 * through AndroidX [PredictiveBackHandler]; this file only draws the in-app part of the
 * animation (the screen shrinks and rounds off while you drag, springs back if you let go early,
 * and finishes if you commit). On devices without predictive back (Android < 14, or 3-button
 * navigation) the handler receives no progress, so there is no animation and back simply
 * navigates - it degrades to a normal back press.
 *
 * Needs `android:enableOnBackInvokedCallback="true"` in the manifest (set on <application>).
 * When no handler is enabled (home tab, nothing open) the SYSTEM plays its own back-to-home animation.
 */
class PredictiveBackState internal constructor(internal val scope: CoroutineScope) {
    /** 0 = at rest, 1 = gesture fully committed. Read only from draw/graphics-layer blocks. */
    internal val progress = Animatable(0f)

    /** Which screen edge the current gesture started from (for the sideways shift direction). */
    @Volatile
    internal var fromRight: Boolean = false
}

@Composable
fun rememberPredictiveBackState(): PredictiveBackState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PredictiveBackState(scope) }
}

/**
 * Registers a predictive back handler. [enabled] must be false whenever this screen should NOT
 * consume back (so the system can show its own animation / hand back to the next handler).
 */
@Composable
fun PredictiveBackNav(
    enabled: Boolean,
    state: PredictiveBackState,
    onBack: () -> Unit
) {
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { events ->
        var sawProgress = false
        try {
            events.collect { e ->
                sawProgress = true
                state.fromRight = e.swipeEdge == BackEventCompat.EDGE_RIGHT
                state.progress.snapTo(e.progress)
            }
            // Gesture committed (or a plain back press with no progress events).
            if (sawProgress) {
                state.progress.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
            }
            currentOnBack()
            state.progress.snapTo(0f)
        } catch (c: CancellationException) {
            // Gesture cancelled: this coroutine is already cancelled, so glide back on the
            // composition scope instead, then let the cancellation propagate as required.
            state.scope.launch {
                state.progress.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            }
            throw c
        }
    }
}

/** Applies the shrink / shift / round-corner effect. Reads progress in the graphics layer only (no recomposition). */
fun Modifier.predictiveBackTransform(state: PredictiveBackState): Modifier = this.graphicsLayer {
    val p = state.progress.value
    if (p > 0f) {
        val s = 1f - 0.10f * p
        scaleX = s
        scaleY = s
        translationX = (if (state.fromRight) -1f else 1f) * 20.dp.toPx() * p
        alpha = 1f - 0.15f * p
        shape = RoundedCornerShape((24f * p).dp)
        clip = true
    }
}
