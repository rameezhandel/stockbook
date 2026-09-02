import Testing
import Foundation
@testable import Stockbook

/// Where each account stood when the day closed, said under the row it appears on.
///
/// The twin of `DaySummaryBalanceTests.kt`, test for test. Two claims are pinned
/// here and both matter more than they look. The first is that the figure is
/// **that day's**, not today's: the page is usually opened on a date in the past,
/// and a balance quietly rolled forward to now would be the one number on it a
/// reader could not reconcile against anything.
///
/// The second is that it is **the statement's figure**. A customer holding a
/// statement and an owner reading this page have to be looking at the same money,
/// and the only way to be sure of that is for there to be one calculation.
@Suite("Day summary balances")
@MainActor
struct DaySummaryBalanceTests {

    private let english = Strings(language: .english)

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func on(_ day: Int, hour: Int = 9) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 8, day: day, hour: hour))!
    }

    private func makeStore() -> StockbookStore {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setOwnerName("Al Salam Hardware")
        return store
    }

    private func page(_ store: StockbookStore, _ day: Date) -> DaySummaryDocument {
        DaySummaryDocument.forDay(
            book: store.dayBook(day, calendar: calendar),
            settings: store.settings,
            strings: english
        )
    }

    private func row(_ page: DaySummaryDocument, _ name: String) throws -> DaySummaryDocument.Row {
        try #require(page.sections.flatMap(\.rows).first { $0.name == name }, "no row for \(name)")
    }

    @Test("A bill on credit carries what the customer owes at the end of that day")
    func billCarriesTheBalance() throws {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, createdAt: on(20))
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, createdAt: on(22))

        let balance = try #require(try row(page(store, on(22)), "Ahmed").balance)

        #expect(balance.label == "Closing balance")
        #expect(balance.value == "SAR 1,500", "the twentieth's thousand and today's five hundred")
    }

    /// The figure on a past day's page is that day's, not today's.
    ///
    /// Opened on the twentieth, after a bill on the twenty-second exists, the row
    /// has to say a thousand — the five hundred had not happened yet. Rolling the
    /// balance forward is the mistake that makes a day page impossible to
    /// reconcile against the cash box it was printed for.
    @Test("A past day does not carry a balance from after it")
    func pastDayStaysInThePast() throws {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, createdAt: on(20))
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, createdAt: on(22))

        let balance = try #require(try row(page(store, on(20)), "Ahmed").balance)

        #expect(balance.value == "SAR 1,000")
    }

    @Test("A receipt shows what is left after it")
    func receiptShowsWhatIsLeft() throws {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, createdAt: on(20))
        _ = store.recordPayment(customerKey: Customer.key(for: "Ahmed"), amount: 300, receivedAt: on(22))

        let balance = try #require(try row(page(store, on(22)), "Ahmed").balance)

        #expect(balance.value == "SAR 700")
    }

    /// The same figure the statement prints. Not equal to it — the same
    /// calculation, which is the only way two documents about one account cannot
    /// drift apart.
    @Test("The balance is the statement's closing balance for that span")
    func matchesTheStatement() throws {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, createdAt: on(20))
        _ = store.recordPayment(customerKey: Customer.key(for: "Ahmed"), amount: 250, receivedAt: on(22))

        let statement = try #require(
            store.statement(forCustomer: "ahmed", period: .custom(from: on(1), to: on(22)))
        )
        let balance = try #require(try row(page(store, on(22)), "Ahmed").balance)

        #expect(balance.value == Money.text(statement.closingBalance, in: store.settings.currency))
    }

    /// A delivery says what the shop owes that supplier, which is the mirror of it.
    @Test("The supplier side carries a balance too")
    func supplierSide() throws {
        let store = makeStore()
        _ = store.addSupplier(name: "Gulf Traders")
        _ = store.recordPurchase(
            lines: [],
            supplierKey: "gulf traders",
            paid: 0,
            amount: 800,
            createdAt: on(22)
        )

        let balance = try #require(try row(page(store, on(22)), "Gulf Traders").balance)

        #expect(balance.value == "SAR 800")
    }

    /// The owner's own spending is joined to nobody, so there is nothing for a
    /// balance to be *of*. A line reading "Closing balance —" under Petrol
    /// would invite the reader to wonder whose.
    @Test("An expense has no balance because it has no account")
    func expenseHasNoAccount() throws {
        let store = makeStore()
        _ = store.addExpense(amount: 30, note: "Petrol", spentAt: on(22))

        #expect(try row(page(store, on(22)), "Petrol").balance == nil)
    }

    /// Three bills to one customer are three records of what was sold and one
    /// answer to what they owe. Every row says it, so the figure is not one the
    /// reader has to hunt for at the bottom of a run.
    @Test("Every row a customer appears on says the same closing figure")
    func repeatedOnEveryRow() throws {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 100, createdAt: on(22, hour: 9))
        store.saveBill(customer: "Ahmed", paid: 0, amount: 200, createdAt: on(22, hour: 11))

        let rows = page(store, on(22)).sections.flatMap(\.rows).filter { $0.name == "Ahmed" }

        #expect(rows.count == 2)
        #expect(rows.compactMap { $0.balance?.value } == ["SAR 300", "SAR 300"])
    }
}
