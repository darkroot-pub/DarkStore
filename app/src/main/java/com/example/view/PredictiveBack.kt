package com.example.view

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android 13+ predictive back for in-app screens. While the user drags the back gesture the screen
 * shrinks and slides a little (revealing what is behind it); releasing completes the back action,
 * dragging back cancels it with a spring. On older Android versions this behaves like a normal BackHandler.
 *
 * Progress lives in a State that is only read inside graphicsLayer {}, so a gesture never recomposes the screen:
 * it only re-draws one layer (cheap on the GPU, no CPU recomposition).
 */
@Stable
class PredictiveBackState {
    var progress by mutableFloatStateOf(0f)
    var fromRight by mutableStateOf(false)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberPredictiveBack(enabled: Boolean = true, onBack: () -> Unit): PredictiveBackState {
    val state = remember { PredictiveBackState() }
    val currentOnBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { e ->
                state.progress = e.progress
                state.fromRight = e.swipeEdge == BackEventCompat.EDGE_RIGHT
            }
            currentOnBack()                       // gesture released past the threshold: go back
        } catch (c: CancellationException) {
            // gesture cancelled: spring the screen back (separate scope - this one is being cancelled)
            val from = state.progress
            scope.launch { animate(from, 0f) { v, _ -> state.progress = v } }
            throw c
        }
    }
    return state
}

/** Apply to the ROOT of a screen that uses [rememberPredictiveBack]. */
fun Modifier.predictiveBackTransform(state: PredictiveBackState): Modifier = graphicsLayer {
    val p = state.progress
    if (p > 0f) {
        val s = 1f - 0.10f * p
        scaleX = s
        scaleY = s
        translationX = (if (state.fromRight) -1f else 1f) * 20.dp.toPx() * p
        alpha = 1f - 0.12f * p
        shape = RoundedCornerShape((28f * p).dp)
        clip = true
    }
}
