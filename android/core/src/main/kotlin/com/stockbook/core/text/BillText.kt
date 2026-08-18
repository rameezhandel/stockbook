package com.stockbook.core.text

import com.stockbook.core.model.Bill
import com.stockbook.core.model.Currency
import com.stockbook.core.money.Money

/**
 * A bill as something you can send somebody.
 *
 * Plain text, for the same reason a statement is plain text: it goes into
 * WhatsApp, which every customer here already has, and it survives being pasted
 * anywhere. A PDF would look more like a document and be worse at being one —
 * it needs an app to open, a file to keep, and it cannot be read in the message
 * list.
 *
 * The same figures the screen shows, in the same order, taken from the same
 * snapshot: what a customer is sent must not be able to disagree with what the
 * owner is looking at.
 */
object BillText {

    fun plainText(bill: Bill, shopName: String, currency: Currency, strings: Strings): String {
        val lines = mutableListOf<String>()

        if (shopName.isNotBlank()) lines.add(shopName)
        lines.add(bill.reference(strings))
        lines.add(strings.billWhen(strings.longDate(bill.createdAt), strings.time(bill.createdAt)))
        if (bill.who.isNotBlank()) lines.add(strings.billedTo(bill.who))
        lines.add("")

        for (line in bill.lines) {
            // The arithmetic stays visible, as it does on the document: a query
            // about a total is nearly always a query about one line.
            lines.add(
                "${line.name}  ${strings.quantityAtPrice(line.qty, Money.text(line.price, currency))}  " +
                    Money.text(line.lineTotal, currency)
            )
        }

        lines.add("")
        lines.add("${strings.total}: ${Money.text(bill.total, currency)}")
        lines.add(
            when {
                bill.paid == null -> strings.paidInFullCash
                else -> strings.partPaidNote(
                    Money.text(bill.paid, currency),
                    bill.who,
                    Money.text(bill.balance, currency)
                )
            }
        )

        return lines.joinToString("\n")
    }
}
