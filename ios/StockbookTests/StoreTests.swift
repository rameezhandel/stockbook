import XCTest
import Foundation
@testable import Stockbook

/// Exercises the rules that the handoff is specific about — the ones where a
/// plausible-looking alternative implementation would be wrong.
/// Store rules
final class StoreTests: XCTestCase {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    // MARK: Products

    /// Duplicate names are ignored case-insensitively
    func testDuplicateNames() {
        let store = makeStore()
        let first = store.addProduct(name: "Padlock", stock: 10, cost: 5, price: 9)
        let second = store.addProduct(name: "  padlock ", stock: 99, cost: 1, price: 2)

        XCTAssertEqual(first.uid, second.uid)
        XCTAssertEqual(store.products.count, 1)
        XCTAssertEqual(store.product(uid: first.uid)?.stock, 10, "the existing product must not be overwritten")
    }

    /// A draft needs a name, a stock figure, a cost figure and a price above zero
    func testDraftCompleteness() {
        XCTAssertTrue(StockbookStore.isProductDraftComplete(name: "Deadbolt", stock: "0", cost: "0", price: "12"))
        XCTAssertFalse(StockbookStore.isProductDraftComplete(name: "", stock: "1", cost: "1", price: "1"))
        XCTAssertFalse(StockbookStore.isProductDraftComplete(name: "Deadbolt", stock: "", cost: "1", price: "1"))
        XCTAssertFalse(StockbookStore.isProductDraftComplete(name: "Deadbolt", stock: "1", cost: "", price: "1"))
        XCTAssertFalse(StockbookStore.isProductDraftComplete(name: "Deadbolt", stock: "1", cost: "1", price: "0"),
                       "a selling price of zero is not a selling price")
    }

    // MARK: Billing

    /// Saving a bill snapshots the line and decrements stock
    func testSaveBill() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Cisa lock", stock: 20, cost: 60, price: 95)

        let bill = try XCTUnwrap(
            store.saveBill(
                lines: [.init(productUID: product.uid, qty: 3, price: 95)],
                customer: "  Ahmed Contracting ",
                paid: nil
            )
        )

        XCTAssertEqual(bill.number, 1)
        XCTAssertEqual(bill.total, 285)
        XCTAssertNil(bill.paid, "nil means paid in full")
        XCTAssertEqual(bill.who, "Ahmed Contracting", "the name is trimmed")
        XCTAssertEqual(store.product(uid: product.uid)?.stock, 17)
        XCTAssertEqual(bill.lines.first?.name, "Cisa lock")
    }

    /// An overridden price is charged, and the product keeps its own price
    func testPriceOverride() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Padlock", stock: 5, cost: 20, price: 45)

        let bill = try XCTUnwrap(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 2, price: 40)], customer: "Walk-in", paid: nil)
        )

        XCTAssertEqual(bill.total, 80)
        XCTAssertEqual(bill.lines.first?.price, 40)
        XCTAssertEqual(store.product(uid: product.uid)?.price, 45, "an override is for that bill only")
    }

    /// Editing a product afterwards does not rewrite history
    func testHistoryIsImmutable() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Padlock", stock: 5, cost: 20, price: 45)
        let bill = try XCTUnwrap(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 45)], customer: "Walk-in", paid: nil)
        )

        store.update(product, name: "Padlock 50mm", stock: 5, cost: 25, price: 60)

        let stored = try XCTUnwrap(store.bills.first { $0.number == bill.number })
        XCTAssertEqual(stored.lines.first?.name, "Padlock")
        XCTAssertEqual(stored.lines.first?.price, 45)
        XCTAssertEqual(stored.total, 45)
    }

    /// Stock floors at zero rather than going negative
    func testStockFloor() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 2, cost: 3, price: 6)

        store.saveBill(lines: [.init(productUID: product.uid, qty: 10, price: 6)], customer: "Walk-in", paid: nil)

        XCTAssertEqual(store.product(uid: product.uid)?.stock, 0)
    }

    /// A part payment is clamped to the total
    func testPartPaymentClamp() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 10, cost: 3, price: 6)

        let bill = try XCTUnwrap(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 2, price: 6)], customer: "Sami", paid: 500)
        )

        XCTAssertEqual(bill.paid, 12)
        XCTAssertEqual(bill.balance, 0)
    }

    /// A bill without a customer name is refused
    func testCustomerRequired() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 10, cost: 3, price: 6)

        XCTAssertNil(store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "   ", paid: nil))
        XCTAssertEqual(store.product(uid: product.uid)?.stock, 10, "a refused bill must not touch stock")
    }

    /// Bill numbers are monotonic
    func testBillNumbering() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 10, cost: 3, price: 6)

        let first = try XCTUnwrap(store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "A", paid: nil))
        let second = try XCTUnwrap(store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 6)], customer: "B", paid: nil))

        XCTAssertEqual(first.number, 1)
        XCTAssertEqual(second.number, 2)
    }

    // MARK: Void

    /// Voiding puts the stock back and keeps the bill
    func testVoiding() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Deadbolt", stock: 8, cost: 40, price: 70)
        let bill = try XCTUnwrap(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 3, price: 70)], customer: "Sami", paid: nil)
        )
        XCTAssertEqual(store.product(uid: product.uid)?.stock, 5)

        store.void(bill)

        XCTAssertEqual(store.product(uid: product.uid)?.stock, 8)
        // Bills are values, so the copy returned by saveBill cannot learn that
        // it was voided — the store's own record is the one that matters.
        XCTAssertEqual(store.bills.first { $0.number == bill.number }?.voided, true)
        XCTAssertEqual(store.bills.count, 1, "bills are voided, never deleted")
    }

    /// Voiding twice does not double-restore stock
    func testVoidingIsIdempotent() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Deadbolt", stock: 8, cost: 40, price: 70)
        let bill = try XCTUnwrap(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 3, price: 70)], customer: "Sami", paid: nil)
        )

        store.void(bill)
        store.void(bill)

        XCTAssertEqual(store.product(uid: product.uid)?.stock, 8)
    }

    // MARK: Customers

    /// The owed banner counts distinct people, not bills
    func testOutstandingCountsPeople() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        // Two unpaid bills from the same customer is one customer.
        store.saveBill(lines: [.init(productUID: product.uid, qty: 10, price: 10)], customer: "Ahmed Contracting", paid: 60)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 10, price: 10)], customer: "Ahmed Contracting", paid: 16)

        let owed = store.outstanding()
        XCTAssertEqual(owed.names, ["Ahmed Contracting"])
        XCTAssertEqual(owed.total, 124)
    }

    /// Suggestions rank debtors first, then frequency
    func testSuggestionOrder() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Frequent", paid: nil)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Frequent", paid: nil)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Rare", paid: nil)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 5, price: 10)], customer: "Debtor", paid: 10)

        let names = store.customerSuggestions(matching: "").map(\.name)
        XCTAssertEqual(names.first, "Debtor")
        XCTAssertEqual(names, ["Debtor", "Frequent", "Rare"])
    }

    /// Suggestions filter by what is typed and drop an exact match
    func testSuggestionFiltering() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Ahmed Contracting", paid: nil)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Sami", paid: nil)

        XCTAssertEqual(store.customerSuggestions(matching: "ahm").map(\.name), ["Ahmed Contracting"])
        XCTAssertTrue(store.customerSuggestions(matching: "Ahmed Contracting").isEmpty,
                      "no point suggesting exactly what has been typed")
    }

    /// A voided bill leaves the customer book
    func testVoidedBillsDropOut() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)
        let bill = try XCTUnwrap(
            store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 10)], customer: "Sami", paid: 0)
        )

        store.void(bill)

        XCTAssertTrue(store.customers().isEmpty)
        XCTAssertTrue(store.outstanding().names.isEmpty)
    }

    // MARK: Restock

    /// Quick add raises stock and leaves the buying price alone
    func testQuickAdd() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 4, cost: 18, price: 30)

        store.restock(product, quantity: 50, mode: .quickAdd, unitCost: 99)

        XCTAssertEqual(store.product(uid: product.uid)?.stock, 54)
        XCTAssertEqual(store.product(uid: product.uid)?.cost, 18)
    }

    /// A purchase entry overwrites the buying price with the latest paid
    func testPurchaseEntry() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 4, cost: 18, price: 30)

        store.restock(product, quantity: 50, mode: .purchase, unitCost: 17)

        XCTAssertEqual(store.product(uid: product.uid)?.stock, 54)
        XCTAssertEqual(store.product(uid: product.uid)?.cost, 17, "cost is latest paid, not a weighted average")
    }

    /// A zero quantity does nothing
    func testEmptyRestock() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 4, cost: 18, price: 30)

        store.restock(product, quantity: 0, mode: .purchase, unitCost: 17)

        XCTAssertEqual(store.product(uid: product.uid)?.stock, 4)
        XCTAssertEqual(store.product(uid: product.uid)?.cost, 18)
    }

    // MARK: Start over

    /// Start over clears the shop and returns to setup
    func testStartOver() {
        let store = makeStore()
        let product = store.addProduct(name: "Hinge", stock: 4, cost: 18, price: 30)
        store.saveBill(lines: [.init(productUID: product.uid, qty: 1, price: 30)], customer: "Sami", paid: nil)
        store.setOwnerName("Khalid")
        store.completeSetup()

        store.startOver()

        XCTAssertTrue(store.products.isEmpty)
        XCTAssertTrue(store.bills.isEmpty)
        XCTAssertTrue(store.settings.ownerName.isEmpty)
        XCTAssertFalse(store.settings.setupCompleted)
        XCTAssertEqual(store.settings.nextBillNumber, 1)
    }
}
