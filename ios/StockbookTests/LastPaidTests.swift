import Testing
import Foundation
@testable import Stockbook

/// How long an account has gone without money moving.
///
/// The twin of `LastPaidTests.kt`, test for test. Home has always said who owes
/// and how much, and never how long. What this pins is that the clock is started
/// and stopped by **money only** — the two things that reduce a balance without
/// anybody paying, a credit note and a balance transfer, must leave it running.
/// Getting that wrong tells the owner they were paid by somebody who has not paid
/// them since spring, and they stop chasing it.
@MainActor
@Suite("Last paid")
struct LastPaidTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    /// 2026-08-27T09:00:00Z, fixed so the arithmetic below is arithmetic and not
    /// a function of when the suite happens to run.
    private let now = Date(timeIntervalSince1970: 1_787_821_200)

    private func daysAgo(_ days: Int) -> Date {
        now.addingTimeInterval(-Double(days) * 86_400)
    }

    private func shopWithProduct() -> (StockbookStore, Product) {
        let store = makeStore()
        let lock = store.addProduct(name: "Cisa lock", stock: 100, cost: 60, price: 95)
        return (store, lock)
    }

    // MARK: The rule on its own

    @Test("With no date to count from it says nothing rather than guessing")
    func noDate() {
        #expect(LastPaid.daysSince(lastPaidAt: nil, since: nil, now: now) == nil)
    }

    @Test("Never paid counts from the day the trading started")
    func fromFirstBill() {
        #expect(LastPaid.daysSince(lastPaidAt: nil, since: daysAgo(40), now: now) == 40)
    }

    @Test("A payment overrides the start date")
    func paymentWins() {
        #expect(
            LastPaid.daysSince(lastPaidAt: daysAgo(5), since: daysAgo(400), now: now) == 5,
            "the clock restarts when money comes in, whatever came before"
        )
    }

    @Test("A clock that has run backwards floors at zero")
    func backwards() {
        // A phone whose date was wrong when a bill was written. Nothing useful to
        // say, but "-3 days ago" on a counter screen is worse than "today".
        let future = now.addingTimeInterval(3 * 86_400)
        #expect(LastPaid.daysSince(lastPaidAt: future, since: nil, now: now) == 0)
    }

    @Test("Nothing is said until it is worth saying")
    func threshold() {
        #expect(!LastPaid.worthSaying(nil))
        #expect(!LastPaid.worthSaying(0))
        #expect(!LastPaid.worthSaying(LastPaid.worthSayingAfterDays - 1))
        #expect(LastPaid.worthSaying(LastPaid.worthSayingAfterDays))
    }

    // MARK: What the store works out from a real book

    @Test("Paying at the counter counts as being paid")
    func counterPayment() throws {
        // A shop whose customers settle on the spot writes no payment rows at
        // all. Reading only those would call every one of them a non-payer.
        let (store, lock) = shopWithProduct()
        store.saveBill(
            lines: [.init(productUID: lock.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: 95,
            createdAt: daysAgo(3)
        )

        let ahmed = try #require(store.customer(key: "ahmed"))
        #expect(ahmed.hasEverPaid)
        #expect(ahmed.quietDays(now: now) == 3)
    }

    @Test("A bill nobody paid anything on does not restart the clock")
    func unpaidBill() throws {
        let (store, lock) = shopWithProduct()
        store.saveBill(
            lines: [.init(productUID: lock.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: 0,
            createdAt: daysAgo(60)
        )

        let ahmed = try #require(store.customer(key: "ahmed"))
        #expect(!ahmed.hasEverPaid, "nothing has come in from them yet")
        #expect(ahmed.quietDays(now: now) == 60, "counted from the bill, which is all there is")
    }

    @Test("The most recent money is the one that counts")
    func mostRecent() throws {
        let (store, lock) = shopWithProduct()
        store.saveBill(
            lines: [.init(productUID: lock.uid, qty: 5, price: 95)],
            customer: "Ahmed",
            paid: 0,
            createdAt: daysAgo(90)
        )
        store.recordPayment(customerKey: "ahmed", amount: 100, receivedAt: daysAgo(70))
        store.recordPayment(customerKey: "ahmed", amount: 100, receivedAt: daysAgo(45))

        #expect(try #require(store.customer(key: "ahmed")).quietDays(now: now) == 45)
    }

    /// The one that would have been wrong in the worst way.
    ///
    /// A credit note reduces what somebody owes and no money changes hands. If it
    /// reset this clock, a customer written off in part would read as having just
    /// paid — and the owner would stop chasing the rest.
    @Test("A credit note is not a payment")
    func creditNoteIsNotPayment() throws {
        let (store, lock) = shopWithProduct()
        store.saveBill(
            lines: [.init(productUID: lock.uid, qty: 5, price: 95)],
            customer: "Ahmed",
            paid: 0,
            createdAt: daysAgo(80)
        )
        store.addCreditNote(customerKey: "ahmed", amount: 200, issuedAt: daysAgo(2))

        let ahmed = try #require(store.customer(key: "ahmed"))
        #expect(!ahmed.hasEverPaid)
        #expect(ahmed.quietDays(now: now) == 80, "still eighty days without a coin")
    }

    /// The same rule, for the other thing that moves a balance without money.
    @Test("A balance transfer is not a payment")
    func transferIsNotPayment() throws {
        let (store, lock) = shopWithProduct()
        store.addCustomer(name: "Ahmed Riyadh")
        store.saveBill(
            lines: [.init(productUID: lock.uid, qty: 5, price: 95)],
            customer: "Ahmed Jeddah",
            paid: 0,
            createdAt: daysAgo(80)
        )
        _ = store.transferBalance(
            fromKey: "ahmed jeddah",
            intoKey: "ahmed riyadh",
            amount: 200,
            movedAt: daysAgo(2)
        )

        let jeddah = try #require(store.customer(key: "ahmed jeddah"))
        #expect(!jeddah.hasEverPaid)
        #expect(jeddah.quietDays(now: now) == 80)

        // And the end it arrived at was not paid either — it was charged.
        let riyadh = try #require(store.customer(key: "ahmed riyadh"))
        #expect(!riyadh.hasEverPaid)
    }

    @Test("An opening balance alone has no date and says nothing")
    func openingBalanceOnly() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed", openingBalance: 500)

        let ahmed = try #require(store.customer(key: "ahmed"))
        #expect(
            ahmed.quietDays(now: now) == nil,
            "carried over from the paper book with no date to count from"
        )
    }

    // MARK: The other side of the book

    @Test("A supplier is how long since the shop paid them")
    func supplierSide() throws {
        let (store, product) = shopWithProduct()
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        store.recordPurchase(
            product: product,
            supplierKey: supplier.key,
            quantity: 10,
            unitCost: 40,
            paid: 0,
            createdAt: daysAgo(50)
        )

        let unpaid = try #require(store.supplier(key: supplier.key))
        #expect(!unpaid.hasEverPaid)
        #expect(unpaid.quietDays(now: now) == 50)

        store.recordSupplierPayment(supplierKey: supplier.key, amount: 200, paidAt: daysAgo(10))

        let paid = try #require(store.supplier(key: supplier.key))
        #expect(paid.hasEverPaid)
        #expect(paid.quietDays(now: now) == 10)
    }
}
