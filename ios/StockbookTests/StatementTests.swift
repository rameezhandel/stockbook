import Testing
import Foundation
@testable import Stockbook

/// The date arithmetic behind "this month" and "this year".
///
/// Pinned to a fixed UTC Gregorian calendar rather than the device's, because a
/// month boundary is exactly the kind of thing that passes in London and fails in
/// Riyadh — and this app is for a shop in Saudi Arabia read on a phone that may be
/// set to anything.
@Suite("Statement periods")
struct StatementPeriodTests {

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 12) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
    }

    @Test("A month runs from its first instant to the next month's, exclusive")
    func monthRange() {
        let range = StatementPeriod.month(date(2026, 8, 17)).range(calendar: calendar)

        #expect(range.start == date(2026, 8, 1, 0))
        #expect(range.end == date(2026, 9, 1, 0))
        #expect(range.contains(date(2026, 8, 1, 0)))
        #expect(range.contains(date(2026, 8, 31, 23)))
        // The load-bearing one. Midnight on the 1st belongs to September, and if
        // it belonged to both, a bill written then would be counted twice.
        #expect(!range.contains(date(2026, 9, 1, 0)))
        #expect(!range.contains(date(2026, 7, 31, 23)))
    }

    @Test("A year runs January to January")
    func yearRange() {
        let range = StatementPeriod.year(date(2026, 8, 17)).range(calendar: calendar)

        #expect(range.start == date(2026, 1, 1, 0))
        #expect(range.end == date(2027, 1, 1, 0))
        #expect(range.contains(date(2026, 12, 31, 23)))
        #expect(!range.contains(date(2027, 1, 1, 0)))
    }

    @Test("A chosen range covers both end days whole")
    func customRange() {
        let range = StatementPeriod
            .custom(from: date(2026, 8, 3, 15), to: date(2026, 8, 5, 9))
            .range(calendar: calendar)

        // Picked mid-afternoon on the 3rd, but the owner means the 3rd.
        #expect(range.start == date(2026, 8, 3, 0))
        #expect(range.contains(date(2026, 8, 3, 0)))
        // And the whole of the 5th, not up to 9am on it.
        #expect(range.contains(date(2026, 8, 5, 23)))
        #expect(!range.contains(date(2026, 8, 6, 0)))
    }

    @Test("A range picked backwards is still the range they meant")
    func customRangeReversed() {
        let forwards = StatementPeriod
            .custom(from: date(2026, 8, 3), to: date(2026, 8, 5))
            .range(calendar: calendar)
        let backwards = StatementPeriod
            .custom(from: date(2026, 8, 5), to: date(2026, 8, 3))
            .range(calendar: calendar)

        #expect(forwards == backwards)
    }

    @Test("Last month is last month, including from the 1st")
    func lastMonth() {
        let range = StatementPeriod
            .lastMonth(now: date(2026, 1, 1, 3), calendar: calendar)
            .range(calendar: calendar)

        // From the small hours of New Year's Day, "last month" is December.
        #expect(range.start == date(2025, 12, 1, 0))
        #expect(range.end == date(2026, 1, 1, 0))
    }
}

/// What a statement says, which is the whole feature.
@Suite("Statements")
struct StatementTests {

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    private let ahmed = Customer(
        name: "Ahmed Contracting",
        key: "ahmed contracting",
        billCount: 0,
        total: 0,
        owed: 0,
        phone: nil,
        place: nil,
        openingBalance: 0,
        isOnRoster: true
    )

    private func bill(
        _ number: Int,
        on date: Date,
        total: Double,
        paid: Double? = nil,
        voided: Bool = false
    ) -> Bill {
        Bill(
            number: number,
            lines: [BillLine(productUID: nil, name: "Cisa lock", qty: 1, price: total)],
            total: total,
            paid: paid,
            who: "Ahmed Contracting",
            createdAt: date,
            voided: voided
        )
    }

    private func payment(_ amount: Double, on date: Date) -> Payment {
        Payment(customerKey: "ahmed contracting", amount: amount, receivedAt: date)
    }

    private func august(
        bills: [Bill],
        payments: [Payment] = []
    ) -> Statement {
        Statement.make(
            customer: ahmed,
            bills: bills,
            payments: payments,
            period: .month(date(2026, 8, 10)),
            calendar: calendar
        )
    }

    @Test("A bill paid in full leaves nothing owed")
    func paidInFull() {
        let statement = august(bills: [bill(1, on: date(2026, 8, 4), total: 900)])

        #expect(statement.billed == 900)
        #expect(statement.received == 900)
        #expect(statement.closingBalance == 0)
        #expect(statement.openingBalance == 0)
    }

    @Test("A part payment at the counter leaves the rest owed")
    func partPaid() {
        let statement = august(bills: [bill(1, on: date(2026, 8, 4), total: 900, paid: 500)])

        #expect(statement.billed == 900)
        #expect(statement.received == 500)
        #expect(statement.closingBalance == 400)
    }

    /// The reason payments exist. Without them this figure could never come down.
    @Test("A payment received later clears the balance")
    func paymentClears() {
        let statement = august(
            bills: [bill(1, on: date(2026, 8, 4), total: 900, paid: 500)],
            payments: [payment(400, on: date(2026, 8, 20))]
        )

        #expect(statement.received == 900)
        #expect(statement.closingBalance == 0)
        #expect(statement.entries.count == 2)
    }

    /// The difference between a statement and a filtered list of bills.
    @Test("What was owed before the period is brought forward")
    func openingBalance() {
        let statement = august(
            bills: [
                bill(1, on: date(2026, 3, 2), total: 900, paid: 0),      // owed since March
                bill(2, on: date(2026, 8, 4), total: 100, paid: 0)
            ]
        )

        #expect(statement.openingBalance == 900)
        #expect(statement.billed == 100, "March is not in August's figures")
        #expect(statement.closingBalance == 1000)
        // Only August's bill is listed; the March one is carried as a figure.
        let numbers = statement.entries.compactMap { entry -> Int? in
            if case .bill(let bill) = entry { return bill.number }
            return nil
        }
        #expect(numbers == [2])
    }

    @Test("A payment before the period comes off the brought-forward figure")
    func paymentBeforePeriod() {
        let statement = august(
            bills: [bill(1, on: date(2026, 3, 2), total: 900, paid: 0)],
            payments: [payment(400, on: date(2026, 7, 30))]
        )

        #expect(statement.openingBalance == 500)
        #expect(statement.closingBalance == 500)
        #expect(statement.isEmpty, "nothing happened in August")
    }

    @Test("A voided bill is listed and counts for nothing")
    func voided() {
        let statement = august(
            bills: [
                bill(1, on: date(2026, 8, 4), total: 900, paid: 0),
                bill(2, on: date(2026, 8, 5), total: 500, paid: 0, voided: true)
            ]
        )

        #expect(statement.entries.count == 2, "history is marked, never hidden")
        #expect(statement.billed == 900)
        #expect(statement.closingBalance == 900)
    }

    @Test("Entries read downwards, oldest first")
    func order() {
        let statement = august(
            bills: [
                bill(2, on: date(2026, 8, 20), total: 100),
                bill(1, on: date(2026, 8, 4), total: 200)
            ],
            payments: [payment(50, on: date(2026, 8, 10))]
        )

        let dates = statement.entries.map(\.date)
        let sorted = dates.sorted()
        // Every other list in this app is newest-first. A statement is a
        // document, and a document reads down the page.
        #expect(dates == sorted)
    }

    /// An invariant, not an example: the two are computed by different routes —
    /// one accumulates per entry, the other is opening + billed − received — and
    /// a statement whose column disagrees with its own total is worthless.
    @Test("The running balance lands exactly on the closing balance")
    func runningBalanceAgrees() {
        let statement = august(
            bills: [
                bill(1, on: date(2026, 3, 2), total: 900, paid: 0),
                bill(2, on: date(2026, 8, 4), total: 250, paid: 100),
                bill(3, on: date(2026, 8, 6), total: 80),
                bill(4, on: date(2026, 8, 9), total: 400, paid: 0, voided: true)
            ],
            payments: [payment(200, on: date(2026, 8, 12)), payment(25, on: date(2026, 8, 28))]
        )

        #expect(statement.runningBalances.count == statement.entries.count)
        let last = statement.runningBalances.last
        #expect(last == statement.closingBalance)
    }

    @Test("Paying more than owed shows as an advance rather than a wrong total")
    func overpayment() {
        let statement = august(
            bills: [bill(1, on: date(2026, 8, 4), total: 100, paid: 0)],
            payments: [payment(250, on: date(2026, 8, 20))]
        )

        #expect(statement.closingBalance == -150)
        // And the customer row says it in words rather than showing a minus sign.
        let paidAhead = Customer(
            name: "Ahmed", key: "ahmed", billCount: 1, total: 100, owed: -150,
            phone: nil, place: nil, openingBalance: 0, isOnRoster: true
        )
        let meta = paidAhead.meta(in: .default, strings: Strings(language: .english))
        #expect(meta.contains("in advance"))
    }

    @Test("A customer with no history at all yields an empty statement, not nil")
    func nothingAtAll() {
        let statement = august(bills: [])

        #expect(statement.isEmpty)
        #expect(statement.openingBalance == 0)
        #expect(statement.closingBalance == 0)
        #expect(statement.runningBalances.isEmpty)
    }
}
