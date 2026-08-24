package com.stockbook.app.feature.today

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.core.text.EarningsDocument
import java.io.File

/**
 * Draws an [EarningsDocument] onto one A4 page.
 *
 * [SummaryPdf]'s geometry — A4 at 72dpi, the same margin, the same rules — so
 * every page the shop prints looks like it came out of the same shop. One page
 * always: the chain is six lines and the confession is two, and a summary that
 * ran over would not be one.
 *
 * **Black on white**, like the statement, and for a different reason. That one is
 * handed to a customer; this one is never handed to anyone — but it is printed,
 * and a dark page prints badly whoever is reading it.
 *
 * The layout decides nothing about wording: every string it draws came from
 * [EarningsDocument], which is shared with the iOS build and tested.
 */
object EarningsPdf {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f

    private const val TITLE_SIZE = 15f
    private const val BODY_SIZE = 9.5f
    private const val ROW_SIZE = 10f
    private const val LINE = 13f
    private const val BAND_HEIGHT = 19f

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

    private val band = Paint().apply {
        isAntiAlias = true
        color = 0xFFEDEDF2.toInt()
    }

    fun write(document: EarningsDocument, into: Context, fileName: String): File {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        var y = MARGIN

        val right = PAGE_WIDTH - MARGIN

        // --- Whose figures these are, and which days

        canvas.drawText(document.shopName, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
        y += LINE + 4
        canvas.drawText(document.title, MARGIN, y + TITLE_SIZE, paint(TITLE_SIZE, bold = true))
        y += TITLE_SIZE + 6
        canvas.drawText(document.onDate, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
        y += LINE + 20

        if (document.isEmpty) {
            canvas.drawText(document.emptyLine, MARGIN, y + BODY_SIZE, paint(BODY_SIZE))
            pdf.finishPage(page)
            return pdf.saveTo(into, fileName)
        }

        // --- Takings down to what was kept

        for (line in document.lines) {
            val isTotal = line.weight == EarningsDocument.Weight.TOTAL
            val isMinus = line.weight == EarningsDocument.Weight.MINUS

            // A total is what the lines above it come to, so the rule goes over
            // it — the same place a subtotal's rule goes on every other page
            // this shop prints.
            if (isTotal) {
                y += 4
                canvas.drawLine(MARGIN, y, right, y, rule)
                y += 9
            }

            val body = paint(ROW_SIZE, bold = isTotal, grey = isMinus && !isTotal)
            canvas.drawText(line.label, MARGIN, y + ROW_SIZE, body)
            canvas.drawTextRight(if (isMinus) "− ${line.value}" else line.value, right, y + ROW_SIZE, body)
            y += if (isTotal) 20 else 16
        }

        // --- What the page could not account for

        if (document.hasGap) {
            y += 14
            canvas.drawRect(MARGIN, y, right, y + BAND_HEIGHT, band)
            canvas.drawText(
                document.gapHeading.uppercase(),
                MARGIN + 7f,
                y + BAND_HEIGHT - 6.5f,
                paint(BODY_SIZE, bold = true)
            )
            y += BAND_HEIGHT + 10

            for (line in document.gap) {
                canvas.drawText(line.label, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
                canvas.drawTextRight(line.value, right, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
                y += 14
            }

            document.gapNote?.let {
                y += 4
                canvas.drawText(it, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
            }
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
}
