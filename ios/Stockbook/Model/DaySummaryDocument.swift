import Foundation

/// One day of the shop on one page: what was sold, what came in against it, what
/// arrived, what went out, and what the cash box did about all of it.
///
/// **The owner's own page, and never anybody else's.** It names every customer
/// billed that day beside what the shop spent its money on — Ahmed can no more be
/// shown this than he can be shown the receivable list, and for both of the same
/// reasons. It is not called a statement anywhere, because a statement is one
/// party's account and this is the whole counter's.
///
/// Sections rather than one long list, because the page is read to be reconciled:
/// an owner holding it beside the drawer wants the takings added up, not
/// interleaved with the day's petrol. A section with nothing in it is left out
/// entirely — a heading over no rows is a question the reader has to answer for
/// themselves.
///
/// Laid out here for the reason `StatementDocument` and `SummaryDocument` are:
/// drawing is platform work, deciding what is drawn is not, and two hand-written
/// layouts drift the first time either is corrected. The Kotlin twin is
/// `DaySummaryDocument.kt`.
struct DaySummaryDocument: Equatable {

    let shopName: String
    /// What this is. Says *summary*, never *statement*.
    let title: String
    /// `22 August 2026` — the one day the page covers.
    let onDate: String
    let sections: [Section]
    /// Money in, money out, and the difference. Empty on a day with nothing on
    /// it, so the page never states a cash position for a day it has no figures
    /// for.
    let cash: [Line]
    /// Shown instead of everything when the day is blank.
    let emptyLine: String

    /// One kind of thing that happened, and what all of it came to.
    struct Section: Equatable {
        let heading: String
        let rows: [Row]
        let subtotalLabel: String
        let subtotalValue: String
    }

    /// One record: who it was with, what it came to, and — where the record says
    /// — what was on it.
    ///
    /// `detail` is the small grey aside: the number on the paper, and on a bill
    /// or a delivery that was not settled, what is still owed on it. That second
    /// part is the difference between a page that says three hundred was sold and
    /// one that says three hundred was sold and two hundred of it is still out.
    struct Row: Equatable {
        let name: String
        var detail: String?
        let amount: String
        /// Where that account stood when the day closed, said on the line under
        /// the row.
        ///
        /// Nil where there is no account — an expense is joined to nobody, and a
        /// record restored from an older file with no name on it has nothing to
        /// be a balance of. A line reading "Closing balance —" would invite
        /// the reader to wonder whose.
        ///
        /// Repeated on every row a person appears on, deliberately. Three bills
        /// to one customer are three records of what was sold and one answer to
        /// what they owe, and a figure printed only against the last of them is
        /// a figure found by whoever happens to read that far.
        var balance: Balance?
        var items: [Item] = []
    }

    /// The labelled figure under a row: what the account came to that day.
    struct Balance: Equatable {
        let label: String
        let value: String
    }

    /// A product under its row: `3 × Padlock 40mm`, and what that line came to.
    struct Item: Equatable {
        let text: String
        let amount: String
    }

    /// A labelled figure at the foot. `isNet` is the one the eye should stop on.
    struct Line: Equatable {
        let label: String
        let value: String
        var isNet: Bool = false
    }

    var isEmpty: Bool { sections.isEmpty }

    /// The order the day is read in: what was sold, what was taken against it,
    /// what was credited back, then the money going the other way.
    ///
    /// Written out rather than taken from the enum's own `allCases`, so changing
    /// how the page reads is a change here and not a change to a type six other
    /// things depend on.
    private static let order: [DayEntryKind] = [
        .bill, .payment, .creditNote, .purchase, .supplierPayment, .expense
    ]

    static func forDay(
        book: DayBook,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
    ) -> DaySummaryDocument {
        let money = currency ?? settings.currency

        let sections: [Section] = order.compactMap { kind in
            let entries = book.entries(of: kind)
            if entries.isEmpty { return nil }
            return Section(
                heading: heading(kind, strings),
                rows: entries.map { row($0, strings, money) },
                subtotalLabel: strings.subtotalLabel,
                // What the section is about, which is what the things came to —
                // not what was paid for them. The cash foot is where that
                // question gets answered, once, for the whole day.
                subtotalValue: Money.text(entries.reduce(0) { $0 + $1.amount }, in: money)
            )
        }

        return DaySummaryDocument(
            shopName: settings.ownerName,
            title: strings.daySummary,
            onDate: strings.longDate(book.day),
            sections: sections,
            cash: sections.isEmpty ? [] : [
                Line(label: strings.moneyInLabel, value: Money.text(book.moneyIn, in: money)),
                Line(label: strings.moneyOutLabel, value: Money.text(book.moneyOut, in: money)),
                // The one figure on the page that can go either way — a shop
                // that restocked in the morning is down at closing time — so it
                // is the one that carries a sign.
                Line(label: strings.netForTheDay, value: Money.signed(book.net, in: money), isNet: true)
            ],
            emptyLine: strings.nothingOnThisDay
        )
    }

    private static func heading(_ kind: DayEntryKind, _ strings: Strings) -> String {
        switch kind {
        case .bill: strings.billsTitle
        case .payment: strings.receivedInPeriod
        case .creditNote: strings.creditNotes
        case .purchase: strings.purchasesTitle
        case .supplierPayment: strings.paidToSuppliers
        case .expense: strings.expensesTitle
        }
    }

    private static func row(_ entry: DayEntry, _ strings: Strings, _ currency: Currency) -> Row {
        let outstanding = entry.amount - entry.settled
        let parts: [String] = [
            reference(entry, strings),
            // Only where money is still owed on the thing itself. A credit note
            // settles nothing by design and would otherwise carry this on every
            // row, saying "on credit" about money that was never going to be
            // paid.
            entry.kind.carriesCredit && outstanding > 0
                ? strings.onCreditAmount(Money.text(outstanding, in: currency))
                : nil
        ].compactMap { $0 }
        let detail = parts.joined(separator: " · ")

        return Row(
            name: entry.who,
            detail: detail.isEmpty ? nil : detail,
            amount: Money.text(entry.amount, in: currency),
            balance: entry.closingBalance.map {
                Balance(label: strings.dayClosingBalance, value: Money.text($0, in: currency))
            },
            items: entry.items.map {
                Item(text: strings.itemLine($0.qty, $0.name), amount: Money.text($0.amount, in: currency))
            }
        )
    }

    /// What to call the paper, worded exactly as `StatementDocument` words it.
    ///
    /// The same load appearing as "Purchase #88" on one page and "Delivery
    /// 88" on another is the owner checking whether they are the same load,
    /// which is work this page exists to remove.
    private static func reference(_ entry: DayEntry, _ strings: Strings) -> String? {
        let no = entry.reference.flatMap { $0.isEmpty ? nil : $0 }
        switch entry.kind {
        case .bill:
            return no.map { strings.invoiceRef($0) } ?? entry.billNumber.map { strings.billNumber($0) }
        case .payment, .supplierPayment:
            return no.map { strings.paymentRef($0) } ?? strings.paymentLabel
        case .creditNote:
            return no.map { strings.creditNoteRef($0) } ?? strings.creditNoteLabel
        case .purchase:
            return no.map { strings.purchaseRef($0) } ?? strings.purchaseLabel
        case .expense:
            // Joined to nobody and numbered by nobody. The row's name is already
            // what it went on, and there is nothing else to say.
            return nil
        }
    }
}

private extension DayEntryKind {
    /// Whether "still owed" is a thing this kind can be.
    ///
    /// A bill and a delivery are the two records that can be part settled; a
    /// payment or an expense is the settling.
    var carriesCredit: Bool {
        switch self {
        case .bill, .purchase: true
        case .payment, .supplierPayment, .creditNote, .expense: false
        }
    }
}
