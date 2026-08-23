package com.stockbook.app.feature.today

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.core.text.SummaryDocument
import java.io.File

/**
 * Draws an [SummaryDocument] onto A4 pages.
 *
 * `StatementPdf`'s smaller cousin, and deliberately the same geometry — A4 at
 * 72dpi, the same margin, the same rules and the same page-break rule — so the
 * two pages look like they came out of the same shop. Two columns instead of
 * four, and no running balance: every row is a name and a figure.
 *
 * **Black on white**, like the statement, and for a different reason. That one is
 * handed to a customer; this one is never handed to anyone — but it is printed,
 * and a dark page prints badly whoever is reading it.
 *
 * The layout decides nothing about wording: every string it draws came from
 * [SummaryDocument], which is shared with the iOS build and tested.
 */
object SummaryPdf {

    // A4 at 72dpi, which is the unit `PdfDocument` works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f

    private const val TITLE_SIZE = 15f
    private const val BODY_SIZE = 9.5f
    private const val ROW_SIZE = 10f
    private const val LINE = 13f

    /** How much of the writable width a name may take before it is cut short. */
    private const val NAME_FRACTION = 0.68f

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
    fun write(document: SummaryDocument, into: Context, fileName: String): File {
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        val right = PAGE_WIDTH - MARGIN
        val width = right - MARGIN

        // --- Whose list this is, and when it was true

        canvas.drawText(document.shopName, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
        y += LINE + 4
        canvas.drawText(document.title, MARGIN, y + TITLE_SIZE, paint(TITLE_SIZE, bold = true))
        y += TITLE_SIZE + 6
        canvas.drawText(document.asOf, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
        y += LINE + 18

        if (document.isEmpty) {
            canvas.drawText(document.emptyLine, MARGIN, y + BODY_SIZE, paint(BODY_SIZE))
            pdf.finishPage(page)
            return pdf.saveTo(into, fileName)
        }

        // --- The list

        fun drawHeadings() {
            canvas.drawText(document.columnHeadings[0], MARGIN, y + ROW_SIZE, paint(ROW_SIZE, bold = true))
            canvas.drawTextRight(document.columnHeadings[1], right, y + ROW_SIZE, paint(ROW_SIZE, bold = true))
            y += 16
            canvas.drawLine(MARGIN, y, right, y, rule)
            y += 6
        }
        drawHeadings()

        for (row in document.rows) {
            // A page break before a row rather than through one, and the headings
            // repeat: a second page whose columns are unlabelled is a page nobody
            // can read on its own.
            if (y > PAGE_HEIGHT - MARGIN - 60) {
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
                drawHeadings()
            }

            val body = paint(ROW_SIZE)
            // Cut short rather than drawn over the figure. A long name running
            // under the amount is how a chasing list becomes unreadable at
            // exactly the row that matters most.
            canvas.drawText(body.ellipsised(row.name, width * NAME_FRACTION), MARGIN, y + ROW_SIZE, body)
            // The aside, where a row has one: how often something was bought, set
            // grey and between the two so it never competes with the figure. A
            // debtor has none — they are behind by an amount, not by a count of
            // anything.
            row.detail?.let {
                canvas.drawTextRight(it, MARGIN + width * 0.86f, y + ROW_SIZE, paint(BODY_SIZE, grey = true))
            }
            canvas.drawTextRight(row.amount, right, y + ROW_SIZE, body)
            y += 17
            canvas.drawLine(MARGIN, y, right, y, rule)
            y += 6
        }

        // The figure the page exists to state, where the eye stops.
        y += 6
        canvas.drawText(document.totalLabel, MARGIN, y + ROW_SIZE, paint(ROW_SIZE, bold = true))
        canvas.drawTextRight(document.totalValue, right, y + ROW_SIZE, paint(ROW_SIZE, bold = true))

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
