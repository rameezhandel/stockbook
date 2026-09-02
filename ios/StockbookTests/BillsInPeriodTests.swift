import Testing
import Foundation
@testable import Stockbook

/// The bills the sales list shows for a span.
///
/// The twin of `BillsInPeriodTests.kt`, test for test. The claim worth pinning is
/// that this uses **the same idea of a period as everything else** — half-open
/// bounds, whole days in the phone's own zone. A screen that invented its own
/// would put a bill written at ten to midnight on the list for one month and the
/// statement for another, and the owner would find it by adding the two up and
/// getting a figure that matches neither.
@Suite("Bills in a period")
@MainActor
struct BillsInPeriodTests {

    private var riyadh: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Riyadh")!
        return calendar
    }

    private func on(_ month: Int, _ day: Int, hour: Int = 9) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.date(from: DateComponents(year: 2026, month: month, day: day, hour: hour))!
    }

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @Test("Only the bills inside the span, newest first")
    func insideTheSpan() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: on(7, 28))
        store.saveBill(customer: "Fatima", paid: nil, amount: 200, createdAt: on(8, 3))
        store.saveBill(customer: "Khalid", paid: nil, amount: 300, createdAt: on(8, 19))
        store.saveBill(customer: "Noura", paid: nil, amount: 400, createdAt: on(9, 2))

        let august = store.billsIn(.month(on(8, 15)), calendar: riyadh)

        #expect(august.map(\.who) == ["Khalid", "Fatima"], "inside the month, newest first")
    }

    /// The same half-open bounds the statement uses. A bill written at midnight
    /// on the 1st belongs to exactly one month, and this and the statement have
    /// to agree which.
    @Test("The span is the statement's span, boundaries and all")
    func matchesTheShopWideFigures() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: on(8, 1, hour: 0))
        store.saveBill(customer: "Fatima", paid: nil, amount: 200, createdAt: on(8, 31, hour: 23))

        let august = StatementPeriod.month(on(8, 15))

        // In the phone's own zone on all three, deliberately. `billCountIn` and
        // `soldIn` take no calendar and use the current one; handing this one a
        // different zone would be comparing two Augusts, which is the very
        // confusion the test exists to rule out.
        #expect(store.billCountIn(august) == store.billsIn(august).count)
        #expect(
            store.soldIn(august) == store.billsIn(august).reduce(0) { $0 + $1.total },
            "the list and the shop's own total cover the same bills"
        )
    }

    @Test("A chosen range covers whole days at both ends")
    func chosenRange() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: on(8, 10, hour: 1))
        store.saveBill(customer: "Fatima", paid: nil, amount: 200, createdAt: on(8, 12, hour: 20))
        store.saveBill(customer: "Khalid", paid: nil, amount: 300, createdAt: on(8, 14))

        let picked = store.billsIn(.custom(from: on(8, 10), to: on(8, 12)), calendar: riyadh)

        #expect(picked.map(\.who) == ["Fatima", "Ahmed"], "both end days are inside")
    }

    /// The other two halves of the book, over the same span and by the same rule.
    ///
    /// Their own tests rather than trust by resemblance: three lists that must
    /// agree about which days belong to a month is exactly the kind of agreement
    /// that decays when one of them is corrected.
    @Test("Deliveries and spending narrow the same way")
    func theOtherTwoHalves() {
        let store = makeStore()
        _ = store.addSupplier(name: "Gulf Traders")
        _ = store.recordPurchase(lines: [], supplierKey: "gulf traders", amount: 500, createdAt: on(7, 30))
        _ = store.recordPurchase(lines: [], supplierKey: "gulf traders", amount: 800, createdAt: on(8, 12))
        _ = store.addExpense(amount: 40, note: "Petrol", spentAt: on(7, 30))
        _ = store.addExpense(amount: 90, note: "Tea", spentAt: on(8, 12))

        let august = StatementPeriod.month(on(8, 15))

        #expect(store.purchasesIn(august, calendar: riyadh).map(\.total) == [800])
        #expect(store.expensesIn(august, calendar: riyadh).map(\.note) == ["Tea"])
    }

    /// Each list ties to the shop-wide figure for the same span.
    @Test("The lists tie to the totals the shop already publishes")
    func tiesToTheTotals() {
        let store = makeStore()
        _ = store.addSupplier(name: "Gulf Traders")
        _ = store.recordPurchase(lines: [], supplierKey: "gulf traders", amount: 800, createdAt: on(8, 12))
        _ = store.addExpense(amount: 90, note: "Tea", spentAt: on(8, 12))

        let august = StatementPeriod.month(on(8, 15))

        #expect(store.boughtIn(august) == store.purchasesIn(august).reduce(0) { $0 + $1.total })
        #expect(store.spentIn(august) == store.expensesIn(august).reduce(0) { $0 + $1.amount })
    }

    @Test("A span with nothing in it is empty rather than everything")
    func emptySpan() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: on(8, 10))

        #expect(store.billsIn(.month(on(5, 4)), calendar: riyadh).isEmpty)
        #expect(store.purchasesIn(.month(on(5, 4)), calendar: riyadh).isEmpty)
        #expect(store.expensesIn(.month(on(5, 4)), calendar: riyadh).isEmpty)
    }
}
