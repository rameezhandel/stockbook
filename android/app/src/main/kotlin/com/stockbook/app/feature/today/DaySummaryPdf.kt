package com.stockbook.app.feature.today

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.core.text.DaySummaryDocument
import java.io.File

/**
 * Draws a [DaySummaryDocument] onto A4 pages.
 *
 * [SummaryPdf]'s taller cousin, and deliberately the same geometry — A4 at 72dpi,
 * the same margin, the same rules, the same page-break rule — so every page the
 * shop prints looks like it came out of the same shop. What it adds is depth: a
 * heading and a subtotal per section, and the products of an itemised bill
 * indented under the row they were sold on.
 *
 * **Black on white**, like the statement, and for a different reason. That one is
 * handed to a customer; this one is never handed to anyone — but it is printed,
 * and a dark page prints badly whoever is reading it.
 *
 * The layout decides nothing about wording: every string it draws came from
 * [DaySummaryDocument], which is shared with the iOS build and tested.
 */
object DaySummaryPdf {

    // A4 at 72dpi, which is the unit `PdfDocument` works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f

    private const val TITLE_SIZE = 15f
    private const val BODY_SIZE = 9.5f
    private const val ROW_SIZE = 10f
    private const val LINE = 13f

    /** How much of the writable width a name may take before it is cut short. */
    private const val NAME_FRACTION = 0.62f

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
    fun write(document: DaySummaryDocument, into: Context, fileName: String): File {
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        val right = PAGE_WIDTH - MARGIN
        val width = right - MARGIN

        /** A page break before a row rather than through one. */
        fun room(needed: Float) {
            if (y <= PAGE_HEIGHT - MARGIN - needed) return
            pdf.finishPage(page)
            pageNumber += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        // --- Whose day this is, and which one

        canvas.drawText(document.shopName, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
        y += LINE + 4
        canvas.drawText(document.title, MARGIN, y + TITLE_SIZE, paint(TITLE_SIZE, bold = true))
        y += TITLE_SIZE + 6
        canvas.drawText(document.onDate, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
        y += LINE + 18

        if (document.isEmpty) {
            canvas.drawText(document.emptyLine, MARGIN, y + BODY_SIZE, paint(BODY_SIZE))
            pdf.finishPage(page)
            return pdf.saveTo(into, fileName)
        }

        // --- What happened, a section at a time

        for (section in document.sections) {
            // The heading goes over at least one row or not at all: a section
            // title alone at the foot of a page is a promise the page does not
            // keep.
            room(60f)
            canvas.drawText(section.heading.uppercase(), MARGIN, y + BODY_SIZE, paint(BODY_SIZE, bold = true))
            y += 14
            canvas.drawLine(MARGIN, y, right, y, rule)
            y += 8

            for (row in section.rows) {
                room(40f)
                val body = paint(ROW_SIZE)
                // Cut short rather than drawn over the figure. A long name
                // running under the amount is how a page becomes unreadable at
                // exactly the row that matters most.
                canvas.drawText(body.ellipsised(row.name, width * NAME_FRACTION), MARGIN, y + ROW_SIZE, body)
                canvas.drawTextRight(row.amount, right, y + ROW_SIZE, body)
                y += 14

                // The paper's number, and what is still owed on it. Grey and
                // under the name, because it qualifies the row rather than
                // competing with the figure.
                row.detail?.let {
                    canvas.drawText(it, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
                    y += 12
                }

                // The products, indented under the row they were sold on, so a
                // bill with four things on it reads as one bill.
                for (item in row.items) {
                    room(24f)
                    val small = paint(BODY_SIZE, grey = true)
                    canvas.drawText(small.ellipsised(item.text, width * NAME_FRACTION), MARGIN + 14f, y + BODY_SIZE, small)
                    canvas.drawTextRight(item.amount, right, y + BODY_SIZE, small)
                    y += 12
                }

                y += 4
                canvas.drawLine(MARGIN, y, right, y, rule)
                y += 6
            }

            room(30f)
            y += 2
            canvas.drawText(section.subtotalLabel, MARGIN, y + ROW_SIZE, paint(ROW_SIZE, bold = true))
            canvas.drawTextRight(section.subtotalValue, right, y + ROW_SIZE, paint(ROW_SIZE, bold = true))
            y += 26
        }

        // --- What the day did to the cash box

        room(30f + document.cash.size * 16f)
        canvas.drawLine(MARGIN, y, right, y, rule)
        y += 12
        for (line in document.cash) {
            val body = paint(ROW_SIZE, bold = line.isNet)
            canvas.drawText(line.label, MARGIN, y + ROW_SIZE, body)
            canvas.drawTextRight(line.value, right, y + ROW_SIZE, body)
            y += 16
        }

        pdf.finishPage(page)
        return pdf.saveTo(into, fileName)
    }

    private fun PdfDocument.saveTo(context: Context, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        file.outputStream().use { writeTo(it) }
        close()
        return file
    }

    private fun Canvas.drawTextRight(text: String, rightEdge: Float, y: Float, paint: Paint) {
        drawText(text, rightEdge - paint.measureText(text), y, paint)
    }

    /** One line, cut to `maxWidth` with an ellipsis. */
    private fun Paint.ellipsised(text: String, maxWidth: Float): String {
        if (measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val room = maxWidth - measureText(ellipsis)
        var end = text.length
        while (end > 0 && measureText(text, 0, end) > room) end -= 1
        return text.take(end).trimEnd() + ellipsis
    }
}
