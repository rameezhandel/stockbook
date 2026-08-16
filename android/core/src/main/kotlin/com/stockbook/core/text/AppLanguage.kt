package com.stockbook.core.text

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * The languages the app is written in.
 *
 * Two, chosen deliberately: this is a shop counter, not a translation project.
 * Adding a third means adding one more case and one more column to [Strings] —
 * the compiler then walks you through every line that needs a word.
 *
 * The serial names are the ISO codes and are **persisted and exported**, so they
 * must not change. They also match what the iOS build writes, so a backup
 * carried between the two keeps its language field readable.
 */
@Serializable
enum class AppLanguage(val code: String) {
    @SerialName("en") ENGLISH("en"),
    @SerialName("kn") KANNADA("kn");

    /**
     * The language's name **in that language** — the only label that is any use
     * to somebody who cannot read the other one.
     */
    val endonym: String
        get() = when (this) {
            ENGLISH -> "English"
            KANNADA -> "ಕನ್ನಡ"
        }

    /**
     * Used for dates and currency *names* only. Money keeps its own locale, so a
     * shop billing in SAR does not suddenly group in lakhs.
     */
    val locale: Locale
        get() = when (this) {
            ENGLISH -> Locale.US
            KANNADA -> Locale.forLanguageTag("kn-IN")
        }

    companion object {
        fun ofCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}
