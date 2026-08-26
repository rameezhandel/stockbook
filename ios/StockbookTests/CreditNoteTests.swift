import Testing
import Foundation
@testable import Stockbook

/// Money the shop gives back without money moving.
///
/// The twin of `CreditNoteTests.kt`, test for test. The figures here are the
/// whole feature: a credit note that reduced the balance by the wrong amount, or
/// that put stock back twice, is invisible on screen until somebody counts a bin
/// or asks a customer for money that was written off — so every test below checks
/// a **figure or a shelf count**, never merely that a call returned non-nil.
@Suite("Credit notes")
@MainActor
struct CreditNoteTests {

    private let english = Strings(language: .english)

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    /// Fixed dates rather than offsets from now, so the month boundaries these
    /// tests lean on cannot drift with the day the suite is run.
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    @discardableResult
    private func aProduct(in store: StockbookStore) -> Product {
        store.addProduct(name: "Cisa lock", stock: 50, cost: 60, price: 95)
    }

    /// 2000 billed, nothing paid — Ahmed owes 2000.
    private func aCustomerOwing(in store: StockbookStore) -> String {
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 2000, invoiceNo: "1001")
        return "ahmed"
    }

    // MARK: What it does to the balance

    @Test("A credit note reduces what the customer owes")
    func reducesBalance() {
        let store = makeStore()
        let key = aCustomerOwing(in: store)
        #expect(store.customer(key: key)?.owed == 2000)

        store.addCreditNote(customerKey: key, amount: 540, noteNo: "00130")

        #expect(store.customer(key: key)?.owed == 1460, "2000 billed less 540 credited")
    }

    @Test("A credit note is not a payment")
    func notAPayment() throws {
        // The distinction the whole type exists for: both reduce the balance and
        // only one of them is cash. A statement that conflated them would tell
        // the owner they had taken money they never took.
        let store = makeStore()
        let key = aCustomerOwing(in: store)

        store.addCreditNote(customerKey: key, amount: 540, noteNo: "00130")

        let statement = try #require(store.statement(forCustomer: key, period: .thisMonth()))
        #expect(statement.received == 0, "no money arrived")
        #expect(statement.credited == 540)
        #expect(statement.billed == 2000, "and the invoice still says what it said")
        #expect(statement.closingBalance == 1460)
    }

    @Test("Credit and cash both come off the closing balance")
    func creditAndCash() throws {
        let store = makeStore()
        let key = aCustomerOwing(in: store)

        store.recordPayment(customerKey: key, amount: 300)
        store.addCreditNote(customerKey: key, amount: 200, noteNo: "00130")

        let statement = try #require(store.statement(forCustomer: key, period: .thisMonth()))
        #expect(statement.received == 300)
        #expect(statement.credited == 200)
        #expect(statement.closingBalance == 1500, "2000 − 300 − 200")
        #expect(store.customer(key: key)?.owed == 1500, "and the roster agrees with the document")
    }

    @Test("A credit note appears on the statement in date order")
    func inDateOrder() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            customer: "Ahmed",
            paid: 0,
            amount: 1000,
            createdAt: date(2026, 8, 1),
            invoiceNo: "1001"
        )
        store.addCreditNote(
            customerKey: "ahmed",
            amount: 250,
            noteNo: "00130",
            issuedAt: date(2026, 8, 5)
        )

        let statement = try #require(
            store.statement(forCustomer: "ahmed", period: .month(date(2026, 8, 10)))
        )

        #expect(statement.entries.count == 2)
        #expect(statement.runningBalances == [1000, 750], "the balance column reads down")
    }

    // MARK: The shelf

    @Test("Returned items go back on the shelf")
    func returnedItemsComeBack() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 5, price: 95)],
            customer: "Ahmed",
            paid: 0,
            invoiceNo: "1001"
        )
        #expect(store.product(uid: product.uid)?.stock == 45, "the sale took five off")

        store.addCreditNote(
            customerKey: "ahmed",
            lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
            noteNo: "00130"
        )

        #expect(store.product(uid: product.uid)?.stock == 47, "and two came back")
        #expect(store.creditNotes.first?.total == 190, "2 × 95, from the lines")
    }

    @Test("A credit note with no items moves no stock")
    func figureOnlyMovesNothing() {
        // The mirror of a bill entered as a figure. An overcharge is not a return.
        let store = makeStore()
        let product = aProduct(in: store)
        let key = aCustomerOwing(in: store)

        store.addCreditNote(customerKey: key, amount: 300, noteNo: "00130")

        #expect(store.product(uid: product.uid)?.stock == 50)
    }

    @Test("Editing a credit note down leaves the right count on the shelf")
    func editingDown() throws {
        // The bug this pins: without taking the old note's goods back first,
        // editing 5 to 3 would leave 8 on the shelf instead of 3.
        let store = makeStore()
        let product = aProduct(in: store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 10, price: 95)],
            customer: "Ahmed",
            paid: 0,
            invoiceNo: "1001"
        )
        #expect(store.product(uid: product.uid)?.stock == 40)

        let note = try #require(
            store.addCreditNote(
                customerKey: "ahmed",
                lines: [DraftLine(productUID: product.uid, qty: 5, price: 95)],
                noteNo: "00130"
            )
        )
        #expect(store.product(uid: product.uid)?.stock == 45)

        store.updateCreditNote(
            id: note.id,
            customerKey: "ahmed",
            lines: [DraftLine(productUID: product.uid, qty: 3, price: 95)],
            noteNo: "00130",
            issuedAt: note.issuedAt
        )

        #expect(store.product(uid: product.uid)?.stock == 43, "40 on the shelf plus the 3 returned")
        #expect(store.creditNotes.first?.total == 285)
    }

    @Test("Removing a credit note takes its goods back off the shelf")
    func removing() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 5, price: 95)],
            customer: "Ahmed",
            paid: 0,
            invoiceNo: "1001"
        )
        let note = try #require(
            store.addCreditNote(
                customerKey: "ahmed",
                lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
                noteNo: "00130"
            )
        )
        #expect(store.product(uid: product.uid)?.stock == 47)

        store.deleteCreditNote(id: note.id)

        #expect(store.product(uid: product.uid)?.stock == 45)
        #expect(store.customer(key: "ahmed")?.owed == 475, "and the credit is gone from the balance")
    }

    // MARK: What it refuses

    @Test("A credit note for nothing is refused")
    func refusesNothing() {
        let store = makeStore()
        let key = aCustomerOwing(in: store)

        #expect(store.addCreditNote(customerKey: key, amount: 0, noteNo: "00130") == nil)
        #expect(store.addCreditNote(customerKey: key, amount: nil, noteNo: "00130") == nil)
        #expect(store.addCreditNote(customerKey: "", amount: 100, noteNo: "00130") == nil)
        #expect(store.creditNotes.isEmpty)
    }

    @Test("A note number already used is found, whatever case it was typed in")
    func clashFound() {
        let store = makeStore()
        let key = aCustomerOwing(in: store)
        store.addCreditNote(customerKey: key, amount: 100, noteNo: "CN-0130")

        #expect(store.creditNoteWithNo(" cn-0130 ") != nil)
        #expect(store.creditNoteWithNo("CN-0131") == nil)
        #expect(store.creditNoteWithNo("") == nil, "an empty box is not a clash with every blank note")
    }

    @Test("A credit note does not clash with a bill on the same number")
    func ownSeries() {
        // Its own series. "#00130" in a credit-note run has nothing to do with
        // invoice 00130, and refusing it would be the app inventing a rule the
        // shop's paper does not have.
        let store = makeStore()
        let key = aCustomerOwing(in: store)

        store.addCreditNote(customerKey: key, amount: 100, noteNo: "1001")

        #expect(store.creditNoteWithNo("1001") != nil)
        #expect(store.creditNotes.count == 1)
        #expect(store.billWithInvoiceNo("1001") != nil, "and the bill keeps its own number")
    }

    @Test("A note being edited does not clash with itself")
    func noSelfClash() throws {
        let store = makeStore()
        let key = aCustomerOwing(in: store)
        let note = try #require(store.addCreditNote(customerKey: key, amount: 100, noteNo: "00130"))

        #expect(store.creditNoteWithNo("00130", exceptId: note.id) == nil)
    }

    @Test("A note with no number is called what it is")
    func fallbackLabel() throws {
        let store = makeStore()
        let key = aCustomerOwing(in: store)
        let note = try #require(store.addCreditNote(customerKey: key, amount: 100))

        #expect(note.noteNo == nil)
        #expect(note.reference(english) == english.creditNoteLabel)
    }

    // MARK: The file

    @Test("Credit notes survive a backup round trip")
    func roundTrip() throws {
        let store = makeStore()
        let product = aProduct(in: store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 5, price: 95)],
            customer: "Ahmed",
            paid: 0,
            invoiceNo: "1001"
        )
        store.addCreditNote(
            customerKey: "ahmed",
            lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
            noteNo: "00130",
            reason: "returned, damaged"
        )

        let document = try BackupService.decode(try BackupService.encode(store.makeBackupDocument()))

        // A reader that dropped these would show every credited customer owing
        // more than they do, and send the owner to ask for money written off
        // weeks ago. That is a figure misread, which is what bumps the version.
        //
        // Asserted as "at least the version that carried them" rather than as a
        // literal: what this test is about is that credit notes travel, and
        // pinning the number here means every later bump edits a credit-note
        // test to say something it was never about.
        #expect(document.version >= 3)
        #expect(document.version == BackupDocument.currentVersion)

        let restored = makeStore()
        restored.replaceEverything(with: document)

        let note = try #require(restored.creditNotes.first)
        #expect(restored.creditNotes.count == 1)
        #expect(note.noteNo == "00130")
        #expect(note.total == 190)
        #expect(note.reason == "returned, damaged")
        #expect(note.lines.count == 1)
        #expect(restored.customer(key: "ahmed")?.owed == 285, "475 billed less 190 credited")
    }
}
