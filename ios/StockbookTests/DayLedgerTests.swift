import Testing
import Foundation
@testable import Stockbook

/// Every customer's position on one day.
///
/// The twin of `DayLedgerTests.kt`, test for test. Two things are asserted over
/// and over here. The first is the **roll-call**: a customer nothing happened to
/// still gets a line, because the page is read down against a paper book and a
/// list that skipped the quiet ones could not be. The second is that **every row
/// balances** — `opening + invoiced − received − credited − transferredOut +
/// transferredIn = closing` — which is the whole claim the page makes and the one
/// thing a reader cannot check for themselves.
@MainActor
@Suite("Day ledger")
struct DayLedgerTests {

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Riyadh")!
        return calendar
    }

    /// Midday, so a day's figures cannot slide either side of a boundary.
    private func at(_ day: Int) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.date(from: DateComponents(year: 2026, month: 8, day: day, hour: 9))!
    }

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    private func shop() -> (StockbookStore, Product) {
        let store = makeStore()
        let lock = store.addProduct(name: "Cisa lock", stock: 500, cost: 60, price: 95)
        return (store, lock)
    }

    private func row(_ ledger: DayLedger, _ key: String) throws -> DayLedger.Row {
        try #require(ledger.rows.first { $0.key == key }, "no line for \(key)")
    }

    /// The claim the page makes, checked on every line of it.
    private func assertEveryRowBalances(_ ledger: DayLedger) {
        for row in ledger.rows {
            let expected = ((row.openingBalance + row.invoiced - row.received
                - row.credited + row.transferredIn - row.transferredOut) * 100).rounded() / 100
            #expect(row.closingBalance == expected, "\(row.name) does not add up")
        }
    }

    @Test("Every customer gets a line, including the ones nothing happened to")
    func rollCall() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.addCustomer(name: "Fatima")
        store.addCustomer(name: "Khalid")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(12))

        let ledger = store.dayLedger(at(12), calendar: calendar)

        #expect(ledger.rows.count == 3, "the roll-call is the point of the page")
        #expect(ledger.rows.map(\.name) == ["Ahmed", "Fatima", "Khalid"], "in name order")
        #expect(ledger.busyRows.count == 1)
        #expect(try row(ledger, "fatima").isQuiet)
        #expect(try !row(ledger, "ahmed").isQuiet)
        assertEveryRowBalances(ledger)
    }

    @Test("A quiet customer carries yesterday's balance straight through")
    func quietCarriesThrough() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Fatima")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 4, price: 95)], customer: "Fatima", paid: 0, createdAt: at(10))

        let fatima = try row(store.dayLedger(at(12), calendar: calendar), "fatima")

        #expect(fatima.isQuiet)
        #expect(fatima.invoiced == 0)
        #expect(fatima.received == 0)
        #expect(fatima.openingBalance == 380, "owed from the tenth")
        #expect(fatima.closingBalance == 380, "and still owed at the end of the twelfth")
    }

    /// The row the whole form is for: billed and part-paid on the same day.
    ///
    /// The bill goes in one column at its full value and the money in the other,
    /// which is what lets the two be read against each other. Netting them would
    /// lose the fact that a sale happened at all.
    @Test("A bill part paid at the counter fills both columns")
    func billAndCounterPayment() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 400, createdAt: at(12))

        let ahmed = try row(store.dayLedger(at(12), calendar: calendar), "ahmed")

        #expect(ahmed.invoiced == 950)
        #expect(ahmed.received == 400)
        #expect(ahmed.openingBalance == 0)
        #expect(ahmed.closingBalance == 550)
    }

    @Test("A receipt against an older bill is money received today")
    func laterReceipt() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(10))
        store.recordPayment(customerKey: "ahmed", amount: 300, receivedAt: at(12))

        let ahmed = try row(store.dayLedger(at(12), calendar: calendar), "ahmed")

        #expect(ahmed.invoiced == 0, "nothing was sold today")
        #expect(ahmed.received == 300)
        #expect(ahmed.openingBalance == 950)
        #expect(ahmed.closingBalance == 650)
    }

    @Test("An opening balance from the paper book is owed on the first day")
    func carriedOver() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed", openingBalance: 1200)

        let ahmed = try row(store.dayLedger(at(12), calendar: calendar), "ahmed")

        #expect(ahmed.openingBalance == 1200)
        #expect(ahmed.closingBalance == 1200)
        #expect(ahmed.isQuiet, "carried over is not something that happened today")
    }

    /// Tomorrow's bill must not appear in today's opening figure.
    ///
    /// Working the opening balance backwards from what somebody owes *now* is the
    /// shape that gets this wrong, and it gets it wrong silently: a customer
    /// billed since would read as having owed that money all along.
    @Test("A bill written later does not reach back into today")
    func laterBillStaysOut() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(10))
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 8, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(20))

        let ahmed = try row(store.dayLedger(at(12), calendar: calendar), "ahmed")

        #expect(ahmed.openingBalance == 190, "only the tenth's bill")
        #expect(ahmed.closingBalance == 190)
        #expect(ahmed.invoiced == 0)
    }

    /// A credit note is not a receipt, and the page says so in its own column.
    ///
    /// Folding it into "received" would be the easy way to keep five columns
    /// adding up, and it would tell the owner money arrived that never did.
    @Test("A credit note gets its own column and the row still balances")
    func creditNoteColumn() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(10))
        store.addCreditNote(customerKey: "ahmed", amount: 150, issuedAt: at(12))

        let ledger = store.dayLedger(at(12), calendar: calendar)
        let ahmed = try row(ledger, "ahmed")

        #expect(ledger.hasCredits, "the column is drawn on a day that has one")
        #expect(ahmed.received == 0, "no money arrived")
        #expect(ahmed.credited == 150)
        #expect(ahmed.openingBalance == 950)
        #expect(ahmed.closingBalance == 800)
        assertEveryRowBalances(ledger)
    }

    @Test("A balance moved between two accounts shows on both lines")
    func transferOnBothLines() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed Jeddah")
        store.addCustomer(name: "Ahmed Riyadh")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed Jeddah", paid: 0, createdAt: at(10))
        _ = store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 950, movedAt: at(12))

        let ledger = store.dayLedger(at(12), calendar: calendar)

        #expect(ledger.hasTransfers)
        let jeddah = try row(ledger, "ahmed jeddah")
        #expect(jeddah.transferredOut == 950)
        #expect(jeddah.openingBalance == 950)
        #expect(jeddah.closingBalance == 0)

        let riyadh = try row(ledger, "ahmed riyadh")
        #expect(riyadh.transferredIn == 950)
        #expect(riyadh.openingBalance == 0)
        #expect(riyadh.closingBalance == 950)

        // Nothing was created or destroyed by moving it.
        #expect(ledger.openingBalance == ledger.closingBalance)
        assertEveryRowBalances(ledger)
    }

    @Test("The columns that are usually empty are only announced when they are not")
    func quietColumnsStayQuiet() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 1, price: 95)], customer: "Ahmed", paid: 95, createdAt: at(12))

        let ledger = store.dayLedger(at(12), calendar: calendar)

        #expect(!ledger.hasCredits)
        #expect(!ledger.hasTransfers)
    }

    @Test("The totals are the columns added up")
    func totals() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed", openingBalance: 100)
        store.addCustomer(name: "Fatima")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 400, createdAt: at(12))
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 2, price: 95)], customer: "Fatima", paid: 0, createdAt: at(12))

        let ledger = store.dayLedger(at(12), calendar: calendar)

        #expect(ledger.invoiced == 1140, "950 and 190")
        #expect(ledger.received == 400)
        #expect(ledger.openingBalance == 100, "Ahmed's carried-over figure alone")
        #expect(ledger.closingBalance == 840, "100 + 1140 − 400")
        assertEveryRowBalances(ledger)
    }

    /// A name that only ever appeared on a bill is a customer too, and the page
    /// that left them out would not tie to what the shop is owed.
    @Test("Somebody never added to the roster still gets a line")
    func historyOnlyCustomer() throws {
        let (store, lock) = shop()
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 1, price: 95)], customer: "Walk-in Sami", paid: 0, createdAt: at(12))

        let sami = try row(store.dayLedger(at(12), calendar: calendar), "walk-in sami")

        #expect(sami.invoiced == 95)
        #expect(sami.closingBalance == 95)
    }

    @Test("A day with nothing on it is every customer, unchanged")
    func emptyDay() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(10))

        let ledger = store.dayLedger(at(17), calendar: calendar)

        #expect(ledger.rows.count == 1)
        #expect(ledger.busyRows.isEmpty)
        #expect(ledger.invoiced == 0)
        #expect(ledger.closingBalance == 190, "still owed, just not today's doing")
    }
}
