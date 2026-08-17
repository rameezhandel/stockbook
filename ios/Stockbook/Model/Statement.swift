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
    /// to mark a voided bill, name the product on a delivery and show a payment's
    /// note. Being an enum is also what made adding the supplier side safe: every
    /// `switch` over it stopped compiling until it had been thought about.
    enum Entry: Equatable, Identifiable {
        case bill(Bill)
        case payment(Payment)
        case purchase(Purchase)
        case supplierPayment(SupplierPayment)

        var date: Date {
            switch self {
            case .bill(let bill): bill.createdAt
            case .payment(let payment): payment.receivedAt
            case .purchase(let purchase): purchase.createdAt
            case .supplierPayment(let payment): payment.paidAt
            }
        }

        var id: String {
            switch self {
            case .bill(let bill): "bill-\(bill.number)"
            case .payment(let payment): "payment-\(payment.id.uuidString)"
            case .purchase(let purchase): "purchase-\(purchase.id.uuidString)"
            case .supplierPayment(let payment): "supplier-payment-\(payment.id.uuidString)"
            }
        }

        /// What the account is charged by this event.
        var charge: Double {
            switch self {
            case .bill(let bill): bill.voided ? 0 : bill.total
            case .purchase(let purchase): purchase.voided ? 0 : purchase.total
            case .payment, .supplierPayment: 0
            }
        }

        /// What it settles at the same moment: the counter payment on a bill or a
        /// delivery, or the whole of a payment made later.
        var settledAtOnce: Double {
            switch self {
            case .bill(let bill): bill.voided ? 0 : bill.total - bill.balance
            case .purchase(let purchase): purchase.voided ? 0 : purchase.total - purchase.balance
            case .payment(let payment): payment.amount
            case .supplierPayment(let payment): payment.amount
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

    /// Everything settled during the period: paid on the bill or delivery itself,
    /// plus payments made afterwards.
    let received: Double

    /// `openingBalance + billed − received`. What they owe at the end of it.
    let closingBalance: Double

    /// The running balance after each entry, parallel to `entries`, so the
    /// document can show a balance column without recomputing as it draws.
    let runningBalances: [Double]

    var isEmpty: Bool { entries.isEmpty }

    /// One customer's account: bills charge it, payments settle it.
    static func make(
        customer: Customer,
        bills: [Bill],
        payments: [Payment],
        period: StatementPeriod,
        calendar: Calendar = .current
    ) -> Statement {
        make(
            party: customer.party,
            entries: bills.map(Entry.bill) + payments.map(Entry.payment),
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
        period: StatementPeriod,
        calendar: Calendar = .current
    ) -> Statement {
        make(
            party: supplier.party,
            entries: purchases.map(Entry.purchase) + payments.map(Entry.supplierPayment),
            period: period,
            calendar: calendar
        )
    }

    /// The arithmetic, once.
    ///
    /// Everything above hands this the same three things: who the account is,
    /// everything that ever happened on it, and the period to report. A voided
    /// bill or delivery did not happen and contributes nothing — it is still
    /// listed, because history is marked here rather than hidden.
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
        let billed = inRange.reduce(0) { $0 + $1.charge }
        let received = inRange.reduce(0) { $0 + $1.settledAtOnce }

        var running: [Double] = []
        var balance = opening
        for entry in inRange {
            // A voided entry moves nothing, which is exactly what makes the
            // running column readable beside it.
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
            closingBalance: opening + billed - received,
            runningBalances: running
        )
    }
}
