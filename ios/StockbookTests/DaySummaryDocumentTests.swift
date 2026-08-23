import Testing
import Foundation
@testable import Stockbook

/// The day, laid out for printing.
///
/// The twin of `DaySummaryDocumentTests.kt`, test for test. `DayBookTests` pins
/// the arithmetic; this pins what the owner actually reads — that a section is
/// only there when it has something in it, that a bill nobody paid for says so
/// on its own row, and that the foot of the page states the same three figures
/// the book computed rather than a fourth opinion about them.
@Suite("Day summary document")
@MainActor
struct DaySummaryDocumentTests {

    private let english = Strings(language: .english)

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func at(_ hour: Int, day: Int = 22) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 8, day: day, hour: hour))!
    }

    private var day: Date { at(9) }

    private func makeStore() -> StockbookStore {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setOwnerName("Al Salam Hardware")
        return store
    }

    private func page(_ store: StockbookStore) -> DaySummaryDocument {
        DaySummaryDocument.forDay(
            book: store.dayBook(day, calendar: calendar),
            settings: store.settings,
            strings: english
        )
    }

    @Test("The page says whose it is, what it is, and which day")
    func heading() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9))

        let document = page(store)

        #expect(document.shopName == "Al Salam Hardware")
        #expect(document.onDate == english.longDate(day))
        // The word matters. This names everybody who was billed and everything
        // the shop spent — it is not one party's account and must never carry
        // the word that means one.
        #expect(document.title == "Day Summary")
        #expect(!document.title.lowercased().contains("statement"))
    }

    @Test("Sections come in the order the day is read, and only where there is something")
    func sectionsPresent() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: 0, amount: 100, createdAt: at(9))
        store.recordPayment(customerKey: Customer.key(for: "Ahmed"), amount: 50, receivedAt: at(10))
        store.addExpense(amount: 30, note: "Petrol", spentAt: at(11))

        // No deliveries, no supplier payments, no credit notes that day — and so
        // no headings for them. A heading over nothing is a question the reader
        // has to answer themselves.
        #expect(page(store).sections.map(\.heading) == ["Bills", "Received", "Expenses"])
    }

    @Test("A section totals what those things came to")
    func sectionSubtotal() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: 0, amount: 100, createdAt: at(9))
        store.saveBill(customer: "Khalid", paid: nil, amount: 250.5, createdAt: at(10))

        let bills = try #require(page(store).sections.first)

        #expect(bills.subtotalLabel == "Subtotal")
        // What was sold, not what was collected for it. The cash foot is where
        // that question gets answered, once, for the whole day.
        #expect(bills.subtotalValue == "SAR 350.50")
    }

    @Test("A bill still owed for says how much, and one paid in full says nothing")
    func creditIsVisible() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: 40, amount: 100, createdAt: at(9), invoiceNo: "6356")
        store.saveBill(customer: "Khalid", paid: nil, amount: 50, createdAt: at(10), invoiceNo: "6357")

        let rows = try #require(page(store).sections.first).rows

        #expect(rows[0].detail == "Invoice #6356 · SAR 60 on credit")
        #expect(rows[1].detail == "Invoice #6357")
    }

    @Test("A credit note never says on credit")
    func creditNoteDetail() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, createdAt: at(9))
        store.addCreditNote(customerKey: Customer.key(for: "Ahmed"), amount: 120, noteNo: "22", issuedAt: at(10))

        let note = try #require(page(store).sections.last?.rows.first)

        // It settles nothing by design. Marking the whole of it "on credit"
        // would be saying money is owed that was never going to be paid.
        #expect(note.detail == "Credit Note #22")
    }

    @Test("A bill lists what was on it, under its own row")
    func itemsUnderTheRow() throws {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 10, cost: 20, price: 30)
        store.saveBill(
            lines: [DraftLine(productUID: padlock.uid, qty: 3, price: 30)],
            customer: "Ahmed",
            paid: nil,
            createdAt: at(9)
        )

        let item = try #require(page(store).sections.first?.rows.first?.items.first)

        #expect(item.text == "3 × Padlock 40mm")
        #expect(item.amount == "SAR 90")
    }

    @Test("The foot states the day's cash, and the net may be negative")
    func cashFoot() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9))
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        store.recordSupplierBill(supplierKey: supplier.key, amount: 250, createdAt: at(10))

        let cash = page(store).cash

        #expect(cash.map(\.label) == ["Money in", "Money out", "Net for the day"])
        #expect(cash.map(\.value) == ["SAR 100", "SAR 250", "-SAR 150"])
        // The one figure the eye should stop on, and the only line marked.
        #expect(cash.map(\.isNet) == [false, false, true])
    }

    @Test("Money in is what the book says, never what the rows add up to")
    func footIsNotTheSubtotal() throws {
        let store = makeStore()
        // Three hundred sold, forty of it taken. A page whose foot read 300
        // would have the owner hunting for money nobody paid.
        store.saveBill(customer: "Ahmed", paid: 40, amount: 100, createdAt: at(9))
        store.saveBill(customer: "Khalid", paid: 0, amount: 200, createdAt: at(10))

        let document = page(store)

        #expect(try #require(document.sections.first).subtotalValue == "SAR 300")
        #expect(document.cash.first?.value == "SAR 40")
    }

    @Test("A day nothing happened on states that and nothing else")
    func emptyDay() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9, day: 20))

        let document = page(store)

        #expect(document.isEmpty)
        #expect(document.emptyLine == "Nothing was recorded on this day.")
        // No cash foot either: a page with no figures on it must not state a
        // cash position, even a zero one, for a day it knows nothing about.
        #expect(document.cash.isEmpty)
    }

    @Test("The paper is called what the statement calls it")
    func referencesMatchTheStatement() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9))
        store.recordPayment(customerKey: Customer.key(for: "Ahmed"), amount: 50, receivedAt: at(10), paymentNo: "1024")
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        store.recordSupplierBill(supplierKey: supplier.key, amount: 300, createdAt: at(11), invoiceNo: "88")
        store.addExpense(amount: 30, note: "Petrol", spentAt: at(12))

        let details = page(store).sections.flatMap(\.rows).map(\.detail)

        #expect(details == [
            // No paper number on the bill, so the app's own counter — the same
            // fallback the statement uses.
            "Bill #1",
            "Payment #1024",
            "Delivery #88",
            // Joined to nobody, numbered by nobody.
            nil
        ])
    }

    @Test("An expense is named by what it went on")
    func expenseRow() throws {
        let store = makeStore()
        store.addExpense(amount: 30, note: "Petrol", spentAt: at(9))

        let row = try #require(page(store).sections.first?.rows.first)

        #expect(row.name == "Petrol")
        #expect(row.amount == "SAR 30")
    }
}
