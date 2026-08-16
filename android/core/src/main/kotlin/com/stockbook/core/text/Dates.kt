package com.stockbook.core.text

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date formatting. Wording lives in [Strings]; this is the machinery under it.
 *
 * Dates are the one piece of copy the app does not write itself — the weekday
 * and month names come from the system for whichever language is in force.
 */
object Dates {

    /**
     * The Today kicker: `TUESDAY, 11 AUGUST` / `ಮಂಗಳವಾರ, 11 ಆಗಸ್ಟ್`. Uppercasing
     * is the type role's job, so this returns natural case — which matters,
     * because Kannada has no upper case to apply.
     */
    fun headerDate(at: Instant, locale: Locale): String =
        formatter("EEEE, d MMMM", locale).format(at.atZone(ZoneId.systemDefault()))

    /**
     * `09:41` — the time stamped on a bill. Fixed 24-hour in both languages: a
     * bill number and a time are read side by side and should not change shape
     * with the interface language.
     */
    fun time(at: Instant): String =
        formatter("HH:mm", Locale.US).format(at.atZone(ZoneId.systemDefault()))

    /** `28 July 2026` — the "saved" line on an import summary. */
    fun longDate(at: Instant, locale: Locale): String =
        formatter("d MMMM yyyy", locale).format(at.atZone(ZoneId.systemDefault()))

    /**
     * `2026-08-11` — used to build the backup filename, so it is deliberately
     * **not** localised: the file has to sort and parse the same everywhere, and
     * be readable by the iPhone it might be carried to.
     */
    fun fileDate(at: Instant): String =
        formatter("yyyy-MM-dd", Locale.US).format(at.atZone(ZoneId.systemDefault()))

    /**
     * `DateTimeFormatter` is immutable and thread-safe once built, unlike the
     * `DateFormatter` its iOS twin has to be careful with — but building one is
     * not free and there are only a handful of (pattern, locale) pairs.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, DateTimeFormatter>()

    private fun formatter(pattern: String, locale: Locale): DateTimeFormatter =
        cache.computeIfAbsent("${locale.toLanguageTag()}|$pattern") {
            DateTimeFormatter.ofPattern(pattern, locale)
        }
}

/** `"Ahmed Al-Amri"` → `"Ahmed"`. The dashboard greeting and the setup kicker. */
val String.firstName: String
    get() = trim().substringBefore(' ')

val String.isBlank_: Boolean get() = trim().isEmpty()
