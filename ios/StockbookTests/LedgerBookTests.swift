import Testing
import Foundation
@testable import Stockbook

/// Every customer's whole history, one statement each.
///
/// The twin of `LedgerBookTests.kt`, test for test. What this pins is that the
/// book leaves **nothing and nobody out**: every name gets a statement, and every
/// statement runs from the first record in the shop to now. A ledger book that
/// quietly skipped an account, or started after somebody's oldest bill, cannot be
/// reconciled against the paper one it replaces — and the reader has no way to
/// notice.
@MainActor
@Suite("Ledger book")
struct LedgerBookTests {

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Riyadh")!
        return calendar
    }

    private func at(_ month: Int, _ day: Int) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.date(from: DateComponents(year: 2026, month: month, day: day, hour: 9))!
    }

    private func shop() -> (StockbookStore, Product) {
        let store = StockbookStore(repository: InMemoryRepository())
        let lock = store.addProduct(name: "Cisa lock", stock: 500, cost: 60, price: 95)
        return (store, lock)
    }

    private func page(_ book: [Statement], _ name: String) throws -> Statement {
        try #require(book.first { $0.party.name == name }, "no page for \(name)")
    }

    @Test("Every customer gets a page, including the ones with no history")
    func everyoneHasAPage() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.addCustomer(name: "Fatima")
        store.addCustomer(name: "Khalid")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(8, 12))

        let book = store.ledgerBook(calendar: calendar)

        #expect(book.count == 3, "a name with no bills is still an account in the book")
        #expect(book.map(\.party.name) == ["Ahmed", "Fatima", "Khalid"], "in name order")
        #expect(try page(book, "Fatima").isEmpty, "nothing to show, but the page exists")
    }

    /// The range has to reach back past the oldest record in the shop.
    ///
    /// A book that started at the beginning of this month would put every older
    /// bill into an opening figure and show none of them — which looks like a
    /// complete history and is not one.
    @Test("The period reaches back to the first record and forward to now")
    func allTime() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(1, 3))
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 1, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(8, 20))

        let ahmed = try page(store.ledgerBook(calendar: calendar), "Ahmed")

        #expect(ahmed.entries.count == 2, "both bills are entries, not an opening figure")
        #expect(ahmed.openingBalance == 0, "nothing predates the first bill")
        #expect(ahmed.closingBalance == 285)
    }

    @Test("A balance carried over from the paper book opens the page")
    func carriedOver() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed", openingBalance: 1000)
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(8, 12))

        let ahmed = try page(store.ledgerBook(calendar: calendar), "Ahmed")

        // The carried figure has no date of its own, so it cannot be an entry —
        // it belongs in the opening balance, which is where a statement puts
        // everything from before the range.
        #expect(ahmed.openingBalance == 1000)
        #expect(ahmed.entries.count == 1)
        #expect(ahmed.closingBalance == 1190)
    }

    @Test("Each page carries only that customer's history")
    func pagesDoNotBleed() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.addCustomer(name: "Fatima")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(8, 12))
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 4, price: 95)], customer: "Fatima", paid: 0, createdAt: at(8, 13))
        store.recordPayment(customerKey: "fatima", amount: 100, receivedAt: at(8, 14))

        let book = store.ledgerBook(calendar: calendar)

        #expect(try page(book, "Ahmed").entries.count == 1)
        #expect(try page(book, "Ahmed").closingBalance == 190)
        #expect(try page(book, "Fatima").entries.count == 2, "her bill and her payment")
        #expect(try page(book, "Fatima").closingBalance == 280)
    }

    @Test("Credit notes and moved balances are on the page too")
    func everyKindOfEntry() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed Jeddah")
        store.addCustomer(name: "Ahmed Riyadh")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed Jeddah", paid: 0, createdAt: at(8, 10))
        store.addCreditNote(customerKey: "ahmed jeddah", amount: 150, issuedAt: at(8, 11))
        _ = store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 300, movedAt: at(8, 12))

        let book = store.ledgerBook(calendar: calendar)
        let jeddah = try page(book, "Ahmed Jeddah")

        #expect(jeddah.entries.count == 3, "the bill, the note and the transfer out")
        #expect(jeddah.closingBalance == 500, "950 less 150 credited less 300 moved")

        let riyadh = try page(book, "Ahmed Riyadh")
        #expect(riyadh.entries.count == 1, "the transfer arriving")
        #expect(riyadh.closingBalance == 300)
    }

    /// The whole book has to tie to what the shop is owed.
    ///
    /// This is the one figure a reader can check without adding up a hundred
    /// pages, and if it disagrees the book is worthless.
    @Test("The closing balances add up to what the shop is owed")
    func bookTiesToReceivable() {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed", openingBalance: 500)
        store.addCustomer(name: "Fatima")
        store.addCustomer(name: "Khalid")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 400, createdAt: at(8, 12))
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 4, price: 95)], customer: "Fatima", paid: 0, createdAt: at(8, 13))
        store.recordPayment(customerKey: "fatima", amount: 80, receivedAt: at(8, 14))

        let book = store.ledgerBook(calendar: calendar)
        let owed = store.customers().reduce(0) { $0 + $1.owed }

        #expect(book.reduce(0) { $0 + $1.closingBalance } == owed)
    }

    @Test("A shop with nobody on the book produces no pages")
    func emptyShop() {
        let (store, _) = shop()

        #expect(store.ledgerBook(calendar: calendar).isEmpty)
    }
}
