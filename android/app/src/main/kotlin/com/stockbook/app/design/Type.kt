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

    /** Section labels — "RECENT BILLS". Uppercased by the caller, not the style. */
    val kicker = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (11 * 0.09).sp
    )
}
