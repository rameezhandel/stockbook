import Foundation

/// A titled list of things and figures, with a total under it: who owes the shop,
/// who it owes, or what it spent its money on.
///
/// **Every page built here is the owner's own, and none of them may be shown to
/// anybody else.** A statement is handed across the counter on purpose — it says
/// what one person owes, to that person. These say what *everybody* owes, or
/// where the shop's own money went; letting Ahmed see that Khalid is four
/// thousand behind is not an untidy page, it is a breach, and the owner's
/// spending is private by a rule written into `Expense` itself. Nothing renders
/// any of them beside a customer's own documents, and each title says whose list
/// it is.
///
/// **`asOf` carries what makes the figures true.** For money owed that is a day,
/// because a balance is true at a moment and meaningless over a span; for
/// spending it is the stretch of days it was spent over. Either way it is the
/// whole of what stops last month's printout reading as this morning's.
///
/// Laid out here for the reason `StatementDocument` is: drawing is platform work,
/// but *what* is drawn is not, and two hand-written layouts drift the first time
/// either is corrected. The Kotlin twin is `SummaryDocument.kt`, and
/// `SummaryDocumentTests` is what holds the two together.
struct SummaryDocument: Equatable {

    let shopName: String
    /// The shop's address for the masthead, from `Settings.addressLines`.
    ///
    /// Every page the app prints carries the same letterhead now — the shop's
    /// name and where it is — so a sheet on a desk says whose it is without
    /// anybody having to remember. The ledger book is the exception, and it is
    /// drawn by a different writer.
    let shopAddressLines: [String]
    /// What this is, said so nobody mistakes it for a statement.
    let title: String
    /// `As of 22 August 2026`, or `1 – 31 August 2026`. See above.
    let asOf: String
    let columnHeadings: [String]
    let rows: [Row]
    let totalLabel: String
    let totalValue: String
    /// Shown instead of the table when nobody owes anything.
    let emptyLine: String
    /// One line under the total, where the page owes a fact the column cannot
    /// carry.
    ///
    /// The payments page is the reason it exists: its column is money in, and
    /// what the shop paid out over the same days belongs on the page but not in
    /// that column — a total that is not what the rows above add up to is the
    /// figure the first reader to check it stops trusting. Absent on every other
    /// page.
    var footnote: String?

    /// One line of the table.
    ///
    /// `name` and `amount` are the two every page has: a customer and what they
    /// owe, or a bill and what it came to. The middle two are what a **register**
    /// needs and a balance list does not — the number on the paper and the day it
    /// was written — and they are absent on the pages that state a position
    /// rather than list records.
    ///
    /// `reference` is absent twice over: on the balance pages, which have no
    /// paper behind a row, and on an expense, which is a receipt from somebody
    /// else's shop and carries no number of the owner's.
    struct Row: Equatable {
        let name: String
        let amount: String
        var reference: String?
        var date: String?
    }

    /// Whether there is a table to draw at all.
    var isEmpty: Bool { rows.isEmpty }

    /// Who owes the shop, from `StockbookStore.customers()`.
    ///
    /// - Parameter customers: **biggest debt first**, which is the order this
    ///   document wants and the order the on-screen list already shows. Sorting
    ///   again here would be a second opinion about which is right.
    static func forReceivable(
        customers: [Customer],
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil,
        now: Date = .now
    ) -> SummaryDocument {
        make(
            parties: customers.map { ($0.name, $0.owed) },
            settings: settings,
            strings: strings,
            title: strings.receivableSummary,
            partyHeading: strings.columnCustomer,
            amountHeading: strings.receivableStat,
            totalLabel: strings.totalReceivable,
            emptyLine: strings.nothingReceivable,
            currency: currency ?? settings.currency,
            now: now
        )
    }

    /// Who the shop owes, from `StockbookStore.suppliers()`.
    ///
    /// The same page pointed the other way, and every word on it flips with it. A
    /// payable list headed "Receivable" would be the most expensive kind of
    /// wrong: one the owner acts on.
    static func forPayable(
        suppliers: [Supplier],
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil,
        now: Date = .now
    ) -> SummaryDocument {
        make(
            parties: suppliers.map { ($0.name, $0.owed) },
            settings: settings,
            strings: strings,
            title: strings.payableSummary,
            partyHeading: strings.supplier,
            amountHeading: strings.payableStat,
            totalLabel: strings.totalPayable,
            emptyLine: strings.nothingPayable,
            currency: currency ?? settings.currency,
            now: now
        )
    }

    /// The ledger book's contents page: every customer and where they stand.
    ///
    /// Built from **the very statements that become the pages behind it**, not
    /// from a second walk of the roster. A contents page naming a figure the page
    /// it points at disagrees with is worse than no contents page, and taking
    /// both from one list is the only way that cannot happen. The order comes
    /// with them, so the index reads in the order the book is filed.
    ///
    /// **Everybody, including the settled and the ones in credit** — unlike
    /// `forReceivable`, which is a chasing list and drops them. This one is an
    /// index: a customer who has a page in the book and no line in the contents
    /// is a customer the reader concludes is missing.
    static func forLedgerBook(
        statements: [Statement],
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil,
        now: Date = .now
    ) -> SummaryDocument {
        let money = currency ?? settings.currency
        return SummaryDocument(
            shopName: settings.ownerName,
            shopAddressLines: settings.addressLines,
            title: strings.customerBalances,
            asOf: strings.asOfDate(strings.longDate(now)),
            columnHeadings: [strings.columnCustomer, strings.balance],
            rows: statements.map {
                Row(name: $0.party.name, amount: Money.text($0.closingBalance, in: money))
            },
            totalLabel: strings.ledgerTotal,
            // The arithmetic sum of the column above, credits and all — **not**
            // the shop's receivable, which counts only what is owed. A figure at
            // the foot of a column that is not what the column adds up to is a
            // figure the first reader to check it stops trusting, and this one is
            // printed under a hundred lines somebody may well add up.
            totalValue: Money.text(statements.reduce(0) { $0 + $1.closingBalance }, in: money),
            emptyLine: strings.ledgerNoCustomers
        )
    }

    /// Every bill written over a stretch of days: who it was for, its number,
    /// the day, and what it came to.
    ///
    /// **A register, not a summary.** One line per bill rather than one per
    /// customer, because a page gets printed to be checked — against the paper
    /// book, against a customer's own copy, line by line — and a grouped page
    /// cannot be checked against anything. Who bought the most is a question the
    /// screen already answers.
    ///
    /// - Parameter lines: newest first, which the store already returns.
    static func forSales(
        lines: [RecordLine],
        range: StatementRange,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
    ) -> SummaryDocument {
        register(
            lines: lines,
            range: range,
            settings: settings,
            strings: strings,
            title: strings.salesReport,
            nameHeading: strings.columnCustomer,
            referenceHeading: strings.columnInvoiceReceipt,
            amountHeading: strings.soldInPeriod,
            totalLabel: strings.totalSoldLabel,
            emptyLine: strings.nothingSoldThen,
            currency: currency ?? settings.currency
        )
    }

    /// The mirror: every purchase over the same span, and who it came from.
    static func forPurchases(
        lines: [RecordLine],
        range: StatementRange,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
    ) -> SummaryDocument {
        register(
            lines: lines,
            range: range,
            settings: settings,
            strings: strings,
            title: strings.purchaseReport,
            nameHeading: strings.supplier,
            referenceHeading: strings.columnInvoiceReceipt,
            amountHeading: strings.boughtInPeriod,
            totalLabel: strings.totalBoughtLabel,
            emptyLine: strings.nothingBoughtThen,
            currency: currency ?? settings.currency
        )
    }

    /// Every receipt over the span — and, under the total, what the shop paid its
    /// suppliers over the same days.
    ///
    /// **Money in is the column; money out is a footnote.** Both directions in
    /// one column would leave a total that is neither the sum of the rows nor a
    /// figure the owner can check against anything, and signing the rows to make
    /// it add up would print `SAR -900` beside a supplier's name — which reads as
    /// a refund, and this app has no notion of one. So the page states one column
    /// honestly and says the other fact in words.
    ///
    /// - Parameter paidOut: what went out over the same span, or zero. Left off
    ///   the page entirely when nothing did, rather than printed as "0 paid to
    ///   suppliers", which is a line that makes the reader stop and check.
    static func forPayments(
        lines: [RecordLine],
        paidOut: Double,
        range: StatementRange,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
    ) -> SummaryDocument {
        let money = currency ?? settings.currency
        return register(
            lines: lines,
            range: range,
            settings: settings,
            strings: strings,
            title: strings.paymentsReport,
            nameHeading: strings.columnCustomer,
            referenceHeading: strings.columnInvoiceReceipt,
            amountHeading: strings.receivedInPeriod,
            totalLabel: strings.totalReceivedLabel,
            emptyLine: strings.nothingReceivedThen,
            currency: money,
            footnote: paidOut > 0 ? strings.alsoPaidOut(Money.text(paidOut, in: money)) : nil
        )
    }

    /// Every expense over the span: what it was for, the day, and the figure.
    ///
    /// Three columns rather than four. An expense is a receipt from somebody
    /// else's shop and carries no number of the owner's, and a column of dashes
    /// is a column that earns nothing.
    static func forSpending(
        lines: [RecordLine],
        range: StatementRange,
        settings: Settings,
        strings: Strings,
        currency: Currency? = nil
    ) -> SummaryDocument {
        register(
            lines: lines,
            range: range,
            settings: settings,
            strings: strings,
            title: strings.expenseReport,
            nameHeading: strings.columnWhatItWentOn,
            referenceHeading: nil,
            amountHeading: strings.expenseInPeriod,
            totalLabel: strings.totalSpentLabel,
            emptyLine: strings.nothingSpentThen,
            currency: currency ?? settings.currency
        )
    }

    /// The four pages that **list records over a stretch of days**.
    ///
    /// Money owed is a balance and true right now; what was sold, bought,
    /// received or spent only means anything over a period, and the header says
    /// which. Everything else about them differs in wording and in whether there
    /// is a number to print, so that is all they differ in here.
    ///
    /// - Parameter referenceHeading: nil where the records carry no number — see
    ///   `forSpending`.
    private static func register(
        lines: [RecordLine],
        range: StatementRange,
        settings: Settings,
        strings: Strings,
        title: String,
        nameHeading: String,
        referenceHeading: String?,
        amountHeading: String,
        totalLabel: String,
        emptyLine: String,
        currency: Currency,
        footnote: String? = nil
    ) -> SummaryDocument {
        SummaryDocument(
            shopName: settings.ownerName,
            shopAddressLines: settings.addressLines,
            title: title,
            asOf: strings.dateSpan(
                from: strings.longDate(range.start),
                // The last day *inside* the range. A period that ends at midnight
                // on the 1st is an August statement titled "to 1 September",
                // which nobody reads as August.
                to: strings.longDate(range.end.addingTimeInterval(-1))
            ),
            columnHeadings: [nameHeading, referenceHeading, strings.columnDate, amountHeading]
                .compactMap { $0 },
            rows: lines.map {
                Row(
                    name: $0.who,
                    amount: Money.text($0.amount, in: currency),
                    reference: referenceHeading == nil ? nil : $0.reference,
                    // The short form, not the long one. Forty rows of "22 August
                    // 2026" is a column three times wider than the fact in it.
                    date: strings.pickedDate($0.at)
                )
            },
            totalLabel: totalLabel,
            // Summed from the same figures the rows print, so the foot of the page
            // can never disagree with the page.
            totalValue: Money.text(lines.reduce(0) { $0 + $1.amount }, in: currency),
            emptyLine: emptyLine,
            footnote: footnote
        )
    }

    /// The page itself, which does not care which way the money points.
    ///
    /// `Customer` and `Supplier` are separate types with the same two fields that
    /// matter here, so they arrive already reduced to a name and a figure rather
    /// than behind a protocol neither of them asked for.
    private static func make(
        parties: [(name: String, owed: Double)],
        settings: Settings,
        strings: Strings,
        title: String,
        partyHeading: String,
        amountHeading: String,
        totalLabel: String,
        emptyLine: String,
        currency: Currency,
        now: Date
    ) -> SummaryDocument {
        // Only what is actually outstanding. Somebody in advance is not a debtor,
        // and a negative row on a chasing list is a line the owner has to stop
        // and think about every time they read it.
        let owing = parties.filter { $0.owed > 0 }

        return SummaryDocument(
            shopName: settings.ownerName,
            shopAddressLines: settings.addressLines,
            title: title,
            asOf: strings.asOfDate(strings.longDate(now)),
            columnHeadings: [partyHeading, amountHeading],
            rows: owing.map { Row(name: $0.name, amount: Money.text($0.owed, in: currency)) },
            totalLabel: totalLabel,
            // Summed from the same figures the rows print, so the foot of the
            // page can never disagree with the page. `outstanding()` and
            // `payable()` walk the same rosters to the same answers, and
            // `SummaryDocumentTests` pins them together.
            totalValue: Money.text(owing.reduce(0) { $0 + $1.owed }, in: currency),
            emptyLine: emptyLine
        )
    }
}
