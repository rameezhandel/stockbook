package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.model.Settings
import com.stockbook.core.model.Supplier
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
         * Who owes the shop, from `StockbookStore.customers()`.
         *
         * @param customers **biggest debt first**, which is the order this
         *   document wants and the order the on-screen list already shows.
         *   Sorting again here would be a second opinion about which is right.
         */
        fun forReceivable(
            customers: List<Customer>,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency,
            now: Instant = Timestamps.now()
        ): OutstandingDocument = make(
            parties = customers.map { it.name to it.owed },
            settings = settings,
            strings = strings,
            title = strings.receivableSummary,
            partyHeading = strings.columnCustomer,
            amountHeading = strings.receivableStat,
            totalLabel = strings.totalReceivable,
            emptyLine = strings.nothingReceivable,
            currency = currency,
            now = now
        )

        /**
         * Who the shop owes, from `StockbookStore.suppliers()`.
         *
         * The same page pointed the other way, and every word on it flips with
         * it. A payable list headed "Receivable" would be the most expensive
         * kind of wrong: one the owner acts on.
         */
        fun forPayable(
            suppliers: List<Supplier>,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency,
            now: Instant = Timestamps.now()
        ): OutstandingDocument = make(
            parties = suppliers.map { it.name to it.owed },
            settings = settings,
            strings = strings,
            title = strings.payableSummary,
            partyHeading = strings.supplier,
            amountHeading = strings.payableStat,
            totalLabel = strings.totalPayable,
            emptyLine = strings.nothingPayable,
            currency = currency,
            now = now
        )

        /**
         * The page itself, which does not care which way the money points.
         *
         * `Customer` and `Supplier` are separate types with the same two fields
         * that matter here, so they arrive already reduced to a name and a
         * figure rather than behind an interface neither of them asked for.
         */
        private fun make(
            parties: List<Pair<String, Double>>,
            settings: Settings,
            strings: Strings,
            title: String,
            partyHeading: String,
            amountHeading: String,
            totalLabel: String,
            emptyLine: String,
            currency: Currency,
            now: Instant
        ): OutstandingDocument {
            // Only what is actually outstanding. Somebody in advance is not a
            // debtor, and a negative row on a chasing list is a line the owner
            // has to stop and think about every time they read it.
            val owing = parties.filter { it.second > 0 }

            return OutstandingDocument(
                shopName = settings.ownerName,
                title = title,
                asOf = strings.asOfDate(strings.longDate(now)),
                columnHeadings = listOf(partyHeading, amountHeading),
                rows = owing.map { Row(it.first, Money.text(it.second, currency)) },
                totalLabel = totalLabel,
                // Summed from the same figures the rows print, so the foot of the
                // page can never disagree with the page. `outstanding()` and
                // `payable()` walk the same rosters to the same answers, and
                // `OutstandingDocumentTests` pins them together.
                totalValue = Money.text(owing.sumOf { it.second }, currency),
                emptyLine = emptyLine
            )
        }
    }
}
