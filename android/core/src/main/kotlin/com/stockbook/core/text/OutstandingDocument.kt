package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Settings
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import java.time.Instant

/**
 * Everyone who owes the shop money, and how much, on one page.
 *
 * **This is the owner's own list, and it is the one document in the app that
 * must never be shown to a customer.** A statement is handed across the counter
 * on purpose — it says what one person owes, to that person. This says what
 * *everybody* owes, and letting Ahmed see that Khalid is four thousand behind is
 * not an untidy page, it is a breach. Nothing renders it beside a customer's own
 * documents, and the title says whose list it is.
 *
 * **A balance, not a period.** Every other document here takes a
 * [com.stockbook.core.model.StatementPeriod]; what is outstanding is true at a
 * moment and meaningless over a span. That is why the header carries the day it
 * was made rather than a range, and it is the whole of what stops a printout
 * from last month reading as this morning's.
 *
 * Laid out here for the reason [StatementDocument] is: drawing is platform work,
 * but *what* is drawn is not, and two hand-written layouts drift the first time
 * either is corrected.
 */
data class OutstandingDocument(
    val shopName: String,
    /** What this is, said so nobody mistakes it for a statement. */
    val title: String,
    /** `As of 22 August 2026` — see the note about balances above. */
    val asOf: String,
    val columnHeadings: List<String>,
    val rows: List<Row>,
    val totalLabel: String,
    val totalValue: String,
    /** Shown instead of the table when nobody owes anything. */
    val emptyLine: String
) {
    /** One debtor: what they are called, and what they are behind by. */
    data class Row(val name: String, val amount: String)

    /** Whether there is a table to draw at all. */
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {

        /**
         * @param customers the roster as [com.stockbook.core.store.StockbookStore.customers]
         *   returns it — **biggest debt first**, which is the order this document
         *   wants and the order the on-screen list already shows. Sorting again
         *   here would be a second opinion about which of the two is right.
         */
        fun make(
            customers: List<Customer>,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency,
            now: Instant = Timestamps.now()
        ): OutstandingDocument {
            // Only what is actually owed. Somebody in advance is not a debtor,
            // and a negative row on a chasing list is a line the owner has to
            // stop and think about every time they read it.
            val owing = customers.filter { it.owed > 0 }

            return OutstandingDocument(
                shopName = settings.ownerName,
                title = strings.moneyOwedToYou,
                asOf = strings.asOfDate(strings.longDate(now)),
                columnHeadings = listOf(strings.columnCustomer, strings.columnOwed),
                rows = owing.map { Row(it.name, Money.text(it.owed, currency)) },
                totalLabel = strings.totalOwedToYou,
                // Summed from the same figures the rows print, so the foot of the
                // page can never disagree with the page. `outstanding()` walks the
                // same roster to the same answer, and `OutstandingDocumentTests`
                // pins the two together.
                totalValue = Money.text(owing.sumOf { it.owed }, currency),
                emptyLine = strings.nobodyOwesYouAnything
            )
        }
    }
}
