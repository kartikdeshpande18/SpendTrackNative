package com.spendtrack.app.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

class SpendTrackHaptics(
    private val view: View,
    private val composeHaptics: HapticFeedback,
) {
    /** Click feedback for primary action buttons, FAB tap, and icon actions. */
    fun click() {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }.onFailure {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** Subtle tactile feedback when switching tabs, selecting chips, or toggling checkboxes. */
    fun selection() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }.onFailure {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** Confirmation feedback when an action succeeds (saving forms, paying bill, settling debts). */
    fun confirm() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }.onFailure {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** Subtle tactile feedback when deleting an item or rejecting an action. */
    fun reject() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }.onFailure {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
fun rememberSpendTrackHaptics(): SpendTrackHaptics {
    val view = LocalView.current
    val composeHaptics = LocalHapticFeedback.current
    return remember(view, composeHaptics) {
        SpendTrackHaptics(view, composeHaptics)
    }
}
