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

        #expect(store.updateCustomer(key: "ahmed", name: "Ahmed", phone: "0500 111 222", place: nil))

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

        #expect(store.updateCustomer(key: "ahmed", name: "Ahmed Contracting", phone: nil, place: nil))

        #expect(store.customers().count == 1, "not one customer with the bills and another with the name")
        let customer = try #require(store.customers().first)
        #expect(customer.key == "ahmed contracting")
        #expect(customer.billCount == 1)
        #expect(store.bills[0].who == "Ahmed Contracting")
        #expect(store.payments[0].customerKey == "ahmed contracting")
        // 95 owed, 40 paid.
        #expect(customer.owed == 55)
    }

    /// The rename that used to swallow another account.
    ///
    /// It merged them: one customer, the other's opening balance thrown away and
    /// their credit notes stranded under a key nothing pointed at any more. On a
    /// book of companies that is two firms' ledgers fused by a typo, with what
    /// each of them owes quietly changed and no undo. This app now offers no way
    /// to join two accounts at all — a keystroke is certainly not one.
    @Test("A rename onto somebody already there is refused")
    func renameOntoExisting() throws {
        let (store, product) = shopWithProduct()
        store.addCustomer(name: "Ahmed", openingBalance: 300)
        store.addCustomer(name: "Ahmed Contracting", openingBalance: 700)
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)], customer: "Ahmed", paid: 0)
        store.addCreditNote(customerKey: "ahmed", amount: 50)

        #expect(!store.updateCustomer(key: "ahmed", name: "Ahmed Contracting", phone: "0500 111 222", place: nil, openingBalance: 300))

        // Both still there, both still whole. The figures are the assertion that
        // matters: the old merge left this test passing on counts alone.
        #expect(store.customers().count == 2)
        let ahmed = try #require(store.customer(key: "ahmed"))
        let contracting = try #require(store.customer(key: "ahmed contracting"))
        #expect(ahmed.owed == 345, "300 opening + 95 billed - 50 credited")
        #expect(contracting.owed == 700, "untouched, opening balance and all")
        #expect(contracting.openingBalance == 700)
        // And nothing was half-moved on the way to being refused.
        #expect(store.bills[0].who == "Ahmed")
        #expect(store.creditNotes[0].customerKey == "ahmed")
    }

    @Test("The form is told which account a name would collide with")
    func clashLookup() {
        let store = makeStore()
        store.addCustomer(name: "Ahmed")
        store.addCustomer(name: "Ahmed Contracting")

        // Spelling and spacing are not identity, so neither is what it answers on.
        #expect(store.customerClashing("  ahmed CONTRACTING ")?.name == "Ahmed Contracting")
        // Their own name is not a clash, or correcting a phone number would be
        // refused for the name the owner is keeping.
        #expect(store.customerClashing("Ahmed", exceptKey: "ahmed") == nil)
        #expect(store.customerClashing("Ahmed Sons") == nil)
        #expect(store.customerClashing("   ") == nil)
    }

    /// A rename onto a name nobody answers to is still a rename, and still works.
    @Test("A rename onto a free name is untouched by the gate")
    func renameOntoFreeName() {
        let (store, product) = shopWithProduct()
        store.addCustomer(name: "Ahmed")
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)], customer: "Ahmed", paid: 0)

        #expect(store.updateCustomer(key: "ahmed", name: "Ahmed Sons", phone: nil, place: nil))

        #expect(store.customers().count == 1)
        #expect(store.bills[0].who == "Ahmed Sons")
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
        #expect(statement.party.key == "ahmed")
        #expect(statement.closingBalance == 300)

        #expect(store.statement(forCustomer: "nobody", period: .thisYear()) == nil)
    }
}

/// The balance a customer brought over from the paper book.
///
/// The reason this matters: a shop that starts using Stockbook on Monday has
/// customers who already owe from Sunday, and without this every one of them
/// starts at zero — which is the app telling the owner they are owed nothing.
@Suite("Opening balance")
@MainActor
struct OpeningBalanceTests {

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    @Test("A customer who owed from before shows it with no bills at all")
    func carriedOverWithNoHistory() throws {
        let store = makeStore()

        store.addCustomer(name: "Ahmed", openingBalance: 5000)

        let customer = try #require(store.customers().first)
        #expect(customer.owed == 5000)
        #expect(customer.openingBalance == 5000)
        #expect(customer.billCount == 0, "owing money is not the same as having bought something here")
    }

    @Test("It is never negative")
    func clamped() throws {
        let store = makeStore()

        store.addCustomer(name: "Ahmed", openingBalance: -200)

        let customer = try #require(store.customers().first)
        #expect(customer.openingBalance == 0)
    }

    @Test("Bills and payments stack on top of it")
    func stacking() throws {
        let store = makeStore()
        let product = store.addProduct(name: "Cisa lock", stock: 100, cost: 60, price: 95)
        store.addCustomer(name: "Ahmed", openingBalance: 1000)
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)], customer: "Ahmed", paid: 0)
        store.recordPayment(customerKey: "ahmed", amount: 300)

        // 1000 carried over + 190 billed − 300 paid.
        let customer = try #require(store.customers().first)
        #expect(customer.owed == 890)
    }

    @Test("Correcting it in the editor corrects what they owe")
    func corrected() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed", openingBalance: 5000)

        #expect(store.updateCustomer(key: "ahmed", name: "Ahmed", phone: nil, place: nil, openingBalance: 500))

        let customer = try #require(store.customers().first)
        #expect(customer.owed == 500)
    }

    /// It predates every bill, so it belongs to every period's brought-forward —
    /// including a period that contains nothing else at all.
    @Test("Every statement period carries it forward")
    func everyPeriod() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed", openingBalance: 700)

        let statement = try #require(store.statement(forCustomer: "ahmed", period: .thisMonth()))

        #expect(statement.openingBalance == 700)
        #expect(statement.closingBalance == 700)
        #expect(statement.isEmpty, "nothing happened this month, and they still owe")
    }

    @Test("The running balance still lands on the closing balance")
    func runningBalanceAgrees() {
        let customer = Customer(
            name: "Ahmed", key: "ahmed", billCount: 0, total: 0, owed: 0,
            phone: nil, place: nil, openingBalance: 1000, isOnRoster: true,
            lastPaidAt: nil, firstBilledAt: nil
        )
        let bill = Bill(
            number: 1,
            lines: [BillLine(productUID: nil, name: "Cisa lock", qty: 1, price: 250)],
            total: 250,
            paid: 0,
            who: "Ahmed",
            createdAt: date(2026, 8, 4)
        )
        let payment = Payment(customerKey: "ahmed", amount: 400, receivedAt: date(2026, 8, 20))

        let statement = Statement.make(
            customer: customer,
            bills: [bill],
            payments: [payment],
            period: .month(date(2026, 8, 10)),
            calendar: calendar
        )

        #expect(statement.openingBalance == 1000)
        // 1000 + 250 − 400.
        #expect(statement.closingBalance == 850)
        let last = statement.runningBalances.last
        #expect(last == statement.closingBalance)
    }

    /// The Today banner. It used to walk bills directly, which meant it ignored
    /// payments *and* opening balances — naming somebody who had settled up and
    /// missing somebody who owed from before the app.
    @Test("The outstanding banner counts what customers actually owe")
    func outstandingIsHonest() {
        let store = makeStore()
        let product = store.addProduct(name: "Cisa lock", stock: 100, cost: 60, price: 95)
        store.addCustomer(name: "Ahmed", openingBalance: 1000)
        store.saveBill(lines: [DraftLine(productUID: product.uid, qty: 1, price: 95)], customer: "Sami", paid: 0)
        // Sami settles up in full; Ahmed has never bought anything here.
        store.recordPayment(customerKey: "sami", amount: 95)

        let outstanding = store.outstanding()

        #expect(outstanding.names == ["Ahmed"], "Sami has paid; Ahmed owes from the old book")
        #expect(outstanding.total == 1000)
    }

    @Test("A backup carries it")
    func backupRoundTrip() throws {
        let store = makeStore()
        store.addCustomer(name: "Ahmed", openingBalance: 5000)

        let document = try BackupService.decode(try BackupService.encode(store.makeBackupDocument()))
        #expect(document.version == BackupDocument.currentVersion)
        let row = try #require(document.customers.first)
        #expect(row.openingBalance == 5000)

        let restored = makeStore()
        restored.replaceEverything(with: document)
        let customer = try #require(restored.customers().first)
        #expect(customer.owed == 5000)
    }

    /// The trap this codebase keeps setting for itself: a default value does not
    /// make Swift's synthesised decoder tolerate a missing key.
    @Test("A customer stored before opening balances existed still decodes")
    func storedRecordWithoutTheKey() throws {
        let json = Data("""
        {
          "key": "ahmed",
          "name": "Ahmed Contracting",
          "phone": "0500 111 222",
          "createdAt": "2026-08-01T06:00:00Z"
        }
        """.utf8)

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let record = try decoder.decode(CustomerRecord.self, from: json)

        #expect(record.key == "ahmed")
        #expect(record.openingBalance == 0)
        #expect(record.phone == "0500 111 222")
    }

}
