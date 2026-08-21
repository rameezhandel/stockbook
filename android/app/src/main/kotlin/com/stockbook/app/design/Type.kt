package com.stockbook.app.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale as named roles, not point sizes.
 *
 * No feature file names a size. Changing what a screen title looks like is a
 * change here and nowhere else — the same rule the iOS build follows, so the
 * two stay one product.
 *
 * Inter is not bundled: the system face at the same metrics is close enough that
 * shipping a font file to every device would cost more than it returns.
 */
object NocturneType {

    fun inter(size: Double, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontSize = size.sp,
        fontWeight = weight,
        // The design tightens large text and leaves small text alone.
        letterSpacing = if (size >= 20) (size * -0.02).sp else 0.sp
    )

    val screenTitle = inter(26.0, FontWeight.Medium)
    val sheetTitle = inter(19.0, FontWeight.Medium)
    val setupTitle = inter(23.0, FontWeight.Medium)
    val rowPrimary = inter(14.5)
    val rowValue = inter(14.0)
    val body = inter(13.0)
    val meta = inter(11.5)
    val fieldLabel = inter(11.5)
    val tabLabel = inter(10.5)

    fun bigNumber(size: Double) = inter(size, FontWeight.Medium)

    /**
     * The size a figure has to drop to so it fits the half-width box it sits in.
     *
     * Chosen from the string's length rather than measured, and deliberately so.
     * A shrink-to-fit that measures is a recomposition loop on Android and, on
     * iOS, `minimumScaleFactor` — which quietly stops working next to
     * `contentTransition(.numericText())`, the rolling-digit animation these
     * cards use. Both platforms then truncate instead of shrinking, which is how
     * "SAR 500,000" came out as "SAR 500,0…" on a card with room for it.
     *
     * Length is a good enough proxy because there is only ever one kind of string
     * here: a currency symbol and a grouped number. The thresholds are twinned in
     * `NocturneType.fittedNumber` on iOS and must move together.
     */
    fun fittedNumber(text: String, max: Double = 26.0): TextStyle = bigNumber(
        when {
            text.length <= 9 -> max
            text.length <= 12 -> max - 4
            text.length <= 15 -> max - 7
            else -> max - 9
        }
    )

    /** Section labels — "RECENT BILLS". Uppercased by the caller, not the style. */
    val kicker = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (11 * 0.09).sp
    )
}
