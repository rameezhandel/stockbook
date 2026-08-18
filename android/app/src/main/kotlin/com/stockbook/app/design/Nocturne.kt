package com.stockbook.app.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.stockbook.core.model.AppTheme

/**
 * Every colour token, and the only file allowed to contain a hex literal.
 *
 * The same values the iOS build uses. Two apps drawn from one palette stay one
 * product; two palettes drift within a release.
 *
 * **Two themes, one set of names.** Every screen reads `Nocturne.surface` and
 * gets the surface of the theme in force; nothing outside this file knows there
 * is more than one. That is what stops a second theme from becoming a second
 * design — a screen cannot forget to handle light, because it never asks.
 */
object Nocturne {

    /**
     * The palette in force.
     *
     * Held in a [mutableStateOf] rather than a plain field so that reading any
     * token below *inside* composition subscribes to it: switching the theme then
     * recomposes exactly what draws, with no tree-wide key to turn and no
     * environment value threaded through 236 call sites. (iOS cannot do this and
     * rebuilds its tree instead — see `RootView`.)
     */
    private var palette by mutableStateOf(Palette.dark)

    fun use(theme: AppTheme) {
        palette = if (theme == AppTheme.LIGHT) Palette.light else Palette.dark
    }

    /** The ground everything sits on. */
    val bg: Color get() = palette.bg

    /** Cards, rows, the tab bar, the sheet. */
    val surface: Color get() = palette.surface

    val text: Color get() = palette.text

    val accent: Color get() = palette.accent
    val accent300: Color get() = palette.accent300
    val accent400: Color get() = palette.accent400
    val accent700: Color get() = palette.accent700
    val accent900: Color get() = palette.accent900

    val neutral400: Color get() = palette.neutral400
    val neutral500: Color get() = palette.neutral500
    val neutral600: Color get() = palette.neutral600

    /** The hairline that stands in for elevation. */
    val neutral800: Color get() = palette.neutral800

    val divider: Color get() = palette.divider

    /**
     * The sheet scrim. Dark in both themes — a scrim's job is to put the screen
     * behind it out of reach, and a pale one over a pale screen does not.
     */
    val scrim: Color get() = palette.scrim

    val primaryPressed: Color get() = palette.accent.copy(alpha = palette.pressedStrength)
    val secondaryPressed: Color get() = palette.text.copy(alpha = palette.pressedStrength * 0.64f)
    val ghostPressed: Color get() = palette.accent.copy(alpha = palette.pressedStrength * 0.82f)

    /** The one gradient in the app: the emphasised stat card on Home. */
    val statCardGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(palette.accent900, palette.surface),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )

    /** Whether the status-bar icons have to be dark. See `MainActivity`. */
    val isLight: Boolean get() = palette.isLight

    const val DISABLED_OPACITY = 0.45f
}

/**
 * One theme's worth of colour.
 *
 * Both are written out in full rather than derived from one another. A light
 * theme is not a dark theme with the lightness flipped: the accent has to darken
 * to stay legible on white while the neutrals do not simply invert, and every
 * value here was picked against the contrast it has to hold.
 */
data class Palette(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val accent: Color,
    val accent300: Color,
    val accent400: Color,
    val accent700: Color,
    val accent900: Color,
    val neutral400: Color,
    val neutral500: Color,
    val neutral600: Color,
    val neutral800: Color,
    val divider: Color,
    val scrim: Color,
    /**
     * How hard a pressed control is washed with its own colour. Lower in light,
     * where the accent is darker and 22% of it reads as a filled button — the one
     * thing this design says it never has.
     */
    val pressedStrength: Float,
    val isLight: Boolean
) {
    companion object {
        /** The design as drawn: `styles.css`, unchanged. */
        val dark = Palette(
            bg = Color(0xFF161826),
            surface = Color(0xFF232532),
            text = Color(0xFFE9E9ED),
            accent = Color(0xFF9184D9),
            accent300 = Color(0xFFD2CEFD),
            accent400 = Color(0xFFB5ABFC),
            accent700 = Color(0xFF5D5294),
            accent900 = Color(0xFF2B2741),
            neutral400 = Color(0xFFB2B6CA),
            neutral500 = Color(0xFF9397AB),
            neutral600 = Color(0xFF75798C),
            neutral800 = Color(0xFF3F424D),
            divider = Color(0xFFE9E9ED).copy(alpha = 0.16f),
            scrim = Color(0xFF10111C).copy(alpha = 0.74f),
            pressedStrength = 0.22f,
            isLight = false
        )

        /**
         * The same design in daylight, token for token.
         *
         * The ground and the surface swap *roles* rather than values: on dark the
         * card is lighter than the page, here the page recedes and the card stays
         * white, so a card still reads as the nearer thing. The accent ramp
         * darkens — `accent300`, the loudest shade on dark, becomes the deepest
         * here, because what the numbers mean is "more attention", not "more
         * light".
         */
        val light = Palette(
            bg = Color(0xFFF3F3F8),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF1B1D2B),
            accent = Color(0xFF5C4FC4),
            accent300 = Color(0xFF453BA0),
            accent400 = Color(0xFF6558CC),
            accent700 = Color(0xFF8B80DC),
            accent900 = Color(0xFFE9E6FA),
            neutral400 = Color(0xFF4C5163),
            neutral500 = Color(0xFF5F6478),
            neutral600 = Color(0xFF8A8FA3),
            neutral800 = Color(0xFFD5D7E2),
            divider = Color(0xFF1B1D2B).copy(alpha = 0.12f),
            scrim = Color(0xFF10111C).copy(alpha = 0.45f),
            pressedStrength = 0.14f,
            isLight = true
        )
    }
}
