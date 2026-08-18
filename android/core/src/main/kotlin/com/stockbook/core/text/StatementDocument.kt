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
    /** Top right: who it is for. */
    val addressedToLabel: String,
    val partyName: String,
    val partyLines: List<String>,
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

    /** One line of the activity table: what, when, how much, and where it left the account. */
    data class ActivityRow(
        val date: String,
        val transaction: String,
        val amount: String,
        val balance: String,
        val deduction: Boolean = false
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
            }

            return StatementDocument(
                shopName = settings.ownerName,
                shopAddressLines = settings.shopAddress.lines().map { it.trim() }.filter { it.isNotEmpty() },
                addressedToLabel = strings.accountStatementFor,
                partyName = statement.party.name,
                partyLines = listOfNotNull(
                    statement.party.place?.takeIf { it.isNotBlank() },
                    statement.party.phone?.takeIf { it.isNotBlank() }
                ),
                summaryTitle = strings.accountSummaryTill(strings.longDate(statement.range.asOf(now))),
                summaryRows = summary,
                activityTitle = strings.accountActivity,
                columnHeadings = listOf(
                    strings.columnDate,
                    strings.columnTransaction,
                    strings.columnAmount,
                    strings.columnBalance
                ),
                activityRows = statement.entries.mapIndexed { index, entry ->
                    val settles = entry.charge == 0.0
                    ActivityRow(
                        date = strings.shortDate(entry.date),
                        transaction = reference(entry, strings),
                        amount = Money.text(
                            if (settles) entry.settledAtOnce else entry.charge,
                            currency
                        ),
                        balance = Money.text(statement.runningBalances[index], currency),
                        deduction = settles
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
                    ?.let { strings.deliveryRef(it) }
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
        }
    }
}
