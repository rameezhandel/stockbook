import Testing
import Foundation
@testable import Stockbook

/// The bill as something you can send somebody.
///
/// A port of `BillTextTests.kt`. Checked as text rather than by reading a screen,
/// which is the point of having it in one pure function: what the customer
/// receives and what the owner is looking at come from the same figures.
@MainActor
@Suite("The bill as a message")
struct BillTextTests {

    private let english = Strings(language: .english)

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    private func text(_ store: StockbookStore, shopName: String = "Handel Hardware") throws -> String {
        let bill = try #require(store.bills.first)
        return BillText.plainText(bill, shopName: shopName, currency: .sar, strings: english)
    }

    @Test("A paid bill reads as the document does")
    func paidBill() throws {
        let store = makeStore()
        let lock = store.addProduct(name: "Cisa lock", stock: 50, cost: 60, price: 95)
        let hinge = store.addProduct(name: "Brass hinge", stock: 100, cost: 4, price: 7.5)
        store.saveBill(
            lines: [
                DraftLine(productUID: lock.uid, qty: 2, price: 95),
                DraftLine(productUID: hinge.uid, qty: 4, price: 7.5),
            ],
            customer: "Ahmed",
            paid: nil,
            invoiceNo: "A-1024"
        )

        let lines = try text(store).components(separatedBy: "\n")

        #expect(lines[0] == "Handel Hardware")
        #expect(lines[1] == "A-1024", "the number the owner wrote, not the app's counter")
        #expect(lines[3].contains("Ahmed"))
        #expect(lines.contains { $0.hasPrefix("Cisa lock") && $0.contains("2 × SAR 95") && $0.hasSuffix("SAR 190") })
        #expect(lines.contains { $0.hasPrefix("Brass hinge") && $0.contains("4 × SAR 7.50") && $0.hasSuffix("SAR 30") })
        #expect(lines.contains { $0 == "Total: SAR 220" })
        #expect(lines.last == english.paidInFullCash)
    }

    @Test("A part-paid bill says what is still owed")
    func partPaidBill() throws {
        let store = makeStore()
        let lock = store.addProduct(name: "Cisa lock", stock: 50, cost: 60, price: 95)
        store.saveBill(
            lines: [DraftLine(productUID: lock.uid, qty: 2, price: 95)],
            customer: "Ahmed",
            paid: 100
        )

        let body = try text(store)

        #expect(body.contains(english.partPaidNote(paid: "SAR 100", who: "Ahmed", balance: "SAR 90")), "\(body)")
    }

    @Test("A shop with no name has no letterhead")
    func noLetterhead() throws {
        let store = makeStore()
        let lock = store.addProduct(name: "Cisa lock", stock: 50, cost: 60, price: 95)
        store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 1, price: 95)], customer: "Ahmed", paid: nil)

        // Not an empty first line: a blank where the shop's name goes reads as
        // something the app failed to fill in.
        let first = try text(store, shopName: "  ").components(separatedBy: "\n").first
        #expect(first == english.billNumber(1))
    }
}
