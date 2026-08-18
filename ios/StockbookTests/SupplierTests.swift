import Testing
import Foundation
@testable import Stockbook

/// The supplier side of the book: who the shop buys from, and what it owes them.
///
/// A port of `SupplierTests.kt`, assertion for assertion, for the same reason
/// `StoreTests` is a port: the two apps share a file and a shop, so they had
/// better share their arithmetic.
///
/// Where a test here mirrors one in `CustomerRosterTests`, that is deliberate.
/// The two halves are the same sum pointed in opposite directions, and the two
/// bugs the customer half shipped — a payment dropped for somebody with no
/// history, and a total that ignored payments — are the ones this half would have
/// shipped too.
@MainActor
@Suite("Suppliers and purchases")
struct SupplierTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @discardableResult
    private func aProduct(in store: StockbookStore, stock: Int = 0) -> Product {
        store.addProduct(name: "Cisa lock", stock: stock, cost: 60, price: 95)
    }

    // MARK: The roster

    @Test("A supplier entered by hand exists before anything has been delivered")
    func rosterOnly() throws {
        let store = makeStore()
        store.addSupplier(name: "Al Faisal Hardware", phone: "0500 111 222", place: "Dammam")

        let supplier = try #require(store.suppliers().first)
        #expect(supplier.key == "al faisal hardware")
        #expect(supplier.place == "Dammam")
        #expect(supplier.purchaseCount == 0)
        #expect(!supplier.hasHistory)
        #expect(supplier.isOnRoster)
    }

    @Test("Adding the same supplier twice corrects them rather than duplicating them")
    func addTwice() throws {
        let store = makeStore()
        store.addSupplier(name: "Al Faisal Hardware")
        store.addSupplier(name: "AL FAISAL hardware", phone: "0500 111 222")

        #expect(store.suppliers().count == 1)
        let supplier = try #require(store.suppliers().first)
        #expect(supplier.name == "AL FAISAL hardware", "the later spelling wins")
        #expect(supplier.phone == "0500 111 222")
    }

    @Test("A blank name is not a supplier")
    func blankName() {
        let store = makeStore()
        #expect(store.addSupplier(name: "   ") == nil)
        #expect(store.suppliers().isEmpty)
    }

    @Test("A rename brings the purchases and the payments with it")
    func rename() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let record = try #require(store.addSupplier(name: "Al Faisal"))
        store.recordPurchase(product: product, supplierKey: record.key, quantity: 10, unitCost: 60, paid: 0)
        store.recordSupplierPayment(supplierKey: record.key, amount: 200)

        store.updateSupplier(key: record.key, name: "Al Faisal Hardware", phone: nil, place: nil)

        #expect(store.suppliers().count == 1)
        let supplier = try #require(store.suppliers().first)
        #expect(supplier.key == "al faisal hardware")
        #expect(store.purchases(forSupplier: supplier.key).count == 1)
        #expect(store.supplierPayments(for: supplier.key).count == 1)
        // 600 delivered, nothing paid on the day, 200 paid since.
        #expect(supplier.owed == 400)
    }

    // MARK: Deliveries

    @Test("A delivery puts stock on the shelf and takes the new buying price")
    func delivery() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 4)
        let record = try #require(store.addSupplier(name: "Al Faisal"))

        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: record.key, quantity: 10, unitCost: 62.5)
        )

        let updated = try #require(store.product(uid: product.uid))
        #expect(updated.stock == 14)
        #expect(updated.cost == 62.5, "cost is latest paid")
        #expect(purchase.total == 625)
        #expect(purchase.paid == nil, "settled on the spot")
        #expect(purchase.balance == 0)
    }

    @Test("A delivery not paid for lands on what the shop owes")
    func unpaidDelivery() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let record = try #require(store.addSupplier(name: "Al Faisal"))

        store.recordPurchase(product: product, supplierKey: record.key, quantity: 10, unitCost: 60, paid: 100)

        #expect(try #require(store.supplier(key: record.key)).owed == 500)
        let payable = store.payable()
        #expect(payable.names == ["Al Faisal"])
        #expect(payable.total == 500)
    }

    @Test("A delivery cannot be overpaid")
    func overpaid() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let record = try #require(store.addSupplier(name: "Al Faisal"))

        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: record.key, quantity: 2, unitCost: 50, paid: 400)
        )

        #expect(purchase.paid == 100, "clamped to the total")
        #expect(purchase.balance == 0)
    }

    @Test("Nothing delivered is not a purchase, and neither is nobody")
    func noOpDelivery() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 3)
        let record = try #require(store.addSupplier(name: "Al Faisal"))

        #expect(store.recordPurchase(product: product, supplierKey: record.key, quantity: 0, unitCost: 60) == nil)
        #expect(store.recordPurchase(product: product, supplierKey: "", quantity: 5, unitCost: 60) == nil)
        #expect(try #require(store.product(uid: product.uid)).stock == 3)
        #expect(store.purchases.isEmpty)
    }

    // MARK: Removing

    @Test("Removing a delivery takes the stock back off the shelf")
    func removePurchase() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 2)
        let record = try #require(store.addSupplier(name: "Al Faisal"))
        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: record.key, quantity: 10, unitCost: 60, paid: 0)
        )

        store.deletePurchase(id: purchase.id)

        #expect(try #require(store.product(uid: product.uid)).stock == 2)
        #expect(store.purchases.isEmpty, "removed outright rather than marked")
        let supplier = try #require(store.supplier(key: record.key))
        #expect(supplier.owed == 0, "and owed for by nobody")
        #expect(supplier.purchaseCount == 0)
    }

    @Test("Removing twice does not remove the stock twice")
    func removeTwice() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 5)
        let record = try #require(store.addSupplier(name: "Al Faisal"))
        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: record.key, quantity: 3, unitCost: 60)
        )

        store.deletePurchase(id: purchase.id)
        store.deletePurchase(id: purchase.id)

        #expect(try #require(store.product(uid: product.uid)).stock == 5)
    }

    @Test("Stock already sold on floors at zero rather than going negative")
    func removeAfterSelling() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 0)
        let record = try #require(store.addSupplier(name: "Al Faisal"))
        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: record.key, quantity: 4, unitCost: 60)
        )
        // All four sold before anybody noticed the delivery was wrong.
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 4, price: 95)], customer: "Ahmed", paid: nil)

        store.deletePurchase(id: purchase.id)

        #expect(try #require(store.product(uid: product.uid)).stock == 0)
    }

    // MARK: Money out

    @Test("A payment brings down what the shop owes")
    func payment() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let record = try #require(store.addSupplier(name: "Al Faisal"))
        store.recordPurchase(product: product, supplierKey: record.key, quantity: 10, unitCost: 60, paid: 0)

        store.recordSupplierPayment(supplierKey: record.key, amount: 250, note: "cash")

        #expect(try #require(store.supplier(key: record.key)).owed == 350)
        #expect(store.supplierPayments(for: record.key).first?.note == "cash")
    }

    /// The bug the customer side shipped, written down before it can be shipped
    /// here: a supplier entered with what the paper book says is owed, paid off
    /// before a single delivery has been recorded through the app.
    @Test("A payment to a supplier who has never delivered still counts")
    func paymentWithoutPurchases() throws {
        let store = makeStore()
        let record = try #require(store.addSupplier(name: "Al Faisal", openingBalance: 1000))

        store.recordSupplierPayment(supplierKey: record.key, amount: 400)

        #expect(try #require(store.supplier(key: record.key)).owed == 600)
    }

    @Test("A deleted payment puts the balance back")
    func deletePayment() throws {
        let store = makeStore()
        let record = try #require(store.addSupplier(name: "Al Faisal", openingBalance: 1000))
        let payment = try #require(store.recordSupplierPayment(supplierKey: record.key, amount: 400))

        store.deleteSupplierPayment(id: payment.id)

        #expect(try #require(store.supplier(key: record.key)).owed == 1000)
    }

    @Test("Paying ahead reads as an advance rather than as nothing")
    func advance() throws {
        let store = makeStore()
        let record = try #require(store.addSupplier(name: "Al Faisal", openingBalance: 100))

        store.recordSupplierPayment(supplierKey: record.key, amount: 250)

        #expect(try #require(store.supplier(key: record.key)).owed == -150)
        #expect(store.payable().names.isEmpty, "an advance is not a debt")
        #expect(store.payable().total == 0)
    }

    /// The mirror of the Today banner's bug: a total that ignored payments.
    @Test("What the shop owes counts payments and carried-over balances")
    func payableIsDerived() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let settled = try #require(store.addSupplier(name: "Settled Up"))
        store.addSupplier(name: "Still Owed", openingBalance: 300)

        store.recordPurchase(product: product, supplierKey: settled.key, quantity: 5, unitCost: 60, paid: 0)
        store.recordSupplierPayment(supplierKey: settled.key, amount: 300)

        let payable = store.payable()
        #expect(payable.names == ["Still Owed"], "a supplier paid in full is not owed anything")
        #expect(payable.total == 300)
    }

    // MARK: Statements

    @Test("A supplier statement carries the balance forward and reads downwards")
    func statement() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let record = try #require(store.addSupplier(name: "Al Faisal", openingBalance: 700))
        store.recordPurchase(
            product: product,
            supplierKey: record.key,
            quantity: 5,
            unitCost: 60,
            paid: 0,
            createdAt: Date(timeIntervalSince1970: 1_785_312_000)
        )
        store.recordSupplierPayment(
            supplierKey: record.key,
            amount: 200,
            paidAt: Date(timeIntervalSince1970: 1_786_694_400)
        )

        let statement = try #require(
            store.statementForSupplier(
                key: record.key,
                period: .custom(
                    from: Date(timeIntervalSince1970: 1_785_225_600),
                    to: Date(timeIntervalSince1970: 1_787_212_800)
                )
            )
        )

        #expect(statement.openingBalance == 700, "carried over from the paper book")
        #expect(statement.billed == 300)
        #expect(statement.received == 200)
        #expect(statement.closingBalance == 800)
        #expect(statement.entries.count == 2)
        #expect(statement.runningBalances.last == statement.closingBalance)
        #expect(statement.party.isSupplier)
    }

    @Test("A statement for a supplier nobody has heard of is nil")
    func noStatement() {
        #expect(makeStore().statementForSupplier(key: "nobody", period: .thisMonth()) == nil)
    }

    // MARK: The file

    @Test("A backup carries the supplier side to the new phone")
    func backupRoundTrip() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let record = try #require(
            store.addSupplier(name: "Al Faisal", phone: "0500 111 222", openingBalance: 700)
        )
        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: record.key, quantity: 5, unitCost: 60, paid: 100)
        )
        store.recordSupplierPayment(supplierKey: record.key, amount: 200, note: "cash")

        let document = try BackupService.decode(try BackupService.encode(store.makeBackupDocument()))

        // The bump is the rule being applied, not abandoned: a reader that dropped
        // these would say the shop owes nobody. Pinned against the constant so a
        // later bump does not drag this test with it — what it checks is that the
        // supplier side reaches the file, not what number is on it.
        #expect(document.version == BackupDocument.currentVersion)
        #expect(document.suppliers.count == 1)
        #expect(document.purchases.count == 1)
        #expect(document.supplierPayments.count == 1)

        let restored = makeStore()
        restored.replaceEverything(with: document)

        let supplier = try #require(restored.supplier(key: record.key))
        #expect(supplier.phone == "0500 111 222")
        #expect(supplier.openingBalance == 700)
        // 700 carried over + 300 delivered − 100 paid on the day − 200 paid since.
        #expect(supplier.owed == 700)
        #expect(restored.purchases.first?.id == purchase.id)
        #expect(restored.supplierPayments.first?.note == "cash")
    }

    @Test("Starting over clears the supplier side too")
    func startOver() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let record = try #require(store.addSupplier(name: "Al Faisal"))
        store.recordPurchase(product: product, supplierKey: record.key, quantity: 5, unitCost: 60, paid: 0)
        store.recordSupplierPayment(supplierKey: record.key, amount: 50)

        store.startOver()

        #expect(store.suppliers().isEmpty)
        #expect(store.purchases.isEmpty)
        #expect(store.supplierPayments.isEmpty)
    }

    @Test("The two sides of the book do not leak into each other")
    func twoSides() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 10)
        let supplier = try #require(store.addSupplier(name: "Ahmed"))
        store.addCustomer(name: "Ahmed", openingBalance: 500)
        store.recordPurchase(product: product, supplierKey: supplier.key, quantity: 5, unitCost: 60, paid: 0)

        // One name, two accounts, pointing opposite ways. The keys match, which is
        // exactly why this has to be checked.
        #expect(Supplier.key(for: "Ahmed") == Customer.key(for: "Ahmed"))
        #expect(try #require(store.supplier(key: "ahmed")).owed == 300, "what the shop owes")
        #expect(try #require(store.customer(key: "ahmed")).owed == 500, "what Ahmed owes the shop")
        #expect(try #require(store.customer(key: "ahmed")).billCount == 0)
    }
}
