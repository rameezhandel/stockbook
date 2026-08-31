package com.stockbook.app.feature.book

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.core.text.DayLedgerDocument
import java.io.File

/**
 * The day's balances as a printable page.
 *
 * Geometry only. What goes on the page is [DayLedgerDocument], which is shared
 * with the iOS build and tested — the same split every other page in this app
 * uses, for the same reason: two hand-written layouts drift the first time
 * either is corrected.
 *
 * **Five columns and often a hundred rows.** The rows are short and the page
 * breaks between them, never through one, and the column headings repeat at the
 * top of each new page: a second sheet whose columns are unlabelled is a sheet
 * nobody can read on its own — least of all beside a paper book, which is what
 * this exists for.
 */
object DayLedgerPdf {

    // A4 at 72dpi, which is the unit `PdfDocument` works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private const val TITLE_SIZE = 15f
    private const val BODY_SIZE = 9.5f
    private const val ROW_SIZE = 8.5f
    private const val NOTE_SIZE = 7.5f

    /**
     * Where each column ends, as fractions of the writable width.
     *
     * The name is left-aligned and takes what is left; the four money columns are
     * right-aligned against these edges, which is how a column of figures is
     * read — by the units lining up under each other.
     */
    private const val EDGE_INVOICED = 0.55f
    private const val EDGE_RECEIVED = 0.70f
    private const val EDGE_OLD = 0.85f
    private const val EDGE_CURRENT = 1.0f

    private fun paint(size: Float, bold: Boolean = false, grey: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = if (grey) 0xFF6B6B76.toInt() else 0xFF14141C.toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private val rule = Paint().apply {
        isAntiAlias = true
        color = 0xFFD6D6DE.toInt()
        strokeWidth = 0.6f
    }

    /**
     * Renders the document and returns the file it was written to.
     *
     * Written into the app's own cache directory, which is the one place a file
     * can be put without asking the phone for anything — the app holds no storage
     * permission and is never going to.
     */
    fun write(document: DayLedgerDocument, into: Context, fileName: String): File {
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        val right = PAGE_WIDTH - MARGIN
        val width = right - MARGIN
        var y = MARGIN

        canvas.drawText(document.shopName, MARGIN, y + TITLE_SIZE, paint(TITLE_SIZE, bold = true))
        y += TITLE_SIZE + 10
        canvas.drawText(document.title, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, bold = true))
        canvas.drawTextRight(document.onDate, right, y + BODY_SIZE, paint(BODY_SIZE))
        y += BODY_SIZE + 6

        // Only on a narrowed page, and said before the figures rather than after:
        // somebody reading the totals has to already know what they are the total
        // of.
        document.filterNote?.let { note ->
            canvas.drawText(note, MARGIN, y + NOTE_SIZE, paint(NOTE_SIZE, grey = true))
            y += NOTE_SIZE + 6
        }
        y += 6

        if (document.isEmpty) {
            canvas.drawText(document.emptyLine, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
            pdf.finishPage(page)
            return pdf.into(into, fileName)
        }

        fun headings() {
            val bold = paint(ROW_SIZE, bold = true)
            canvas.drawText(document.columnHeadings[0], MARGIN, y + 12, bold)
            canvas.drawTextRight(document.columnHeadings[1], MARGIN + width * EDGE_INVOICED, y + 12, bold)
            canvas.drawTextRight(document.columnHeadings[2], MARGIN + width * EDGE_RECEIVED, y + 12, bold)
            canvas.drawTextRight(document.columnHeadings[3], MARGIN + width * EDGE_OLD, y + 12, bold)
            canvas.drawTextRight(document.columnHeadings[4], MARGIN + width * EDGE_CURRENT, y + 12, bold)
            y += 17
            canvas.drawLine(MARGIN, y, right, y, rule)
        }
        headings()

        val body = paint(ROW_SIZE)
        val noteInk = paint(NOTE_SIZE, grey = true)
        for (row in document.rows) {
            val height = if (row.note == null) 15f else 24f
            // A break before a row rather than through one, and the headings
            // repeat so the second page stands on its own. The totals need room
            // too, which is why the margin here is more than one row.
            if (y + height > PAGE_HEIGHT - MARGIN - 30) {
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
                headings()
            }

            canvas.drawText(row.name, MARGIN, y + 11, body)
            canvas.drawTextRight(row.invoiced, MARGIN + width * EDGE_INVOICED, y + 11, body)
            canvas.drawTextRight(row.received, MARGIN + width * EDGE_RECEIVED, y + 11, body)
            canvas.drawTextRight(row.oldBalance, MARGIN + width * EDGE_OLD, y + 11, body)
            canvas.drawTextRight(row.currentBalance, MARGIN + width * EDGE_CURRENT, y + 11, body)
            row.note?.let { canvas.drawText(it, MARGIN + 8, y + 20, noteInk) }
            y += height
            canvas.drawLine(MARGIN, y, right, y, rule)
        }

        // The columns added up, and they are the columns above rather than the
        // whole book — see `DayLedger.movedOnly`.
        val total = paint(ROW_SIZE, bold = true)
        y += 4
        canvas.drawText(document.totalLabel, MARGIN, y + 12, total)
        canvas.drawTextRight(document.totals[0], MARGIN + width * EDGE_INVOICED, y + 12, total)
        canvas.drawTextRight(document.totals[1], MARGIN + width * EDGE_RECEIVED, y + 12, total)
        canvas.drawTextRight(document.totals[2], MARGIN + width * EDGE_OLD, y + 12, total)
        canvas.drawTextRight(document.totals[3], MARGIN + width * EDGE_CURRENT, y + 12, total)

        pdf.finishPage(page)
        return pdf.into(into, fileName)
    }

    private fun PdfDocument.into(context: Context, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        file.outputStream().use { writeTo(it) }
        close()
        return file
    }

    private fun Canvas.drawTextRight(text: String, rightEdge: Float, y: Float, paint: Paint) {
        drawText(text, rightEdge - paint.measureText(text), y, paint)
    }
}
