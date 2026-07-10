package com.skysense.app.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Extension functions for rich haptic feedback that safely degrades on older API levels.
 */

fun View.performHapticConfirm() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

fun View.performHapticReject() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.REJECT)
    } else {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

fun View.performHapticSegmentTick() {
    if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
        performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
    } else {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

fun View.performHapticToggleOn() {
    if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
        performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
    } else {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

fun View.performHapticToggleOff() {
    if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
        performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
    } else {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

fun View.performHapticGestureEnd() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    } else {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}
