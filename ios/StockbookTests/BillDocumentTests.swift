import Testing
import Foundation
@testable import Stockbook

/// The bill as the paper the customer walks out with.
///
/// The twin of `BillDocumentTests.kt`, test for test. The claims worth pinning
/// are the ones a customer would notice and argue about: that the arithmetic
/// behind every line is on the page, that a discount is shown rather than quietly
/// folded into the total, and that a part-paid bill says so — with the figure
/// still owed and the name of who owes it.
///
/// It is also the document that replaced a plain-text bill, so it has to carry
/// everything that text carried. Nothing here may be less than what was sent
/// before.
@Suite("Bill document")
@MainActor
struct BillDocumentTests {

    private let english = Strings(language: .english)

    private var at: Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.date(from: DateComponents(year: 2026, month: 9, day: 2, hour: 13, minute: 49))!
    }

    private func makeStore() -> StockbookStore {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setOwnerName("Al Salam Hardware")
        store.setShopAddress("King Fahd Road\n\nAl Khobar")
        return store
    }

    private func page(_ store: StockbookStore) throws -> BillDocument {
        let bill = try #require(store.bills.first)
        return BillDocument.make(
            bill: bill,
            settings: store.settings,
            strings: english,
            customer: store.customer(key: "ahmed")
        )
    }

    @Test("The letterhead is the shop, and the page says what it is")
    func letterhead() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 235, createdAt: at, invoiceNo: "5678")

        let page = try page(store)

        #expect(page.shopName == "Al Salam Hardware")
        #expect(page.shopAddressLines == ["King Fahd Road", "Al Khobar"])
        #expect(page.docType == "Invoice")
        #expect(page.addressedToLabel == "Billed to:")
        #expect(page.partyName == "Ahmed")
    }

    /// One number, never both. Two numbers on a document is how somebody reads
    /// out the wrong one over the phone — the rule `Bill.reference` already
    /// keeps, and this page takes it from there rather than deciding again.
    @Test("The paper's own number wins, and the app's stands in where there is none")
    func reference() throws {
        let typed = makeStore()
        typed.saveBill(customer: "Ahmed", paid: nil, amount: 235, createdAt: at, invoiceNo: "5678")
        #expect(try page(typed).reference == "5678")

        let untyped = makeStore()
        untyped.saveBill(customer: "Ahmed", paid: nil, amount: 235, createdAt: at)
        #expect(try page(untyped).reference == "Bill #1")
    }

    @Test("An itemised bill shows the arithmetic behind every line")
    func itemised() throws {
        let store = makeStore()
        let lock = store.addProduct(name: "Cisa lock", stock: 50, cost: 60, price: 95)
        let hinge = store.addProduct(name: "Brass hinge", stock: 100, cost: 4, price: 7.5)
        store.saveBill(
            lines: [
                DraftLine(productUID: lock.uid, qty: 2, price: 95),
                DraftLine(productUID: hinge.uid, qty: 4, price: 7.5)
            ],
            customer: "Ahmed",
            paid: nil,
            createdAt: at
        )

        let page = try page(store)

        #expect(page.isItemised)
        #expect(page.lines.count == 2)
        #expect(page.lines[0].name == "Cisa lock")
        #expect(page.lines[0].detail == "2 × SAR 95")
        #expect(page.lines[0].amount == "SAR 190")
        #expect(page.lines[1].detail == "4 × SAR 7.50")
        #expect(page.lines[1].amount == "SAR 30")
        #expect(page.totalValue == "SAR 220")
    }

    /// A bill copied out of the paper book is a figure and nothing else, and the
    /// page has to be a page anyway. The shape of this shop, not an edge case.
    @Test("A bill entered as a figure has no lines and still prints")
    func amountOnly() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 235, createdAt: at)

        let page = try page(store)

        #expect(!page.isItemised)
        #expect(page.lines.isEmpty)
        #expect(page.totalLabel == "Total")
        #expect(page.totalValue == "SAR 235")
    }

    /// The customer's own copy is exactly where a discount belongs: it is the
    /// reason the figure is what it is, and a shop that gave ten per cent away
    /// should get the credit for it.
    @Test("A discount is shown, and only where one was given")
    func discount() throws {
        let plain = makeStore()
        plain.saveBill(customer: "Ahmed", paid: nil, amount: 200, createdAt: at)
        #expect(try page(plain).summaryRows.isEmpty, "no discount, so no line about one")

        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 200, createdAt: at, discountPercent: 10)

        let rows = try page(store).summaryRows

        #expect(rows.count == 2)
        #expect(rows[0].label == "Subtotal")
        #expect(rows[0].value == "SAR 200")
        #expect(rows[1].label == "Discount 10%")
        #expect(rows[1].value == "SAR 20")
        #expect(rows[1].deduction, "the one line that comes off")
        #expect(try page(store).totalValue == "SAR 180")
    }

    @Test("A bill settled at the counter says so")
    func paidInFull() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 235, createdAt: at)

        #expect(try page(store).paymentNote == "Paid in full, cash.")
    }

    /// The line the customer checks. It names what was handed over, who still
    /// owes and how much — a part-paid bill that merely said "part paid" would
    /// send them back to the counter to ask.
    @Test("A part paid bill names what is left and who owes it")
    func partPaid() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: 25, amount: 235, createdAt: at)

        let note = try page(store).paymentNote

        #expect(note.contains("SAR 25"), "\(note)")
        #expect(note.contains("SAR 210"), "\(note)")
        #expect(note.contains("Ahmed"), "\(note)")
    }

    /// The roster's own place and phone, where the roster knows them.
    @Test("The customer's details come from the roster, and are left out when there are none")
    func partyLines() throws {
        let known = makeStore()
        _ = known.addCustomer(name: "Ahmed", phone: "0501234567", place: "Al Khobar")
        known.saveBill(customer: "Ahmed", paid: nil, amount: 235, createdAt: at)
        #expect(try page(known).partyLines == ["Al Khobar", "0501234567"])

        let stranger = makeStore()
        stranger.saveBill(customer: "Ahmed", paid: nil, amount: 235, createdAt: at)
        #expect(try page(stranger).partyLines.isEmpty)
    }

    @Test("The date carries the time, because two bills a day to one customer is ordinary")
    func dateAndTime() throws {
        let store = makeStore()
        store.saveBill(customer: "Ahmed", paid: nil, amount: 235, createdAt: at)

        let page = try page(store)

        #expect(page.dateLabel == "Date")
        #expect(
            page.dateValue == english.billWhen(
                date: english.longDate(at),
                time: english.time(at)
            )
        )
    }
}
