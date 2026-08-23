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
    private const val COL_DETAILS = 0f
    private const val EDGE_CHARGE = 0.62f
    private const val EDGE_SETTLED = 0.81f
    private const val EDGE_BALANCE = 1.0f

    private fun paint(size: Float, bold: Boolean = false, grey: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = if (grey) 0xFF6B6B76.toInt() else 0xFF14141C.toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private val rule = Paint().apply {
        isAntiAlias = true
        color = 0xFFD6D6DE.toInt()
        strokeWidth = 0.8f
    }

    /**
     * Renders the document and returns the file it was written to.
     *
     * Written into the app's own cache directory, which is the one place a file
     * can be put without asking the phone for anything — the app holds no storage
     * permission and is never going to. Sharing hands out a `content://` URI to
     * it rather than a path.
     */
    fun write(document: StatementDocument, into: Context, fileName: String): File {
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        val right = PAGE_WIDTH - MARGIN
        val width = right - MARGIN

        // --- Who it is from, and who it is for

        canvas.drawText(document.shopName, MARGIN, y + TITLE_SIZE, paint(TITLE_SIZE, bold = true))
        var leftY = y + TITLE_SIZE + LINE + 3
        for (line in document.shopAddressLines) {
            canvas.drawText(line, MARGIN, leftY, paint(BODY_SIZE))
            leftY += LINE
        }

        // The right-hand block is right-aligned against the margin, so a long
        // shop name on the left cannot push into it.
        var rightY = y + TITLE_SIZE
        canvas.drawTextRight(document.addressedToLabel, right, rightY, paint(BODY_SIZE, grey = true))
        rightY += LINE + 2
        canvas.drawTextRight(document.partyName, right, rightY, paint(BODY_SIZE, bold = true))
        rightY += LINE
        for (line in document.partyLines) {
            canvas.drawTextRight(line, right, rightY, paint(BODY_SIZE))
            rightY += LINE
        }

        y = maxOf(leftY, rightY) + 26

        // --- The summary box

        val boxTop = y
        val rows = document.summaryRows.size + 2 // title row, and the closing row
        val boxHeight = rows * 22f
        canvas.drawRect(MARGIN, boxTop, right, boxTop + boxHeight, rule.stroke())

        var rowY = boxTop
        canvas.drawText(document.summaryTitle, MARGIN + 10, rowY + 15, paint(BODY_SIZE, bold = true))
        rowY += 22
        canvas.drawLine(MARGIN, rowY, right, rowY, rule)

        for (row in document.summaryRows) {
            canvas.drawText(row.label, MARGIN + 10, rowY + 15, paint(BODY_SIZE))
            canvas.drawTextRight(row.value.bracketed(row.deduction), right - 10, rowY + 15, paint(BODY_SIZE))
            rowY += 22
            canvas.drawLine(MARGIN, rowY, right, rowY, rule)
        }

        canvas.drawText(document.closingLabel, MARGIN + 10, rowY + 15, paint(BODY_SIZE, bold = true))
        canvas.drawTextRight(document.closingValue, right - 10, rowY + 15, paint(BODY_SIZE, bold = true))

        y = boxTop + boxHeight + 30

        // --- The activity table

        canvas.drawText(document.activityTitle, MARGIN, y, paint(12f, bold = true))
        y += 16

        fun headings() {
            val bold = paint(ROW_SIZE, bold = true)
            canvas.drawText(document.columnHeadings[0], MARGIN + width * COL_DETAILS, y + 14, bold)
            canvas.drawTextRight(document.columnHeadings[1], MARGIN + width * EDGE_CHARGE, y + 14, bold)
            canvas.drawTextRight(document.columnHeadings[2], MARGIN + width * EDGE_SETTLED, y + 14, bold)
            canvas.drawTextRight(document.columnHeadings[3], MARGIN + width * EDGE_BALANCE, y + 14, bold)
            y += 20
            canvas.drawLine(MARGIN, y, right, y, rule)
        }
        headings()

        for (row in document.activityRows) {
            // A page break before a row rather than through one, and the headings
            // repeat: a second page whose columns are unlabelled is a page nobody
            // can read on its own.
            if (y > PAGE_HEIGHT - MARGIN - 60) {
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
            canvas.drawText(row.details, MARGIN + width * COL_DETAILS, y + 15, body)
            canvas.drawTextRight(row.charge, MARGIN + width * EDGE_CHARGE, y + 15, body)
            canvas.drawTextRight(row.settled, MARGIN + width * EDGE_SETTLED, y + 15, body)
            canvas.drawTextRight(row.balance, MARGIN + width * EDGE_BALANCE, y + 15, body)
            y += 21
            canvas.drawLine(MARGIN, y, right, y, rule)
        }

        // The figure the document exists to state, repeated where the eye stops.
        val closing = paint(ROW_SIZE, bold = true)
        canvas.drawText(document.closingLabel, MARGIN + width * COL_DETAILS, y + 17, closing)
        canvas.drawTextRight(document.closingValue, MARGIN + width * EDGE_BALANCE, y + 17, closing)

        pdf.finishPage(page)

        val file = File(into.cacheDir, fileName)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
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
