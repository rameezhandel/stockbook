import Testing
import Foundation
@testable import Stockbook

/// The day's balances as they print.
///
/// The twin of `DayLedgerDocumentTests.kt`, test for test. The arithmetic is
/// `DayLedger`'s and tested there; what this pins is the *page* — which columns
/// exist, what they are called, and which cells are left empty. It matters
/// because the two apps draw the PDF with entirely different graphics code, and
/// this structure is the only thing making them agree.
@MainActor
@Suite("Day ledger document")
struct DayLedgerDocumentTests {

    private let english = Strings(language: .english)

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Riyadh")!
        return calendar
    }

    private func at(_ day: Int) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.date(from: DateComponents(year: 2026, month: 8, day: day, hour: 9))!
    }

    private func shop() -> (StockbookStore, Product) {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setOwnerName("Tayba Trading")
        let lock = store.addProduct(name: "Cisa lock", stock: 500, cost: 60, price: 95)
        return (store, lock)
    }

    private func document(_ store: StockbookStore, onlyMoved: Bool = false) -> DayLedgerDocument {
        let whole = store.dayLedger(at(12), calendar: calendar)
        let ledger = onlyMoved ? whole.movedOnly() : whole
        return DayLedgerDocument.forDay(
            ledger: ledger,
            settings: store.settings,
            strings: english,
            onlyMoved: onlyMoved
        )
    }

    @Test("The five columns are named in the order they are drawn")
    func headings() {
        let (store, _) = shop()
        store.addCustomer(name: "Ahmed")

        #expect(
            document(store).columnHeadings == ["Customers", "Invoice", "Received", "Old", "Current"]
        )
    }

    @Test("A quiet row carries its balances and nothing else")
    func quietRow() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Fatima")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 4, price: 95)], customer: "Fatima", paid: 0, createdAt: at(10))

        let row = try #require(document(store).rows.first { $0.name == "Fatima" })

        // Empty rather than "0": an empty cell says nothing happened here, and a
        // zero is a figure somebody may go looking for.
        #expect(row.invoiced == "")
        #expect(row.received == "")
        #expect(row.oldBalance == "380")
        #expect(row.currentBalance == "380")
        #expect(row.note == nil)
    }

    @Test("A busy row shows both movement columns")
    func busyRow() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 400, createdAt: at(12))

        let row = try #require(document(store).rows.first { $0.name == "Ahmed" })

        #expect(row.invoiced == "950")
        #expect(row.received == "400")
        #expect(row.oldBalance == "0")
        #expect(row.currentBalance == "550")
    }

    /// What the five columns cannot hold has to be said in words, or the row does
    /// not add up.
    @Test("A credit note is spelled out under the name")
    func creditNote() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 0, createdAt: at(10))
        store.addCreditNote(customerKey: "ahmed", amount: 150, issuedAt: at(12))

        let row = try #require(document(store).rows.first { $0.name == "Ahmed" })

        #expect(row.received == "", "no money arrived")
        #expect(try #require(row.note).contains("Credited"))
        #expect(row.oldBalance == "950")
        #expect(row.currentBalance == "800")
    }

    /// The page has to say it was narrowed.
    ///
    /// A printed roll-call and a printed selection look identical on paper and
    /// their totals differ. Without this line the owner files a sheet whose
    /// figures do not tie to the shop's own and has no way to tell why.
    @Test("A narrowed page says so, and a whole one says nothing")
    func filterNote() throws {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.addCustomer(name: "Fatima", openingBalance: 2000)
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 400, createdAt: at(12))

        #expect(document(store).filterNote == nil)

        let narrowed = document(store, onlyMoved: true)
        #expect(narrowed.filterNote == "Only accounts that moved on this day")
        #expect(narrowed.rows.count == 1)
    }

    /// The figure under a column is the column added up, on a narrowed page too.
    @Test("The totals are the totals of the rows printed")
    func totals() {
        let (store, lock) = shop()
        store.addCustomer(name: "Ahmed")
        store.addCustomer(name: "Fatima", openingBalance: 2000)
        store.saveBill(lines: [.init(productUID: lock.uid, qty: 10, price: 95)], customer: "Ahmed", paid: 400, createdAt: at(12))

        #expect(document(store).totals == ["950", "400", "2,000", "2,550"])
        #expect(
            document(store, onlyMoved: true).totals == ["950", "400", "0", "550"],
            "Fatima is not on the page, so her balance is not in the total either"
        )
    }

    @Test("A shop with nobody on the book prints a line saying so")
    func emptyBook() {
        let (store, _) = shop()

        let document = document(store)

        #expect(document.isEmpty)
        #expect(document.emptyLine == "No customers yet")
    }
}
