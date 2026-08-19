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

    // MARK: Receipts

    @Test("A payment keeps the number written on the receipt")
    func paymentKeepsItsNumber() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, invoiceNo: "1024")

        let payment = try #require(
            store.recordPayment(customerKey: "ahmed", amount: 300, paymentNo: " 008455 ")
        )

        // Trimmed, like every other typed number: a trailing space is a thumb
        // slip, not part of what the shop wrote.
        #expect(payment.paymentNo == "008455")
    }

    @Test("A receipt book is numbered separately from the bill book")
    func receiptsAreTheirOwnSeries() throws {
        // The shop's paper works this way, and pretending otherwise would be the
        // app inventing a rule. Receipt 1024 and invoice 1024 are different slips.
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, invoiceNo: "1024")

        #expect(store.paymentWithNo("1024") == nil)

        store.recordPayment(customerKey: "ahmed", amount: 100, paymentNo: "1024")
        #expect(store.paymentWithNo("1024") != nil)
    }

    @Test("Money out is its own book again")
    func supplierReceiptsAreTheirOwnSeries() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        store.recordPayment(customerKey: "ahmed", amount: 100, paymentNo: "77")

        // Nothing the shop received tells it anything about what it paid out.
        #expect(store.supplierPaymentWithNo("77") == nil)

        store.recordSupplierPayment(supplierKey: supplier.key, amount: 50, paymentNo: "77")
        #expect(store.supplierPaymentWithNo("77") != nil)
    }

    @Test("A receipt never clashes with itself")
    func receiptExcludesItself() throws {
        // What the sheet asks while somebody is looking at a saved receipt.
        // Without the exception, opening one to fix its date would be told its
        // own number is taken.
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        let payment = try #require(
            store.recordPayment(customerKey: "ahmed", amount: 100, paymentNo: "008455")
        )

        #expect(store.paymentWithNo("008455", exceptId: payment.id) == nil)
        #expect(store.paymentWithNo("008455") != nil)
    }

    @Test("A receipt with no number asks nothing of the book")
    func blankReceiptNumbersNeverClash() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        store.recordPayment(customerKey: "ahmed", amount: 100, paymentNo: "  ")

        // Blank is absent, and absent cannot clash — otherwise the first
        // unnumbered receipt would block every one after it.
        #expect(store.payments.first?.paymentNo == nil)
        #expect(store.paymentWithNo("") == nil)
        #expect(store.paymentWithNo(nil) == nil)
    }

    // MARK: Correcting one

    @Test("A payment can be corrected in every part")
    func paymentCorrectsWholly() throws {
        // All four, because all four can be mistyped. This did not exist while a
        // payment was an amount and a date; a receipt number is what made
        // deleting and re-entering the wrong answer.
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        let payment = try #require(
            store.recordPayment(customerKey: "ahmed", amount: 300, receivedAt: date("2026-08-01T09:00:00Z"), paymentNo: "1")
        )

        let corrected = try #require(
            store.updatePayment(
                id: payment.id,
                amount: 350,
                receivedAt: date("2026-08-03T09:00:00Z"),
                note: "cheque",
                paymentNo: "008455"
            )
        )

        #expect(corrected.id == payment.id, "correcting is not re-recording")
        #expect(corrected.amount == 350)
        #expect(corrected.paymentNo == "008455")
        #expect(corrected.note == "cheque")
        #expect(corrected.receivedAt == date("2026-08-03T09:00:00Z"))
    }

    @Test("Correcting a payment moves what the customer owes")
    func correctionMovesTheBalance() throws {
        // The figure on the statement is the point. A correction that left the
        // balance where it was would be a correction in name only.
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, invoiceNo: "1")
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 300, paymentNo: "R-1"))

        #expect(try #require(store.customer(key: "ahmed")).owed == 700)

        store.updatePayment(id: payment.id, amount: 400, receivedAt: payment.receivedAt, paymentNo: "R-1")

        #expect(try #require(store.customer(key: "ahmed")).owed == 600)
    }

    @Test("A corrected receipt never clashes with itself")
    func correctedReceiptExcludesItself() throws {
        // What the sheet asks while the payment is open. Without the exception,
        // fixing the amount would be refused because the number is "taken" — by
        // the very record being edited.
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 300, paymentNo: "008455"))

        #expect(store.paymentWithNo("008455", exceptId: payment.id) == nil)

        store.updatePayment(id: payment.id, amount: 300, receivedAt: payment.receivedAt, paymentNo: "008455")
        #expect(store.payments.first?.paymentNo == "008455")
    }

    @Test("A payment cannot be corrected to nothing")
    func correctionRefusesZero() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 300, paymentNo: "R-1"))

        #expect(store.updatePayment(id: payment.id, amount: 0, receivedAt: payment.receivedAt) == nil)
        #expect(store.payments.first?.amount == 300)
    }

    @Test("Correcting one that is not there changes nothing")
    func correctionOfNothing() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        store.recordPayment(customerKey: "ahmed", amount: 300, paymentNo: "R-1")

        #expect(store.updatePayment(id: UUID(), amount: 999, receivedAt: .now) == nil)
        #expect(store.payments.count == 1)
    }

    @Test("Money paid out corrects the same way")
    func supplierPaymentCorrects() throws {
        let store = makeStore()
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        let product = store.addProduct(name: "Cisa lock", stock: 0, cost: 60, price: 95)
        store.recordPurchase(product: product, supplierKey: supplier.key, quantity: 10, unitCost: 60, paid: 0, invoiceNo: "INV-1")
        let payment = try #require(store.recordSupplierPayment(supplierKey: supplier.key, amount: 200, paymentNo: "P-1"))

        #expect(try #require(store.supplier(key: supplier.key)).owed == 400)

        let corrected = try #require(
            store.updateSupplierPayment(
                id: payment.id,
                amount: 500,
                paidAt: date("2026-08-05T09:00:00Z"),
                paymentNo: "P-2"
            )
        )

        #expect(corrected.paymentNo == "P-2")
        #expect(try #require(store.supplier(key: supplier.key)).owed == 100)
    }

    @Test("A corrected payment survives a backup round trip")
    func correctedPaymentRoundTrips() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 300, paymentNo: "R-1"))
        store.updatePayment(id: payment.id, amount: 450, receivedAt: payment.receivedAt, paymentNo: "008455")

        let restored = makeStore()
        restored.replaceEverything(with: try BackupService.decode(try BackupService.encode(store.makeBackupDocument())))

        #expect(restored.payments.first?.amount == 450)
        #expect(restored.payments.first?.paymentNo == "008455")
    }

    // MARK: The file

    @Test("Receipt numbers survive a backup round trip")
    func receiptRoundTrip() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        store.recordPayment(customerKey: "ahmed", amount: 300, paymentNo: "008455")
        store.recordSupplierPayment(supplierKey: supplier.key, amount: 500, paymentNo: "P-12")

        let document = try BackupService.decode(try BackupService.encode(store.makeBackupDocument()))
        let restored = makeStore()
        restored.replaceEverything(with: document)

        // Both directions. The export side has two separate call sites and the
        // restore side two more; a patch that catches three of the four drops
        // numbers silently on the way to a new phone.
        #expect(restored.payments.first?.paymentNo == "008455")
        #expect(restored.supplierPayments.first?.paymentNo == "P-12")
    }

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

        // Invoice numbers did not bump the version: a reader that drops these
        // shows "Bill #1" where the owner wrote 1024, which is a label lost
        // rather than a figure misread. Pinned against the constant, so the
        // *next* bump does not have to come back and edit this line — what this
        // test is about is the numbers surviving, not what the version is.
        #expect(document.version == BackupDocument.currentVersion)

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
