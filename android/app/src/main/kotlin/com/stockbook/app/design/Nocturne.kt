package com.stockbook.app.design

import androidx.compose.ui.graphics.Color

/**
 * Every colour token, and the only file allowed to contain a hex literal.
 *
 * The same values the iOS build uses. Two apps drawn from one palette stay one
 * product; two palettes drift within a release.
 */
object Nocturne {
    /** The ground everything sits on. */
    val bg = Color(0xFF161826)

    /** Cards, rows, the tab bar, the sheet. */
    val surface = Color(0xFF232532)

    val text = Color(0xFFE9E9ED)

    val accent = Color(0xFF9184D9)
    val accent300 = Color(0xFFD2CEFD)
    val accent400 = Color(0xFFB5ABFC)
    val accent700 = Color(0xFF5D5294)
    val accent900 = Color(0xFF2B2741)

    val neutral400 = Color(0xFFB2B6CA)
    val neutral500 = Color(0xFF9397AB)
    val neutral600 = Color(0xFF75798C)

    /** The hairline that stands in for elevation on this dark ground. */
    val neutral800 = Color(0xFF3F424D)

    val divider = Color(0xFFE9E9ED).copy(alpha = 0.16f)
    val scrim = Color(0xFF10111C).copy(alpha = 0.74f)

    val primaryPressed = accent.copy(alpha = 0.22f)
    val secondaryPressed = text.copy(alpha = 0.14f)
    val ghostPressed = accent.copy(alpha = 0.18f)

    const val DISABLED_OPACITY = 0.45f
}
