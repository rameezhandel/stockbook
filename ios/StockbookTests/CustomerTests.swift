import Testing
import Foundation
@testable import Stockbook

/// Customers are derived from bills and identified by name, so how that name is
/// compared *is* the feature. A shop where "ahmed" and "Ahmed" are two people
/// has a broken debtors list.
@Suite("Customers")
@MainActor
struct CustomerTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @Test("Case and stray spaces do not split one person in two")
    func caseInsensitiveIdentity() {
        let store = makeStore()
        let hinge = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 1, price: 10)], customer: "Ahmed", paid: nil)
        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 2, price: 10)], customer: "  ahmed ", paid: nil)
        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 3, price: 10)], customer: "AHMED", paid: nil)

        let customers = store.customers()
        #expect(customers.count == 1)
        #expect(customers.first?.billCount == 3)
        #expect(customers.first?.total == 60)
    }

    @Test("The most recent spelling is the one shown")
    func mostRecentSpellingWins() {
        let store = makeStore()
        let hinge = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 1, price: 10)], customer: "ahmed", paid: nil)
        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 1, price: 10)], customer: "Ahmed Al-Amri", paid: nil)

        // Correcting the capitalisation on a new bill corrects it everywhere it
        // is shown, without rewriting what the older bill records.
        #expect(store.customers().first?.name == "Ahmed Al-Amri")
        #expect(store.bills.last?.who == "ahmed")
    }

    @Test("Filtering by customer ignores case, and a removed bill is not listed")
    func filterByCustomer() throws {
        let store = makeStore()
        let hinge = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 1, price: 10)], customer: "Ahmed", paid: nil)
        let second = try #require(
            store.saveBill(lines: [.init(productUID: hinge.uid, qty: 1, price: 10)], customer: "ahmed", paid: nil)
        )
        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 1, price: 10)], customer: "Sami", paid: nil)

        store.deleteBill(number: second.number)

        let key = Customer.key(for: "AHMED")
        // Removed outright: it is not history any more, so it is not listed.
        #expect(store.bills(forCustomer: key).count == 1)
        #expect(store.bills(forCustomer: Customer.key(for: "sami")).count == 1)
    }

    @Test("A removed bill is neither a sale nor a debt")
    func removedBillsLeaveTheFigures() throws {
        let store = makeStore()
        let hinge = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 5, price: 10)], customer: "Ahmed", paid: 20)
        let mistake = try #require(
            store.saveBill(lines: [.init(productUID: hinge.uid, qty: 9, price: 10)], customer: "Ahmed", paid: 0)
        )

        store.deleteBill(number: mistake.number)

        let ahmed = try #require(store.customers().first)
        #expect(ahmed.billCount == 1)
        #expect(ahmed.total == 50)
        #expect(ahmed.owed == 30)
    }

    @Test("The owed banner counts one person however they were capitalised")
    func outstandingDedupesByKey() {
        let store = makeStore()
        let hinge = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)

        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 10, price: 10)], customer: "Ahmed", paid: 60)
        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 10, price: 10)], customer: "AHMED", paid: 16)

        let owed = store.outstanding()
        #expect(owed.names.count == 1)
        #expect(owed.total == 124)
    }

    @Test("Suggestions match without regard to case")
    func suggestionsIgnoreCase() {
        let store = makeStore()
        let hinge = store.addProduct(name: "Hinge", stock: 100, cost: 3, price: 10)
        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 1, price: 10)], customer: "Ahmed Contracting", paid: nil)

        #expect(store.customerSuggestions(matching: "AHM").map(\.name) == ["Ahmed Contracting"])
        #expect(
            store.customerSuggestions(matching: "  ahmed contracting ").isEmpty,
            "an exact match differing only in case is still an exact match"
        )
    }

    @Test("The key is the one place a name becomes an identity")
    func keyNormalisation() {
        #expect(Customer.key(for: "Ahmed") == Customer.key(for: "  AHMED  "))
        #expect(Customer.key(for: "Ahmed") != Customer.key(for: "Ahmad"))
        #expect(Customer.key(for: "   ").isEmpty)
    }
}
