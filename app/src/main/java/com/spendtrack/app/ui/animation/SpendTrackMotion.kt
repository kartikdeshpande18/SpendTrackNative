package com.spendtrack.app.ui.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

object SpendTrackMotion {
    const val DurationInstant = 0
    const val DurationPress = 120
    const val DurationFast = 140
    const val DurationNormal = 220
    const val DurationChart = 240

    // Emil Kowalski custom curves: strong, intentional easing without weak platform defaults
    val EaseOut = CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)
    val EaseInOut = CubicBezierEasing(0.77f, 0.0f, 0.175f, 1.0f)
    val SheetCurve = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)

    // DecelEasing points to EaseOut so all existing animation call sites automatically gain punchy easing
    val DecelEasing = EaseOut
    val StandardEasing = FastOutSlowInEasing

    private val routeOrder = mapOf(
        "home" to 0,
        "spending" to 1,
        "loans_owed" to 2,
        "accounts" to 3,
        "settings" to 4,
    )

    fun routeTransition(from: String, to: String): ContentTransform {
        val fromIndex = routeOrder[from] ?: 0
        val toIndex = routeOrder[to] ?: 0
        val forward = toIndex >= fromIndex

        val enter = fadeIn(animationSpec = tween(DurationNormal, easing = EaseOut)) +
            scaleIn(initialScale = 0.98f, animationSpec = tween(DurationNormal, easing = EaseOut)) +
            slideInHorizontally(animationSpec = tween(DurationNormal, easing = EaseOut)) {
                if (forward) it / 6 else -it / 6
            }

        val exit = fadeOut(animationSpec = tween(DurationFast, easing = EaseOut)) +
            scaleOut(targetScale = 0.98f, animationSpec = tween(DurationFast, easing = EaseOut)) +
            slideOutHorizontally(animationSpec = tween(DurationFast, easing = EaseOut)) {
                if (forward) -it / 6 else it / 6
            }

        return enter togetherWith exit
    }

    /**
     * Overlay transition applying Emil's principle:
     * Never animate from scale(0); start from scale(0.95f) with opacity.
     * Exits are asymmetric and 35% faster than entrances.
     */
    fun overlayTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(DurationNormal, easing = EaseOut)) +
            scaleIn(initialScale = 0.95f, animationSpec = tween(DurationNormal, easing = EaseOut)) +
            slideInVertically(animationSpec = tween(DurationNormal, easing = SheetCurve)) { it / 10 }

        val exit = fadeOut(animationSpec = tween(DurationFast, easing = EaseOut)) +
            scaleOut(targetScale = 0.97f, animationSpec = tween(DurationFast, easing = EaseOut)) +
            slideOutVertically(animationSpec = tween(DurationFast, easing = EaseOut)) { it / 12 }

        return enter togetherWith exit
    }

    fun numberTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(DurationFast, easing = EaseOut)) +
            slideInVertically(animationSpec = tween(DurationFast, easing = EaseOut)) { it / 4 }

        val exit = fadeOut(animationSpec = tween(DurationFast, easing = EaseOut)) +
            slideOutVertically(animationSpec = tween(DurationFast, easing = EaseOut)) { -it / 4 }

        return enter togetherWith exit
    }
}

/**
 * Tactile press scale modifier (Emil Kowalski principle: buttons must feel responsive).
 * Scales to [targetScale] (default 0.97f) on press with strong ease-out, returning with a snappy physical spring.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.97f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = if (isPressed) {
            tween(durationMillis = SpendTrackMotion.DurationPress, easing = SpendTrackMotion.EaseOut)
        } else {
            spring(dampingRatio = 0.7f, stiffness = 600f)
        },
        label = "PressScale"
    )
    return this.scale(scale)
}
