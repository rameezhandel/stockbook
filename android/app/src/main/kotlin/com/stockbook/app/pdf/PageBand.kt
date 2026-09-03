package com.stockbook.app.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * The letterhead every printed page starts with: the shop's name and address
 * reversed out of a violet band, and on the right what the page is and which
 * days it covers.
 *
 * **One drawer rather than one per writer.** The statement had this and nothing
 * else did, so eight of the app's pages printed with no letterhead at all — a
 * sheet on a desk that did not say whose shop it came from. Copying the band
 * into three more writers would have been three more sets of constants to drift
 * apart, which is already how this app ended up with two greys for one hairline.
 *
 * **The ledger book does not use it, on purpose.** That is a hundred pages
 * printed at once and filed; `StatementPdf` draws it with a rule where the band
 * would be. Everything else in the app is a sheet or three, where a band costs
 * nothing worth counting.
 */
object PageBand {

    /** Full bleed, top of the page. The same 74pt the statement has always used. */
    const val HEIGHT = 74f

    /** Where a page's own content starts, band included. */
    const val CONTENT_TOP = HEIGHT + 24f

    private val fill = Paint().apply {
        isAntiAlias = true
        color = 0xFF5C4FC4.toInt()
    }

    private fun reversed(size: Float, bold: Boolean = false, alpha: Int = 255) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = 0xFFFFFFFF.toInt()
        this.alpha = alpha
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /**
     * Draws the band across the top of [canvas].
     *
     * @param docType what the page is, set small and uppercased — it labels the
     *   figure beside it rather than competing with the shop's name.
     * @param dateLine the day or the span. A page without it is a page somebody
     *   files and later mistakes for this morning's.
     */
    fun draw(
        canvas: Canvas,
        pageWidth: Float,
        margin: Float,
        shopName: String,
        addressLines: List<String>,
        docType: String,
        dateLine: String
    ) {
        canvas.drawRect(0f, 0f, pageWidth, HEIGHT, fill)

        canvas.drawText(shopName, margin, 30f, reversed(15f, bold = true))
        // Held back from white so the name above stays the first thing read.
        // Two or three lines fit; a longer address runs under the band and is
        // cut by it, which is the honest failure — the name and the page's own
        // title are what the sheet has to establish.
        val address = reversed(7.5f, alpha = 200)
        for ((index, line) in addressLines.take(3).withIndex()) {
            canvas.drawText(line, margin, 44f + index * 10f, address)
        }

        val right = pageWidth - margin
        canvas.drawTextRight(docType.uppercase(), right, 26f, reversed(8f, alpha = 210))
        canvas.drawTextRight(dateLine, right, 40f, reversed(11f))
    }

    private fun Canvas.drawTextRight(text: String, rightEdge: Float, y: Float, paint: Paint) {
        drawText(text, rightEdge - paint.measureText(text), y, paint)
    }
}
