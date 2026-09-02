import Testing
import Foundation
@testable import Stockbook

/// The three pages the book gained beside the expense summary.
///
/// The twin of the trading half of `SummaryDocumentTests.kt`. One claim matters
/// more than the rest: **the foot of each page is the figure the card above the
/// list was showing.** Two answers to one question is the whole thing these pages
/// exist to avoid.
@Suite("Summary pages")
@MainActor
struct SummaryPagesTests {

    private let strings = Strings(language: .english)
    private let day = Date(timeIntervalSince1970: 1_787_048_400)

    private func trading() -> StockbookStore {
        let store = StockbookStore(repository: InMemoryRepository())
        _ = store.addCustomer(name: "Ahmed")
        _ = store.addCustomer(name: "Fatima")
        _ = store.addSupplier(name: "Gulf Traders")
        store.saveBill(customer: "Ahmed", paid: nil, amount: 300, createdAt: day)
        store.saveBill(customer: "Ahmed", paid: nil, amount: 200, createdAt: day)
        store.saveBill(customer: "Fatima", paid: nil, amount: 900, createdAt: day)
        _ = store.recordPurchase(lines: [], supplierKey: "gulf traders", amount: 800, createdAt: day)
        _ = store.recordPayment(customerKey: "ahmed", amount: 250, receivedAt: day)
        _ = store.recordSupplierPayment(supplierKey: "gulf traders", amount: 600, paidAt: day)
        return store
    }

    /// Several bills to one customer are one line and a count, not several rows.
    /// A month is three hundred bills, and the page exists to be read.
    @Test("The sales page folds a customer's bills into one line")
    func salesFolds() {
        let store = trading()
        let month = StatementPeriod.month(day)

        let page = SummaryDocument.forSales(
            lines: store.salesByCustomerIn(month),
            range: month.range(),
            settings: store.settings,
            strings: strings
        )

        #expect(page.rows.count == 2, "one row each, not one per bill")
        #expect(page.rows.first?.name == "Fatima", "biggest first")
        #expect(page.rows.last?.detail == "2 bills")
    }

    /// The foot of every one of these pages is the figure the card above the list
    /// was showing.
    @Test("Each page's total is the shop's own figure for that span")
    func totalsTie() {
        let store = trading()
        let month = StatementPeriod.month(day)
        let range = month.range()

        let sales = SummaryDocument.forSales(
            lines: store.salesByCustomerIn(month), range: range,
            settings: store.settings, strings: strings
        )
        let purchases = SummaryDocument.forPurchases(
            lines: store.purchasesBySupplierIn(month), range: range,
            settings: store.settings, strings: strings
        )
        let payments = SummaryDocument.forPayments(
            lines: store.receiptsByCustomerIn(month), paidOut: store.paidOutIn(month),
            range: range, settings: store.settings, strings: strings
        )

        let currency = store.settings.currency
        #expect(sales.totalValue == Money.text(store.soldIn(month), in: currency))
        #expect(purchases.totalValue == Money.text(store.boughtIn(month), in: currency))
        #expect(payments.totalValue == Money.text(store.receivedIn(month), in: currency))
    }

    /// Money out is stated, but never inside a column of money in.
    ///
    /// A total that is not what the rows above add up to is the figure the first
    /// reader to check it stops trusting — so what the shop paid its suppliers
    /// goes under the total, in words.
    @Test("The payments page states what went out without counting it in")
    func paymentsFootnote() {
        let store = trading()
        let month = StatementPeriod.month(day)

        let page = SummaryDocument.forPayments(
            lines: store.receiptsByCustomerIn(month), paidOut: store.paidOutIn(month),
            range: month.range(), settings: store.settings, strings: strings
        )

        #expect(page.rows.count == 1, "the vouchers are not rows")
        #expect(page.totalValue == Money.text(250, in: store.settings.currency))
        #expect(page.footnote?.contains("600") == true, "and says what went out")
    }

    /// No footnote where nothing went out — "0 paid to suppliers" is a line that
    /// makes the reader stop.
    @Test("A span with no vouchers has no footnote")
    func noFootnote() {
        let store = StockbookStore(repository: InMemoryRepository())
        _ = store.addCustomer(name: "Ahmed")
        _ = store.recordPayment(customerKey: "ahmed", amount: 250, receivedAt: day)
        let month = StatementPeriod.month(day)

        let page = SummaryDocument.forPayments(
            lines: store.receiptsByCustomerIn(month), paidOut: store.paidOutIn(month),
            range: month.range(), settings: store.settings, strings: strings
        )

        #expect(page.footnote == nil)
    }

    /// Nothing in the span is a sentence, not an empty table.
    @Test("An empty span says so on each of the three")
    func emptySpan() {
        let store = trading()
        let quiet = StatementPeriod.month(day.addingTimeInterval(-180 * 24 * 3600))
        let range = quiet.range()

        #expect(SummaryDocument.forSales(
            lines: store.salesByCustomerIn(quiet), range: range,
            settings: store.settings, strings: strings
        ).isEmpty)
        #expect(SummaryDocument.forPurchases(
            lines: store.purchasesBySupplierIn(quiet), range: range,
            settings: store.settings, strings: strings
        ).isEmpty)
        #expect(SummaryDocument.forPayments(
            lines: store.receiptsByCustomerIn(quiet), paidOut: store.paidOutIn(quiet),
            range: range, settings: store.settings, strings: strings
        ).isEmpty)
    }
}
