import Testing
import Foundation
@testable import Stockbook

/// The statement as it prints.
///
/// The twin of `StatementDocumentTests.kt`, test for test. The arithmetic is
/// `Statement`'s and tested there; what this pins is the *document* — which rows
/// exist, what they are called, and which figures are bracketed. It matters
/// because the two apps draw the PDF with entirely different graphics code, and
/// this structure is the only thing making them agree. A row added on one
/// platform and not the other would be invisible until somebody compared two
/// printouts side by side.
@Suite("Statement document")
@MainActor
struct StatementDocumentTests {

    private let english = Strings(language: .english)

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    @discardableResult
    private func aShop(_ store: StockbookStore) -> StockbookStore {
        store.setOwnerName("Tayba Trading Services Establishment")
        store.setShopAddress("4343 4343 Adi Ibn Rabi'ah,\nAl-Aziziyah District, 9373\nMadinah, Madinah 42376")
        return store
    }

    private func document(_ store: StockbookStore, key: String = "ahmed") throws -> StatementDocument {
        let statement = try #require(store.statement(forCustomer: key, period: .thisMonth()))
        return StatementDocument.make(statement: statement, settings: store.settings, strings: english)
    }

    // MARK: Who it is from and to

    @Test("The shop's address prints as the lines it was typed on")
    func shopAddressLines() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed", phone: "0500 111 222", place: "Al Khobar")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, invoiceNo: "06011")

        let document = try document(store)

        #expect(document.shopName == "Tayba Trading Services Establishment")
        #expect(document.shopAddressLines == [
            "4343 4343 Adi Ibn Rabi'ah,",
            "Al-Aziziyah District, 9373",
            "Madinah, Madinah 42376"
        ])
    }

    @Test("A shop with no address prints no blank lines")
    func noAddress() throws {
        // The block is skipped rather than drawn empty: a run of blank lines
        // under the shop's name reads as a printer fault.
        let store = makeStore()
        store.setOwnerName("Khalid")
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, invoiceNo: "06011")

        #expect(try document(store).shopAddressLines.isEmpty)
    }

    @Test("The customer block carries what is known and nothing else")
    func customerBlock() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed", phone: "0500 111 222", place: "Al Khobar")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, invoiceNo: "06011")

        let document = try document(store)

        #expect(document.partyName == "Ahmed")
        #expect(document.partyLines == ["Al Khobar", "0500 111 222"])
    }

    @Test("A customer with no details listed shows only a name")
    func bareCustomer() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, invoiceNo: "06011")

        #expect(try document(store).partyLines.isEmpty)
    }

    // MARK: The summary box

    @Test("The summary reads opening, billed, received, due")
    func summaryOrder() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 200, amount: 1000, invoiceNo: "06011")

        let document = try document(store)

        #expect(document.summaryRows.map(\.label) == [
            english.openingBalance,
            english.billedInPeriod,
            english.receivedInPeriod
        ])
        #expect(document.summaryRows[0].value == "SAR 0")
        #expect(document.summaryRows[1].value == "SAR 1,000")
        #expect(document.summaryRows[2].value == "SAR 200")
        #expect(document.closingValue == "SAR 800")
        #expect(document.closingLabel == english.balanceDue)
    }

    @Test("Money coming off is marked as a deduction")
    func deductions() throws {
        // What puts a figure in brackets when it is drawn. A bare minus sign in
        // front of a currency symbol reads as a typo on a printed page.
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 200, amount: 1000, invoiceNo: "06011")

        #expect(try document(store).summaryRows.map(\.deduction) == [false, false, true])
    }

    @Test("Credit notes get their own summary row, and only when there are some")
    func creditRow() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 1000, invoiceNo: "06011")

        #expect(
            try !document(store).summaryRows.contains { $0.label == english.creditNotes },
            "no row before there is one to show"
        )

        store.addCreditNote(customerKey: "ahmed", amount: 540, noteNo: "00130")
        let document = try document(store)

        let credited = try #require(document.summaryRows.first { $0.label == english.creditNotes })
        #expect(credited.value == "SAR 540")
        #expect(credited.deduction)
        #expect(document.closingValue == "SAR 460", "1000 billed less 540 credited")
    }

    /// The gap this closes was real: the rows existed on Android and not on iOS,
    /// so an iPhone printed a statement whose closing balance had moved with no
    /// line saying why. On a document handed across a counter that is the worst
    /// kind of wrong — it looks like an arithmetic mistake by the shop.
    @Test("A moved balance gets its own summary row at each end")
    func transferRows() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed Jeddah")
        store.addCustomer(name: "Ahmed Riyadh")
        store.saveBill(customer: "Ahmed Jeddah", paid: 0, amount: 1000, invoiceNo: "06011")

        let before = try document(store, key: "ahmed jeddah").summaryRows
        #expect(
            !before.contains { $0.label == english.transferredInLabel || $0.label == english.transferredOutLabel },
            "no rows before anything has moved"
        )

        let moved = store.transferBalance(fromKey: "ahmed jeddah", intoKey: "ahmed riyadh", amount: 600)
        #expect(moved != nil)

        // The end it left: a deduction, and the closing figure follows it down.
        let left = try document(store, key: "ahmed jeddah")
        let out = try #require(left.summaryRows.first { $0.label == english.transferredOutLabel })
        #expect(out.value == "SAR 600")
        #expect(out.deduction)
        #expect(left.closingValue == "SAR 400", "1000 billed less 600 moved away")

        // The end it arrived at: a charge, not a deduction, because the account
        // now owes it.
        let arrived = try document(store, key: "ahmed riyadh")
        let into = try #require(arrived.summaryRows.first { $0.label == english.transferredInLabel })
        #expect(into.value == "SAR 600")
        #expect(!into.deduction)
        #expect(arrived.closingValue == "SAR 600")
    }

    @Test("A supplier statement says bought and paid out rather than billed and received")
    func supplierWording() throws {
        let store = makeStore()
        aShop(store)
        let supplier = try #require(store.addSupplier(name: "Al Faisal"))
        let product = store.addProduct(name: "Cisa lock", stock: 0, cost: 60, price: 95)
        store.recordPurchase(
            product: product,
            supplierKey: supplier.key,
            quantity: 10,
            unitCost: 60,
            paid: 0,
            invoiceNo: "INV-88"
        )

        let statement = try #require(store.statementForSupplier(key: supplier.key, period: .thisMonth()))
        let document = StatementDocument.make(statement: statement, settings: store.settings, strings: english)

        #expect(document.summaryRows[1].label == english.purchasedInPeriod)
        #expect(document.summaryRows[2].label == english.paidOutInPeriod)
    }

    // MARK: The activity table

    @Test("Every row names the paper it came from")
    func rowsNameTheirDocument() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            customer: "Ahmed",
            paid: 0,
            amount: 1000,
            createdAt: date(2026, 8, 1),
            invoiceNo: "06011"
        )
        store.recordPayment(
            customerKey: "ahmed",
            amount: 300,
            receivedAt: date(2026, 8, 3),
            paymentNo: "R-1"
        )
        store.addCreditNote(
            customerKey: "ahmed",
            amount: 200,
            noteNo: "00130",
            issuedAt: date(2026, 8, 5)
        )

        let statement = try #require(store.statement(forCustomer: "ahmed", period: .month(date(2026, 8, 10))))
        let document = StatementDocument.make(statement: statement, settings: store.settings, strings: english)

        // The kind of document, then its number. "06011" alone tells somebody
        // checking against their own file nothing about what 06011 *is*, and the
        // three books are numbered separately.
        #expect(
            document.activityRows.map(\.reference)
                == ["Invoice #06011", "Payment #R-1", "Credit Note #00130"]
        )
        // The running balance reads down, which is the column's whole job.
        #expect(document.activityRows.map(\.balance) == ["SAR 1,000", "SAR 700", "SAR 500"])

        // The charge is in one column and what came off is in the other. That is
        // what replaced the brackets.
        #expect(document.activityRows.map(\.charge) == ["SAR 1,000", "", ""])
        #expect(document.activityRows.map(\.settled) == ["", "SAR 300", "SAR 200"])
    }

    @Test("An itemised bill still prints as one row")
    func itemisedBillIsOneRow() throws {
        // A statement lists documents, not what was on them. The bill itself is
        // where somebody looks for the lines.
        let store = makeStore()
        aShop(store)
        let product = store.addProduct(name: "Cisa lock", stock: 50, cost: 60, price: 95)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 2, price: 95)],
            customer: "Ahmed",
            paid: 0,
            invoiceNo: "06011"
        )

        let document = try document(store)

        #expect(document.activityRows.count == 1)
        #expect(document.activityRows.first?.reference == "Invoice #06011")
        #expect(document.activityRows.first?.charge == "SAR 190")
    }

    @Test("A record with no number of its own is still named")
    func unnumberedRecordsAreStillNamed() throws {
        // A cell holding only a date is unreadable, and it matters more than it
        // used to: a payment and a credit note both land in the same money
        // column, so the word in this cell is the only thing telling them apart.
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, invoiceNo: "06011")
        store.recordPayment(customerKey: "ahmed", amount: 100)
        store.addCreditNote(customerKey: "ahmed", amount: 50)

        let named = try document(store).activityRows
            .map(\.reference)

        #expect(named.contains(english.paymentLabel), "\(named)")
        #expect(named.contains(english.creditNoteLabel), "\(named)")
    }

    @Test("Dates in the table are numeric so the column lines up")
    func numericDates() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            customer: "Ahmed",
            paid: 0,
            amount: 500,
            createdAt: date(2026, 5, 19),
            invoiceNo: "06011"
        )

        let statement = try #require(store.statement(forCustomer: "ahmed", period: .month(date(2026, 5, 10))))
        let document = StatementDocument.make(statement: statement, settings: store.settings, strings: english)

        #expect(document.activityRows.first?.reference == "Invoice #06011")
        #expect(document.activityRows.first?.date == "19/05/2026")
    }

    @Test("The column headings are the five the table draws")
    func headings() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 500, invoiceNo: "06011")

        #expect(try document(store).columnHeadings == [
            "Date",
            "Invoice / Receipt",
            "Invoice amount",
            "Received amount",
            "Balance"
        ])
    }

    @Test("A bill paid at the counter shows both what it charged and what it took")
    func billPaidAtTheCounterShowsBoth() throws {
        // The row that was wrong. A bill settled at the till charges and receives
        // in the same moment, so the balance beside it does not move — and with
        // only the charge printed, the page read as an arithmetic mistake. It was
        // one: the money handed over was missing from it.
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: nil, amount: 155, invoiceNo: "12")

        let row = try #require(try document(store).activityRows.first)

        #expect(row.charge == "SAR 155")
        #expect(row.settled == "SAR 155", "paid in full at the counter, and the page has to say so")
        #expect(row.balance == "SAR 0")
    }

    @Test("A bill part paid at the counter shows the part")
    func billPartPaidShowsThePart() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 100, amount: 155, invoiceNo: "12")

        let row = try #require(try document(store).activityRows.first)

        #expect(row.charge == "SAR 155")
        #expect(row.settled == "SAR 100")
        #expect(row.balance == "SAR 55", "and the balance moves by the difference")
    }

    @Test("A bill on credit leaves the received column empty")
    func billOnCreditLeavesReceivedEmpty() throws {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(customer: "Ahmed", paid: 0, amount: 155, invoiceNo: "12")

        let row = try #require(try document(store).activityRows.first)

        #expect(row.charge == "SAR 155")
        #expect(row.settled == "")
        #expect(row.balance == "SAR 155")
    }

    @Test("A supplier's statement says paid where a customer's says received")
    func supplierHeadingsPointTheOtherWay() throws {
        // The same table serves both. "Received amount" against money the shop
        // handed over is backwards, and a statement is the page the other side
        // reads most carefully.
        let store = makeStore()
        aShop(store)
        store.addSupplier(name: "Al-Riyadh Hardware")
        store.recordSupplierBill(supplierKey: "al-riyadh hardware", amount: 800, paid: 0, invoiceNo: "INV-1")

        let statement = try #require(
            store.statementForSupplier(key: "al-riyadh hardware", period: .thisYear())
        )
        let document = StatementDocument.make(statement: statement, settings: store.settings, strings: english)

        #expect(document.columnHeadings == ["Date", "Bill / Receipt", "Bill amount", "Paid amount", "Balance"])
    }

    @Test("A finished month is titled with its own last day")
    func finishedMonthTitle() throws {
        // Not the exclusive end. Saying "till 1 September" on an August statement
        // claims a day it does not include.
        let document = try august(printedOn: date(2026, 11, 2))

        #expect(document.summaryTitle.contains("31 August 2026"), "\(document.summaryTitle)")
    }

    @Test("The month running now is titled with today")
    func runningMonthTitle() throws {
        // A statement printed on the 18th and headed "till 31 August" claims a
        // fortnight that has not happened, and the customer reading it would
        // take the balance as final with a week of deliveries still to come.
        let document = try august(printedOn: date(2026, 8, 18))

        #expect(document.summaryTitle.contains("18 August 2026"), "\(document.summaryTitle)")
    }

    @Test("A period picked ahead of today is dated from its own first day")
    func futureMonthTitle() throws {
        // Rather than from a moment before it began. Nothing can be in it yet,
        // and "till 2 August" on a September statement reads as a bug.
        let document = try august(printedOn: date(2026, 6, 2))

        #expect(document.summaryTitle.contains("1 August 2026"), "\(document.summaryTitle)")
    }

    /// One August bill, and a statement for August printed on whatever day.
    private func august(printedOn: Date) throws -> StatementDocument {
        let store = makeStore()
        aShop(store)
        store.addCustomer(name: "Ahmed")
        store.saveBill(
            customer: "Ahmed",
            paid: 0,
            amount: 500,
            createdAt: date(2026, 8, 10),
            invoiceNo: "06011"
        )

        let statement = try #require(store.statement(forCustomer: "ahmed", period: .month(date(2026, 8, 10))))
        return StatementDocument.make(
            statement: statement,
            settings: store.settings,
            strings: english,
            now: printedOn
        )
    }
}
