import Testing
import Foundation
@testable import Stockbook

/// Finding one piece of paper, when the owner knows what is printed on it and
/// nothing else.
///
/// The twin of `SearchTests.kt`, test for test. This is the question the four
/// lists in the book cannot answer: each of them narrows to a span, and somebody
/// holding receipt 008455 does not know which month it was written in — that is
/// the reason they are looking it up.
@Suite("Search")
@MainActor
struct SearchTests {

    private func on(_ month: Int, _ day: Int) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.date(from: DateComponents(year: 2026, month: month, day: day, hour: 9))!
    }

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    /// A shop with one of each of the six kinds in it.
    private func shop() -> StockbookStore {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed Al Harbi")
        _ = store.addSupplier(name: "Gulf Traders")
        store.saveBill(
            customer: "Ahmed Al Harbi",
            paid: nil,
            amount: 500,
            createdAt: on(3, 2),
            invoiceNo: "1207"
        )
        _ = store.recordPayment(
            customerKey: "ahmed al harbi",
            amount: 300,
            receivedAt: on(4, 9),
            paymentNo: "008455"
        )
        _ = store.addCreditNote(
            customerKey: "ahmed al harbi",
            amount: 120,
            noteNo: "CN-14",
            issuedAt: on(5, 1)
        )
        _ = store.recordPurchase(
            lines: [],
            supplierKey: "gulf traders",
            amount: 800,
            createdAt: on(6, 3),
            invoiceNo: "GT-902"
        )
        _ = store.recordSupplierPayment(
            supplierKey: "gulf traders",
            amount: 250,
            paidAt: on(7, 7),
            paymentNo: "V-31"
        )
        _ = store.addExpense(amount: 90, note: "Petrol", spentAt: on(8, 11))
        return store
    }

    @Test("A receipt number finds the receipt")
    func receiptNumber() {
        let hits = shop().search("008455")

        #expect(hits.count == 1)
        #expect(hits.first?.kind == .payment)
        #expect(hits.first?.amount == 300)
        #expect(hits.first?.who == "Ahmed Al Harbi", "named, not keyed")
    }

    /// The leading zeros are decoration on the slip, not part of the number.
    /// `InvoiceNo` already settled that for the duplicate check, and search has
    /// to agree with it or the two disagree about what "the same number" means.
    @Test("A number is found without its leading zeros")
    func withoutLeadingZeros() {
        #expect(shop().search("8455").count == 1)
    }

    /// Every kind is reachable, or the search is one nobody can trust.
    @Test("All six kinds turn up")
    func allSixKinds() {
        let store = shop()

        #expect(store.search("1207").first?.kind == .bill)
        #expect(store.search("008455").first?.kind == .payment)
        #expect(store.search("CN-14").first?.kind == .creditNote)
        #expect(store.search("GT-902").first?.kind == .purchase)
        #expect(store.search("V-31").first?.kind == .supplierPayment)
        #expect(store.search("petrol").first?.kind == .expense)
    }

    /// A name pulls up everything filed under it, whichever list it lives on.
    @Test("A name finds that person's whole trail")
    func aNameFindsEverything() {
        let kinds = Set(shop().search("ahmed").map(\.kind))

        #expect(kinds == Set([.bill, .payment, .creditNote]))
    }

    /// Case is not something the owner should have to get right.
    @Test("Matching ignores case on both sides")
    func caseInsensitive() {
        #expect(shop().search("gt-902").count == 1)
        #expect(shop().search("PETROL").count == 1)
    }

    @Test("An amount finds what it was for")
    func anAmount() {
        #expect(shop().search("800").first?.kind == .purchase)
        #expect(shop().search("800").count == 1)
    }

    /// The whole point of the ordering.
    ///
    /// A shop that sold something for 8,455 riyals and also wrote receipt 008455
    /// must still be handed the receipt first — finding it third is the search
    /// failing at the one job it was added for.
    @Test("An exact number beats an amount that happens to match")
    func exactNumberLeads() {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: nil, amount: 8455, createdAt: on(9, 1))
        _ = store.recordPayment(customerKey: "ahmed", amount: 300, receivedAt: on(3, 1), paymentNo: "008455")

        let hits = store.search("008455")

        #expect(hits.count == 2, "both matched")
        #expect(hits.first?.kind == .payment, "the slip with that number leads")
    }

    /// After the exact match, the most recent — which is how the lists read too.
    @Test("The rest come newest first")
    func newestFirst() {
        let dates = shop().search("a").map(\.at)

        #expect(dates == dates.sorted(by: >))
    }

    @Test("Nothing typed finds nothing, rather than everything")
    func emptyQuery() {
        #expect(shop().search("").isEmpty)
        #expect(shop().search("   ").isEmpty)
    }

    @Test("A query nothing answers to comes back empty")
    func noMatch() {
        #expect(shop().search("zzzz").isEmpty)
    }

    /// A single letter must not build a page per record. The cap is what stops a
    /// shop with four thousand bills drawing all of them on a keystroke.
    @Test("The results are capped")
    func capped() {
        let store = makeStore()
        _ = store.addCustomer(name: "Ahmed")
        for _ in 0..<60 {
            store.saveBill(customer: "Ahmed", paid: nil, amount: 10, createdAt: on(3, 2))
        }

        #expect(store.search("ahmed").count == 40)
        #expect(store.search("ahmed", limit: 5).count == 5)
    }
}
