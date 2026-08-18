package com.stockbook.core.model

/**
 * The rules for the number written on a bill.
 *
 * Two of them, and both exist because the number is typed by a person:
 *
 * - **Matching.** "A-1024", "a-1024 " and "A-1024" are one paper, so comparison
 *   is on a trimmed, lowercased key — the same rule customer names group under,
 *   for the same reason.
 * - **Suggesting.** A bill book runs 1024, 1025, 1026, and the app should offer
 *   the next one rather than make somebody type it. The run of digits at the
 *   *end* is what moves; anything in front of it is the book's prefix and stays,
 *   as does the width, so "A-0099" becomes "A-0100" and not "A-100".
 *
 * Neither rule touches identity. A bill is identified by `Bill.number`, the
 * app's own counter, which nobody types and nothing here can change.
 */
object InvoiceNo {

    /** What two numbers are compared as. Blank in means blank out. */
    fun key(raw: String?): String = raw?.trim()?.lowercase().orEmpty()

    /**
     * The number after this one, or null when there is no run of digits to move.
     *
     * "1024" → "1025", "A-1024" → "A-1025", "0099" → "0100", "INV" → null.
     */
    fun next(previous: String?): String? {
        val text = previous?.trim().orEmpty()
        if (text.isEmpty()) return null

        val end = text.length
        var start = end
        while (start > 0 && text[start - 1].isDigit()) start--
        if (start == end) return null

        val digits = text.substring(start, end)
        // A bill book that has reached the width of a Long is not a real one, but
        // the arithmetic still must not throw.
        val incremented = (digits.toLongOrNull() ?: return null) + 1
        val grown = incremented.toString().padStart(digits.length, '0')
        return text.substring(0, start) + grown
    }
}
