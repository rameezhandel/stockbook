import Testing
import Foundation
@testable import Stockbook

/// The roster: customers as stored records, merged with what history says.
///
/// The rule under all of it is that neither source may be lost. Somebody entered
/// during setup who has never bought anything is a customer; a name typed at the
/// counter that nobody added is a customer too.
@Suite("Customer roster")
@MainActor
struct CustomerRosterTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    /// A store with one product and a bill-writing helper, since every figure a
    /// customer has is derived from bills.
    private func shopWithProduct() -> (StockbookStore, Product) {
        let store = makeStore()
        let product = store.addProduct(name: "Cisa lock", stock: 100, cost: 60, price: 95)
        return (store, product)
    }

    @Test("A customer added at setup exists before they have ever bought anything")
    func addedWithNoHistory() throws {
        let store = makeStore()

        store.addCustomer(name: "Ahmed Contracting", phone: "0500 111 222", place: "Al Khobar")

        let customer = try #require(store.customers().first)
        #expect(customer.name == "Ahmed Contracting")
        #expect(customer.key == "ahmed contracting")
        #expect(customer.billCount == 0)
        #expect(customer.owed == 0)
        #expect(customer.phone == "0500 111 222")
        #expect(customer.place == "Al Khobar")
        #expect(customer.isOnRoster)
        #expect(!customer.hasHistory)
    }

    @Test("Adding the same person twice corrects them rather than duplicating them")
    func duplicateIsACorrection() {
        let store = makeStore()

        store.addCustomer(name: "Ahmed Contracting", phone: "0500 111 222")
        // Same person, typed differently and with a better phone number.
        store.addCustomer(name: "  ahmed contracting ", phone: "0500 999 888")

        #expect(store.customerRecords.count == 1)
        #expect(store.customers().count == 1)
        #expect(store.customerRecords[0].phone == "0500 999 888")
    }

    @Test("A blank name is not a customer")
    func blankIsRejected() {
        let store = makeStore()

        let record = store.addCustomer(name: "   ")

        #expect(record == nil)
        #expect(store.customerRecords.isEmpty)
    }

    @Test("A field left blank is absent, not an empty string")
    func blankFieldsAreAbsent() throws {
        let store = makeStore()

        store.addCustomer(name: "Sami", phone: "", place: "   ")

        let record = try #require(store.customerRecords.first)
        #expect(record.phone == nil)
        #expect(record.place == nil)
    }

    @Test("A name only ever typed on a bill is still a customer")
    func historyOnly() throws {
        let (store, product) = shopWithProduct()

        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)], customer: "Walk-in Sami", paid: nil)

        let customer = try #require(store.customers().first)
        #expect(customer.name == "Walk-in Sami")
        #expect(customer.billCount == 1)
        #expect(!customer.isOnRoster)
    }

    @Test("The roster's spelling wins over whatever was typed at the counter")
    func rosterSpellingWins() throws {
        let (store, product) = shopWithProduct()
        store.addCustomer(name: "Ahmed Contracting")

        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)], customer: "ahmed CONTRACTING", paid: nil)

        let customer = try #require(store.customers().first)
        // One person, and shown the way somebody deliberately typed it.
        #expect(store.customers().count == 1)
        #expect(customer.name == "Ahmed Contracting")
        #expect(customer.billCount == 1)
    }

    @Test("Removing a customer keeps their bills")
    func removeKeepsHistory() throws {
        let (store, product) = shopWithProduct()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)], customer: "Ahmed", paid: nil)

        store.removeCustomer(key: "ahmed")

        #expect(store.customerRecords.isEmpty)
        #expect(store.bills.count == 1, "a bill is history and is never deleted")
        let customer = try #require(store.customers().first)
        #expect(customer.billCount == 1)
        #expect(!customer.isOnRoster)
    }

    @Test("Correcting a spelling that keeps the same person keeps their bills together")
    func editWithoutRekeying() throws {
        let (store, product) = shopWithProduct()
        store.addCustomer(name: "ahmed")
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)], customer: "ahmed", paid: nil)

        store.updateCustomer(key: "ahmed", name: "Ahmed", phone: "0500 111 222", place: nil)

        let customer = try #require(store.customers().first)
        #expect(customer.name == "Ahmed")
        #expect(customer.key == "ahmed", "case alone is not a different person")
        #expect(customer.billCount == 1)
        #expect(customer.phone == "0500 111 222")
    }

    /// The one case where a saved bill is edited, and it has to be: the
    /// alternative is a roster entry and its own bills never meeting again.
    @Test("A real rename follows through onto the bills and payments")
    func renameFollowsThrough() throws {
        let (store, product) = shopWithProduct()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)], customer: "Ahmed", paid: 0)
        store.recordPayment(customerKey: "ahmed", amount: 40)

        store.updateCustomer(key: "ahmed", name: "Ahmed Contracting", phone: nil, place: nil)

        #expect(store.customers().count == 1, "not one customer with the bills and another with the name")
        let customer = try #require(store.customers().first)
        #expect(customer.key == "ahmed contracting")
        #expect(customer.billCount == 1)
        #expect(store.bills[0].who == "Ahmed Contracting")
        #expect(store.payments[0].customerKey == "ahmed contracting")
        // 95 owed, 40 paid.
        #expect(customer.owed == 55)
    }

    @Test("Renaming onto somebody already there merges rather than duplicating")
    func renameOntoExisting() {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        store.addCustomer(name: "Ahmed Contracting")

        store.updateCustomer(key: "ahmed", name: "Ahmed Contracting", phone: "0500 111 222", place: nil)

        #expect(store.customerRecords.count == 1)
        #expect(store.customers().count == 1)
    }
}

/// Payments, and the figure they exist to move.
@Suite("Payments")
@MainActor
struct PaymentTests {

    private func shopWithDebt() -> (StockbookStore, Product) {
        let store = StockbookStore(repository: InMemoryRepository())
        let product = store.addProduct(name: "Cisa lock", stock: 100, cost: 60, price: 95)
        // Bought 900, paid 500 at the counter: owes 400.
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 10, price: 90)],
            customer: "Ahmed",
            paid: 500
        )
        return (store, product)
    }

    @Test("A payment brings down what the customer owes")
    func reducesOwed() throws {
        let (store, _) = shopWithDebt()
        let before = try #require(store.customers().first)
        #expect(before.owed == 400)

        store.recordPayment(customerKey: "ahmed", amount: 150)

        let after = try #require(store.customers().first)
        #expect(after.owed == 250)
    }

    /// Not just the customer row: this is the figure on the Today screen that
    /// tells the owner who to chase.
    @Test("Paying in full clears the outstanding total")
    func clearsOutstanding() {
        let (store, _) = shopWithDebt()

        store.recordPayment(customerKey: "ahmed", amount: 400)

        let customer = store.customers().first
        #expect(customer?.owed == 0)
    }

    @Test("Nothing typed is not a payment")
    func zeroIsNoOp() {
        let (store, _) = shopWithDebt()

        let none = store.recordPayment(customerKey: "ahmed", amount: 0)
        let negative = store.recordPayment(customerKey: "ahmed", amount: -50)

        #expect(none == nil)
        #expect(negative == nil)
        #expect(store.payments.isEmpty)
    }

    @Test("A payment with no customer goes nowhere")
    func needsACustomer() {
        let (store, _) = shopWithDebt()

        let payment = store.recordPayment(customerKey: "", amount: 100)

        #expect(payment == nil)
    }

    @Test("A deleted payment puts the balance back")
    func deleteRestores() throws {
        let (store, _) = shopWithDebt()
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 150))

        store.deletePayment(id: payment.id)

        let customer = try #require(store.customers().first)
        #expect(customer.owed == 400)
        #expect(store.payments.isEmpty)
    }

    @Test("Payments survive a relaunch")
    func persisted() throws {
        let repository = InMemoryRepository()
        let store = StockbookStore(repository: repository)
        let product = store.addProduct(name: "Cisa lock", stock: 10, cost: 60, price: 95)
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)], customer: "Ahmed", paid: 0)
        store.addCustomer(name: "Ahmed", phone: "0500 111 222")
        store.recordPayment(customerKey: "ahmed", amount: 20, note: "cash")

        let reopened = StockbookStore(repository: repository)

        #expect(reopened.payments.count == 1)
        #expect(reopened.payments[0].note == "cash")
        #expect(reopened.customerRecords.count == 1)
        let customer = try #require(reopened.customers().first)
        #expect(customer.owed == 75)
        #expect(customer.phone == "0500 111 222")
    }

    @Test("A statement comes off the store for a real customer and nil for a stranger")
    func statementFromStore() throws {
        let (store, _) = shopWithDebt()
        store.recordPayment(customerKey: "ahmed", amount: 100)

        let statement = try #require(store.statement(forCustomer: "ahmed", period: .thisYear()))
        #expect(statement.customer.key == "ahmed")
        #expect(statement.closingBalance == 300)

        #expect(store.statement(forCustomer: "nobody", period: .thisYear()) == nil)
    }
}
