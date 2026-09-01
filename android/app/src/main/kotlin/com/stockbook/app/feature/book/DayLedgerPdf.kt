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

    /** Darker and heavier than it looks like it needs: these pages photocopy. */
    private val rule = Paint().apply {
        isAntiAlias = true
        color = 0xFFC4C4D0.toInt()
        strokeWidth = 0.9f
    }

    /**
     * The band behind every other row, at 10% rather than the 6% it looks like
     * it wants.
     *
     * A mono laser cannot print grey — it halftones into a dot screen, and below
     * roughly 8% that screen comes out patchy or not at all, which would stripe
     * some pages and not others. Ten per cent survives, and on a hundred-row
     * roll-call the banding is what keeps the eye on one line.
     */
    private val band = Paint().apply {
        isAntiAlias = true
        color = 0xFFE6E6EC.toInt()
    }

    /** The app's own violet, as on the statement. */
    private val accent = Paint().apply {
        isAntiAlias = true
        color = 0xFF5C4FC4.toInt()
    }

    /** The same violet at a tenth, behind the column headings. */
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

        // The same band the statement carries, full bleed, so a folder of these
        // reads as one shop's paperwork.
        val bandHeight = 58f
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), bandHeight, accent)
        canvas.drawText(document.shopName, MARGIN, 28f, reversed(TITLE_SIZE))
        canvas.drawTextRight(document.title.uppercase(), right, 24f, reversed(8f).apply { alpha = 210 })
        canvas.drawTextRight(document.onDate, right, 40f, reversed(11f))
        y = bandHeight + 18

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
            canvas.drawRect(MARGIN, y - 2, right, y + 13, accentSoft)
            val head = paint(7.5f, grey = true)
            canvas.drawText(document.columnHeadings[0].uppercase(), MARGIN, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[1].uppercase(), MARGIN + width * EDGE_INVOICED, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[2].uppercase(), MARGIN + width * EDGE_RECEIVED, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[3].uppercase(), MARGIN + width * EDGE_OLD, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[4].uppercase(), MARGIN + width * EDGE_CURRENT, y + 10, head)
            y += 17
        }
        headings()

        val body = paint(ROW_SIZE)
        val noteInk = paint(NOTE_SIZE, grey = true)
        var striped = false
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

            // Every other row banded, which is the whole reason a hundred-line
            // roll-call can be read across five columns without losing the line.
            striped = !striped
            if (striped) canvas.drawRect(MARGIN, y, right, y + height, band)

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
        // Reversed out of solid black, like the statement's balance due: the
        // line the whole page adds up to, and the one a reader checks first.
        y += 4
        val totalHeight = 22f
        canvas.drawRect(MARGIN, y, right, y + totalHeight, accent)
        val total = reversed(ROW_SIZE)
        canvas.drawText(document.totalLabel, MARGIN + 6, y + 14, total)
        canvas.drawTextRight(document.totals[0], MARGIN + width * EDGE_INVOICED - 2, y + 14, total)
        canvas.drawTextRight(document.totals[1], MARGIN + width * EDGE_RECEIVED - 2, y + 14, total)
        canvas.drawTextRight(document.totals[2], MARGIN + width * EDGE_OLD - 2, y + 14, total)
        canvas.drawTextRight(document.totals[3], MARGIN + width * EDGE_CURRENT - 2, y + 14, total)

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
