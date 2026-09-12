package com.spendtrack.app.ui.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

object SpendTrackMotion {
    const val DurationFast = 120
    const val DurationNormal = 200
    const val DurationChart = 380

    val DecelEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
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

        val enter = fadeIn(animationSpec = tween(DurationNormal, easing = DecelEasing)) +
            slideInHorizontally(animationSpec = tween(DurationNormal, easing = DecelEasing)) {
                if (forward) it / 6 else -it / 6
            }

        val exit = fadeOut(animationSpec = tween(DurationFast)) +
            slideOutHorizontally(animationSpec = tween(DurationFast)) {
                if (forward) -it / 6 else it / 6
            }

        return enter togetherWith exit
    }

    fun overlayTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(DurationNormal, easing = DecelEasing)) +
            slideInVertically(animationSpec = tween(DurationNormal, easing = DecelEasing)) { it / 8 }

        val exit = fadeOut(animationSpec = tween(DurationFast)) +
            slideOutVertically(animationSpec = tween(DurationFast)) { it / 8 }

        return enter togetherWith exit
    }

    fun numberTransition(): ContentTransform {
        val enter = fadeIn(animationSpec = tween(DurationFast)) +
            slideInVertically(animationSpec = tween(DurationFast)) { it / 4 }

        val exit = fadeOut(animationSpec = tween(DurationFast)) +
            slideOutVertically(animationSpec = tween(DurationFast)) { -it / 4 }

        return enter togetherWith exit
    }
}
