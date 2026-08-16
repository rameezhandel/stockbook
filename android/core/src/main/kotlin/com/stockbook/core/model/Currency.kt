package com.stockbook.core.model

/**
 * The one currency the shop bills in.
 *
 * One at a time, always — the app never converts, never holds a rate, and never
 * shows two currencies on one screen. The symbol is a prefix on a number and
 * nothing more.
 *
 * Identified by its ISO 4217 code, which is what gets persisted and exported.
 * The symbol and the minor-unit count are derived from the table below, so a
 * wrong symbol is fixed in one place rather than migrated out of everyone's
 * saved settings.
 */
data class Currency private constructor(
    /** ISO 4217. The stored value. */
    val code: String,
    /**
     * What prefixes every amount, **including its own spacing**. Alphabetic
     * codes read as `SAR 194`; a glyph reads as `₹194`, which is how each is
     * written in the places they are used.
     */
    val symbol: String,
    /**
     * Digits after the point when an amount is not whole. Two almost everywhere;
     * the Gulf dinars and the rial are three, and rendering 0.125 as `0.13` in a
     * shop that bills in fils is a real error, not a rounding preference.
     */
    val fractionDigits: Int
) {
    companion object {
        private fun of(code: String, symbol: String, fractionDigits: Int = 2) =
            Currency(code, symbol, fractionDigits)

        val SAR = of("SAR", "SAR ")
        val AED = of("AED", "AED ")
        val QAR = of("QAR", "QAR ")
        val KWD = of("KWD", "KWD ", fractionDigits = 3)
        val BHD = of("BHD", "BHD ", fractionDigits = 3)
        val OMR = of("OMR", "OMR ", fractionDigits = 3)
        val INR = of("INR", "₹")
        val PKR = of("PKR", "PKR ")
        val BDT = of("BDT", "৳")
        val LKR = of("LKR", "LKR ")
        val NPR = of("NPR", "NPR ")
        val USD = of("USD", "$")
        val EUR = of("EUR", "€")
        val GBP = of("GBP", "£")

        /**
         * What the picker offers, in the order it offers them: the Gulf first,
         * because that is where this shop is, then the countries its customers
         * and suppliers come from, then the three everyone recognises.
         */
        val supported: List<Currency> = listOf(
            SAR, AED, QAR, KWD, BHD, OMR,
            INR, PKR, BDT, LKR, NPR,
            USD, EUR, GBP
        )

        val default: Currency = SAR

        /**
         * The stored code back into a currency. An unknown code — a file from a
         * build with a longer table — falls back rather than failing: showing
         * the wrong symbol beats refusing to open the shop.
         */
        fun named(code: String): Currency =
            supported.firstOrNull { it.code == code } ?: default

        /**
         * Recovers a currency from a bare symbol, for settings and backups
         * written before the code was stored. `"SAR "` and `"SAR"` both resolve.
         */
        fun matching(symbol: String): Currency? {
            val needle = symbol.trim()
            if (needle.isEmpty()) return null
            return supported.firstOrNull { it.symbol.trim() == needle }
        }
    }
}
