import Testing
import Foundation
@testable import Stockbook

/// Cash the shop lends a customer.
///
/// The twin of `LoanTests.kt`, test for test. Half of what is asserted here is
/// that a loan is **not** the other things it resembles — not a sale, not an
/// expense, not a payment with a sign on it. A rule of the form "these two must
/// never meet" decays silently, which is why it is written down as a test rather
/// than left in a comment.
@Suite("Loans")
@MainActor
struct LoanTests {

    private let english = Strings(language: .english)

    /// A fixed date rather than an offset from now, so the month boundaries
    /// these tests lean on cannot drift with the day the suite is run.
    private var day: Date { Date(timeIntervalSince1970: 1_789_203_600) }

    private func makeStore() -> StockbookStore {
        let store = StockbookStore(repository: InMemoryRepository())
        _ = store.addCustomer(name: "Ahmed")
        return store
    }

    // MARK: What it does to the balance

    /// The whole feature in one assertion: lending 400 to somebody owing 800
    /// leaves them owing 1,200.
    ///
    /// One debt, not two. Goods owed and cash lent land on the same balance,
    /// because there is no second balance anywhere in this app to put it in and
    /// an owner asking what Ahmed owes wants one number.
    @Test func aLoanAddsToWhatTheCustomerOwes() throws {
        let store = makeStore()
        _ = store.saveBill(customer: "Ahmed", paid: 0, amount: 800, createdAt: day)

        _ = store.recordLoan(customerKey: "ahmed", amount: 400, lentAt: day)

        #expect(store.customer(key: "ahmed")?.owed == 1_200)
    }

    /// Repayment is an ordinary payment. Nothing new was needed for it.
    @Test func anOrdinaryPaymentSettlesALoan() throws {
        let store = makeStore()
        _ = store.recordLoan(customerKey: "ahmed", amount: 400, lentAt: day)

        _ = store.recordPayment(
            customerKey: "ahmed", amount: 400, receivedAt: day.addingTimeInterval(86_400)
        )

        #expect(store.customer(key: "ahmed")?.owed == 0)
    }

    /// Somebody the owner has only ever lent money to still appears.
    ///
    /// The seeding trap: applying loans to the book without seeding it first
    /// drops a customer who has no bill and no roster entry, in silence. That is
    /// not a rare case — it is the first loan to a new face, and it is exactly
    /// how the credit notes were once stranded by a rename.
    @Test func aCustomerKnownOnlyByALoanIsStillOnTheBooks() throws {
        let store = StockbookStore(repository: InMemoryRepository())

        _ = store.recordLoan(customerKey: "khalid", amount: 250, lentAt: day)

        let khalid = try #require(store.customers().first { $0.key == "khalid" })
        #expect(khalid.owed == 250)
    }

    /// A renamed borrower keeps their debt.
    ///
    /// The failure this prevents is silent and one-directional: a loan left under
    /// the old key stops adding to what the customer owes, so the balance falls
    /// by whatever they were lent and nothing anywhere says why.
    @Test func renamingABorrowerCarriesTheirLoans() throws {
        let store = makeStore()
        _ = store.recordLoan(customerKey: "ahmed", amount: 400, lentAt: day)

        _ = store.updateCustomer(key: "ahmed", name: "Ahmed Al Faisal", phone: nil, place: nil)

        #expect(store.customer(key: "ahmed al faisal")?.owed == 400)
    }

    // MARK: What it is not

    /// Not a sale. Nothing left the shelf and the shop earned nothing.
    @Test func aLoanIsNotASaleAndEarnsNothing() throws {
        let store = makeStore()
        let period = StatementPeriod.month(day)

        _ = store.recordLoan(customerKey: "ahmed", amount: 400, lentAt: day)

        #expect(store.soldIn(period) == 0, "not takings")
        #expect(store.earningsIn(period).sold == 0, "not revenue")
        #expect(store.spentIn(period) == 0, "and not the owner's spending either")
        #expect(store.billCountIn(period) == 0, "no bill was written")
    }

    /// Not an expense. An expense is gone; this is expected back.
    @Test func lendingStandsApartFromSpending() throws {
        let store = makeStore()
        let period = StatementPeriod.month(day)
        _ = store.addExpense(amount: 60, note: "Petrol", spentAt: day)
        _ = store.recordLoan(customerKey: "ahmed", amount: 400, lentAt: day)

        #expect(store.spentIn(period) == 60)
        #expect(store.lentIn(period) == 400)
    }

    /// Neither zero nor a negative figure is a loan. A negative one is a payment.
    @Test func aLoanHasToBeMoneyAndHasToBeForSomebody() throws {
        let store = makeStore()

        #expect(store.recordLoan(customerKey: "ahmed", amount: 0, lentAt: day) == nil)
        #expect(store.recordLoan(customerKey: "ahmed", amount: -400, lentAt: day) == nil)
        #expect(store.recordLoan(customerKey: "", amount: 400, lentAt: day) == nil)
        #expect(store.loans.isEmpty)
    }

    // MARK: On the statement

    /// A loan charges the account and settles nothing — the mirror of a payment,
    /// which settles and charges nothing.
    @Test func theStatementShowsALoanAsAChargeThatSettlesNothing() throws {
        let store = makeStore()
        _ = store.saveBill(customer: "Ahmed", paid: 0, amount: 800, createdAt: day)
        _ = store.recordLoan(
            customerKey: "ahmed", amount: 400, lentAt: day.addingTimeInterval(3_600)
        )

        let statement = try #require(
            store.statement(forCustomer: "ahmed", period: .month(day))
        )
        let loan = try #require(statement.entries.first {
            if case .loan = $0 { return true } else { return false }
        })

        #expect(loan.charge == 400)
        #expect(loan.settledAtOnce == 0)
        #expect(statement.closingBalance == 1_200)
    }

    /// **Billed says what was billed.** Eight hundred of goods and four hundred
    /// lent is not "billed 1,200" — the customer never got an invoice for the
    /// cash, and would be right to query one.
    ///
    /// This is why a loan is its own `Entry.Kind` rather than another trade entry.
    @Test func aLoanIsKeptOutOfWhatWasBilled() throws {
        let store = makeStore()
        _ = store.saveBill(customer: "Ahmed", paid: 0, amount: 800, createdAt: day)
        _ = store.recordLoan(
            customerKey: "ahmed", amount: 400, lentAt: day.addingTimeInterval(3_600)
        )

        let statement = try #require(
            store.statement(forCustomer: "ahmed", period: .month(day))
        )

        #expect(statement.billed == 800, "goods only")
        #expect(statement.lent == 400, "and the cash on its own line")
        #expect(statement.received == 0)
    }

    /// No number to print, because a loan comes out of no numbered book.
    @Test func aLoanRowIsNamedRatherThanNumbered() throws {
        let store = makeStore()
        _ = store.recordLoan(customerKey: "ahmed", amount: 400, lentAt: day)
        let statement = try #require(
            store.statement(forCustomer: "ahmed", period: .month(day))
        )

        let row = try #require(statement.entries.first)
        #expect(StatementDocument.reference(row, english) == "Loan")
    }

    // MARK: On the day

    /// Money out of the cash box on the day it left it, however certain the owner
    /// is of getting it back. The day's net is what the box did.
    @Test func aLoanIsMoneyOutOnTheDayItWasHandedOver() throws {
        let store = makeStore()
        _ = store.recordPayment(customerKey: "ahmed", amount: 100, receivedAt: day)
        _ = store.recordLoan(
            customerKey: "ahmed", amount: 400, lentAt: day.addingTimeInterval(60)
        )

        let book = store.dayBook(day)

        #expect(book.entries.contains { $0.kind == .loan })
        #expect(book.moneyIn == 100)
        #expect(book.moneyOut == 400)
        #expect(book.net == -300)
    }

    // MARK: Correcting one

    @Test func aLoanCanBeCorrectedAndRemoved() throws {
        let store = makeStore()
        let loan = try #require(store.recordLoan(customerKey: "ahmed", amount: 400, lentAt: day))

        _ = store.updateLoan(id: loan.id, amount: 250, lentAt: day)
        #expect(store.customer(key: "ahmed")?.owed == 250)

        store.deleteLoan(id: loan.id)
        #expect(store.customer(key: "ahmed")?.owed == 0)
        #expect(store.loans.isEmpty)
    }

    // MARK: Across the wire

    /// A loan survives export and import, and the file says version 5.
    ///
    /// The bump is the point: a reader built before loans existed would drop them
    /// and show every borrower owing less than they do, so it must refuse the
    /// file instead. That is the credit-note rule pointing the other way.
    @Test func loansTravelInTheBackupWhichIsVersionFive() throws {
        let store = makeStore()
        _ = store.saveBill(customer: "Ahmed", paid: 0, amount: 800, createdAt: day)
        _ = store.recordLoan(
            customerKey: "ahmed", amount: 400, lentAt: day, note: "for the school fees"
        )

        let document = store.makeBackupDocument(at: day)
        #expect(document.version == 5)

        let restored = StockbookStore(repository: InMemoryRepository())
        restored.replaceEverything(with: try BackupService.decode(BackupService.encode(document)))

        let loan = try #require(restored.loans.first)
        #expect(restored.loans.count == 1)
        #expect(loan.amount == 400)
        #expect(loan.customerKey == "ahmed")
        #expect(loan.note == "for the school fees")
        #expect(
            restored.customer(key: "ahmed")?.owed == 1_200,
            "and the balance comes back with it"
        )
    }

    /// A file written before loans existed still loads.
    ///
    /// The Swift trap this exists for: a default does **not** make the
    /// synthesised decoder tolerate a missing key — it throws. `loans` is read
    /// with `decodeIfPresent` by hand in both `ShopState` and `BackupDocument`,
    /// and this is what proves it.
    ///
    /// Every other array is present, and that is not padding. `BackupDocument`'s
    /// decoder is strict on purpose about the keys that have always existed: a
    /// file with no `customers` or `purchases` is not one this app wrote, and
    /// refusing it is the right answer. Only the keys added after v1 are
    /// tolerant. The first draft of this test left them out and was rejected —
    /// correctly.
    @Test func aFileWrittenBeforeLoansExistedStillLoads() throws {
        let json = """
        {"version":4,"exportedAt":"2026-08-11T00:00:00Z","ownerName":"K",\
        "currencyCode":"SAR","products":[],"bills":[],"customers":[],\
        "payments":[],"suppliers":[],"purchases":[],"supplierPayments":[]}
        """
        let document = try BackupService.decode(Data(json.utf8))

        #expect(document.loans.isEmpty)
    }

    // MARK: Finding one

    @Test func aLoanCanBeSearchedForByTheBorrowersName() throws {
        let store = makeStore()
        _ = store.recordLoan(customerKey: "ahmed", amount: 400, lentAt: day)

        let hit = try #require(store.search("ahmed").first { $0.kind == .loan })

        #expect(hit.amount == 400)
        #expect(hit.who == "Ahmed")
        #expect(hit.reference == nil, "there is no number to show")
    }
}
