package com.stockbook.core.money

import com.stockbook.core.model.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Currency rendering. One rule, one place.
 *
 * From the handoff: the symbol prefixes the amount, whole numbers render without
 * decimals (`SAR 194`), and anything else renders to exactly the currency's
 * minor units (`SAR 0.25`, `KWD 0.125`).
 *
 * **Grouping is `en_US` for every currency.** It is the app's own rule rather
 * than the locale's, so the same number reads the same way whatever the shop
 * bills in and whichever language it reads — a shop that switches to Kannada
 * does not start seeing its riyals grouped in lakhs.
 */
object Money {

    /** `SAR 1,240` / `SAR 0.25` / `₹12`. */
    fun text(value: Double, currency: Currency = Currency.default): String =
        currency.symbol + amount(value, currency)

    /**
     * The same figure with the sign in front of the symbol: `-SAR 150`.
     *
     * [text] puts the symbol first, so a negative comes out of it as `SAR -150`
     * — readable, and not how anybody writes it. Kept beside [text] rather than
     * folded into it because **nothing else in this book is signed**: every
     * other figure is a total, a balance or a sum, each of them clamped at zero
     * on the way in, and all of them must go on printing exactly as they do now.
     * The day's net cash is the first figure here that can honestly go either
     * way.
     */
    fun signed(value: Double, currency: Currency = Currency.default): String {
        // The sign is read off the *rounded* number, not the raw double. A net
        // of -0.004 rounds to zero and prints `SAR 0`; a minus in front of that
        // would be the page claiming a loss of nothing.
        val negative = amount(value, currency).startsWith("-")
        return (if (negative) "-" else "") + text(abs(value), currency)
    }

    /** The number alone, no symbol. */
    fun amount(value: Double, currency: Currency = Currency.default): String {
        val scale = 10.0.pow(currency.fractionDigits)
        val rounded = (value * scale).roundToLong() / scale
        // `-0.0` would otherwise print as "-0".
        val normalised = if (rounded == 0.0) 0.0 else rounded

        return if (isWhole(normalised)) {
            String.format(Locale.US, "%,d", normalised.toLong())
        } else {
            String.format(Locale.US, "%,.${currency.fractionDigits}f", normalised)
        }
    }

    /**
     * What a percentage off comes to, rounded to the currency's smallest unit.
     *
     * Rounded **here**, once, rather than left as a fraction and rounded again
     * wherever it is drawn: the figure is subtracted from the subtotal to make
     * the bill's stored total, and a total that does not equal
     * `subtotal − discount` to the last halala is a document nobody can check by
     * hand. A percentage at or below zero is not a discount and comes to nothing;
     * above a hundred it is capped, because a bill cannot go negative.
     */
    fun discount(subtotal: Double, percent: Double, currency: Currency = Currency.default): Double {
        if (percent <= 0 || subtotal <= 0) return 0.0
        val scale = 10.0.pow(currency.fractionDigits)
        val off = subtotal * (percent.coerceAtMost(100.0) / 100.0)
        return (off * scale).roundToLong() / scale
    }

    /**
     * Parses a value the owner typed. Returns null for anything that is not a
     * number, so callers can tell "empty" from "zero".
     */
    fun parse(input: String): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.replace(",", "").toDoubleOrNull()
    }

    private fun isWhole(value: Double): Boolean =
        value.isFinite() && abs(value - value.toLong()) < 1e-9
}
