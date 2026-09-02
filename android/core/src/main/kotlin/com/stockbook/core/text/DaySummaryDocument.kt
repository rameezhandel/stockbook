package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.model.Settings
import com.stockbook.core.money.Money
import com.stockbook.core.store.DayBook
import com.stockbook.core.store.DayEntry
import com.stockbook.core.store.DayEntryKind

/**
 * One day of the shop on one page: what was sold, what came in against it, what
 * arrived, what went out, and what the cash box did about all of it.
 *
 * **The owner's own page, and never anybody else's.** It names every customer
 * billed that day beside what the shop spent its money on — Ahmed can no more be
 * shown this than he can be shown the receivable list, and for both of the same
 * reasons. It is not called a statement anywhere, because a statement is one
 * party's account and this is the whole counter's.
 *
 * Sections rather than one long list, because the page is read to be reconciled:
 * an owner holding it beside the drawer wants the takings added up, not
 * interleaved with the day's petrol. A section with nothing in it is left out
 * entirely — a heading over no rows is a question the reader has to answer for
 * themselves.
 *
 * Laid out here for the reason [StatementDocument] and [SummaryDocument] are:
 * drawing is platform work, deciding what is drawn is not, and two hand-written
 * layouts drift the first time either is corrected.
 */
data class DaySummaryDocument(
    val shopName: String,
    /** What this is. Says *summary*, never *statement*. */
    val title: String,
    /** `22 August 2026` — the one day the page covers. */
    val onDate: String,
    val sections: List<Section>,
    /**
     * Money in, money out, and the difference. Empty on a day with nothing on
     * it, so the page never states a cash position for a day it has no figures
     * for.
     */
    val cash: List<Line>,
    /** Shown instead of everything when the day is blank. */
    val emptyLine: String
) {
    /** One kind of thing that happened, and what all of it came to. */
    data class Section(
        val heading: String,
        val rows: List<Row>,
        val subtotalLabel: String,
        val subtotalValue: String
    )

    /**
     * One record: who it was with, what it came to, and — where the record says
     * — what was on it.
     *
     * [detail] is the small grey aside: the number on the paper, and on a bill
     * or a delivery that was not settled, what is still owed on it. That second
     * part is the difference between a page that says three hundred was sold and
     * one that says three hundred was sold and two hundred of it is still out.
     */
    data class Row(
        val name: String,
        val detail: String?,
        val amount: String,
        /**
         * Where that account stood when the day closed, said on the line under
         * the row.
         *
         * Null where there is no account — an expense is joined to nobody, and a
         * counter sale with no name typed on it has nothing to be a balance of.
         * A line reading "Closing balance —" would invite the reader to
         * wonder whose.
         *
         * Repeated on every row a person appears on, deliberately. Three bills
         * to one customer are three records of what was sold and one answer to
         * what they owe, and a figure printed only against the last of them is a
         * figure found by whoever happens to read that far.
         */
        val balance: Balance? = null,
        val items: List<Item> = emptyList()
    )

    /** The labelled figure under a row: what the account came to that day. */
    data class Balance(val label: String, val value: String)

    /** A product under its row: `3 × Padlock 40mm`, and what that line came to. */
    data class Item(val text: String, val amount: String)

    /** A labelled figure at the foot. [isNet] is the one the eye should stop on. */
    data class Line(val label: String, val value: String, val isNet: Boolean = false)

    val isEmpty: Boolean get() = sections.isEmpty()

    companion object {

        /**
         * The order the day is read in: what was sold, what was taken against
         * it, what was credited back, then the money going the other way.
         *
         * Written out rather than taken from the enum's own order, so changing
         * how the page reads is a change here and not a change to a type six
         * other things depend on.
         */
        private val ORDER = listOf(
            DayEntryKind.BILL,
            DayEntryKind.PAYMENT,
            DayEntryKind.CREDIT_NOTE,
            DayEntryKind.DELIVERY,
            DayEntryKind.SUPPLIER_PAYMENT,
            DayEntryKind.EXPENSE
        )

        fun forDay(
            book: DayBook,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): DaySummaryDocument {
            val sections = ORDER.mapNotNull { kind ->
                val entries = book.entriesOf(kind)
                if (entries.isEmpty()) return@mapNotNull null
                Section(
                    heading = heading(kind, strings),
                    rows = entries.map { row(it, strings, currency) },
                    subtotalLabel = strings.subtotalLabel,
                    // What the section is about, which is what the things came
                    // to — not what was paid for them. The cash foot is where
                    // that question is answered, once, for the whole day.
                    subtotalValue = Money.text(entries.sumOf { it.amount }, currency)
                )
            }

            return DaySummaryDocument(
                shopName = settings.ownerName,
                title = strings.daySummary,
                onDate = strings.longDate(book.day),
                sections = sections,
                cash = if (sections.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        Line(strings.moneyInLabel, Money.text(book.moneyIn, currency)),
                        Line(strings.moneyOutLabel, Money.text(book.moneyOut, currency)),
                        // The one figure on the page that can go either way — a
                        // shop that restocked in the morning is down at closing
                        // time — so it is the one that carries a sign.
                        Line(strings.netForTheDay, Money.signed(book.net, currency), isNet = true)
                    )
                },
                emptyLine = strings.nothingOnThisDay
            )
        }

        private fun heading(kind: DayEntryKind, strings: Strings): String = when (kind) {
            DayEntryKind.BILL -> strings.billsTitle
            DayEntryKind.PAYMENT -> strings.receivedInPeriod
            DayEntryKind.CREDIT_NOTE -> strings.creditNotes
            DayEntryKind.DELIVERY -> strings.deliveriesTitle
            DayEntryKind.SUPPLIER_PAYMENT -> strings.paidToSuppliers
            DayEntryKind.EXPENSE -> strings.expensesTitle
        }

        private fun row(entry: DayEntry, strings: Strings, currency: Currency): Row {
            val outstanding = entry.amount - entry.settled
            val detail = listOfNotNull(
                reference(entry, strings),
                // Only where money is still owed on the thing itself. A credit
                // note settles nothing by design and would otherwise carry this
                // on every row, saying "on credit" about money that was never
                // going to be paid.
                if (entry.kind.carriesCredit && outstanding > 0) {
                    strings.onCreditAmount(Money.text(outstanding, currency))
                } else {
                    null
                }
            ).joinToString(" · ").takeIf { it.isNotEmpty() }

            return Row(
                name = entry.who,
                detail = detail,
                amount = Money.text(entry.amount, currency),
                balance = entry.closingBalance?.let {
                    Balance(strings.dayClosingBalance, Money.text(it, currency))
                },
                items = entry.items.map {
                    Item(
                        text = strings.itemLine(it.qty, it.name),
                        amount = Money.text(it.amount, currency)
                    )
                }
            )
        }

        /**
         * Whether "still owed" is a thing this kind can be.
         *
         * A bill and a delivery are the two records that can be part settled; a
         * payment or an expense is the settling.
         */
        private val DayEntryKind.carriesCredit: Boolean
            get() = when (this) {
                DayEntryKind.BILL, DayEntryKind.DELIVERY -> true
                DayEntryKind.PAYMENT, DayEntryKind.SUPPLIER_PAYMENT,
                DayEntryKind.CREDIT_NOTE, DayEntryKind.EXPENSE -> false
            }

        /**
         * What to call the paper, worded exactly as [StatementDocument] words it.
         *
         * The same delivery appearing as "Delivery #88" on one page and
         * "Purchase 88" on another is the owner checking whether they are the
         * same delivery, which is work this page exists to remove.
         */
        private fun reference(entry: DayEntry, strings: Strings): String? {
            val no = entry.reference?.takeIf { it.isNotBlank() }
            return when (entry.kind) {
                DayEntryKind.BILL ->
                    no?.let { strings.invoiceRef(it) } ?: entry.billNumber?.let { strings.billNumber(it) }
                DayEntryKind.PAYMENT, DayEntryKind.SUPPLIER_PAYMENT ->
                    no?.let { strings.paymentRef(it) } ?: strings.paymentLabel
                DayEntryKind.CREDIT_NOTE ->
                    no?.let { strings.creditNoteRef(it) } ?: strings.creditNoteLabel
                DayEntryKind.DELIVERY ->
                    no?.let { strings.deliveryRef(it) } ?: strings.purchaseLabel
                // Joined to nobody and numbered by nobody. The row's name is
                // already what it went on, and there is nothing else to say.
                DayEntryKind.EXPENSE -> null
            }
        }
    }
}
