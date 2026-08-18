import Testing
import Foundation
@testable import Stockbook

/// A bill is a number, a date, somebody and a figure. Saying what was sold is
/// optional.
///
/// A port of `AmountFirstTests.kt`, assertion for assertion. This is the shape of
/// the shop rather than a shortcut: the bill is written in a paper book first, so
/// the total is already known, and rebuilding it product by product to arrive at a
/// figure that can be read off the paper is work for nothing.
///
/// The whole cost of that decision is one rule, and it is what these tests pin
/// down: **the shelf moves only for what was itemised.** Anything else would be
/// the app inventing stock movements nobody described.
@MainActor
@Suite("Amount-first bills")
struct AmountFirstTests {

    private let english = Strings(language: .english)

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @discardableResult
    private func aProduct(in store: StockbookStore, stock: Int = 50) -> Product {
        store.addProduct(name: "Cisa lock", stock: stock, cost: 60, price: 95)
    }

    // MARK: Sales

    @Test("A bill can be a figure and nothing else")
    func billIsAFigure() throws {
        let store = makeStore()

        let bill = try #require(
            store.saveBill(customer: "Ahmed", paid: nil, amount: 450, invoiceNo: "A-1024")
        )

        #expect(bill.total == 450)
        #expect(bill.lines.isEmpty)
        #expect(!bill.isItemised)
        #expect(bill.reference(english) == "A-1024")
    }

    @Test("A bill with no items leaves the shelf alone")
    func noItemsLeavesTheShelfAlone() throws {
        // The point of the whole trade: nothing here says a lock left the shop,
        // so nothing may claim one did.
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)

        store.saveBill(customer: "Ahmed", paid: nil, amount: 450)

        #expect(try #require(store.product(uid: product.uid)).stock == 50)
    }

    @Test("An itemised bill still moves the shelf")
    func itemisedStillMovesTheShelf() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)

        let bill = try #require(
            store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)], customer: "Ahmed", paid: nil)
        )

        #expect(bill.isItemised)
        #expect(bill.total == 190)
        #expect(try #require(store.product(uid: product.uid)).stock == 48)
    }

    @Test("Items win over a typed figure")
    func itemsWinOverATypedFigure() throws {
        // Two answers to "what did it come to" is one too many. The lines are the
        // ones with arithmetic behind them, so they are the ones that count.
        let store = makeStore()
        let product = aProduct(in: store)

        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
                customer: "Ahmed",
                paid: nil,
                amount: 9_999
            )
        )

        #expect(bill.total == 190)
    }

    @Test("A bill for nothing is not a bill")
    func billForNothing() {
        let store = makeStore()

        #expect(store.saveBill(customer: "Ahmed", paid: nil) == nil, "no lines and no figure")
        #expect(store.saveBill(customer: "Ahmed", paid: nil, amount: 0) == nil)
        #expect(store.saveBill(customer: "", paid: nil, amount: 450) == nil, "and somebody is still required")
        #expect(store.bills.isEmpty)
    }

    @Test("Part payment works the same either way")
    func partPaymentEitherWay() throws {
        let store = makeStore()

        let bill = try #require(store.saveBill(customer: "Ahmed", paid: 100, amount: 450))

        #expect(bill.balance == 350)
        let ahmed = try #require(store.customers().first { $0.key == "ahmed" })
        #expect(ahmed.owed == 350)
    }

    @Test("Voiding a bill with no items takes nothing off the shelf")
    func voidingABillWithNoItems() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let bill = try #require(store.saveBill(customer: "Ahmed", paid: 0, amount: 450))

        store.void(bill)

        #expect(try #require(store.product(uid: product.uid)).stock == 50, "nothing went out, so nothing comes back")
        // Ahmed was never on the roster and his one bill is now void, so he may
        // not be listed at all — what matters is that nothing is owed either way.
        let owed = store.customers().first { $0.key == "ahmed" }?.owed ?? 0
        #expect(owed == 0)
    }

    // MARK: Supplier bills

    @Test("A supplier bill can be a figure and nothing else")
    func supplierBillIsAFigure() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))

        let purchase = try #require(
            store.recordSupplierBill(supplierKey: supplier.key, amount: 800, paid: 0, invoiceNo: "INV-88")
        )

        #expect(purchase.total == 800)
        #expect(!purchase.isItemised)
        #expect(purchase.name == nil)
        #expect(try #require(store.product(uid: product.uid)).stock == 50, "and no stock arrived")
        #expect(try #require(store.suppliers().first).owed == 800)
    }

    @Test("A delivery with a product on it still fills the shelf")
    func deliveryStillFillsTheShelf() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 10)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))

        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: supplier.key, quantity: 5, unitCost: 62)
        )

        #expect(purchase.isItemised)
        #expect(purchase.total == 310)
        let restocked = try #require(store.product(uid: product.uid))
        #expect(restocked.stock == 15)
        #expect(restocked.cost == 62, "latest paid takes over, as it always has")
    }

    @Test("A product with no quantity is half an answer")
    func productWithNoQuantity() throws {
        // Rejected rather than guessed: putting an invented count on the shelf is
        // exactly the sort of quiet wrongness the shelf rule exists to prevent.
        let store = makeStore()
        let product = aProduct(in: store, stock: 10)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))

        #expect(store.recordPurchase(product: product, supplierKey: supplier.key, quantity: 0, unitCost: 60) == nil)
        #expect(try #require(store.product(uid: product.uid)).stock == 10)
    }

    @Test("Voiding a supplier bill with no product takes nothing off the shelf")
    func voidingASupplierBillWithNoProduct() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 10)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        let purchase = try #require(store.recordSupplierBill(supplierKey: supplier.key, amount: 800, paid: 0))

        store.voidPurchase(id: purchase.id)

        #expect(try #require(store.product(uid: product.uid)).stock == 10)
        #expect(try #require(store.suppliers().first).owed == 0)
    }

    // MARK: The shelf, corrected by hand

    @Test("A counted shelf is set, not adjusted")
    func countedShelfIsSet() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)

        store.setStock(product, count: 12)

        #expect(try #require(store.product(uid: product.uid)).stock == 12)
    }

    @Test("A shelf cannot be counted below nothing")
    func shelfCannotBeCountedBelowNothing() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)

        store.setStock(product, count: -3)

        #expect(try #require(store.product(uid: product.uid)).stock == 0)
    }

    // MARK: Everything downstream

    @Test("Both kinds of bill appear on a statement")
    func bothKindsOnAStatement() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0)
        store.saveBill(customer: "Ahmed", paid: 0, amount: 450)

        let statement = try #require(store.statement(forCustomer: "ahmed", period: .month(Date.now)))

        #expect(statement.entries.count == 2)
        #expect(statement.billed == 640)
        #expect(statement.closingBalance == 640)
    }

    @Test("Both kinds survive a backup round trip")
    func bothKindsSurviveABackupRoundTrip() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: nil,
            invoiceNo: "A-1"
        )
        store.saveBill(customer: "Sami", paid: nil, amount: 450, invoiceNo: "A-2")
        store.recordPurchase(
            product: product,
            supplierKey: supplier.key,
            quantity: 5,
            unitCost: 60,
            invoiceNo: "INV-1"
        )
        store.recordSupplierBill(supplierKey: supplier.key, amount: 800, invoiceNo: "INV-2")

        let document = try BackupService.decode(try BackupService.encode(store.makeBackupDocument()))
        let restored = makeStore()
        restored.replaceEverything(with: document)

        let bills = restored.bills.sorted { $0.number < $1.number }
        #expect(bills[0].isItemised)
        #expect(!bills[1].isItemised)
        #expect(bills[1].total == 450)

        let purchases = restored.purchases.sorted { ($0.invoiceNo ?? "") < ($1.invoiceNo ?? "") }
        #expect(purchases[0].isItemised)
        #expect(!purchases[1].isItemised)
        #expect(purchases[1].total == 800)
    }
}
