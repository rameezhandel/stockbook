import Testing
import Foundation
@testable import Stockbook

/// The ledger book's contents page: every customer, and where they stand.
///
/// The twin of `LedgerIndexTests.kt`, test for test. The claim being pinned is
/// that **the index and the pages behind it are one document**. Both come from
/// the same `ledgerBook()` list, in the same order, with the same figures — a
/// contents page naming a balance the page it points at disagrees with is worse
/// than no contents page at all, and the only way that cannot happen is for there
/// to be one list.
///
/// The second claim is that it is an **index and not a chasing list**. The
/// receivable summary drops anybody who does not owe; this one cannot, because a
/// customer with a page in the book and no line in the contents reads as a
/// customer who was left out.
@Suite("Ledger book index")
@MainActor
struct LedgerIndexTests {

    private let english = Strings(language: .english)

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func at(_ day: Int) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 8, day: day, hour: 9))!
    }

    private var now: Date { at(22) }

    private func makeStore() -> StockbookStore {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setOwnerName("Al Salam Hardware")
        return store
    }

    private func index(_ store: StockbookStore) -> SummaryDocument {
        SummaryDocument.forLedgerBook(
            statements: store.ledgerBook(),
            settings: store.settings,
            strings: english,
            now: now
        )
    }

    @Test("The index says whose book it is and what it lists")
    func heading() {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")

        let page = index(store)

        #expect(page.shopName == "Al Salam Hardware")
        #expect(page.title == "Customer Balances")
        #expect(page.asOf == "As of \(english.longDate(now))")
        #expect(page.columnHeadings == ["Customer", "Balance"])
    }

    /// The whole roll-call, not the debtors. A name with a page in the book and
    /// no line in the contents is a name the reader concludes is missing.
    @Test("Everybody is listed, including the settled and the ones in credit")
    func everybodyIsListed() {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        _ = store.addCustomer(name: "Fatima")
        _ = store.addCustomer(name: "Khalid")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, createdAt: at(10))
        // Settled up: billed and paid in full.
        store.saveBill(customer: "Fatima", paid: nil, amount: 400, createdAt: at(11))
        // Paid ahead of any bill, so in credit.
        _ = store.recordPayment(customerKey: Customer.key(for: "Khalid"), amount: 250, receivedAt: at(12))

        let page = index(store)

        #expect(page.rows.count == 3)
        #expect(page.rows.map(\.name) == ["Ahmed", "Fatima", "Khalid"])
        #expect(page.rows[0].amount == "SAR 1,000")
        #expect(page.rows[1].amount == "SAR 0", "settled up still gets a line")
        // Exactly as that customer's own page states it — `Money.text`, sign and
        // all. The index matching the page matters more here than a prettier
        // bracket would.
        #expect(page.rows[2].amount == "SAR -250", "and so does money held in advance")
    }

    /// The index and the pages are one list read twice. Same names, same order,
    /// same figures — checked against the statement pages themselves rather than
    /// against literals, because it is the agreement that matters.
    @Test("Every line matches the page it points at")
    func linesMatchTheirPages() {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        _ = store.addCustomer(name: "Fatima")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, createdAt: at(10))
        _ = store.recordPayment(customerKey: Customer.key(for: "Ahmed"), amount: 300, receivedAt: at(12))
        store.saveBill(customer: "Fatima", paid: 0, amount: 250, createdAt: at(11))

        let book = store.ledgerBook()
        let page = SummaryDocument.forLedgerBook(
            statements: book, settings: store.settings, strings: english, now: now
        )
        let pages = book.map {
            StatementDocument.make(statement: $0, settings: store.settings, strings: english, now: now)
        }

        #expect(page.rows.count == pages.count)
        for (line, statement) in zip(page.rows, pages) {
            #expect(line.name == statement.partyName)
            #expect(
                line.amount == statement.closingValue,
                "the contents disagrees with \(statement.partyName)'s page"
            )
        }
    }

    /// The foot is what the column adds up to, credits included — not the shop's
    /// receivable, which counts only what is owed. A total that is not the sum of
    /// the lines above it is the figure a reader stops trusting the page over.
    @Test("The total is the column, not the receivable")
    func totalIsTheColumn() {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        _ = store.addCustomer(name: "Khalid")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, createdAt: at(10))
        _ = store.recordPayment(customerKey: Customer.key(for: "Khalid"), amount: 250, receivedAt: at(12))

        let page = index(store)

        #expect(page.totalLabel == "Total")
        #expect(page.totalValue == "SAR 750", "a thousand owed less two hundred and fifty held")
        // The chasing list is the other document, and it says something else on
        // purpose. Both are right; they answer different questions.
        let receivable = SummaryDocument.forReceivable(
            customers: store.customers(), settings: store.settings, strings: english, now: now
        )
        #expect(receivable.totalValue == "SAR 1,000")
        #expect(receivable.rows.count == 1, "Khalid is not a debtor")
    }

    @Test("A shop with no customers has an index that says so")
    func emptyShop() {
        let page = index(makeStore())

        #expect(page.isEmpty)
        #expect(page.emptyLine == english.ledgerNoCustomers)
    }
}
