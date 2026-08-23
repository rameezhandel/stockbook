import Testing
import Foundation
@testable import Stockbook

/// One day of the shop, read back off the six records that carry a date.
///
/// The twin of `DayBookTests.kt`, test for test. Most of what is asserted here
/// is **the cash line**, because that is the figure the owner checks against the
/// drawer at closing time and the one place a plausible mistake is expensive:
/// counting a credit sale as takings, or a credit note as money handed back,
/// gives a page that looks right and does not reconcile. The rest pins that a
/// day is a day — that the record entered ten minutes before midnight belongs to
/// the day it was entered on and to no other.
@Suite("Day book")
@MainActor
struct DayBookTests {

    // Fixed rather than the runner's own: a test that passes in Riyadh and fails
    // on a CI machine set to UTC teaches nothing about the code.
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func at(_ hour: Int) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 8, day: 22, hour: hour))!
    }

    private func at(day: Int, hour: Int, minute: Int = 0, second: Int = 0) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 8, day: day, hour: hour, minute: minute, second: second))!
    }

    private var day: Date { at(9) }

    private func makeStore() -> StockbookStore { StockbookStore(repository: InMemoryRepository()) }

    private func book(_ store: StockbookStore) -> DayBook { store.dayBook(day, calendar: calendar) }

    @Test("All six kinds of record reach the page")
    func everyKind() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9))
        store.recordPayment(customerKey: Customer.key(for: "Ahmed"), amount: 50, receivedAt: at(10))
        store.addCreditNote(customerKey: Customer.key(for: "Ahmed"), amount: 20, issuedAt: at(11))
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        store.recordSupplierBill(supplierKey: supplier.key, amount: 300, createdAt: at(12))
        store.recordSupplierPayment(supplierKey: supplier.key, amount: 80, paidAt: at(13))
        store.addExpense(amount: 30, note: "Petrol", spentAt: at(14))

        // In the order they happened, which is the order the store returns them
        // in — the document is what groups them, not this.
        #expect(book(store).entries.map(\.kind) == [
            .bill, .payment, .creditNote, .delivery, .supplierPayment, .expense
        ])
    }

    @Test("Yesterday and tomorrow stay off today")
    func onlyThisDay() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 10, createdAt: at(day: 21, hour: 23, minute: 59, second: 59))
        store.saveBill(customer: "Ahmed", paid: nil, amount: 20, createdAt: at(day: 22, hour: 0))
        store.saveBill(customer: "Ahmed", paid: nil, amount: 30, createdAt: at(day: 22, hour: 23, minute: 59, second: 59))
        store.saveBill(customer: "Ahmed", paid: nil, amount: 40, createdAt: at(day: 23, hour: 0))

        // Both ends of the day itself, and neither midnight belonging to two
        // days — the half-open range `StatementPeriod` already uses.
        #expect(book(store).entries.map(\.amount) == [20, 30])
    }

    @Test("Money in is what was taken, not what was billed")
    func takingsAreNotSales() {
        let store = makeStore()
        // Paid at the counter, part paid, and entirely on credit.
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9))
        store.saveBill(customer: "Khalid", paid: 40, amount: 100, createdAt: at(10))
        store.saveBill(customer: "Saeed", paid: 0, amount: 100, createdAt: at(11))

        let day = book(store)

        // Three hundred sold, a hundred and forty in the drawer. A day book that
        // reported 300 here would have the owner hunting for money nobody paid.
        #expect(day.entries.reduce(0) { $0 + $1.amount } == 300)
        #expect(day.moneyIn == 140)
    }

    @Test("A receipt against an old bill is money in on the day it arrives")
    func receiptLandsToday() {
        let store = makeStore()
        store.saveBill(
            customer: "Ahmed",
            paid: 0,
            amount: 500,
            createdAt: calendar.date(from: DateComponents(year: 2026, month: 7, day: 4, hour: 9))!
        )
        store.recordPayment(customerKey: Customer.key(for: "Ahmed"), amount: 200, receivedAt: at(11))

        let day = book(store)

        // The bill is July's; only the receipt is today's.
        #expect(day.entries.count == 1)
        #expect(day.moneyIn == 200)
    }

    @Test("Money out is deliveries settled, supplier payments and spending")
    func moneyOut() throws {
        let store = makeStore()
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        // Settled on the spot, part paid, and taken entirely on the shop's account.
        store.recordSupplierBill(supplierKey: supplier.key, amount: 300, createdAt: at(9))
        store.recordSupplierBill(supplierKey: supplier.key, amount: 200, paid: 50, createdAt: at(10))
        store.recordSupplierBill(supplierKey: supplier.key, amount: 400, paid: 0, createdAt: at(11))
        store.recordSupplierPayment(supplierKey: supplier.key, amount: 80, paidAt: at(12))
        store.addExpense(amount: 30, note: "Petrol", spentAt: at(13))

        #expect(book(store).moneyOut == 300 + 50 + 80 + 30)
    }

    @Test("A credit note moves no money either way")
    func creditNoteIsNotCash() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, createdAt: at(9))
        store.addCreditNote(customerKey: Customer.key(for: "Ahmed"), amount: 120, issuedAt: at(10))

        let day = book(store)
        let note = try #require(day.entries(of: .creditNote).first)

        // It is on the page — the owner wants to see it — and it is in neither
        // column. Nothing was taken and nothing was handed back.
        #expect(note.amount == 120)
        #expect(note.settled == 0)
        #expect(day.moneyIn == 0)
        #expect(day.moneyOut == 0)
    }

    @Test("Net is what the day did to the cash box, and may be negative")
    func netMayBeNegative() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9))
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        store.recordSupplierBill(supplierKey: supplier.key, amount: 250, createdAt: at(10))

        let day = book(store)

        #expect(day.moneyIn == 100)
        #expect(day.moneyOut == 250)
        // A shop that restocked in the morning is a hundred and fifty down at
        // closing time, and the page has to be able to say so.
        #expect(day.net == -150)
    }

    @Test("An itemised bill carries what was on it")
    func billCarriesItsLines() throws {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 10, cost: 20, price: 30)
        store.saveBill(
            lines: [DraftLine(productUID: padlock.uid, qty: 3, price: 30)],
            customer: "Ahmed",
            paid: nil,
            createdAt: at(9)
        )

        let bill = try #require(book(store).entries(of: .bill).first)
        let item = try #require(bill.items.first)

        #expect(item.name == "Padlock 40mm")
        #expect(item.qty == 3)
        #expect(item.amount == 90)
    }

    @Test("A bill entered as a figure has nothing to list")
    func figureOnlyBill() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9))

        let bill = try #require(book(store).entries(of: .bill).first)

        // Ordinary, not exceptional: a shop entering the paper bill it already
        // wrote knows the total and has no reason to rebuild it.
        #expect(bill.items.isEmpty)
    }

    @Test("A bill shows the number on the paper where there is one")
    func paperNumberWins() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(9), invoiceNo: "6356")
        store.saveBill(customer: "Khalid", paid: nil, amount: 50, createdAt: at(10))

        let entries = book(store).entries(of: .bill)

        #expect(entries[0].reference == "6356")
        // No paper number, so the app's own counter travels instead — as a
        // number, because "Bill #7" is words and words live in `Strings`.
        #expect(entries[1].reference == nil)
        #expect(entries[1].billNumber == 2)
    }

    @Test("A person is spelled the way the rest of the app spells them")
    func oneSpellingPerPerson() {
        let store = makeStore()
        // The roster spelling wins over whatever was typed in a hurry at the
        // counter, exactly as `customers()` decides it. A day book that named
        // somebody differently from the statement beside it is a page the owner
        // stops trusting.
        store.addCustomer(name: "Ahmed Contracting")
        store.saveBill(customer: "ahmed contracting", paid: 0, amount: 100, createdAt: at(9))
        store.recordPayment(customerKey: Customer.key(for: "AHMED CONTRACTING"), amount: 40, receivedAt: at(10))

        #expect(book(store).entries.map(\.who) == ["Ahmed Contracting", "Ahmed Contracting"])
    }

    @Test("A supplier is named, never keyed")
    func supplierIsNamed() throws {
        let store = makeStore()
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        store.recordSupplierPayment(supplierKey: supplier.key, amount: 80, paidAt: at(9))

        #expect(book(store).entries.first?.who == "Gulf Locks")
        #expect(supplier.key == Supplier.key(for: "Gulf Locks"))
    }

    @Test("An expense is named by what it went on, because it is joined to nobody")
    func expenseIsItsOwnName() throws {
        let store = makeStore()
        store.addExpense(amount: 30, note: "Petrol", spentAt: at(9))

        let expense = try #require(book(store).entries(of: .expense).first)

        #expect(expense.who == "Petrol")
        #expect(expense.reference == nil)
    }

    @Test("A day nothing happened on is empty")
    func emptyDay() {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 100, createdAt: at(day: 20, hour: 9))

        let day = book(store)

        #expect(day.isEmpty)
        #expect(day.moneyIn == 0)
        #expect(day.moneyOut == 0)
        #expect(day.net == 0)
    }
}
