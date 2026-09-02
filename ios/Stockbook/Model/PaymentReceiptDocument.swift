import Foundation

/// A receipt laid out as a printable document: every label and every figure,
/// already worded and already formatted.
///
/// The half-page the shop tears off and hands over when somebody settles up.
/// Where `StatementDocument` answers "what has this account done", this answers
/// one question — *did you get my money* — and it answers it in one figure, set
/// large enough to be read across a counter.
///
/// Shared with the Android build and tested there for the reason every other
/// document in this app is: drawing is platform work, deciding what is drawn is
/// not, and two hand-written layouts drift the first time either is corrected.
///
/// **The summary is always the same three figures**, unlike the statement's,
/// which leaves out what did not happen. A receipt with the previous balance
/// missing is a receipt somebody has to fetch a statement to understand, and the
/// whole point of it is that it stands alone.
struct PaymentReceiptDocument: Equatable {
    let shopName: String
    let shopAddressLines: [String]
    /// Set against the letterhead: what this piece of paper is.
    let docType: String
    /// "Received from" going one way, "Paid to" going the other.
    let addressedToLabel: String
    let partyName: String
    let partyLines: [String]
    /// The two boxed facts: which slip this is, and the day it was written.
    let receiptLabel: String
    let receiptValue: String
    let dateLabel: String
    let dateValue: String
    /// The figure the page exists to state, and the only large thing on it.
    let amountLabel: String
    let amountValue: String
    /// The owner's own note — "cheque 4471", "part settlement" — drawn only
    /// where there is one. A labelled empty line invites the reader to wonder
    /// what was left out.
    let noteLabel: String?
    let noteValue: String?
    let summaryTitle: String
    /// Previous balance, then this receipt coming off it.
    let summaryRows: [StatementDocument.Row]
    /// Where the account stands now, set apart from the two lines above it.
    let closingLabel: String
    let closingValue: String
    /// The one thing a customer might otherwise get wrong: this settles the
    /// account, not an invoice. Said on the paper because it is not said
    /// anywhere else the customer can see.
    let footnote: String

    /// Where a record carries no number of its own — a payment entered before
    /// the receipt field existed — the box says so rather than standing empty.
    /// An empty box on a numbered document reads as a printing fault.
    private static let noNumber = "—"

    static func make(
        receipt: PaymentReceipt,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
    ) -> PaymentReceiptDocument {
        let money = currency ?? settings.currency
        let isSupplier = receipt.party.isSupplier
        let number = receipt.paymentNo.flatMap { $0.isBlank ? nil : $0 }
        let note = receipt.note.flatMap { $0.isBlank ? nil : $0 }

        return PaymentReceiptDocument(
            shopName: settings.ownerName,
            shopAddressLines: settings.shopAddress
                .split(separator: "\n", omittingEmptySubsequences: false)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty },
            // A shop paying its own supplier is not receiving anything, and
            // handing that supplier a page headed "Payment Receipt" would have
            // it read from the wrong end.
            docType: isSupplier ? strings.paymentVoucher : strings.paymentReceipt,
            addressedToLabel: isSupplier ? strings.paidTo : strings.receivedFrom,
            partyName: receipt.party.name,
            partyLines: [receipt.party.place, receipt.party.phone]
                .compactMap { $0 }
                .filter { !$0.isBlank },
            receiptLabel: strings.paymentNoField,
            receiptValue: number ?? noNumber,
            dateLabel: isSupplier ? strings.paidOn : strings.receivedOn,
            dateValue: strings.longDate(receipt.at),
            amountLabel: isSupplier ? strings.amountPaid : strings.amountReceived,
            amountValue: Money.text(receipt.amount, in: money),
            noteLabel: note == nil ? nil : strings.paymentNote,
            noteValue: note,
            summaryTitle: strings.accountAfterThisReceipt,
            summaryRows: [
                StatementDocument.Row(
                    label: strings.previousBalance,
                    value: Money.text(receipt.balanceBefore, in: money)
                ),
                // Bracketed, as the statement brackets what comes off an
                // account: this is the one line on the page that reduces the
                // figure under it.
                StatementDocument.Row(
                    label: isSupplier ? strings.amountPaid : strings.amountReceived,
                    value: Money.text(receipt.amount, in: money),
                    deduction: true
                )
            ],
            closingLabel: strings.balanceNow,
            closingValue: Money.text(receipt.balanceAfter, in: money),
            footnote: isSupplier
                ? strings.paymentNotAgainstOnePurchase
                : strings.paymentNotAgainstOneBill
        )
    }
}
