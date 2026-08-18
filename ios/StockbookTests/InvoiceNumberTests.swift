import Testing
import Foundation
@testable import Stockbook

/// The number on the paper, and the day the thing actually happened.
///
/// A port of `InvoiceNumberTests.kt`, assertion for assertion. Both sides of the
/// book carry a number now, and both are the same kind of thing: a label the
/// owner recognises, not a key. The app's own `Bill.number` stays what identity
/// is built on — these tests exist partly to pin that difference down, because
/// conflating the two is how a duplicate typed number would start overwriting
/// history.
@MainActor
@Suite("Invoice numbers")
struct InvoiceNumberTests {

    private let english = Strings(language: .english)

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @discardableResult
    private func aProduct(in store: StockbookStore) -> Product {
        store.addProduct(name: "Cisa lock", stock: 50, cost: 60, price: 95)
    }

    // MARK: Sales

    @Test("A bill keeps the number written on the paper")
    func billKeepsPaperNumber() throws {
        let store = makeStore()
        let product = aProduct(in: store)

        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
                customer: "Ahmed",
                paid: nil,
                invoiceNo: "A-1024"
            )
        )

        #expect(bill.invoiceNo == "A-1024")
        #expect(bill.reference(english) == "A-1024", "the paper's number is what shows")
        #expect(bill.number == 1, "and the app's own counter is untouched")
    }

    @Test("A bill with no paper behind it falls back to the app's own number")
    func billWithoutPaperNumber() throws {
        let store = makeStore()
        let product = aProduct(in: store)

        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
                customer: "Ahmed",
                paid: nil
            )
        )

        #expect(bill.invoiceNo == nil)
        #expect(bill.reference(english) == english.billNumber(1))
    }

    @Test("A blank invoice number is absent, not an empty string")
    func blankIsAbsent() throws {
        let store = makeStore()
        let product = aProduct(in: store)

        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
                customer: "Ahmed",
                paid: nil,
                invoiceNo: "   "
            )
        )

        #expect(bill.invoiceNo == nil, "otherwise “has an invoice number” is true for a bill with none")
        #expect(bill.reference(english) == english.billNumber(1))
    }

    // MARK: Typed, not generated

    @Test("The next number carries on from the last one written")
    func nextNumber() {
        let store = makeStore()
        let product = aProduct(in: store)
        #expect(store.nextInvoiceNo() == nil, "nothing to go on before the first bill")

        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: nil,
            invoiceNo: "A-1024"
        )
        #expect(store.nextInvoiceNo() == "A-1025", "the prefix is the book's, only the digits move")

        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
            customer: "Sami",
            paid: nil,
            invoiceNo: "0099"
        )
        #expect(store.nextInvoiceNo() == "0100", "and the width the shop writes is kept")
    }

    @Test("A number with no digits in it suggests nothing")
    func nothingToIncrement() {
        let store = makeStore()
        let product = aProduct(in: store)
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: nil,
            invoiceNo: "INV"
        )

        // Better an empty box than a wrong guess: the owner types what the paper
        // says, and the run continues from there.
        #expect(store.nextInvoiceNo() == nil)
    }

    @Test("A number already used is found, whatever case it was typed in")
    func clashFound() {
        let store = makeStore()
        let product = aProduct(in: store)
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: nil,
            invoiceNo: "A-1024"
        )

        #expect(store.billWithInvoiceNo(" a-1024 ")?.who == "Ahmed")
        #expect(store.billWithInvoiceNo("A-1025") == nil)
        #expect(store.billWithInvoiceNo("") == nil, "an empty box is not a clash with every blank bill")
    }

    @Test("Removing a bill frees its number")
    func removingFreesTheNumber() throws {
        // The correction path: a bill typed wrong is removed and entered again,
        // and the wrong one must not keep the paper's number to itself.
        let store = makeStore()
        let product = aProduct(in: store)
        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
                customer: "Ahmed",
                paid: nil,
                invoiceNo: "1024"
            )
        )

        store.deleteBill(number: bill.number)

        #expect(store.billWithInvoiceNo("1024") == nil)
    }

    @Test("The store still records what it is told")
    func storeDoesNotRefuse() {
        // The refusal is the screen's: it can put the number back in front of the
        // person who typed it. The store cannot, and a backup being restored — or
        // a file written by an older build — must never lose a bill because two
        // of them share a label.
        let store = makeStore()
        let product = aProduct(in: store)

        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: nil,
            invoiceNo: "1024"
        )
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
            customer: "Sami",
            paid: nil,
            invoiceNo: "1024"
        )

        #expect(store.bills.count == 2)
        #expect(store.bills.map(\.number) == [2, 1], "distinct, and newest first")
    }

    // MARK: The day it happened

    @Test("A bill can be entered for the day it actually happened")
    func backdating() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let yesterday = date("2026-08-16T09:30:00Z")

        let bill = try #require(
            store.saveBill(
                lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
                customer: "Ahmed",
                paid: nil,
                createdAt: yesterday
            )
        )

        #expect(bill.createdAt == yesterday)
    }

    @Test("A backdated bill lands in the period it belongs to")
    func backdatedStatement() throws {
        // The reason the date matters at all: a statement is the document somebody
        // settles up against, and a bill entered at closing time under today's
        // date would appear in the wrong month at the turn of one.
        let store = makeStore()
        let product = aProduct(in: store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
            customer: "Ahmed",
            paid: 0,
            createdAt: date("2026-07-20T09:30:00Z")
        )

        let july = try #require(store.statement(forCustomer: "ahmed", period: .month(date("2026-07-10T00:00:00Z"))))
        let august = try #require(store.statement(forCustomer: "ahmed", period: .month(date("2026-08-10T00:00:00Z"))))

        #expect(july.billed == 190)
        #expect(august.billed == 0)
        #expect(august.openingBalance == 190, "and July's debt is carried forward, not lost")
    }

    // MARK: Deliveries

    @Test("A delivery keeps the supplier's invoice number")
    func deliveryKeepsNumber() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))

        let purchase = try #require(
            store.recordPurchase(
                product: product,
                supplierKey: supplier.key,
                quantity: 10,
                unitCost: 60,
                invoiceNo: "INV-88"
            )
        )

        #expect(purchase.invoiceNo == "INV-88")
        #expect(purchase.reference(english) == "INV-88", "which is what a statement calls it")
    }

    @Test("A delivery with no paper is called what it is")
    func deliveryWithoutNumber() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))

        let purchase = try #require(
            store.recordPurchase(product: product, supplierKey: supplier.key, quantity: 10, unitCost: 60)
        )

        #expect(purchase.invoiceNo == nil)
        #expect(purchase.reference(english) == english.purchaseLabel)
    }

    @Test("A delivery already filed under a number is found across suppliers")
    func deliveryClashAcrossSuppliers() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let first = try #require(store.addSupplier(name: "Al Faisal"))
        _ = try #require(store.addSupplier(name: "Rashid Trading"))
        store.recordPurchase(
            product: product,
            supplierKey: first.key,
            quantity: 5,
            unitCost: 60,
            invoiceNo: "INV-88"
        )

        #expect(store.purchaseWithInvoiceNo("inv-88")?.supplierKey == first.key)
        #expect(store.purchaseWithInvoiceNo("INV-89") == nil)
    }

    // MARK: The file

    @Test("Both numbers survive a backup round trip")
    func roundTrip() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)],
            customer: "Ahmed",
            paid: nil,
            invoiceNo: "A-1024"
        )
        store.recordPurchase(
            product: product,
            supplierKey: supplier.key,
            quantity: 5,
            unitCost: 60,
            invoiceNo: "INV-88"
        )

        let document = try BackupService.decode(try BackupService.encode(store.makeBackupDocument()))

        // No version bump: a reader that drops these shows "Bill #1" where the
        // owner wrote 1024. A label lost, not a figure misread.
        #expect(document.version == 2)

        let restored = makeStore()
        restored.replaceEverything(with: document)
        #expect(restored.bills.first?.invoiceNo == "A-1024")
        #expect(restored.purchases.first?.invoiceNo == "INV-88")
    }

    private func date(_ iso: String) -> Date {
        let formatter = ISO8601DateFormatter()
        return formatter.date(from: iso)!
    }
}
