package com.stockbook.core.text

import com.stockbook.core.model.Currency
import com.stockbook.core.model.PaymentReceipt
import com.stockbook.core.model.Settings
import com.stockbook.core.money.Money

/**
 * A receipt laid out as a printable document: every label and every figure,
 * already worded and already formatted.
 *
 * The half-page the shop tears off and hands over when somebody settles up.
 * Where [StatementDocument] answers "what has this account done", this answers
 * one question — *did you get my money* — and it answers it in one figure, set
 * large enough to be read across a counter.
 *
 * Shared with the iOS build and tested here for the reason every other document
 * in this app is: drawing is platform work, deciding what is drawn is not, and
 * two hand-written layouts drift the first time either is corrected.
 *
 * **The summary is three lines and always three lines**, unlike the statement's,
 * which leaves out what did not happen. A receipt with the previous balance
 * missing is a receipt somebody has to fetch a statement to understand, and the
 * whole point of it is that it stands alone.
 */
data class PaymentReceiptDocument(
    val shopName: String,
    val shopAddressLines: List<String>,
    /** Set against the letterhead: what this piece of paper is. */
    val docType: String,
    /** "Received from" going one way, "Paid to" going the other. */
    val addressedToLabel: String,
    val partyName: String,
    val partyLines: List<String>,
    /** The two boxed facts: which slip this is, and the day it was written. */
    val receiptLabel: String,
    val receiptValue: String,
    val dateLabel: String,
    val dateValue: String,
    /** The figure the page exists to state, and the only large thing on it. */
    val amountLabel: String,
    val amountValue: String,
    /**
     * The owner's own note — "cheque 4471", "part settlement" — drawn only where
     * there is one. A labelled empty line invites the reader to wonder what was
     * left out.
     */
    val noteLabel: String?,
    val noteValue: String?,
    val summaryTitle: String,
    /** Previous balance, then this receipt coming off it. */
    val summaryRows: List<StatementDocument.Row>,
    /** Where the account stands now, set apart from the two lines above it. */
    val closingLabel: String,
    val closingValue: String,
    /**
     * The one thing a customer might otherwise get wrong: this settles the
     * account, not an invoice. Said on the paper because it is not said anywhere
     * else the customer can see.
     */
    val footnote: String
) {

    companion object {

        /**
         * Where a record carries no number of its own — a payment entered before
         * the receipt field existed — the box says so rather than standing empty.
         * An empty box on a numbered document reads as a printing fault.
         */
        private const val NO_NUMBER = "—"

        fun make(
            receipt: PaymentReceipt,
            settings: Settings,
            strings: Strings,
            currency: Currency = settings.currency
        ): PaymentReceiptDocument {
            val isSupplier = receipt.party.isSupplier
            return PaymentReceiptDocument(
                shopName = settings.ownerName,
                shopAddressLines = settings.shopAddress
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                // A shop paying its own supplier is not receiving anything, and
                // handing that supplier a page headed "Payment Receipt" would
                // have it read from the wrong end.
                docType = if (isSupplier) strings.paymentVoucher else strings.paymentReceipt,
                addressedToLabel = if (isSupplier) strings.paidTo else strings.receivedFrom,
                partyName = receipt.party.name,
                partyLines = listOfNotNull(receipt.party.place, receipt.party.phone)
                    .filter { it.isNotBlank() },
                receiptLabel = strings.paymentNoField,
                receiptValue = receipt.paymentNo?.takeIf { it.isNotBlank() } ?: NO_NUMBER,
                dateLabel = if (isSupplier) strings.paidOn else strings.receivedOn,
                dateValue = strings.longDate(receipt.at),
                amountLabel = if (isSupplier) strings.amountPaid else strings.amountReceived,
                amountValue = Money.text(receipt.amount, currency),
                noteLabel = receipt.note?.takeIf { it.isNotBlank() }?.let { strings.paymentNote },
                noteValue = receipt.note?.takeIf { it.isNotBlank() },
                summaryTitle = strings.accountAfterThisReceipt,
                summaryRows = listOf(
                    StatementDocument.Row(
                        label = strings.previousBalance,
                        value = Money.text(receipt.balanceBefore, currency)
                    ),
                    // Bracketed, as the statement brackets what comes off an
                    // account: this is the one line on the page that reduces
                    // the figure under it.
                    StatementDocument.Row(
                        label = if (isSupplier) strings.amountPaid else strings.amountReceived,
                        value = Money.text(receipt.amount, currency),
                        deduction = true
                    )
                ),
                closingLabel = strings.balanceNow,
                closingValue = Money.text(receipt.balanceAfter, currency),
                footnote = if (isSupplier) {
                    strings.paymentNotAgainstOnePurchase
                } else {
                    strings.paymentNotAgainstOneBill
                }
            )
        }
    }
}
