package com.stockbook.core.text

import com.stockbook.core.model.Bill
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Settings
import com.stockbook.core.money.Money

/**
 * A bill laid out as a printable document: every label and every figure, already
 * worded and already formatted.
 *
 * The paper the customer walks out with. It replaced a plain-text bill sent into
 * WhatsApp — text read in the message list without opening anything, which a PDF
 * cannot do, and that was a real thing to give up. What it buys is a document
 * that looks like one: the shop's letterhead, the arithmetic laid out in columns,
 * and a page that prints and files rather than one that scrolls away up a chat.
 *
 * Shared with the iOS build and tested here for the reason every other document
 * in this app is: drawing is platform work, deciding what is drawn is not, and
 * two hand-written layouts drift the first time either is corrected.
 *
 * Every figure is the **snapshot taken at sale time**. A product renamed or
 * repriced since does not change what this says.
 */
data class BillDocument(
    val shopName: String,
    val shopAddressLines: List<String>,
    /** Set against the letterhead: what this piece of paper is. */
    val docType: String,
    /**
     * `Invoice #6356`, or `Bill #7` where the shop wrote no number of its own.
     *
     * Straight from [Bill.reference] — one number, never both. Two numbers on a
     * document is how somebody reads out the wrong one over the phone.
     */
    val reference: String,
    val addressedToLabel: String,
    val partyName: String,
    val partyLines: List<String>,
    val dateLabel: String,
    val dateValue: String,
    /**
     * What was sold, where the bill says. **Empty is the ordinary case** for a
     * shop copying a paper bill it has already written: the total is known, and
     * rebuilding it product by product to arrive at it is work for nothing.
     */
    val lines: List<Line>,
    /**
     * Subtotal and discount, drawn only where a discount was given.
     *
     * The customer's own copy is exactly where a discount belongs — it is the
     * reason the figure is what it is, and a shop that gave ten per cent away
     * should get the credit for it. The *statement* is the document that carries
     * only the total.
     */
    val summaryRows: List<StatementDocument.Row>,
    val totalLabel: String,
    val totalValue: String,
    /** Settled at the counter, or what is left and who owes it. */
    val paymentNote: String
) {

    /** One line: what it was, the arithmetic behind it, and what it came to. */
    data class Line(val name: String, val detail: String, val amount: String)

    val isItemised: Boolean get() = lines.isNotEmpty()

    companion object {

        fun make(
            bill: Bill,
            settings: Settings,
            strings: Strings,
            /**
             * The customer as the roster knows them, when they are on it.
             *
             * `Bill.who` is the name typed at the counter and is all a bill
             * carries; the place and phone live on the roster. Optional, because
             * a bill can name somebody the roster has never heard of.
             */
            customer: Customer? = null,
            currency: Currency = settings.currency
        ): BillDocument = BillDocument(
            shopName = settings.ownerName,
            shopAddressLines = settings.shopAddress
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            docType = strings.billDocType,
            reference = bill.reference(strings),
            addressedToLabel = strings.billedToLabel,
            partyName = bill.who,
            partyLines = listOfNotNull(customer?.place, customer?.phone)
                .filter { it.isNotBlank() },
            dateLabel = strings.billDate,
            dateValue = strings.billWhen(strings.longDate(bill.createdAt), strings.time(bill.createdAt)),
            lines = bill.lines.map {
                Line(
                    name = it.name,
                    // The arithmetic stays visible, as it does on screen: a query
                    // about a total is nearly always a query about one line's
                    // quantity or price, and this is the answer without anybody
                    // recomputing it.
                    detail = strings.quantityAtPrice(it.qty, Money.text(it.price, currency)),
                    amount = Money.text(it.lineTotal, currency)
                )
            },
            summaryRows = if (!bill.isDiscounted) {
                emptyList()
            } else {
                listOf(
                    StatementDocument.Row(
                        label = strings.subtotalLabel,
                        value = Money.text(bill.subtotal, currency)
                    ),
                    StatementDocument.Row(
                        label = strings.discountOf(Money.amount(bill.discountPercent ?: 0.0, currency)),
                        value = Money.text(bill.discountAmount ?: 0.0, currency),
                        deduction = true
                    )
                )
            },
            totalLabel = strings.total,
            totalValue = Money.text(bill.total, currency),
            paymentNote = bill.paid?.let {
                strings.partPaidNote(
                    paid = Money.text(it, currency),
                    who = bill.who,
                    balance = Money.text(bill.balance, currency)
                )
            } ?: strings.paidInFullCash
        )
    }
}
