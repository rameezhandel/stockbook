package com.stockbook.app.feature.bills

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.core.text.BillDocument
import java.io.File

/**
 * Draws a [BillDocument] onto A4 pages.
 *
 * The **Counter** treatment, same as the statement and the payment receipt: this
 * is a single sheet handed to one person, which is where the band is worth its
 * toner. Drawn on the same grid as those two — same band, same facts row, same
 * tinted card — so every piece of paper a customer is handed obviously comes
 * from the same shop.
 *
 * **It breaks across pages.** A bill is usually one line or none, but a load of
 * fittings can run to forty, and a break through a row would leave a quantity on
 * one sheet and its price on the next. The break comes before a row, the column
 * of items carries on, and the total block is never orphaned onto a page of its
 * own.
 *
 * The layout decides nothing about wording: every string here came from
 * [BillDocument], which is shared with the iOS build and tested. This file is
 * only geometry.
 */
object BillPdf {

    // A4 at 72dpi, which is the unit `PdfDocument` works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f

    private const val TITLE_SIZE = 15f
    private const val BODY_SIZE = 9.5f
    private const val ROW_SIZE = 10f

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

    /** The app's own violet, carried onto the paper. */
    private val accent = Paint().apply {
        isAntiAlias = true
        color = 0xFF5C4FC4.toInt()
    }

    /** The same violet at a tenth, for the total card. */
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
     * Renders the bill and returns the file it was written to.
     *
     * Written into the app's own cache directory, which is the one place a file
     * can be put without asking the phone for anything — the app holds no
     * storage permission and is never going to. Sharing hands out a `content://`
     * URI to it rather than a path.
     */
    fun write(document: BillDocument, into: Context, fileName: String): File {
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        val right = PAGE_WIDTH - MARGIN
        val width = right - MARGIN

        // --- The band
        //
        // Full bleed to the paper's edge, not inset to the margin: an inset
        // colour block reads as a box somebody drew, where a band that runs off
        // both sides reads as the head of the page. Sans throughout, because the
        // shop's own name may be in Arabic or Kannada and a serif's coverage of
        // those is patchy — the failure being tofu boxes in the largest text on
        // the page.
        val bandHeight = 74f
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), bandHeight, accent)
        canvas.drawText(document.shopName, MARGIN, 30f, reversed(TITLE_SIZE))
        for ((index, line) in document.shopAddressLines.withIndex()) {
            canvas.drawText(line, MARGIN, 44f + index * 10f, reversed(7.5f).apply { alpha = 200 })
        }
        canvas.drawTextRight(document.docType.uppercase(), right, 26f, reversed(8f).apply { alpha = 210 })
        canvas.drawTextRight(document.reference, right, 44f, reversed(13f))
        var y = bandHeight + 24

        // --- Who it is for, and when it was written
        val factsTop = y
        val factsHeight = 46f
        val middle = MARGIN + width / 2
        val factLabel = accentText(7.5f, bold = true)

        canvas.drawText(document.addressedToLabel.uppercase(), MARGIN, factsTop + 12, factLabel)
        canvas.drawText(document.partyName, MARGIN, factsTop + 27, paint(11f, bold = true))
        canvas.drawText(document.partyLines.joinToString(" · "), MARGIN, factsTop + 39, paint(8f, grey = true))

        canvas.drawText(document.dateLabel.uppercase(), middle, factsTop + 12, factLabel)
        canvas.drawText(document.dateValue, middle, factsTop + 27, paint(11f, bold = true))

        y = factsTop + factsHeight + 20

        // --- What was sold, where the bill says
        //
        // Nothing at all on a bill entered as a figure, and no empty heading
        // either: a table head over no rows is a question the reader has to
        // answer for themselves.
        if (document.isItemised) {
            canvas.drawLine(MARGIN, y, right, y, rule)
            y += 14

            for (line in document.lines) {
                // A break before a row rather than through one — a quantity on
                // one sheet and its price on the next is worse than a short page.
                // The room kept back is for the totals block.
                if (y + 26 > PAGE_HEIGHT - MARGIN - 150) {
                    pdf.finishPage(page)
                    pageNumber += 1
                    page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    )
                    canvas = page.canvas
                    y = MARGIN
                }

                canvas.drawText(line.name, MARGIN, y + 10, paint(ROW_SIZE))
                canvas.drawTextRight(line.amount, right, y + 10, paint(ROW_SIZE))
                canvas.drawText(line.detail, MARGIN, y + 21, paint(BODY_SIZE, grey = true))
                y += 28
            }
            y += 2
            canvas.drawLine(MARGIN, y, right, y, rule)
            y += 16
        }

        // --- Subtotal and discount, where one was given
        val totalsLeft = MARGIN + width * 0.52f
        for (row in document.summaryRows) {
            canvas.drawText(row.label, totalsLeft, y + 9, paint(BODY_SIZE, grey = true))
            canvas.drawTextRight(row.value.deducted(row.deduction), right, y + 9, paint(BODY_SIZE))
            y += 15
        }
        if (document.summaryRows.isNotEmpty()) y += 4

        // --- The figure the page exists to state
        //
        // Set large and alone in its own card, because this is the one thing the
        // person holding the bill is checking.
        val cardHeight = 58f
        canvas.drawRect(totalsLeft, y, right, y + cardHeight, accentSoft)
        canvas.drawRect(totalsLeft, y, totalsLeft + 3.5f, y + cardHeight, accent)
        canvas.drawText(document.totalLabel.uppercase(), totalsLeft + 14, y + 20, factLabel)
        canvas.drawTextRight(document.totalValue, right - 14, y + 46, accentText(24f, bold = true))
        y += cardHeight + 16

        // --- Settled, or what is left and who owes it
        canvas.drawText(document.paymentNote, MARGIN, y + 9, paint(BODY_SIZE))

        pdf.finishPage(page)
        val file = File(into.cacheDir, fileName)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /**
     * `− SAR 20` — a discount reads as something taken off rather than as a
     * second figure to add. Brackets are the statement's convention for a
     * deduction inside a column of positives; here the line sits alone above a
     * total, where a minus is plainer.
     */
    private fun String.deducted(deduction: Boolean): String = if (deduction) "− $this" else this

    private fun Canvas.drawTextRight(text: String, rightEdge: Float, y: Float, paint: Paint) {
        drawText(text, rightEdge - paint.measureText(text), y, paint)
    }
}
