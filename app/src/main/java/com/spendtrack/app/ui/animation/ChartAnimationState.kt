package com.spendtrack.app.ui.animation

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks whether a chart's entrance animation has already played in this app session.
 * Prevents replaying animations repeatedly when navigating back and forth between screens.
 */
object ChartAnimationState {
    private val playedAnimations = ConcurrentHashMap.newKeySet<String>()

    fun shouldAnimate(chartKey: String): Boolean {
        return playedAnimations.add(chartKey)
    }

    fun hasAnimated(chartKey: String): Boolean {
        return playedAnimations.contains(chartKey)
    }

    fun resetForTesting() {
        playedAnimations.clear()
    }
}
