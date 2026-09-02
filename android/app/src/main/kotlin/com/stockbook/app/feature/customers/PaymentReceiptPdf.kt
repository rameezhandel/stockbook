package com.stockbook.app.feature.customers

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.core.text.PaymentReceiptDocument
import java.io.File

/**
 * Draws a [PaymentReceiptDocument] onto one A4 page.
 *
 * The **Counter** treatment, same as the statement: this is a single sheet
 * handed to one person, which is where the band is worth its toner. It is drawn
 * on the same grid as the statement, with the same band, the same facts row and
 * the same tinted card, so the two pieces of paper a customer is handed
 * obviously come from the same shop.
 *
 * **Half a page, and it says so.** A receipt is four figures; padding it down an
 * A4 sheet would make it look like a form somebody failed to fill in. Everything
 * sits in the top half and a dashed rule closes it, which is what a counter does
 * with a receipt anyway.
 *
 * The layout decides nothing about wording: every string here came from
 * [PaymentReceiptDocument], which is shared with the iOS build and tested. This
 * file is only geometry.
 */
object PaymentReceiptPdf {

    // A4 at 72dpi, which is the unit `PdfDocument` works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f

    private const val TITLE_SIZE = 15f
    private const val BODY_SIZE = 9.5f

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
     * The line the slip is torn along.
     *
     * Dashed rather than solid, and running the full width of the paper: a solid
     * rule across a page is a divider between two things, and there is nothing
     * below this one.
     */
    private val cut = Paint().apply {
        isAntiAlias = true
        color = 0xFFC4C4D0.toInt()
        strokeWidth = 0.9f
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }

    /** The app's own violet, carried onto the paper. */
    private val accent = Paint().apply {
        isAntiAlias = true
        color = 0xFF5C4FC4.toInt()
    }

    /** The same violet at a tenth, for the amount card and the summary card. */
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
     * Renders the receipt and returns the file it was written to.
     *
     * Written into the app's own cache directory, which is the one place a file
     * can be put without asking the phone for anything — the app holds no
     * storage permission and is never going to. Sharing hands out a `content://`
     * URI to it rather than a path.
     */
    fun write(document: PaymentReceiptDocument, into: Context, fileName: String): File {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
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
        canvas.drawTextRight(document.dateValue, right, 40f, reversed(11f))
        var y = bandHeight + 24

        // --- Who it was, and which slip
        //
        // The statement's facts row exactly: label, the fact, then the smaller
        // one under it. No box round it — the accent labels already group the
        // two halves, and a hairline as well is one device too many.
        val factsTop = y
        val factsHeight = 46f
        val middle = MARGIN + width / 2
        val factLabel = accentText(7.5f, bold = true)

        canvas.drawText(document.addressedToLabel.uppercase(), MARGIN, factsTop + 12, factLabel)
        canvas.drawText(document.partyName, MARGIN, factsTop + 27, paint(11f, bold = true))
        canvas.drawText(document.partyLines.joinToString(" · "), MARGIN, factsTop + 39, paint(8f, grey = true))

        canvas.drawText(document.receiptLabel.uppercase(), middle, factsTop + 12, factLabel)
        canvas.drawText(document.receiptValue, middle, factsTop + 27, paint(11f, bold = true))
        canvas.drawText(
            "${document.dateLabel} ${document.dateValue}",
            middle,
            factsTop + 39,
            paint(8f, grey = true)
        )

        y = factsTop + factsHeight + 24

        // --- The figure the page exists to state
        //
        // Set large and alone in its own card, because this is the one thing the
        // person holding the slip is checking. Everything else on the page is
        // context for it.
        val amountHeight = 58f
        canvas.drawRect(MARGIN, y, right, y + amountHeight, accentSoft)
        canvas.drawRect(MARGIN, y, MARGIN + 3.5f, y + amountHeight, accent)
        canvas.drawText(document.amountLabel.uppercase(), MARGIN + 14, y + 20, factLabel)
        canvas.drawTextRight(document.amountValue, right - 14, y + 46, accentText(26f, bold = true))
        y += amountHeight + 22

        // --- The owner's own note, where there is one
        document.noteLabel?.let { label ->
            canvas.drawText(label.uppercase(), MARGIN, y, paint(7.5f, grey = true))
            canvas.drawText(document.noteValue.orEmpty(), MARGIN, y + 15, paint(BODY_SIZE))
            y += 30
        }

        // --- Where the account stands now
        //
        // Against the right edge under the figure above, in the same card the
        // statement's totals sit in: previous balance, this receipt coming off
        // it, and the line the reader checks last.
        canvas.drawText(document.summaryTitle.uppercase(), MARGIN, y, paint(8f, grey = true))
        y += 12

        val cardLeft = MARGIN + width * 0.52f
        val lineHeight = 18f
        val closingHeight = 30f
        val cardHeight = document.summaryRows.size * lineHeight + closingHeight
        canvas.drawRect(cardLeft, y, right, y + cardHeight, accentSoft)
        canvas.drawRect(cardLeft, y, cardLeft + 3.5f, y + cardHeight, accent)

        var rowY = y
        for (row in document.summaryRows) {
            canvas.drawText(row.label, cardLeft + 12, rowY + 12, paint(BODY_SIZE, grey = true))
            canvas.drawTextRight(row.value.bracketed(row.deduction), right - 9, rowY + 12, paint(BODY_SIZE))
            rowY += lineHeight
        }
        canvas.drawLine(cardLeft + 12, rowY, right - 9, rowY, rule)
        canvas.drawText(document.closingLabel.uppercase(), cardLeft + 12, rowY + 18, factLabel)
        canvas.drawTextRight(document.closingValue, right - 9, rowY + 21, accentText(15f, bold = true))
        y = rowY + closingHeight + 22

        // --- The one thing a customer might otherwise get wrong
        canvas.drawText(document.footnote, MARGIN, y, paint(7.5f, grey = true))
        y += 26

        canvas.drawLine(0f, y, PAGE_WIDTH.toFloat(), y, cut)

        pdf.finishPage(page)
        val file = File(into.cacheDir, fileName)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /**
     * `(SAR 300)` — accounting brackets, the convention the shop's own supplier
     * statements use. A bare minus in front of a currency symbol reads as a typo
     * on paper.
     */
    private fun String.bracketed(deduction: Boolean): String = if (deduction) "($this)" else this

    private fun Canvas.drawTextRight(text: String, rightEdge: Float, y: Float, paint: Paint) {
        drawText(text, rightEdge - paint.measureText(text), y, paint)
    }
}
