import Testing
import Foundation
@testable import Stockbook

/// Correcting a document, rather than marking it.
///
/// A port of `EditingTests.kt`, case for case. A bill entered wrongly is **edited
/// or removed**: this is the shop's own book, kept by the one person who writes in
/// it, and the record that outlives a correction is the paper bill rather than a
/// crossed-out row in the app.
///
/// All of the risk is in the shelf. An edit has to move stock by the *difference*
/// between what the bill used to say and what it says now, and getting that wrong
/// is invisible until somebody counts a bin — which is why every case below checks
/// the count rather than only the figures.
@MainActor
@Suite("Editing and removing")
struct EditingTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @discardableResult
    private func aProduct(in store: StockbookStore, stock: Int = 50) -> Product {
        store.addProduct(name: "Cisa lock", stock: stock, cost: 60, price: 95)
    }

    /// A fixed day, so an edited document's date is something to assert against
    /// rather than whatever "now" happened to be.
    private let day = ISO8601DateFormatter().date(from: "2026-08-18T09:00:00Z") ?? .now

    // MARK: Editing a bill

    @Test("Editing a typed figure changes what is owed and leaves the shelf alone")
    func editingATypedFigure() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let bill = try #require(
            store.saveBill(customer: "Ahmed", paid: nil, amount: 450, invoiceNo: "A-1024")
        )

        let edited = try #require(
            store.updateBill(
                number: bill.number,
                customer: "Ahmed",
                paid: 100,
                amount: 400,
                createdAt: day,
                invoiceNo: "A-1024"
            )
        )

        #expect(edited.total == 400)
        #expect(edited.balance == 300)
        #expect(edited.number == bill.number, "the same bill, not a new one")
        #expect(store.bills.count == 1)
        #expect(try #require(store.product(uid: product.uid)).stock == 50)
    }

    @Test("Editing a quantity upwards takes only the difference off the shelf")
    func editingAQuantityUpwards() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
                customer: "Ahmed",
                paid: nil
            )
        )
        #expect(try #require(store.product(uid: product.uid)).stock == 48)

        store.updateBill(
            number: bill.number,
            lines: [DraftLine(productUID: product.uid, qty: 5, price: 95)],
            customer: "Ahmed",
            paid: nil,
            createdAt: day
        )

        #expect(try #require(store.product(uid: product.uid)).stock == 45,
                "three more went out, not five")
    }

    @Test("Editing a quantity downwards puts the difference back")
    func editingAQuantityDownwards() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 5, price: 95)],
                customer: "Ahmed",
                paid: nil
            )
        )

        store.updateBill(
            number: bill.number,
            lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
            customer: "Ahmed",
            paid: nil,
            createdAt: day
        )

        #expect(try #require(store.product(uid: product.uid)).stock == 48)
    }

    @Test("Dropping the items altogether gives all the stock back")
    func droppingTheItems() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 4, price: 95)],
                customer: "Ahmed",
                paid: nil
            )
        )

        let edited = try #require(
            store.updateBill(
                number: bill.number,
                customer: "Ahmed",
                paid: nil,
                amount: 380,
                createdAt: day
            )
        )

        #expect(!edited.isItemised)
        #expect(edited.total == 380)
        #expect(try #require(store.product(uid: product.uid)).stock == 50)
    }

    @Test("Itemising a typed bill takes the stock off")
    func itemisingATypedBill() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let bill = try #require(store.saveBill(customer: "Ahmed", paid: nil, amount: 380))

        let edited = try #require(
            store.updateBill(
                number: bill.number,
                lines: [DraftLine(productUID: product.uid, qty: 4, price: 95)],
                customer: "Ahmed",
                paid: nil,
                createdAt: day
            )
        )

        #expect(edited.isItemised)
        #expect(edited.total == 380, "and the sum takes over from the typed figure")
        #expect(try #require(store.product(uid: product.uid)).stock == 46)
    }

    @Test("An edit that would not be a bill changes nothing at all")
    func aRefusedEditChangesNothing() throws {
        // Half-applying an edit is the worst outcome available: the stock would
        // have moved for a bill that was never saved, and nothing on screen would
        // say so.
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
                customer: "Ahmed",
                paid: nil
            )
        )

        #expect(
            store.updateBill(number: bill.number, customer: "   ", paid: nil, amount: 100, createdAt: day) == nil
        )
        #expect(
            store.updateBill(number: bill.number, customer: "Ahmed", paid: nil, amount: 0, createdAt: day) == nil
        )

        #expect(try #require(store.product(uid: product.uid)).stock == 48)
        #expect(store.bills.first?.total == 190)
    }

    @Test("Editing an unknown bill does nothing")
    func editingAnUnknownBill() {
        let store = makeStore()

        #expect(store.updateBill(number: 99, customer: "Ahmed", paid: nil, amount: 100, createdAt: day) == nil)
    }

    @Test("A bill does not clash with its own number")
    func aBillDoesNotClashWithItself() throws {
        // Without this, opening 1024 to fix its date would be told 1024 is taken.
        let store = makeStore()
        let bill = try #require(
            store.saveBill(customer: "Ahmed", paid: nil, amount: 450, invoiceNo: "1024")
        )
        store.saveBill(customer: "Sami", paid: nil, amount: 200, invoiceNo: "1025")

        #expect(store.billWithInvoiceNo("1024", exceptNumber: bill.number) == nil)
        #expect(store.billWithInvoiceNo("1025", exceptNumber: bill.number)?.who == "Sami")
    }

    @Test("Moving a bill to another customer moves the debt with it")
    func movingABillToAnotherCustomer() throws {
        let store = makeStore()
        let bill = try #require(store.saveBill(customer: "Ahmed", paid: 0, amount: 450))

        store.updateBill(number: bill.number, customer: "Sami", paid: 0, amount: 450, createdAt: day)

        #expect(store.customers().first { $0.key == "ahmed" } == nil)
        #expect(try #require(store.customers().first { $0.key == "sami" }).owed == 450)
    }

    // MARK: Removing a bill

    @Test("Removing a bill puts its stock back and leaves the others alone")
    func removingABill() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 50)
        let first = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
                customer: "Ahmed",
                paid: nil
            )
        )
        let second = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 3, price: 95)],
                customer: "Sami",
                paid: nil
            )
        )
        #expect(try #require(store.product(uid: product.uid)).stock == 45)

        store.deleteBill(number: second.number)

        #expect(try #require(store.product(uid: product.uid)).stock == 48)
        #expect(store.bills.map(\.number) == [first.number])
    }

    // MARK: The other side of the book

    @Test("Editing a delivery moves the shelf by the difference")
    func editingADelivery() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 10)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: supplier.key, quantity: 5, unitCost: 60)
        )
        #expect(try #require(store.product(uid: product.uid)).stock == 15)

        let edited = try #require(
            store.updatePurchase(
                id: purchase.id,
                product: product,
                supplierKey: supplier.key,
                quantity: 8,
                unitCost: 62,
                createdAt: day
            )
        )

        #expect(try #require(store.product(uid: product.uid)).stock == 18)
        #expect(edited.total == 496)
        #expect(try #require(store.product(uid: product.uid)).cost == 62, "latest paid still takes over")
    }

    @Test("Editing a delivery down to a bare figure gives back everything it added")
    func editingADeliveryDownToAFigure() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 10)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: supplier.key, quantity: 5, unitCost: 60)
        )

        let edited = try #require(
            store.updatePurchase(
                id: purchase.id,
                product: nil,
                supplierKey: supplier.key,
                paid: 0,
                amount: 300,
                createdAt: day
            )
        )

        #expect(!edited.isItemised)
        #expect(try #require(store.product(uid: product.uid)).stock == 10)
        #expect(try #require(store.supplier(key: supplier.key)).owed == 300)
    }

    @Test("A supplier bill does not clash with its own number")
    func aSupplierBillDoesNotClashWithItself() throws {
        let store = makeStore()
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        let purchase = try #require(
            store.recordSupplierBill(supplierKey: supplier.key, amount: 800, invoiceNo: "INV-88")
        )

        #expect(store.purchaseWithInvoiceNo("INV-88", exceptId: purchase.id) == nil)
    }

    @Test("An edit that would not be a delivery changes nothing")
    func aRefusedDeliveryEditChangesNothing() throws {
        let store = makeStore()
        let product = aProduct(in: store, stock: 10)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: supplier.key, quantity: 5, unitCost: 60)
        )

        #expect(
            store.updatePurchase(id: purchase.id, product: nil, supplierKey: supplier.key, createdAt: day) == nil
        )
        #expect(
            store.updatePurchase(id: purchase.id, product: nil, supplierKey: "", amount: 50, createdAt: day) == nil
        )

        #expect(try #require(store.product(uid: product.uid)).stock == 15)
        #expect(store.purchases.first?.total == 300)
    }
}
