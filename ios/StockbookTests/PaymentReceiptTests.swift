import Testing
import Foundation
@testable import Stockbook

/// The slip handed over when somebody settles up.
///
/// The twin of `PaymentReceiptTests.kt`, test for test. The claim being pinned
/// is a narrow one and it is the whole feature: **the balance on the receipt is
/// the balance on the statement.** Not equal to it — *the same figure*, lifted
/// out of the same calculation. A customer holding a receipt saying 550 and a
/// statement saying 650 has no way to tell which of the two the shop believes,
/// and neither has the shop.
@MainActor
@Suite("Payment receipts")
struct PaymentReceiptTests {

    private func at(_ day: Int) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.date(from: DateComponents(year: 2026, month: 8, day: day, hour: 9))!
    }

    private let strings = Strings(language: .english)
    /// `Settings` declares `init()`, which suppresses the memberwise one — so
    /// the shop is named after it is made, as the repository tests do.
    private var settings: Settings {
        var settings = Settings()
        settings.ownerName = "Al Salam Hardware"
        return settings
    }

    private func shop() -> (StockbookStore, Product) {
        let store = StockbookStore(repository: InMemoryRepository())
        let lock = store.addProduct(name: "Cisa lock", stock: 500, cost: 60, price: 95)
        return (store, lock)
    }

    @Test("The balance on the receipt is the balance beside it on the statement")
    func balanceMatchesTheStatement() throws {
        let (store, lock) = shop()
        _ = store.addCustomer(name: "Ahmed")
        _ = store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 10, price: 95)],
                           customer: "Ahmed", paid: 0, createdAt: at(10))
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 300, receivedAt: at(12)))

        let receipt = try #require(store.receipt(forPayment: payment.id))

        #expect(receipt.balanceAfter == 650, "950 billed, 300 paid")
        #expect(receipt.balanceBefore == 950, "what the account stood at a minute earlier")
        #expect(receipt.amount == 300)
        #expect(receipt.party.name == "Ahmed")

        // The same figure, read out of the other document. This is the assertion
        // the feature exists to keep true.
        let statement = try #require(
            store.statement(forCustomer: "ahmed", period: .custom(from: at(1), to: at(20)))
        )
        let row = try #require(
            statement.entries.firstIndex { $0.id == "payment-\(payment.id.uuidString)" }
        )
        #expect(statement.runningBalances[row] == receipt.balanceAfter)
    }

    /// A receipt written for money that arrived before anything was billed.
    ///
    /// The balance goes negative, and it must be allowed to: this app reads a
    /// negative balance as money held in advance, and a receipt that clamped it
    /// at zero would be telling somebody who paid ahead that they are square.
    @Test("Money paid in advance leaves the account in credit")
    func paidInAdvance() throws {
        let (store, _) = shop()
        _ = store.addCustomer(name: "Fatima")
        let payment = try #require(store.recordPayment(customerKey: "fatima", amount: 200, receivedAt: at(3)))

        let receipt = try #require(store.receipt(forPayment: payment.id))

        #expect(receipt.balanceBefore == 0)
        #expect(receipt.balanceAfter == -200)
    }

    @Test("A payment that is not there has no receipt")
    func deletedPayment() throws {
        let (store, _) = shop()
        _ = store.addCustomer(name: "Ahmed")
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 50, receivedAt: at(4)))

        store.deletePayment(id: payment.id)

        #expect(store.receipt(forPayment: payment.id) == nil, "the record is gone, so the slip is too")
        #expect(store.receipt(forPayment: UUID()) == nil)
    }

    /// The range the statement is read over is stretched to hold the payment.
    ///
    /// Two ways it would otherwise miss: the shop's earliest record does not look
    /// at supplier payments at all, and nothing stops a date being picked in the
    /// future. Either would leave the payment outside its own statement and hand
    /// back nothing.
    @Test("A supplier voucher works when the payment is the only record in the shop")
    func supplierPaymentAlone() throws {
        let (store, _) = shop()
        _ = store.addSupplier(name: "Gulf Traders")
        let payment = try #require(
            store.recordSupplierPayment(supplierKey: "gulf traders", amount: 700, paidAt: at(6))
        )

        let receipt = try #require(store.receipt(forSupplierPayment: payment.id))

        #expect(receipt.party.isSupplier)
        #expect(receipt.amount == 700)
        #expect(receipt.balanceAfter == -700, "paid ahead of any delivery")
    }

    @Test("A payment dated ahead of today is still inside its own statement")
    func futureDatedPayment() throws {
        let (store, lock) = shop()
        _ = store.addCustomer(name: "Ahmed")
        _ = store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 4, price: 95)],
                           customer: "Ahmed", paid: 0, createdAt: at(10))
        let ahead = Date.now.addingTimeInterval(60 * 60 * 24 * 30)
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 100, receivedAt: ahead))

        let receipt = try #require(store.receipt(forPayment: payment.id))

        #expect(receipt.balanceAfter == 280)
    }

    // MARK: - The document

    @Test("The customer's slip is a receipt and reads from their end")
    func customerWording() throws {
        let (store, lock) = shop()
        _ = store.addCustomer(name: "Ahmed")
        _ = store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 10, price: 95)],
                           customer: "Ahmed", paid: 0, createdAt: at(10))
        let payment = try #require(
            store.recordPayment(
                customerKey: "ahmed", amount: 300, receivedAt: at(12),
                note: "cheque 4471", paymentNo: "008455"
            )
        )

        let document = PaymentReceiptDocument.make(
            receipt: try #require(store.receipt(forPayment: payment.id)),
            settings: settings,
            strings: strings
        )

        #expect(document.docType == "Payment Receipt")
        #expect(document.addressedToLabel == "Received from:")
        #expect(document.amountLabel == "Amount received")
        #expect(document.receiptValue == "008455")
        #expect(document.shopName == "Al Salam Hardware")
        #expect(document.noteValue == "cheque 4471")
        #expect(document.noteLabel == strings.paymentNote)
    }

    @Test("The supplier's slip is a voucher and reads from the other end")
    func supplierWording() throws {
        let (store, _) = shop()
        _ = store.addSupplier(name: "Gulf Traders")
        let payment = try #require(
            store.recordSupplierPayment(supplierKey: "gulf traders", amount: 700, paidAt: at(6))
        )

        let document = PaymentReceiptDocument.make(
            receipt: try #require(store.receipt(forSupplierPayment: payment.id)),
            settings: settings,
            strings: strings
        )

        #expect(document.docType == "Payment Voucher")
        #expect(document.addressedToLabel == "Paid to:")
        #expect(document.amountLabel == "Amount paid")
        #expect(document.dateLabel == "Paid on")
    }

    /// Three figures, whatever happened. The statement leaves out what did not
    /// happen; a receipt cannot, because it is read on its own — a slip missing
    /// the previous balance is one somebody has to fetch a statement to
    /// understand.
    @Test("The summary is always previous balance, this receipt, and what is left")
    func summaryIsAlwaysWhole() throws {
        let (store, lock) = shop()
        _ = store.addCustomer(name: "Ahmed")
        _ = store.saveBill(lines: [DraftLine(productUID: lock.uid, qty: 10, price: 95)],
                           customer: "Ahmed", paid: 0, createdAt: at(10))
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 300, receivedAt: at(12)))

        let document = PaymentReceiptDocument.make(
            receipt: try #require(store.receipt(forPayment: payment.id)),
            settings: settings,
            strings: strings
        )

        #expect(document.summaryRows.count == 2)
        #expect(document.summaryRows[0].label == "Previous balance")
        #expect(document.summaryRows[0].value == "SAR 950")
        #expect(document.summaryRows[1].label == "Amount received")
        #expect(document.summaryRows[1].deduction, "the one line that comes off")
        #expect(document.closingLabel == "Balance now")
        #expect(document.closingValue == "SAR 650")
    }

    /// A payment entered before the receipt field existed still prints a
    /// document. An empty box on a numbered slip reads as a printing fault.
    @Test("A payment with no number says so rather than leaving a gap")
    func noNumber() throws {
        let (store, _) = shop()
        _ = store.addCustomer(name: "Ahmed")
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 50, receivedAt: at(4)))

        let document = PaymentReceiptDocument.make(
            receipt: try #require(store.receipt(forPayment: payment.id)),
            settings: settings,
            strings: strings
        )

        #expect(document.receiptValue == "—")
        #expect(document.noteLabel == nil, "no note, so no label for one")
        #expect(document.noteValue == nil)
    }

    @Test("The shop's address is printed line by line, blanks dropped")
    func addressLines() throws {
        let (store, _) = shop()
        _ = store.addCustomer(name: "Ahmed")
        let payment = try #require(store.recordPayment(customerKey: "ahmed", amount: 50, receivedAt: at(4)))

        var withAddress = settings
        withAddress.shopAddress = "King Fahd Road\n\nAl Khobar"

        let document = PaymentReceiptDocument.make(
            receipt: try #require(store.receipt(forPayment: payment.id)),
            settings: withAddress,
            strings: strings
        )

        #expect(document.shopAddressLines == ["King Fahd Road", "Al Khobar"])
    }
}
