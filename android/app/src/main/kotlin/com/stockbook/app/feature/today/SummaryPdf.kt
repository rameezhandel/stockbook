package com.stockbook.app.feature.today

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.app.pdf.PageBand
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

    /**
     * Where each column starts, as a fraction of the writable width, by how many
     * columns the page has.
     *
     * Three shapes, because three kinds of page come through here. A balance list
     * is a name and a figure. A register of expenses adds the day. A register of
     * bills, purchases or receipts adds the number on the paper as well — and the
     * name gives up the room for it, since a customer's name is the one thing on
     * the row a reader can still recognise cut short.
     *
     * The last column is right-aligned against the margin and takes no start.
     */
    private val COLUMN_STARTS: Map<Int, List<Float>> = mapOf(
        2 to listOf(0f),
        3 to listOf(0f, 0.62f),
        4 to listOf(0f, 0.38f, 0.66f)
    )

    /** How much room a column has: up to the next one, less a gutter. */
    private fun columnWidth(starts: List<Float>, index: Int): Float =
        (starts.getOrNull(index + 1) ?: 0.98f) - starts[index] - 0.02f

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

        // The letterhead, on every page of every document the app prints but the
        // ledger book. A sheet in a folder has to say whose shop it came from,
        // and until now this one did not.
        fun masthead() {
            PageBand.draw(
                canvas = canvas,
                pageWidth = PAGE_WIDTH.toFloat(),
                margin = MARGIN,
                shopName = document.shopName,
                addressLines = document.shopAddressLines,
                docType = document.title,
                dateLine = document.asOf
            )
            y = PageBand.CONTENT_TOP
        }
        masthead()

        if (document.isEmpty) {
            canvas.drawText(document.emptyLine, MARGIN, y + BODY_SIZE, paint(BODY_SIZE))
            pdf.finishPage(page)
            return pdf.saveTo(into, fileName)
        }

        // --- The list

        // Two, three or four, and the last of them is the figure. A page whose
        // headings this does not know about would draw its columns on top of one
        // another, so it falls back to the plainest shape rather than to nothing.
        val starts = COLUMN_STARTS[document.columnHeadings.size] ?: COLUMN_STARTS.getValue(2)

        fun drawHeadings() {
            val heading = paint(ROW_SIZE, bold = true)
            for ((index, start) in starts.withIndex()) {
                canvas.drawText(document.columnHeadings[index], MARGIN + width * start, y + ROW_SIZE, heading)
            }
            canvas.drawTextRight(document.columnHeadings.last(), right, y + ROW_SIZE, heading)
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
                masthead()
                drawHeadings()
            }

            val body = paint(ROW_SIZE)
            val aside = paint(BODY_SIZE, grey = true)

            // Every cell cut short rather than drawn over its neighbour. A long
            // name running under the number is how a register becomes unreadable
            // at exactly the row somebody is looking for.
            //
            // The middle cells are grey: the row is a name and a figure, and the
            // number and the day are how you find it, not what it says.
            // Positional, not compacted. A receipt written without a number
            // leaves an empty cell; dropping it would slide the date up into the
            // number's column and put the whole row out of step with its heading.
            val cells = when (starts.size) {
                3 -> listOf(row.name, row.reference.orEmpty(), row.date.orEmpty())
                2 -> listOf(row.name, row.date.orEmpty())
                else -> listOf(row.name)
            }
            for ((index, cell) in cells.withIndex()) {
                if (cell.isEmpty()) continue
                val ink = if (index == 0) body else aside
                canvas.drawText(
                    ink.ellipsised(cell, width * columnWidth(starts, index)),
                    MARGIN + width * starts[index],
                    y + ROW_SIZE,
                    ink
                )
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

        // The fact the column could not carry, set small and grey under the total
        // so it is plainly not another row. Only the payments page has one: what
        // the shop paid out over the same days, which belongs on the page but not
        // in a column of money coming in.
        document.footnote?.let {
            y += 17
            canvas.drawText(it, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
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
