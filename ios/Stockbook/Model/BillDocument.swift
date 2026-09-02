import Foundation

/// A bill laid out as a printable document: every label and every figure,
/// already worded and already formatted.
///
/// The paper the customer walks out with. It replaced a plain-text bill sent
/// into WhatsApp — text read in the message list without opening anything, which
/// a PDF cannot do, and that was a real thing to give up. What it buys is a
/// document that looks like one: the shop's letterhead, the arithmetic laid out
/// in columns, and a page that prints and files rather than one that scrolls
/// away up a chat.
///
/// Shared with the Android build and tested there for the reason every other
/// document in this app is: drawing is platform work, deciding what is drawn is
/// not, and two hand-written layouts drift the first time either is corrected.
///
/// Every figure is the **snapshot taken at sale time**. A product renamed or
/// repriced since does not change what this says.
struct BillDocument: Equatable {
    let shopName: String
    let shopAddressLines: [String]
    /// Set against the letterhead: what this piece of paper is.
    let docType: String
    /// `Invoice #6356`, or `Bill #7` where the shop wrote no number of its own.
    ///
    /// Straight from `Bill.reference` — one number, never both. Two numbers on a
    /// document is how somebody reads out the wrong one over the phone.
    let reference: String
    let addressedToLabel: String
    let partyName: String
    let partyLines: [String]
    let dateLabel: String
    let dateValue: String
    /// What was sold, where the bill says. **Empty is the ordinary case** for a
    /// shop copying a paper bill it has already written: the total is known, and
    /// rebuilding it product by product to arrive at it is work for nothing.
    let lines: [Line]
    /// Subtotal and discount, drawn only where a discount was given.
    ///
    /// The customer's own copy is exactly where a discount belongs — it is the
    /// reason the figure is what it is, and a shop that gave ten per cent away
    /// should get the credit for it. The *statement* is the document that
    /// carries only the total.
    let summaryRows: [StatementDocument.Row]
    let totalLabel: String
    let totalValue: String
    /// Settled at the counter, or what is left and who owes it.
    let paymentNote: String

    /// One line: what it was, the arithmetic behind it, and what it came to.
    struct Line: Equatable {
        let name: String
        let detail: String
        let amount: String
    }

    var isItemised: Bool { !lines.isEmpty }

    static func make(
        bill: Bill,
        settings: Settings,
        strings: Strings,
        /// The customer as the roster knows them, when they are on it.
        ///
        /// `Bill.who` is the name typed at the counter and is all a bill
        /// carries; the place and phone live on the roster. Optional, because a
        /// bill can name somebody the roster has never heard of.
        customer: Customer? = nil,
        currency: Currency? = nil
    ) -> BillDocument {
        let money = currency ?? settings.currency

        return BillDocument(
            shopName: settings.ownerName,
            shopAddressLines: settings.shopAddress
                .split(separator: "\n", omittingEmptySubsequences: false)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty },
            docType: strings.billDocType,
            reference: bill.reference(strings),
            addressedToLabel: strings.billedToLabel,
            partyName: bill.who,
            partyLines: [customer?.place, customer?.phone]
                .compactMap { $0 }
                .filter { !$0.isBlank },
            dateLabel: strings.billDate,
            dateValue: strings.billWhen(
                date: strings.longDate(bill.createdAt),
                time: strings.time(bill.createdAt)
            ),
            lines: bill.lines.map {
                Line(
                    name: $0.name,
                    // The arithmetic stays visible, as it does on screen: a query
                    // about a total is nearly always a query about one line's
                    // quantity or price, and this is the answer without anybody
                    // recomputing it.
                    detail: strings.quantityAtPrice($0.qty, Money.text($0.price, in: money)),
                    amount: Money.text($0.lineTotal, in: money)
                )
            },
            summaryRows: bill.isDiscounted
                ? [
                    StatementDocument.Row(
                        label: strings.subtotalLabel,
                        value: Money.text(bill.subtotal, in: money)
                    ),
                    StatementDocument.Row(
                        label: strings.discountOf(Money.amount(bill.discountPercent ?? 0, in: money)),
                        value: Money.text(bill.discountAmount ?? 0, in: money),
                        deduction: true
                    )
                ]
                : [],
            totalLabel: strings.total,
            totalValue: Money.text(bill.total, in: money),
            paymentNote: bill.paid.map {
                strings.partPaidNote(
                    paid: Money.text($0, in: money),
                    who: bill.who,
                    balance: Money.text(bill.balance, in: money)
                )
            } ?? strings.paidInFullCash
        )
    }
}
