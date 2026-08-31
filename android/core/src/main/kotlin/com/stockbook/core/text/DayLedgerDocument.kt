package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.model.DayLedger
import com.stockbook.core.model.Settings
import com.stockbook.core.money.Money

/**
 * Every customer's position on one day, laid out for paper.
 *
 * The page the owner prints at the close of a day and reads down beside the
 * paper book: a name, what they were invoiced, what they paid, and what they
 * owed either side of it.
 *
 * **The owner's own page.** Every customer's balance is on it, so it can no more
 * be handed across the counter than the receivable list can — the same rule
 * [DaySummaryDocument] carries, for the same reason. A customer is shown their
 * own statement and nobody else's.
 *
 * Laid out here rather than in either UI, for the reason [StatementDocument] is:
 * drawing is platform work, deciding what is drawn is not, and two hand-written
 * layouts drift the first time either is corrected.
 */
data class DayLedgerDocument(
    val shopName: String,
    val title: String,
    val onDate: String,
    /**
     * Said only on a page that was narrowed before it was printed.
     *
     * A printed roll-call and a printed selection look identical once they are
     * on paper, and the totals differ. The page has to say which it is, or the
     * owner files a sheet whose figures do not tie to the shop's own.
     */
    val filterNote: String?,
    /** Name, invoice, received, old, current — in the order they are drawn. */
    val columnHeadings: List<String>,
    val rows: List<Row>,
    val totalLabel: String,
    /** The four money columns added up, in the same order as [columnHeadings]. */
    val totals: List<String>,
    val emptyLine: String
) {
    /**
     * One customer's line.
     *
     * The money is already formatted, and a column with nothing in it is an empty
     * string rather than a zero: an empty cell says "nothing happened here" and a
     * `0.00` is a figure somebody may go looking for.
     */
    data class Row(
        val name: String,
        val invoiced: String,
        val received: String,
        val oldBalance: String,
        val currentBalance: String,
        /**
         * A credit note or a moved balance, spelled out under the name.
         *
         * The five columns cannot hold these and the row would not add up
         * without them, so they are said in words where there is room.
         */
        val note: String?
    )

    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        /**
         * [ledger] is already narrowed to what is being printed — see
         * [DayLedger.movedOnly] — so the totals here are the totals of these
         * rows and cannot disagree with the column above them.
         */
        fun forDay(
            ledger: DayLedger,
            settings: Settings,
            strings: Strings,
            /** Whether [ledger] is the whole roll-call or only what moved. */
            onlyMoved: Boolean = false
        ): DayLedgerDocument {
            val currency = settings.currency
            fun money(amount: Double, always: Boolean = false): String =
                if (!always && amount == 0.0) "" else Money.amount(amount, currency)

            return DayLedgerDocument(
                shopName = settings.ownerName,
                title = strings.dayBalances,
                onDate = strings.longDate(ledger.day),
                filterNote = if (onlyMoved) strings.ledgerMovedOnlyNote else null,
                columnHeadings = listOf(
                    strings.customersTitle,
                    strings.ledgerInvoiced,
                    strings.ledgerReceived,
                    strings.ledgerOldBalance,
                    strings.ledgerCurrentBalance
                ),
                rows = ledger.rows.map { row ->
                    Row(
                        name = row.name,
                        invoiced = money(row.invoiced),
                        received = money(row.received),
                        oldBalance = money(row.openingBalance, always = true),
                        currentBalance = money(row.closingBalance, always = true),
                        note = note(row, strings, currency)
                    )
                },
                totalLabel = strings.ledgerTotal,
                totals = listOf(
                    money(ledger.invoiced),
                    money(ledger.received),
                    money(ledger.openingBalance, always = true),
                    money(ledger.closingBalance, always = true)
                ),
                emptyLine = strings.ledgerNoCustomers
            )
        }

        /** What a five-column table cannot say, said in words. */
        private fun note(row: DayLedger.Row, strings: Strings, currency: Currency): String? {
            val parts = buildList {
                if (row.credited != 0.0) {
                    add("${strings.ledgerCredited} ${Money.text(row.credited, currency)}")
                }
                if (row.transferredIn != 0.0) {
                    add("${strings.ledgerMoved} +${Money.text(row.transferredIn, currency)}")
                }
                if (row.transferredOut != 0.0) {
                    add("${strings.ledgerMoved} −${Money.text(row.transferredOut, currency)}")
                }
            }
            return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
        }
    }
}
