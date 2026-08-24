import Testing
import Foundation
@testable import Stockbook

/// What a stretch of trading left the shop with.
///
/// The twin of `EarningsTests.kt` and `EarningsDocumentTests.kt`, folded into one
/// suite. Two things here are worth more than the arithmetic. **The discount
/// needs no apportioning** — `Bill.total` is stored after it, so a bill's takings
/// less its lines' cost is exactly what that bill earned. And **a bill that
/// cannot answer is set aside whole**, never half-counted: subtracting part of a
/// bill's cost from all of its takings would flatter the figure by the
/// difference, which is the one direction a page like this must never be wrong
/// in.
@MainActor
@Suite("Earnings")
struct EarningsTests {

    private let english = Strings(language: .english)

    private func makeStore() -> StockbookStore {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setOwnerName("Al Salam Hardware")
        return store
    }

    private func period() -> StatementPeriod { .thisMonth() }

    private func page(_ store: StockbookStore) -> EarningsDocument {
        EarningsDocument.make(
            earnings: store.earningsIn(period()),
            range: period().range(),
            settings: store.settings,
            strings: english
        )
    }

    @Test("What the goods earned is what they sold for less what they cost")
    func earned() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        let handle = store.addProduct(name: "Door handle", stock: 100, cost: 35, price: 50)
        store.saveBill(
            lines: [
                .init(productUID: padlock.uid, qty: 3, price: 30),
                .init(productUID: handle.uid, qty: 2, price: 50)
            ],
            customer: "Ahmed",
            paid: nil
        )

        let earnings = store.earningsIn(period())

        #expect(earnings.sold == 190)
        #expect(earnings.costOfGoods == 130)
        #expect(earnings.goodsEarned == 60)
    }

    @Test("A discount comes off the earnings without being apportioned")
    func discountNeedsNoShareOut() {
        // `Bill.total` is already stored net of the discount, so takings less
        // cost is exact. Apportioning across lines would reach the same answer by
        // a longer route and pick up a rounding error on the way.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(
            lines: [.init(productUID: padlock.uid, qty: 3, price: 30)],
            customer: "Ahmed",
            paid: nil,
            discountPercent: 10
        )

        let earnings = store.earningsIn(period())

        #expect(earnings.sold == 81)
        #expect(earnings.costOfGoods == 60)
        #expect(earnings.goodsEarned == 21)
    }

    @Test("A bill entered as a figure is set aside, and the page says how much")
    func figureOnlyBillIsDisclosed() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)
        store.saveBill(customer: "Khalid", paid: nil, amount: 500)

        let earnings = store.earningsIn(period())

        #expect(earnings.sold == 590)
        #expect(earnings.soldWithoutCost == 500)
        #expect(earnings.billsWithoutCost == 1)
        #expect(earnings.counted == 90)
        #expect(earnings.goodsEarned == 30)
        #expect(earnings.hasGap)
    }

    @Test("What the shop kept is what the goods earned less what it spent")
    func kept() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.addExpense(amount: 45, note: "Petrol")

        let earnings = store.earningsIn(period())

        #expect(earnings.goodsEarned == 100)
        #expect(earnings.expenses == 45)
        #expect(earnings.kept == 55)
    }

    @Test("Stock bought and not sold is not a cost yet")
    func onlyWhatWasSold() throws {
        // A hundred padlocks in and three out is not a loss. This is why the
        // figure is the cost of what was *sold*, and why `boughtIn` has no part
        // in it.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        store.recordPurchase(
            product: try #require(store.product(uid: padlock.uid)),
            supplierKey: supplier.key,
            quantity: 100,
            unitCost: 20
        )
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)

        #expect(store.boughtIn(period()) == 2000)
        #expect(store.earningsIn(period()).costOfGoods == 60)
        #expect(store.earningsIn(period()).goodsEarned == 30)
    }

    @Test("A price rise after the sale does not move what the month earned")
    func historyDoesNotMove() throws {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)

        let before = store.earningsIn(period()).goodsEarned
        store.update(try #require(store.product(uid: padlock.uid)), name: "Padlock 40mm", cost: 26, price: 30)

        #expect(store.earningsIn(period()).goodsEarned == before)
        #expect(before == 30)
    }

    @Test("Credit notes are disclosed and never subtracted")
    func creditNotesAreDisclosed() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: 0)
        store.addCreditNote(customerKey: Customer.key(for: "Ahmed"), amount: 30)

        let earnings = store.earningsIn(period())

        #expect(earnings.sold == 90)
        #expect(earnings.goodsEarned == 30)
        #expect(earnings.credited == 30)
        #expect(earnings.creditNotes == 1)
    }

    @Test("Sold matches what Home shows, so the two screens cannot disagree")
    func soldAgreesWithHome() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)
        store.saveBill(customer: "Khalid", paid: nil, amount: 500)

        #expect(store.earningsIn(period()).sold == store.soldIn(period()))
    }

    // MARK: The page

    @Test("A shop that itemises everything reads sold, cost, earned, spent, kept")
    func shortChain() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.addExpense(amount: 45, note: "Petrol")

        let document = page(store)

        #expect(document.lines.map(\.label) == [
            "Sold", "Cost of goods", "What the goods earned", "Expenses", "What the shop kept"
        ])
        #expect(document.lines.map(\.value) == ["SAR 300", "SAR 200", "SAR 100", "SAR 45", "SAR 55"])
        #expect(!document.hasGap)
        #expect(document.gapNote == nil)
    }

    @Test("Takings the page cannot answer for are taken off in front of the reader")
    func longChain() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.saveBill(customer: "Khalid", paid: nil, amount: 500)

        let document = page(store)

        #expect(document.lines.map(\.label) == [
            "Sold", "Not counted", "Counted", "Cost of goods",
            "What the goods earned", "Expenses", "What the shop kept"
        ])
        #expect(document.gap.map(\.label) == ["1 bill entered as a total"])
        #expect(document.gap.map(\.value) == ["SAR 500"])
    }

    @Test("A month that lost money says so with a sign")
    func negativeMonth() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 2, price: 30)], customer: "Ahmed", paid: nil)
        store.addExpense(amount: 400, note: "Rent")

        #expect(page(store).lines.last?.value == "-SAR 380")
    }

    @Test("The page is a summary and never a statement")
    func neverAStatement() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 1, price: 30)], customer: "Ahmed", paid: nil)

        let document = page(store)

        #expect(document.shopName == "Al Salam Hardware")
        #expect(document.title == "Earnings Summary")
        #expect(!document.title.lowercased().contains("statement"))
    }

    @Test("A quiet period states that and draws no chain of zeroes")
    func quietPeriod() {
        let document = page(makeStore())

        #expect(document.isEmpty)
        #expect(!document.hasGap)
        #expect(document.emptyLine == "Nothing sold in this period.")
    }
}
