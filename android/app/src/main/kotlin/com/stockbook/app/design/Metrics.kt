package com.stockbook.app.design

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/** Spacing, radii and the sizes the design is specific about. */
object Metrics {
    val screenPadding = 18.dp
    val cardGap = 10.dp
    val rowGap = 7.dp

    val cardRadius = 14.dp
    val rowRadius = 12.dp
    val statRadius = 16.dp
    val controlRadius = 10.dp
    val pillRadius = 999.dp
    val sheetRadius = 18.dp

    val inputHeight = 42.dp
    val tallInputHeight = 46.dp
    val primaryButtonHeight = 46.dp
    val iconButtonSize = 36.dp

    /** The in-cart stepper and price boxes. */
    val compactControlHeight = 34.dp

    /** The smallest a tap target may be, whatever it looks like. */
    val minimumTouchTarget = 44.dp

    val hairline = 1.dp
}

/**
 * How things move.
 *
 * The rule: **motion carries information or it does not happen.** A number that
 * rolls says it changed while you were looking somewhere else; a row that fades
 * says it was added rather than always having been there. Anything that only
 * decorates is a delay between the owner and the next customer.
 */
object Motion {
    /** Money and counts that change under the owner's eyes. No bounce. */
    val numbers = tween<Float>(durationMillis = 220)

    /** Rows arriving and leaving a list. */
    val list = spring<Float>(dampingRatio = 0.86f, stiffness = 400f)

    /** Moving between screens and tabs. */
    val screen = tween<Float>(durationMillis = 180)

    const val QUICK_MILLIS = 180
    const val SHEET_MILLIS = 270

    /**
     * Crossfade between tabs. Declared after the constants it reads: a property
     * initialiser cannot reach forward to one below it.
     */
    val screenSpec = tween<Float>(durationMillis = QUICK_MILLIS)
}
