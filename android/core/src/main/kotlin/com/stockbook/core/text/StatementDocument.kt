package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.model.Settings
import com.stockbook.core.model.Statement
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import java.time.Instant

/**
 * A statement laid out as a printable document: every label and every figure,
 * already worded and already formatted.
 *
 * This exists so the PDF says the same thing on both phones. Drawing is
 * unavoidably platform work — Core Graphics on one side, a `Canvas` on the
 * other — but *what* is drawn does not have to be, and two hand-written layouts
 * would drift the first time either was corrected. Each platform walks this
 * structure and draws boxes; neither decides what a row is called or what goes
 * in it.
 *
 * It is also the only part of the document that can be checked without a
 * device: the arithmetic behind it is [Statement]'s, and the wording is here.
 */
data class StatementDocument(
    /** Top left: who is sending it. */
    val shopName: String,
    val shopAddressLines: List<String>,
    /** Set against the letterhead, so the page says what it is at a glance. */
    val docType: String,
    /** Top right: who it is for. */
    val addressedToLabel: String,
    val partyName: String,
    val partyLines: List<String>,
    /**
     * The two boxed facts under the letterhead: whose account, and over what.
     *
     * Boxed rather than run into the address block, because these are the two
     * things a reader checks before reading anything else — that it is their
     * account, and that it is the month they were asking about.
     */
    val accountLabel: String,
    val periodLabel: String,
    val periodValue: String,
    /** The boxed summary, in the order it prints. */
    val summaryTitle: String,
    val summaryRows: List<Row>,
    /** The table below it. */
    val activityTitle: String,
    val columnHeadings: List<String>,
    val activityRows: List<ActivityRow>,
    /** The figure the whole document exists to state, repeated under the table. */
    val closingLabel: String,
    val closingValue: String
) {
    /**
     * One line of the summary box.
     *
     * [deduction] is what puts a figure in brackets. Accounting convention, and
     * the one the shop's own supplier statements use: `(SAR 530.00)` reads as
     * money coming off, where a bare minus sign in front of a currency symbol
     * reads as a typo.
     */
    data class Row(val label: String, val value: String, val deduction: Boolean = false)

    /**
     * One line of the activity table: what it was and when, then the money in
     * whichever of the two columns it belongs to, then where the account stood.
     *
     * **Two money columns, filled independently.** What the account was charged
     * goes in [charge] and what came off it goes in [settled]. A payment or a
     * credit note fills only the second; a bill on credit fills only the first —
     * but **a bill paid at the counter fills both**, and it has to. Its charge
     * and its receipt happened in the same moment, so the balance beside it does
     * not move, and a row showing a charge against an unmoved balance with an
     * empty column next to it reads as an arithmetic mistake. It was one: the
     * money taken at the till was simply missing from the page.
     *
     * This is what replaced a single Amount column with brackets round the
     * deductions — the position now says which way the money went, where a
     * bracketed figure needed a convention explained to whoever was reading it.
     *
     * [reference] carries the kind as well as the number — `Invoice #6356`, not
     * `6356` — because a credit note and a payment both land in [settled], and
     * without the word the customer cannot tell which of the two took the
     * money off their account.
     *
     * [date] is its own column rather than trailing the reference. A statement
     * is scanned down its left edge for *when*, and a reader hunting a date
     * inside `Invoice #6356 · 19/05/2026` has to read every row in full to do
     * it — which on a ledger book page is the difference between finding a week
     * and reading a year.
     */
    data class ActivityRow(
        /** `19/05/2026` — the day it happened, on its own. */
        val date: String,
        /** `Invoice #6356` — what it was. */
        val reference: String,
        /** What was charged. Empty on a row that settles. */
        val charge: String,
        /** What came off: a payment, or a credit note. Empty on a charge. */
        val settled: String,
        val balance: String
    )

    companion object {

        fun make(
            statement: Statement,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency,
            now: Instant = Timestamps.now()
        ): StatementDocument {
            val isSupplier = statement.party.isSupplier

            // The summary is only ever as long as it needs to be. A shop that has
            // issued no credit notes should not read a row of zeroes and learn to
            // skip the block — the same rule the on-screen total already follows.
            val summary = buildList {
                add(Row(strings.openingBalance, Money.text(statement.openingBalance, currency)))
                add(
                    Row(
                        if (isSupplier) strings.purchasedInPeriod else strings.billedInPeriod,
                        Money.text(statement.billed, currency)
                    )
                )
                add(
                    Row(
                        if (isSupplier) strings.paidOutInPeriod else strings.receivedInPeriod,
                        Money.text(statement.received, currency),
                        deduction = true
                    )
                )
                if (statement.credited > 0) {
                    add(Row(strings.creditNotes, Money.text(statement.credited, currency), deduction = true))
                }
                // Their own rows, drawn only where there is one — for the reason
                // the credit notes have theirs: a transfer in is not something
                // the shop invoiced and a transfer out is not money it took, so
                // folding either into a trading figure would make that figure
                // mean two things on a document somebody is handed.
                if (statement.transferredIn > 0) {
                    add(Row(strings.transferredInLabel, Money.text(statement.transferredIn, currency)))
                }
                if (statement.transferredOut > 0) {
                    add(
                        Row(
                            strings.transferredOutLabel,
                            Money.text(statement.transferredOut, currency),
                            deduction = true
                        )
                    )
                }
            }

            return StatementDocument(
                shopName = settings.ownerName,
                shopAddressLines = settings.shopAddress.lines().map { it.trim() }.filter { it.isNotEmpty() },
                docType = strings.statementOfAccount,
                addressedToLabel = strings.accountStatementFor,
                partyName = statement.party.name,
                partyLines = listOfNotNull(
                    statement.party.place?.takeIf { it.isNotBlank() },
                    statement.party.phone?.takeIf { it.isNotBlank() }
                ),
                accountLabel = strings.accountLabel,
                periodLabel = strings.statementPeriod,
                periodValue = strings.dateRange(
                    strings.longDate(statement.range.start),
                    strings.longDate(statement.range.asOf(now))
                ),
                summaryTitle = strings.accountSummaryTill(strings.longDate(statement.range.asOf(now))),
                summaryRows = summary,
                activityTitle = strings.accountActivity,
                // Four headings for four columns, and the middle two flip with
                // the direction: money the shop is owed was *received*, money it
                // owes was *paid*, and one pair of words for both would be
                // backwards on one of the two documents.
                columnHeadings = listOf(
                    strings.columnDate,
                    if (isSupplier) strings.columnBillReceipt else strings.columnInvoiceReceipt,
                    if (isSupplier) strings.columnBillAmount else strings.columnInvoiceAmount,
                    if (isSupplier) strings.columnPaidAmount else strings.columnReceivedAmount,
                    strings.columnBalance
                ),
                activityRows = statement.entries.mapIndexed { index, entry ->
                    // Each column asks its own question of the entry rather than
                    // the two sharing one answer. A bill settled at the counter
                    // has both a charge and a receipt, and reading it as one or
                    // the other is what hid every over-the-counter payment.
                    ActivityRow(
                        date = strings.shortDate(entry.date),
                        reference = reference(entry, strings),
                        charge = if (entry.charge > 0) Money.text(entry.charge, currency) else "",
                        settled = if (entry.settledAtOnce > 0) Money.text(entry.settledAtOnce, currency) else "",
                        balance = Money.text(statement.runningBalances[index], currency)
                    )
                },
                closingLabel = strings.balanceDue,
                closingValue = Money.text(statement.closingBalance, currency)
            )
        }

        /**
         * What the Transaction column calls each row: **the kind of document,
         * then its number**.
         *
         * "06011" alone tells somebody checking against their own file nothing
         * about what 06011 *is*, and the books are numbered separately — invoice
         * 130 and credit note 130 are different pieces of paper. Where a record
         * carries no number of its own the type is still named, which is the
         * honest answer rather than a blank cell.
         *
         * Public, and called by the on-screen statement too. The two are read
         * side by side when somebody checks a PDF against the app, and a row
         * named two different ways is a row they reconcile by eye.
         */
        fun reference(entry: Statement.Entry, strings: Strings): String = when (entry) {
            is Statement.Entry.ForBill ->
                entry.bill.invoiceNo?.takeIf { it.isNotBlank() }
                    ?.let { strings.invoiceRef(it) }
                    ?: strings.billNumber(entry.bill.number)
            is Statement.Entry.ForPurchase ->
                entry.purchase.invoiceNo?.takeIf { it.isNotBlank() }
                    ?.let { strings.purchaseRef(it) }
                    ?: strings.purchaseLabel
            is Statement.Entry.ForCreditNote ->
                entry.note.noteNo?.takeIf { it.isNotBlank() }
                    ?.let { strings.creditNoteRef(it) }
                    ?: strings.creditNoteLabel
            is Statement.Entry.ForPayment ->
                entry.payment.paymentNo?.takeIf { it.isNotBlank() }
                    ?.let { strings.paymentRef(it) }
                    ?: strings.paymentLabel
            is Statement.Entry.ForSupplierPayment ->
                entry.payment.paymentNo?.takeIf { it.isNotBlank() }
                    ?.let { strings.paymentRef(it) }
                    ?: strings.paymentLabel
            // No number to show — nothing was written for it — so the row names
            // the account at the other end instead. "Transferred" alone would
            // leave the customer holding a figure they cannot place.
            is Statement.Entry.ForTransfer ->
                if (entry.outgoing) strings.transferredTo(entry.otherName)
                else strings.transferredFrom(entry.otherName)
        }
    }
}
