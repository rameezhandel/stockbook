import Foundation

/// A half-open span of time: `start` counts, `end` does not.
///
/// Written out rather than using `DateInterval`, whose `contains` includes its end
/// instant. With months laid end to end that puts midnight on the 1st in both
/// months, so a bill written at midnight would appear on two statements and be
/// counted twice. Half-open removes the question.
struct StatementRange: Equatable, Sendable {
    let start: Date
    /// Exclusive.
    let end: Date

    func contains(_ date: Date) -> Bool { date >= start && date < end }

    /// The last day this statement can honestly say it covers.
    ///
    /// For a finished month that is the month's own last day. For the month
    /// running now it is **today**: a statement printed on the 18th and headed
    /// "till 31 August" claims a fortnight that has not happened, and the
    /// customer reading it would take the balance as final when a week of
    /// deliveries is still to come.
    ///
    /// Clamped at both ends, so a period picked entirely in the future is dated
    /// from its own first day rather than from a moment before it began.
    func asOf(_ now: Date = .now) -> Date {
        min(max(now, start), end.addingTimeInterval(-1))
    }
}

/// What span a statement covers.
///
/// The quick choices are cases rather than pre-computed dates, so "this month"
/// still means this month when the app is left open across midnight on the 1st.
enum StatementPeriod: Equatable, Hashable, Sendable {
    /// The whole calendar month containing this date.
    case month(Date)
    /// The whole calendar year containing this date.
    case year(Date)
    /// Whole days, inclusive of both ends as the owner would read them.
    case custom(from: Date, to: Date)

    static func thisMonth(now: Date = .now) -> StatementPeriod { .month(now) }
    static func thisYear(now: Date = .now) -> StatementPeriod { .year(now) }

    static func lastMonth(now: Date = .now, calendar: Calendar = .current) -> StatementPeriod {
        .month(calendar.date(byAdding: .month, value: -1, to: now) ?? now)
    }

    func range(calendar: Calendar = .current) -> StatementRange {
        switch self {
        case .month(let date):
            return Self.unit(.month, containing: date, calendar: calendar)
        case .year(let date):
            return Self.unit(.year, containing: date, calendar: calendar)
        case .custom(let from, let to):
            // Whichever way round they were picked. A range the owner dragged
            // backwards is still the range they meant.
            let low = min(from, to)
            let high = max(from, to)
            let start = calendar.startOfDay(for: low)
            let endOfLastDay = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: high))
            return StatementRange(start: start, end: endOfLastDay ?? high)
        }
    }

    private static func unit(
        _ component: Calendar.Component,
        containing date: Date,
        calendar: Calendar
    ) -> StatementRange {
        guard let interval = calendar.dateInterval(of: component, for: date) else {
            // Unreachable for month and year in any real calendar; a range of
            // one day beats a crash if some calendar disagrees.
            let start = calendar.startOfDay(for: date)
            return StatementRange(start: start, end: start.addingTimeInterval(86_400))
        }
        // `dateInterval` already gives a half-open span: end is the first instant
        // of the next month or year.
        return StatementRange(start: interval.start, end: interval.end)
    }
}

/// Who a statement is for.
///
/// A statement is an **account**, and the shop keeps two kinds: customers, who
/// owe it money, and suppliers, whom it owes. The arithmetic is identical —
/// opening balance, charges, settlements, closing balance — so it lives in one
/// place and takes one of these rather than a `Customer` or a `Supplier`.
///
/// `kind` exists for the wording alone. "Billed" and "Received" are the wrong
/// words for a delivery note, and a screen cannot infer which it is holding from
/// figures that look the same either way.
struct StatementParty: Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        case customer
        case supplier
    }

    let name: String
    let key: String
    let phone: String?
    let place: String?
    /// Carried over from the paper book, in whichever direction this account runs.
    let openingBalance: Double
    let kind: Kind

    init(
        name: String,
        key: String,
        phone: String? = nil,
        place: String? = nil,
        openingBalance: Double = 0,
        kind: Kind
    ) {
        self.name = name
        self.key = key
        self.phone = phone
        self.place = place
        self.openingBalance = openingBalance
        self.kind = kind
    }

    var isSupplier: Bool { kind == .supplier }
}

/// One account over a period: what was bought, what was paid, and what is left.
///
/// A pure function of the events — `make` takes them as arguments rather than
/// reaching for a store — because the arithmetic here is the whole feature and it
/// has to be checkable against literal values.
///
/// **The opening balance is what makes this a statement** rather than a filtered
/// list. Without it, a customer who owed 900 from March and paid 400 in April
/// reads as being 400 in credit.
///
/// Both directions run through this type. For a customer the figures mean what
/// they owe the shop; for a supplier, what the shop owes them. Nothing in the
/// sums below distinguishes the two, which is exactly why there is one of them.
struct Statement: Equatable {

    /// What happened, in the order it happened.
    ///
    /// Four cases rather than a neutral row of numbers, because the document has
    /// to name the product on a delivery and show a payment's note. Being an enum
    /// is also what made adding the supplier side safe: every `switch` over it
    /// stopped compiling until it had been thought about.
    enum Entry: Equatable, Identifiable {
        case bill(Bill)
        case payment(Payment)
        case creditNote(CreditNote)
        case purchase(Purchase)
        case supplierPayment(SupplierPayment)
        /// A balance moved to or from another account, seen from one end of it.
        ///
        /// One case rather than two, because it is one event: the same record
        /// appears on both statements and has to say the same amount on each,
        /// charged on one and settled on the other. `outgoing` is which end this
        /// is. `otherName` is resolved when the statement is built rather than
        /// stored on the record, so a party renamed afterwards reads correctly
        /// and there is no second copy of a name to drift.
        case transfer(BalanceTransfer, outgoing: Bool, otherName: String)

        var date: Date {
            switch self {
            case .bill(let bill): bill.createdAt
            case .payment(let payment): payment.receivedAt
            case .creditNote(let note): note.issuedAt
            case .purchase(let purchase): purchase.createdAt
            case .supplierPayment(let payment): payment.paidAt
            case .transfer(let transfer, _, _): transfer.movedAt
            }
        }

        var id: String {
            switch self {
            case .bill(let bill): "bill-\(bill.number)"
            case .payment(let payment): "payment-\(payment.id.uuidString)"
            case .creditNote(let note): "credit-note-\(note.id.uuidString)"
            case .purchase(let purchase): "purchase-\(purchase.id.uuidString)"
            case .supplierPayment(let payment): "supplier-payment-\(payment.id.uuidString)"
            case .transfer(let transfer, _, _): "transfer-\(transfer.id.uuidString)"
            }
        }

        /// What the account is charged by this event.
        var charge: Double {
            switch self {
            case .bill(let bill): bill.total
            case .purchase(let purchase): purchase.total
            case .payment, .supplierPayment, .creditNote: 0
            case .transfer(let transfer, let outgoing, _): outgoing ? 0 : transfer.amount
            }
        }

        /// What it settles at the same moment: the counter payment on a bill or a
        /// delivery, or the whole of a payment made later.
        var settledAtOnce: Double {
            switch self {
            case .bill(let bill): bill.total - bill.balance
            case .purchase(let purchase): purchase.total - purchase.balance
            case .payment(let payment): payment.amount
            case .supplierPayment(let payment): payment.amount
            case .creditNote(let note): note.total
            case .transfer(let transfer, let outgoing, _): outgoing ? transfer.amount : 0
            }
        }

        /// Which total this entry belongs in.
        ///
        /// The running balance treats all three identically — a charge is a
        /// charge and a settlement is a settlement — but the totals must keep
        /// them apart, because they answer different questions. `trade` is what
        /// the shop reconciles against its till. `creditNote` reduced the balance
        /// with no money moving. `transfer` did not touch this shop's money at
        /// all; it moved a figure between two of its own accounts.
        ///
        /// An enum rather than the boolean this replaces: a third bucket cannot
        /// be expressed by one flag, and two flags could both be true.
        enum Kind { case trade, creditNote, transfer }

        var kind: Kind {
            switch self {
            case .creditNote: .creditNote
            case .transfer: .transfer
            case .bill, .payment, .purchase, .supplierPayment: .trade
            }
        }
    }

    let party: StatementParty
    let period: StatementPeriod
    let range: StatementRange

    /// Net owed the instant before `range.start`: the customer's carried-over
    /// opening balance, plus unpaid bills, less payments, from everything earlier.
    let openingBalance: Double

    /// Bills and payments inside the period, oldest first — a statement reads
    /// downwards, unlike every list in the app, which reads newest first.
    let entries: [Entry]

    /// Sum of live charges in the period: bills billed, or deliveries taken.
    let billed: Double

    /// Everything settled during the period **with money**: paid on the bill or
    /// delivery itself, plus payments made afterwards.
    ///
    /// Credit notes are not in here, deliberately — see `credited`. Both reduce
    /// what is owed and only one of them is cash, and this is the figure a shop
    /// reconciles its till against.
    let received: Double

    /// Credited back over the period, with no money changing hands.
    ///
    /// Its own line on the document rather than folded into `billed` as a
    /// negative charge: the owner needs to see what was invoiced and what was
    /// given back as two facts, not as one net figure that hides both.
    let credited: Double

    /// A balance that arrived from another account over the period, and one that
    /// left for another.
    ///
    /// Their own lines rather than folded into `billed` and `received`, for the
    /// reason `credited` has its own: a transfer in is not something the shop
    /// invoiced, and a transfer out is not money it took. Netting either into a
    /// trading figure would make that figure mean two things.
    let transferredIn: Double
    let transferredOut: Double

    /// `openingBalance + billed + transferredIn − received − credited −
    /// transferredOut`. What they owe at the end of it.
    let closingBalance: Double

    /// The running balance after each entry, parallel to `entries`, so the
    /// document can show a balance column without recomputing as it draws.
    let runningBalances: [Double]

    var isEmpty: Bool { entries.isEmpty }

    /// One customer's account: bills charge it, payments settle it, credit notes
    /// reduce it without settling anything.
    static func make(
        customer: Customer,
        bills: [Bill],
        payments: [Payment],
        creditNotes: [CreditNote] = [],
        transfers: [Entry] = [],
        period: StatementPeriod,
        calendar: Calendar = .current
    ) -> Statement {
        make(
            party: customer.party,
            entries: bills.map(Entry.bill)
                + payments.map(Entry.payment)
                + creditNotes.map(Entry.creditNote)
                + transfers,
            period: period,
            calendar: calendar
        )
    }

    /// One supplier's account: deliveries charge it, payments out settle it.
    ///
    /// The same call as the customer one, with the words meaning the opposite
    /// side of the counter. Nothing was copied to get here — that is the whole
    /// point of `StatementParty`.
    static func make(
        supplier: Supplier,
        purchases: [Purchase],
        payments: [SupplierPayment],
        transfers: [Entry] = [],
        period: StatementPeriod,
        calendar: Calendar = .current
    ) -> Statement {
        make(
            party: supplier.party,
            entries: purchases.map(Entry.purchase) + payments.map(Entry.supplierPayment) + transfers,
            period: period,
            calendar: calendar
        )
    }

    /// The arithmetic, once.
    ///
    /// Everything above hands this the same three things: who the account is,
    /// everything that ever happened on it, and the period to report.
    private static func make(
        party: StatementParty,
        entries: [Entry],
        period: StatementPeriod,
        calendar: Calendar
    ) -> Statement {
        let range = period.range(calendar: calendar)
        let ordered = entries.sorted { $0.date < $1.date }

        // What was carried over predates every entry, so it is part of the
        // brought-forward figure whatever period is being shown.
        let opening = party.openingBalance
            + ordered.filter { $0.date < range.start }
                .reduce(0) { $0 + $1.charge - $1.settledAtOnce }

        let inRange = ordered.filter { range.contains($0.date) }
        // Split by where each figure came from, not by how big it was. All of
        // them still move the running balance below, together.
        let trade = inRange.filter { $0.kind == .trade }
        let billed = trade.reduce(0) { $0 + $1.charge }
        let received = trade.reduce(0) { $0 + $1.settledAtOnce }
        let credited = inRange.filter { $0.kind == .creditNote }.reduce(0) { $0 + $1.settledAtOnce }
        let transfers = inRange.filter { $0.kind == .transfer }
        let transferredIn = transfers.reduce(0) { $0 + $1.charge }
        let transferredOut = transfers.reduce(0) { $0 + $1.settledAtOnce }

        var running: [Double] = []
        var balance = opening
        for entry in inRange {
            balance += entry.charge - entry.settledAtOnce
            running.append(balance)
        }

        return Statement(
            party: party,
            period: period,
            range: range,
            openingBalance: opening,
            entries: inRange,
            billed: billed,
            received: received,
            credited: credited,
            transferredIn: transferredIn,
            transferredOut: transferredOut,
            closingBalance: opening + billed + transferredIn - received - credited - transferredOut,
            runningBalances: running
        )
    }
}
