import Testing
import Foundation
@testable import Stockbook

/// Exercises the rules that the handoff is specific about — the ones where a
/// plausible-looking alternative implementation would be wrong.
@MainActor
@Suite("Store rules")
struct StoreTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    // MARK: Products

    @Test("Duplicate names are ignored case-insensitively")
    func duplicateNames() {
        let store = makeStore()
        let first = store.addProduct(name: "Padlock", stock: 10, cost: 5, price: 9)
        let second = store.addProduct(name: "  padlock ", stock: 99, cost: 1, price: 2)

        #expect(first.uid == second.uid)
        #expect(store.products.count == 1)
        #expect(store.product(uid: first.uid)?.stock == 10, "the existing product must not be overwritten")
    }

    @Test("A draft needs a name, a stock figure, a cost figure and a price above zero")
    func draftCompleteness() {
        #expect(StockbookStore.isProductDraftComplete(name: "Deadbolt", stock: "0", cost: "0", price: "12"))
        #expect(!StockbookStore.isProductDraftComplete(name: "", stock: "1", cost: "1", price: "1"))
        #expect(!StockbookStore.isProductDraftComplete(name: "Deadbolt", stock: "", cost: "1", price: "1"))
        #expect(!StockbookStore.isProductDraftComplete(name: "Deadbolt", stock: "1", cost: "", price: "1"))
        #expect(!StockbookStore.isProductDraftComplete(name: "Deadbolt", stock: "1", cost: "1", price: "0"),
                "a selling price of zero is not a selling price")
    }

    // MARK: Billing

    @Test("Saving a bill snapshots the line and decrements stock")
    func saveBill() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Cisa lock", stock: 20, cost: 60, price: 95)

        let bill = try #require(
            store.saveBill(
                lines: [.init(productUID: product.uid, qty: 3, price: 95)],
                customer: "  Ahmed Contracting ",
                paid: nil
            )
        )

        #expect(bill.number == 1)
        #expect(bill.total == 285)
        #expect(bill.paid == nil, "nil means paid in full")
        #expect(bill.who == "Ahmed Contracting", "the name is trimmed")
        #expect(store.product(uid: product.uid)?.stock == 17)
        #expect(bill.lines.first?.name == "Cisa lock")
    }

    @Test("An overridden price is charged, and the product keeps its own price")
    func priceOverride() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Padlock", stock: 5, cost: 20, price: 45)

        let bill = try #require(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 2, price: 40)], customer: "Walk-in", paid: nil)
        )

        #expect(bill.total == 80)
        #expect(bill.lines.first?.price == 40)
        #expect(store.product(uid: product.uid)?.price == 45, "an override is for that bill only")
    }

    @Test("Editing a product afterwards does not rewrite history")
    func historyIsImmutable() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Padlock", stock: 5, cost: 20, price: 45)
        let bill = try #require(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 45)], customer: "Walk-in", paid: nil)
        )

        store.update(product, name: "Padlock 50mm", stock: 5, cost: 25, price: 60)

        let stored = try #require(store.bills.first { $0.number == bill.number })
        #expect(stored.lines.first?.name == "Padlock")
        #expect(stored.lines.first?.price == 45)
        #expect(stored.total == 45)
    }

    @Test("Stock floors at zero rather than going negative")
    func stockFloor() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 2, cost: 3, price: 6)

        store.saveBill(lines: [.init(productUID: product.uid, qty: 10, price: 6)], customer: "Walk-in", paid: nil)

        #expect(store.product(uid: product.uid)?.stock == 0)
    }

    @Test("Paying the whole total is paid in full, not a part payment")
    func payingTheWholeTotal() throws {
        // The twin of `paying the whole total is paid in full` in StoreTests.kt.
        // This suite asserted the opposite for months: a figure equal to the
        // total was stored as a part payment, so iOS wrote "Paid SAR 12 · Sami
        // owes SAR 0" on a receipt Android called paid in full — and the two
        // platforms wrote different backup files for the same bill.
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 10, cost: 3, price: 6)

        let bill = try #require(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 2, price: 6)], customer: "Sami", paid: 500)
        )

        #expect(bill.paid == nil, "otherwise the receipt says somebody owes zero")
        #expect(!bill.isPartPaid)
        #expect(bill.balance == 0)
    }

    @Test("A part payment below the total is kept as one")
    func partPaymentClamp() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 10, cost: 3, price: 6)

        let bill = try #require(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 2, price: 6)], customer: "Sami", paid: 5)
        )

        #expect(bill.paid == 5)
        #expect(bill.isPartPaid)
        #expect(bill.balance == 7)
    }

    @Test("A bill without a customer name is refused")
    func customerRequired() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 10, cost: 3, price: 6)

        #expect(store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "   ", paid: nil) == nil)
        #expect(store.product(uid: product.uid)?.stock == 10, "a refused bill must not touch stock")
    }

    @Test("Bill numbers are monotonic")
    func billNumbering() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 10, cost: 3, price: 6)

        let first = try #require(store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "A", paid: nil))
        let second = try #require(store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "B", paid: nil))

        #expect(first.number == 1)
        #expect(second.number == 2)
    }

    // MARK: Void

    @Test("Voiding puts the stock back and keeps the bill")
    func voiding() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Deadbolt", stock: 8, cost: 40, price: 70)
        let bill = try #require(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 3, price: 70)], customer: "Sami", paid: nil)
        )
        #expect(store.product(uid: product.uid)?.stock == 5)

        store.void(bill)

        #expect(store.product(uid: product.uid)?.stock == 8)
        // Bills are values, so the copy returned by saveBill cannot learn that
        // it was voided — the store's own record is the one that matters.
        #expect(store.bills.first { $0.number == bill.number }?.voided == true)
        #expect(store.bills.count == 1, "bills are voided, never deleted")
    }

    @Test("Voiding twice does not double-restore stock")
    func voidingIsIdempotent() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Deadbolt", stock: 8, cost: 40, price: 70)
        let bill = try #require(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 3, price: 70)], customer: "Sami", paid: nil)
        )

        store.void(bill)
        store.void(bill)

        #expect(store.product(uid: product.uid)?.stock == 8)
    }

    // MARK: Customers

    @Test("The owed banner counts distinct people, not bills")
    func outstandingCountsPeople() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        // Two unpaid bills from the same customer is one customer.
        store.saveBill(lines: [.init(productUID: product.uid, qty: 10, price: 10)], customer: "Ahmed Contracting", paid: 60)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 10, price: 10)], customer: "Ahmed Contracting", paid: 16)

        let owed = store.outstanding()
        #expect(owed.names == ["Ahmed Contracting"])
        #expect(owed.total == 124)
    }

    @Test("Suggestions rank debtors first, then frequency")
    func suggestionOrder() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Frequent", paid: nil)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Frequent", paid: nil)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Rare", paid: nil)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 5, price: 10)], customer: "Debtor", paid: 10)

        let names = store.customerSuggestions(matching: "").map(\.name)
        #expect(names.first == "Debtor")
        #expect(names == ["Debtor", "Frequent", "Rare"])
    }

    @Test("Suggestions filter by what is typed and drop an exact match")
    func suggestionFiltering() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Ahmed Contracting", paid: nil)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Sami", paid: nil)

        #expect(store.customerSuggestions(matching: "ahm").map(\.name) == ["Ahmed Contracting"])
        #expect(store.customerSuggestions(matching: "Ahmed Contracting").isEmpty,
                "no point suggesting exactly what has been typed")
    }

    @Test("A voided bill leaves the customer book")
    func voidedBillsDropOut() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)
        let bill = try #require(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Sami", paid: 0)
        )

        store.void(bill)

        #expect(store.customers().isEmpty)
        #expect(store.outstanding().names.isEmpty)
    }

    // MARK: Restock

    @Test("Quick add raises stock and leaves the buying price alone")
    func quickAdd() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 4, cost: 18, price: 30)

        store.restock(product, quantity: 50, mode: .quickAdd, unitCost: 99)

        #expect(store.product(uid: product.uid)?.stock == 54)
        #expect(store.product(uid: product.uid)?.cost == 18)
    }

    @Test("A purchase entry overwrites the buying price with the latest paid")
    func purchaseEntry() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 4, cost: 18, price: 30)

        store.restock(product, quantity: 50, mode: .purchase, unitCost: 17)

        #expect(store.product(uid: product.uid)?.stock == 54)
        #expect(store.product(uid: product.uid)?.cost == 17, "cost is latest paid, not a weighted average")
    }

    @Test("A zero quantity does nothing")
    func emptyRestock() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 4, cost: 18, price: 30)

        store.restock(product, quantity: 0, mode: .purchase, unitCost: 17)

        #expect(store.product(uid: product.uid)?.stock == 4)
        #expect(store.product(uid: product.uid)?.cost == 18)
    }

    // MARK: Start over

    @Test("Start over clears the shop and returns to setup")
    func startOver() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 4, cost: 18, price: 30)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 30)], customer: "Sami", paid: nil)
        store.setOwnerName("Khalid")
        store.completeSetup()

        store.startOver()

        #expect(store.products.isEmpty)
        #expect(store.bills.isEmpty)
        #expect(store.settings.ownerName.isEmpty)
        #expect(!store.settings.setupCompleted)
        #expect(store.settings.nextBillNumber == 1)
    }
}
