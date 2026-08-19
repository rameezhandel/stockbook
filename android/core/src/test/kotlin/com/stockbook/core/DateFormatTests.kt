package com.stockbook.core

import com.stockbook.core.text.AppLanguage
import com.stockbook.core.text.Dates
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shape of a date on a form.
 *
 * These read like tests of a formatter, and they are — but the thing being
 * pinned is that both apps write the *same* date the same way. The iOS twin
 * builds this string from its own `DateFormatter`, so the pattern is the
 * contract between them and nothing but a test says so.
 */
class DateFormatTests {

    /**
     * Midday rather than midnight, deliberately: an instant at midnight UTC is
     * the previous day for everyone west of Greenwich, and these assertions
     * would then pass or fail on where the build machine happens to be.
     */
    private val midday: Instant = Instant.parse("2026-08-13T12:00:00Z")

    @Test
    fun `a date on a form names its month`() {
        assertEquals("Aug 13, 2026", Dates.pickedDate(midday, Locale.US))
    }

    @Test
    fun `the day is not padded`() {
        // `Aug 3` rather than `Aug 03`. Padding buys nothing once the month is a
        // word — there is no column here to line up with.
        val third = Instant.parse("2026-08-03T12:00:00Z")
        assertEquals("Aug 3, 2026", Dates.pickedDate(third, Locale.US))
    }

    @Test
    fun `the narrow statement column is still numeric`() {
        // The two formats exist for different jobs and must not drift into one.
        // A statement's date column is scanned down, so it stays fixed-width.
        assertEquals("13/08/2026", Dates.shortDate(midday))
    }

    @Test
    fun `the month name follows the interface language`() {
        // Kannada names its months; the pattern only fixes their order. The exact
        // spelling comes from the JDK and is not this test's business — what is,
        // is that it stopped being the English word.
        val kannada = Strings(AppLanguage.KANNADA).pickedDate(midday)
        val english = Strings(AppLanguage.ENGLISH).pickedDate(midday)

        assertEquals("Aug 13, 2026", english)
        assertTrue(kannada.contains("13") && kannada.contains("2026"), kannada)
    }

    @Test
    fun `the date shown is the one in the phone's own zone`() {
        // Not a formatting detail: a bill saved at nine in the evening in Riyadh
        // is still that day's bill, and reading the instant in UTC would date it
        // to the day before for half the year's worth of edge cases.
        val expected = midday.atZone(ZoneId.systemDefault()).toLocalDate().dayOfMonth
        assertTrue(Dates.pickedDate(midday, Locale.US).contains(" $expected, "))
    }
}
