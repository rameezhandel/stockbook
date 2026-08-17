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

/// One customer's account over a period: what they bought, what they paid, and
/// what is left.
///
/// A pure function of bills and payments — `make` takes them as arguments rather
/// than reaching for a store — because the arithmetic here is the whole feature
/// and it has to be checkable against literal values.
///
/// **The opening balance is what makes this a statement** rather than a filtered
/// list of bills. Without it, a customer who owed 900 from March and paid 400 in
/// April reads as being 400 in credit.
struct Statement: Equatable {

    /// A bill or a payment, in the order they happened.
    enum Entry: Equatable, Identifiable {
        case bill(Bill)
        case payment(Payment)

        var date: Date {
            switch self {
            case .bill(let bill): bill.createdAt
            case .payment(let payment): payment.receivedAt
            }
        }

        var id: String {
            switch self {
            case .bill(let bill): "bill-\(bill.number)"
            case .payment(let payment): "payment-\(payment.id.uuidString)"
            }
        }
    }

    let customer: Customer
    let period: StatementPeriod
    let range: StatementRange

    /// Net owed the instant before `range.start`: the customer's carried-over
    /// opening balance, plus unpaid bills, less payments, from everything earlier.
    let openingBalance: Double

    /// Bills and payments inside the period, oldest first — a statement reads
    /// downwards, unlike every list in the app, which reads newest first.
    let entries: [Entry]

    /// Sum of live bill totals in the period. What they bought.
    let billed: Double

    /// Everything that came in during the period: paid at the counter on the
    /// bills themselves, plus payments received afterwards.
    let received: Double

    /// `openingBalance + billed − received`. What they owe at the end of it.
    let closingBalance: Double

    /// The running balance after each entry, parallel to `entries`, so the
    /// document can show a balance column without recomputing as it draws.
    let runningBalances: [Double]

    var isEmpty: Bool { entries.isEmpty }

    static func make(
        customer: Customer,
        bills: [Bill],
        payments: [Payment],
        period: StatementPeriod,
        calendar: Calendar = .current
    ) -> Statement {
        let range = period.range(calendar: calendar)

        // A voided bill did not happen: it contributes nothing to any figure. It
        // is still listed, because history is marked here rather than hidden.
        let liveBefore = bills.filter { !$0.voided && $0.createdAt < range.start }
        let paymentsBefore = payments.filter { $0.receivedAt < range.start }
        // The customer's carried-over balance predates every bill, so it is part
        // of the brought-forward figure whatever period is being shown.
        let opening = customer.openingBalance
            + liveBefore.reduce(0) { $0 + $1.balance }
            - paymentsBefore.reduce(0) { $0 + $1.amount }

        let billsInRange = bills.filter { range.contains($0.createdAt) }
        let paymentsInRange = payments.filter { range.contains($0.receivedAt) }

        let live = billsInRange.filter { !$0.voided }
        let billed = live.reduce(0) { $0 + $1.total }
        // What the bill itself collected: its total less what is still owed on it.
        let atCounter = live.reduce(0) { $0 + ($1.total - $1.balance) }
        let received = atCounter + paymentsInRange.reduce(0) { $0 + $1.amount }

        let entries = (billsInRange.map(Entry.bill) + paymentsInRange.map(Entry.payment))
            .sorted { $0.date < $1.date }

        var running: [Double] = []
        var balance = opening
        for entry in entries {
            switch entry {
            case .bill(let bill):
                // A voided bill moves nothing, which is exactly what makes the
                // running column readable beside it.
                if !bill.voided { balance += bill.balance }
            case .payment(let payment):
                balance -= payment.amount
            }
            running.append(balance)
        }

        return Statement(
            customer: customer,
            period: period,
            range: range,
            openingBalance: opening,
            entries: entries,
            billed: billed,
            received: received,
            closingBalance: opening + billed - received,
            runningBalances: running
        )
    }
}
