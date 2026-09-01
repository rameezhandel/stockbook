package com.stockbook.app.feature.customers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.core.text.StatementDocument
import java.io.File

/**
 * Draws a [StatementDocument] onto A4 pages.
 *
 * **Black on white**, unlike every other surface in this app. The screen is a
 * dark instrument the owner reads at a counter; this is a document they hand to
 * a customer, forward on WhatsApp, or print — and a dark page does none of those
 * well. Nothing here reads `Nocturne`.
 *
 * The layout decides nothing about wording: every string it draws came from
 * [StatementDocument], which is shared with the iOS build and tested. This file
 * is only geometry.
 */
object StatementPdf {

    // A4 at 72dpi, which is the unit `PdfDocument` works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f

    private const val TITLE_SIZE = 15f
    private const val BODY_SIZE = 9.5f
    private const val ROW_SIZE = 9f
    private const val LINE = 13f

    /**
     * Where each column sits, as fractions of the writable width.
     *
     * The details column is left-aligned and wide, because it now carries what a
     * row is *and* when it happened — two columns' worth in one. The three money
     * columns are right-aligned against the edges below, which is how a column of
     * figures is read: by the units lining up.
     */
    private const val COL_DATE = 0f
    private const val COL_REFERENCE = 0.16f
    private const val EDGE_CHARGE = 0.62f
    private const val EDGE_SETTLED = 0.81f
    private const val EDGE_BALANCE = 1.0f

    private fun paint(size: Float, bold: Boolean = false, grey: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = if (grey) 0xFF6B6B76.toInt() else 0xFF14141C.toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /**
     * The hairline, darker and heavier than it looks like it needs to be.
     *
     * These pages get photocopied, and a 16%-grey 0.8pt rule is the first thing
     * a copier loses. At 22% and 0.9pt it survives a generation or two, which is
     * what a statement handed across a counter has to do.
     */
    private val rule = Paint().apply {
        isAntiAlias = true
        color = 0xFFC4C4D0.toInt()
        strokeWidth = 0.9f
    }

    /** The heavy rule the monochrome treatment uses in place of the band. */
    private val heavyRule = Paint().apply {
        isAntiAlias = true
        color = 0xFF14141C.toInt()
        strokeWidth = 1.6f
    }

    private val fillInk = Paint().apply {
        isAntiAlias = true
        color = 0xFF14141C.toInt()
    }

    /**
     * The app's own violet, carried onto the paper.
     *
     * Somebody who has seen the phone recognises the page. It also costs colour:
     * on a mono printer the band becomes a heavy grey slab and the tinted card
     * goes pale but survives, which is the trade this treatment makes.
     */
    private val accent = Paint().apply {
        isAntiAlias = true
        color = 0xFF5C4FC4.toInt()
    }

    /** The same violet at a tenth, for the heading row and the totals card. */
    private val accentSoft = Paint().apply {
        isAntiAlias = true
        color = 0xFFEDEAFB.toInt()
    }

    private fun accentText(size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = 0xFF5C4FC4.toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun reversed(size: Float) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    /**
     * Renders the document and returns the file it was written to.
     *
     * Written into the app's own cache directory, which is the one place a file
     * can be put without asking the phone for anything — the app holds no storage
     * permission and is never going to. Sharing hands out a `content://` URI to
     * it rather than a path.
     */
    fun write(document: StatementDocument, into: Context, fileName: String): File =
        write(listOf(document), into, fileName)

    /**
     * Several statements into one file, **each starting on a fresh page**.
     *
     * This is the ledger book: every customer's own sheet, one after another, so
     * a page can be pulled out and filed on its own. Written as a loop over the
     * single-statement drawing rather than as a second layout — a book whose
     * pages did not match the statement the customer was handed would be two
     * documents claiming to be one, and the first correction to either would
     * separate them for good.
     */
    fun write(
        documents: List<StatementDocument>,
        into: Context,
        fileName: String,
        /**
         * Whether to spend colour on it.
         *
         * A statement is one sheet handed to one customer, and the band is worth
         * its toner there. The ledger book is a hundred of them printed at once
         * and filed, so it takes the monochrome treatment: the same page, drawn
         * with a rule where the band would be and the balance reversed out of
         * black rather than sitting in a tint. Same routine, same geometry — a
         * sheet pulled from the book is still the statement, just cheaper.
         */
        colour: Boolean = true
    ): File {
        val pdf = PdfDocument()
        var pageNumber = 0
        for (document in documents) {
            pageNumber = draw(document, pdf, pageNumber, colour)
        }
        val file = File(into.cacheDir, fileName)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /** Draws one statement from a fresh page, and returns the last page it used. */
    private fun draw(document: StatementDocument, pdf: PdfDocument, from: Int, colour: Boolean): Int {
        var pageNumber = from + 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        val right = PAGE_WIDTH - MARGIN
        val width = right - MARGIN

        // --- The band
        //
        // Full bleed to the paper's edge, not inset to the margin: an inset
        // colour block reads as a box somebody drew, where a band that runs off
        // both sides reads as the head of the page.
        //
        // Sans throughout, deliberately. A serif here would set the shop's own
        // name — which the owner types, and which may be in Arabic or Kannada —
        // in a face whose coverage of those scripts is patchy, and the failure
        // is tofu boxes in the largest text on the page.

        if (colour) {
            val bandHeight = 74f
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), bandHeight, accent)
            canvas.drawText(document.shopName, MARGIN, 30f, reversed(TITLE_SIZE))
            for ((index, line) in document.shopAddressLines.withIndex()) {
                canvas.drawText(line, MARGIN, 44f + index * 10f, reversed(7.5f).apply { alpha = 200 })
            }
            canvas.drawTextRight(document.docType.uppercase(), right, 26f, reversed(8f).apply { alpha = 210 })
            canvas.drawTextRight(document.periodValue, right, 40f, reversed(11f))
            y = bandHeight + 24
        } else {
            canvas.drawText(document.shopName, MARGIN, y + TITLE_SIZE, paint(TITLE_SIZE, bold = true))
            canvas.drawTextRight(document.docType, right, y + TITLE_SIZE, paint(13f))
            y += TITLE_SIZE + 6
            canvas.drawText(
                document.shopAddressLines.joinToString(", "),
                MARGIN,
                y + 8,
                paint(7.5f, grey = true)
            )
            y += 14
            canvas.drawLine(MARGIN, y, right, y, heavyRule)
            y += 20
        }

        // --- The two boxed facts: whose account, and over what

        val factsTop = y
        val factsHeight = 46f
        val middle = MARGIN + width / 2
        canvas.drawRect(MARGIN, factsTop, right, factsTop + factsHeight, rule.stroke())
        canvas.drawLine(middle, factsTop, middle, factsTop + factsHeight, rule)

        val factLabel = if (colour) accentText(7.5f, bold = true) else paint(7.5f, grey = true)
        canvas.drawText(document.accountLabel.uppercase(), MARGIN + 10, factsTop + 14, factLabel)
        canvas.drawText(document.partyName, MARGIN + 10, factsTop + 28, paint(BODY_SIZE, bold = true))
        canvas.drawText(document.partyLines.joinToString(" · "), MARGIN + 10, factsTop + 39, paint(8f, grey = true))

        canvas.drawText(document.periodLabel.uppercase(), middle + 10, factsTop + 14, factLabel)
        canvas.drawText(document.periodValue, middle + 10, factsTop + 28, paint(BODY_SIZE, bold = true))
        canvas.drawText(document.summaryTitle, middle + 10, factsTop + 39, paint(8f, grey = true))

        y = factsTop + factsHeight + 26

        // --- The activity table

        canvas.drawText(document.activityTitle.uppercase(), MARGIN, y, paint(8f, grey = true))
        y += 10

        fun headings() {
            // Set on a tinted row rather than over a rule: the band at the top
            // has already said this page uses colour, and a second heavy black
            // rule would be a different page's idea.
            if (colour) canvas.drawRect(MARGIN, y - 2, right, y + 13, accentSoft)
            val head = paint(7.5f, grey = true)
            canvas.drawText(document.columnHeadings[0].uppercase(), MARGIN + width * COL_DATE, y + 10, head)
            canvas.drawText(document.columnHeadings[1].uppercase(), MARGIN + width * COL_REFERENCE, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[2].uppercase(), MARGIN + width * EDGE_CHARGE, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[3].uppercase(), MARGIN + width * EDGE_SETTLED, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[4].uppercase(), MARGIN + width * EDGE_BALANCE, y + 10, head)
            y += 17
            if (!colour) canvas.drawLine(MARGIN, y - 4, right, y - 4, heavyRule)
        }
        headings()

        for (row in document.activityRows) {
            // A page break before a row rather than through one, and the headings
            // repeat: a second page whose columns are unlabelled is a page nobody
            // can read on its own. The room kept back is for the totals block.
            if (y > PAGE_HEIGHT - MARGIN - 130) {
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
                headings()
            }

            // Exactly one of the two money columns carries anything, so the empty
            // one draws nothing at all rather than a dash or a zero: an empty cell
            // is unambiguous, and a zero in the Received column is a payment
            // somebody might go looking for.
            val body = paint(ROW_SIZE)
            canvas.drawText(row.date, MARGIN + width * COL_DATE, y + 14, paint(ROW_SIZE, grey = true))
            canvas.drawText(row.reference, MARGIN + width * COL_REFERENCE, y + 14, body)
            canvas.drawTextRight(row.charge, MARGIN + width * EDGE_CHARGE, y + 14, body)
            canvas.drawTextRight(row.settled, MARGIN + width * EDGE_SETTLED, y + 14, body)
            canvas.drawTextRight(row.balance, MARGIN + width * EDGE_BALANCE, y + 14, body)
            y += 19
            canvas.drawLine(MARGIN, y, right, y, rule)
        }

        // --- The totals, set against the right edge under the money columns

        y += 14
        val totalsLeft = MARGIN + width * 0.52f
        val lineHeight = 18f
        var totalY = y
        canvas.drawRect(totalsLeft, totalY, right, totalY + document.summaryRows.size * lineHeight, rule.stroke())
        for (row in document.summaryRows) {
            canvas.drawText(row.label, totalsLeft + 9, totalY + 12, paint(BODY_SIZE, grey = true))
            canvas.drawTextRight(row.value.bracketed(row.deduction), right - 9, totalY + 12, paint(BODY_SIZE))
            totalY += lineHeight
            if (row !== document.summaryRows.last()) {
                canvas.drawLine(totalsLeft, totalY, right, totalY, rule)
            }
        }

        // The one figure the reader came for, in the accent and on a tint, with
        // a solid bar down its left edge so it still reads as the end of the
        // column when the page is photocopied and the tint goes.
        val dueHeight = 28f
        if (colour) {
            canvas.drawRect(totalsLeft, totalY, right, totalY + dueHeight, accentSoft)
            canvas.drawRect(totalsLeft, totalY, totalsLeft + 3.5f, totalY + dueHeight, accent)
            canvas.drawText(document.closingLabel.uppercase(), totalsLeft + 12, totalY + 18, accentText(7.5f, bold = true))
            canvas.drawTextRight(document.closingValue, right - 9, totalY + 19, accentText(13f, bold = true))
        } else {
            canvas.drawRect(totalsLeft, totalY, right, totalY + dueHeight, fillInk)
            canvas.drawText(document.closingLabel.uppercase(), totalsLeft + 12, totalY + 18, reversed(7.5f))
            canvas.drawTextRight(document.closingValue, right - 9, totalY + 19, reversed(13f))
        }

        // --- Footer: the address, and which page this is

        val footY = PAGE_HEIGHT - MARGIN + 4
        canvas.drawLine(MARGIN, footY - 14, right, footY - 14, rule)
        canvas.drawText(document.partyName, MARGIN, footY, paint(7.5f, grey = true))
        canvas.drawTextRight("$pageNumber", right, footY, paint(7.5f, grey = true))

        pdf.finishPage(page)
        return pageNumber
    }

    /**
     * `(SAR 530.00)` — accounting brackets, the convention the shop's own
     * supplier statements use. A bare minus in front of a currency symbol reads
     * as a typo on paper.
     */
    private fun String.bracketed(deduction: Boolean): String = if (deduction) "($this)" else this

    private fun Paint.stroke(): Paint = Paint(this).apply { style = Paint.Style.STROKE }

    private fun Canvas.drawTextRight(text: String, rightEdge: Float, y: Float, paint: Paint) {
        drawText(text, rightEdge - paint.measureText(text), y, paint)
    }
}
