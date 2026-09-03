package com.stockbook.app.feature.customers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.stockbook.app.pdf.PageBand
import com.stockbook.core.text.StatementDocument
import com.stockbook.core.text.SummaryDocument
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
    private const val COL_DATE = 0f
    private const val COL_REFERENCE = 0.16f
    private const val EDGE_CHARGE = 0.62f
    private const val EDGE_SETTLED = 0.81f
    private const val EDGE_BALANCE = 1.0f

    private fun paint(size: Float, bold: Boolean = false, grey: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = if (grey) 0xFF6B6B76.toInt() else 0xFF14141C.toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /**
     * The hairline, darker and heavier than it looks like it needs to be.
     *
     * These pages get photocopied, and a 16%-grey 0.8pt rule is the first thing
     * a copier loses. At 22% and 0.9pt it survives a generation or two, which is
     * what a statement handed across a counter has to do.
     */
    private val rule = Paint().apply {
        isAntiAlias = true
        color = 0xFFC4C4D0.toInt()
        strokeWidth = 0.9f
    }

    /** The heavy rule the monochrome treatment uses in place of the band. */
    private val heavyRule = Paint().apply {
        isAntiAlias = true
        color = 0xFF14141C.toInt()
        strokeWidth = 1.6f
    }

    private val fillInk = Paint().apply {
        isAntiAlias = true
        color = 0xFF14141C.toInt()
    }

    /**
     * The app's own violet, carried onto the paper.
     *
     * Somebody who has seen the phone recognises the page. It also costs colour:
     * on a mono printer the band becomes a heavy grey slab and the tinted card
     * goes pale but survives, which is the trade this treatment makes.
     */
    private val accent = Paint().apply {
        isAntiAlias = true
        color = 0xFF5C4FC4.toInt()
    }

    /** The same violet at a tenth, for the heading row and the totals card. */
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
     * permission and is never going to. Sharing hands out a `content://` URI to
     * it rather than a path.
     */
    fun write(document: StatementDocument, into: Context, fileName: String): File =
        write(listOf(document), into, fileName)

    /**
     * Several statements into one file, **each starting on a fresh page**.
     *
     * This is the ledger book: every customer's own sheet, one after another, so
     * a page can be pulled out and filed on its own. Written as a loop over the
     * single-statement drawing rather than as a second layout — a book whose
     * pages did not match the statement the customer was handed would be two
     * documents claiming to be one, and the first correction to either would
     * separate them for good.
     */
    fun write(
        documents: List<StatementDocument>,
        into: Context,
        fileName: String
    ): File {
        val pdf = PdfDocument()
        var pageNumber = 0
        for (document in documents) {
            pageNumber = draw(document, pdf, pageNumber)
        }
        val file = File(into.cacheDir, fileName)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /**
     * The ledger book: a contents page, then every customer's own sheet.
     *
     * The index is drawn from [SummaryDocument.forLedgerBook], which is built
     * from the very statements passed here — so a line in the contents and the
     * page it points at cannot state different balances.
     *
     * **The book prints like every other page in the app.** It carried a
     * monochrome treatment of its own for a while, on the argument that a hundred
     * pages of band is toner worth saving. Doing the arithmetic put that at
     * something under forty riyals a print — real, but not enough to be the one
     * document that does not match the other thirteen, and not enough to keep a
     * second layout alive for.
     */
    fun writeLedgerBook(
        index: SummaryDocument,
        pages: List<StatementDocument>,
        into: Context,
        fileName: String
    ): File {
        val pdf = PdfDocument()
        var pageNumber = drawIndex(index, pdf, 0)
        for (page in pages) {
            pageNumber = draw(page, pdf, pageNumber)
        }
        val file = File(into.cacheDir, fileName)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /**
     * The contents page: two columns, however many sheets it takes.
     *
     * Its own routine rather than [SummaryPdf], which writes a file of its own —
     * this one has to append into a book already being built. The wording is
     * still shared: every string here came from [SummaryDocument].
     *
     * Returns the last page it used, so the statements know where to carry on.
     */
    private fun drawIndex(document: SummaryDocument, pdf: PdfDocument, from: Int): Int {
        var pageNumber = from + 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        val right = PAGE_WIDTH - MARGIN
        val width = right - MARGIN
        var y = MARGIN

        // Named at the foot of every sheet, because an index that runs to three
        // pages is three loose sheets the moment the staple comes out.
        fun footer() {
            val footY = PAGE_HEIGHT - MARGIN + 4
            canvas.drawLine(MARGIN, footY - 14, right, footY - 14, rule)
            canvas.drawText(document.title, MARGIN, footY, paint(7.5f, grey = true))
            canvas.drawTextRight("$pageNumber", right, footY, paint(7.5f, grey = true))
        }

        // The same letterhead every other page carries. `asOf` rides in the
        // band's date slot: a balance is true at a moment, and without the moment
        // last month's printout reads as this morning's.
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
            canvas.drawText(document.emptyLine, MARGIN, y + BODY_SIZE, paint(BODY_SIZE, grey = true))
            footer()
            pdf.finishPage(page)
            return pageNumber
        }

        fun headings() {
            val head = paint(7.5f, grey = true)
            canvas.drawText(document.columnHeadings[0].uppercase(), MARGIN, y + 8, head)
            canvas.drawTextRight(document.columnHeadings[1].uppercase(), right, y + 8, head)
            y += 12
            canvas.drawLine(MARGIN, y, right, y, heavyRule)
            y += 6
        }
        headings()

        val body = paint(ROW_SIZE)
        for (row in document.rows) {
            // A break before a row rather than through one, and the headings
            // repeat: a second sheet whose columns are unlabelled is a sheet
            // nobody can read on its own. The room kept back is for the total,
            // which must not be orphaned onto a page of its own.
            if (y + 17 > PAGE_HEIGHT - MARGIN - 60) {
                footer()
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                masthead()
                headings()
            }

            // Cut short rather than drawn over the figure. A long name running
            // under the balance is how a page becomes unreadable at exactly the
            // line somebody is looking for.
            canvas.drawText(body.ellipsised(row.name, width * 0.7f), MARGIN, y + 11, body)
            canvas.drawTextRight(row.amount, right, y + 11, body)
            y += 17
            canvas.drawLine(MARGIN, y, right, y, rule)
        }

        // Reversed out of solid black, like the statement's balance due in this
        // treatment: the line the whole page adds up to, and the one a reader
        // checks first.
        y += 10
        val totalHeight = 30f
        canvas.drawRect(MARGIN, y, right, y + totalHeight, fillInk)
        canvas.drawText(document.totalLabel.uppercase(), MARGIN + 12, y + 19, reversed(9f))
        canvas.drawTextRight(document.totalValue, right - 12, y + 20, reversed(13f))

        footer()
        pdf.finishPage(page)
        return pageNumber
    }

    /** Draws one statement from a fresh page, and returns the last page it used. */
    private fun draw(document: StatementDocument, pdf: PdfDocument, from: Int): Int {
        var pageNumber = from + 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        val right = PAGE_WIDTH - MARGIN
        val width = right - MARGIN

        // The letterhead, through the shared drawer so this page and the eight
        // that gained one later cannot drift apart. Full bleed to the paper's
        // edge, not inset to the margin: an inset colour block reads as a box
        // somebody drew, where a band that runs off both sides reads as the head
        // of the page.
        fun masthead() {
            PageBand.draw(
                canvas = canvas,
                pageWidth = PAGE_WIDTH.toFloat(),
                margin = MARGIN,
                shopName = document.shopName,
                addressLines = document.shopAddressLines,
                docType = document.docType,
                dateLine = document.periodValue
            )
            y = PageBand.CONTENT_TOP
        }
        masthead()

        // --- Whose account, and over what

        val factsTop = y
        val factsHeight = 46f
        val middle = MARGIN + width / 2

        // Not boxed: the accent labels group these two facts already, and a
        // hairline round them as well is one device too many — the reason this
        // page once read busier than it was meant to.
        val factLabel = accentText(7.5f, bold = true)
        canvas.drawText(document.accountLabel.uppercase(), MARGIN, factsTop + 12, factLabel)
        canvas.drawText(document.partyName, MARGIN, factsTop + 27, paint(11f, bold = true))
        canvas.drawText(document.partyLines.joinToString(" · "), MARGIN, factsTop + 39, paint(8f, grey = true))

        canvas.drawText(document.periodLabel.uppercase(), middle, factsTop + 12, factLabel)
        canvas.drawText(document.periodValue, middle, factsTop + 27, paint(11f, bold = true))
        canvas.drawText(document.summaryTitle, middle, factsTop + 39, paint(8f, grey = true))

        y = factsTop + factsHeight + 26

        // --- The activity table

        canvas.drawText(document.activityTitle.uppercase(), MARGIN, y, paint(8f, grey = true))
        y += 10

        fun headings() {
            // Set on a tinted row rather than over a rule: the band at the top
            // has already said this page uses colour, and a second heavy black
            // rule would be a different page's idea.
            canvas.drawRect(MARGIN, y - 2, right, y + 13, accentSoft)
            val head = paint(7.5f, grey = true)
            canvas.drawText(document.columnHeadings[0].uppercase(), MARGIN + width * COL_DATE, y + 10, head)
            canvas.drawText(document.columnHeadings[1].uppercase(), MARGIN + width * COL_REFERENCE, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[2].uppercase(), MARGIN + width * EDGE_CHARGE, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[3].uppercase(), MARGIN + width * EDGE_SETTLED, y + 10, head)
            canvas.drawTextRight(document.columnHeadings[4].uppercase(), MARGIN + width * EDGE_BALANCE, y + 10, head)
            y += 17
        }
        headings()

        for (row in document.activityRows) {
            // A page break before a row rather than through one, and the headings
            // repeat: a second page whose columns are unlabelled is a page nobody
            // can read on its own. The room kept back is for the totals block.
            if (y > PAGE_HEIGHT - MARGIN - 130) {
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                masthead()
                headings()
            }

            // Exactly one of the two money columns carries anything, so the empty
            // one draws nothing at all rather than a dash or a zero: an empty cell
            // is unambiguous, and a zero in the Received column is a payment
            // somebody might go looking for.
            val body = paint(ROW_SIZE)
            canvas.drawText(row.date, MARGIN + width * COL_DATE, y + 14, paint(ROW_SIZE, grey = true))
            canvas.drawText(row.reference, MARGIN + width * COL_REFERENCE, y + 14, body)
            canvas.drawTextRight(row.charge, MARGIN + width * EDGE_CHARGE, y + 14, body)
            canvas.drawTextRight(row.settled, MARGIN + width * EDGE_SETTLED, y + 14, body)
            canvas.drawTextRight(row.balance, MARGIN + width * EDGE_BALANCE, y + 14, body)
            y += 19
            canvas.drawLine(MARGIN, y, right, y, rule)
        }

        // --- The totals, set against the right edge under the money columns

        y += 14
        val totalsLeft = MARGIN + width * 0.52f
        val lineHeight = 18f
        val dueHeight = 30f
        var totalY = y

        // **One card, not a box and then a card.** The summary lines and the
        // figure they add up to are one thought, and drawing a hairline
        // rectangle round the lines and a tinted panel under them made two — the
        // shape that had this page reading as a different design from the one it
        // was meant to be.
        val cardHeight = document.summaryRows.size * lineHeight + dueHeight
        canvas.drawRect(totalsLeft, totalY, right, totalY + cardHeight, accentSoft)
        canvas.drawRect(totalsLeft, totalY, totalsLeft + 3.5f, totalY + cardHeight, accent)

        val labelInset = 12f
        for (row in document.summaryRows) {
            canvas.drawText(row.label, totalsLeft + labelInset, totalY + 12, paint(BODY_SIZE, grey = true))
            canvas.drawTextRight(row.value.bracketed(row.deduction), right - 9, totalY + 12, paint(BODY_SIZE))
            totalY += lineHeight
        }

        // The one figure the reader came for, closing the card it already sits
        // in, over a hairline.
        canvas.drawLine(totalsLeft + labelInset, totalY, right - 9, totalY, rule)
        canvas.drawText(document.closingLabel.uppercase(), totalsLeft + labelInset, totalY + 18, accentText(7.5f, bold = true))
        canvas.drawTextRight(document.closingValue, right - 9, totalY + 21, accentText(15f, bold = true))

        // --- Footer: the address, and which page this is

        val footY = PAGE_HEIGHT - MARGIN + 4
        canvas.drawLine(MARGIN, footY - 14, right, footY - 14, rule)
        canvas.drawText(document.partyName, MARGIN, footY, paint(7.5f, grey = true))
        canvas.drawTextRight("$pageNumber", right, footY, paint(7.5f, grey = true))

        pdf.finishPage(page)
        return pageNumber
    }

    /**
     * `(SAR 530.00)` — accounting brackets, the convention the shop's own
     * supplier statements use. A bare minus in front of a currency symbol reads
     * as a typo on paper.
     */
    private fun String.bracketed(deduction: Boolean): String = if (deduction) "($this)" else this


    /** `Muhammad Al Q…` — a name cut to the room it has, rather than over the figure. */
    private fun Paint.ellipsised(text: String, maxWidth: Float): String {
        if (measureText(text) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && measureText("$cut…") > maxWidth) cut = cut.dropLast(1)
        return "$cut…"
    }

    private fun Canvas.drawTextRight(text: String, rightEdge: Float, y: Float, paint: Paint) {
        drawText(text, rightEdge - paint.measureText(text), y, paint)
    }
}
