import Testing
import Foundation
@testable import Stockbook

/// What a stretch of trading left the shop with.
///
/// The twin of `EarningsTests.kt` and `EarningsDocumentTests.kt`, folded into one
/// suite. Two things here are worth more than the arithmetic. **The discount
/// needs no apportioning** — `Bill.total` is stored after it, so a bill's takings
/// less its lines' cost is exactly what that bill earned. And **a bill that
/// cannot answer is set aside whole**, never half-counted: subtracting part of a
/// bill's cost from all of its takings would flatter the figure by the
/// difference, which is the one direction a page like this must never be wrong
/// in.
@MainActor
@Suite("Earnings")
struct EarningsTests {

    private let english = Strings(language: .english)

    private func makeStore() -> StockbookStore {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setOwnerName("Al Salam Hardware")
        return store
    }

    private func period() -> StatementPeriod { .thisMonth() }

    /// Every bill **and credit note** in the book as an older file would have
    /// restored it: itemised, but with no cost on any line.
    ///
    /// The notes matter as much as the bills. A return is costed the same way,
    /// so a helper that stripped only one of the two would leave every
    /// old-book test quietly half-modern.
    private func stripCosts(_ store: StockbookStore) {
        var document = store.makeBackupDocument()
        document.bills = document.bills.map { bill in
            var bill = bill
            bill.lines = bill.lines.map { line in
                var line = line
                line.cost = nil
                return line
            }
            return bill
        }
        document.creditNotes = document.creditNotes.map { note in
            var note = note
            note.lines = note.lines.map { line in
                var line = line
                line.cost = nil
                return line
            }
            return note
        }
        store.replaceEverything(with: document)
    }

    private func page(_ store: StockbookStore) -> EarningsDocument {
        EarningsDocument.make(
            earnings: store.earningsIn(period()),
            range: period().range(),
            settings: store.settings,
            strings: english
        )
    }

    @Test("What the goods earned is what they sold for less what they cost")
    func earned() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        let handle = store.addProduct(name: "Door handle", stock: 100, cost: 35, price: 50)
        store.saveBill(
            lines: [
                .init(productUID: padlock.uid, qty: 3, price: 30),
                .init(productUID: handle.uid, qty: 2, price: 50)
            ],
            customer: "Ahmed",
            paid: nil
        )

        let earnings = store.earningsIn(period())

        #expect(earnings.sold == 190)
        #expect(earnings.costOfGoods == 130)
        #expect(earnings.goodsEarned == 60)
    }

    @Test("A discount comes off the earnings without being apportioned")
    func discountNeedsNoShareOut() {
        // `Bill.total` is already stored net of the discount, so takings less
        // cost is exact. Apportioning across lines would reach the same answer by
        // a longer route and pick up a rounding error on the way.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(
            lines: [.init(productUID: padlock.uid, qty: 3, price: 30)],
            customer: "Ahmed",
            paid: nil,
            discountPercent: 10
        )

        let earnings = store.earningsIn(period())

        #expect(earnings.sold == 81)
        #expect(earnings.costOfGoods == 60)
        #expect(earnings.goodsEarned == 21)
    }

    @Test("A bill entered as a figure is set aside, and the page says how much")
    func figureOnlyBillIsDisclosed() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)
        store.saveBill(customer: "Khalid", paid: nil, amount: 500)

        let earnings = store.earningsIn(period())

        #expect(earnings.sold == 590)
        #expect(earnings.soldWithoutCost == 500)
        #expect(earnings.billsWithoutCost == 1)
        #expect(earnings.counted == 90)
        #expect(earnings.goodsEarned == 30)
        #expect(earnings.hasGap)
    }

    @Test("What the shop kept is what the goods earned less what it spent")
    func kept() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.addExpense(amount: 45, note: "Petrol")

        let earnings = store.earningsIn(period())

        #expect(earnings.goodsEarned == 100)
        #expect(earnings.expenses == 45)
        #expect(earnings.kept == 55)
    }

    @Test("Stock bought and not sold is not a cost yet")
    func onlyWhatWasSold() throws {
        // A hundred padlocks in and three out is not a loss. This is why the
        // figure is the cost of what was *sold*, and why `boughtIn` has no part
        // in it.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        let supplier = try #require(store.addSupplier(name: "Gulf Locks"))
        let stocked = try #require(store.product(uid: padlock.uid))
        store.recordPurchase(
            product: stocked,
            supplierKey: supplier.key,
            quantity: 100,
            unitCost: 20
        )
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)

        #expect(store.boughtIn(period()) == 2000)
        #expect(store.earningsIn(period()).costOfGoods == 60)
        #expect(store.earningsIn(period()).goodsEarned == 30)
    }

    @Test("A price rise after the sale does not move what the month earned")
    func historyDoesNotMove() throws {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)

        let before = store.earningsIn(period()).goodsEarned
        let repriced = try #require(store.product(uid: padlock.uid))
        store.update(repriced, name: "Padlock 40mm", cost: 26, price: 30)

        #expect(store.earningsIn(period()).goodsEarned == before)
        #expect(before == 30)
    }

    @Test("A credit note written as a figure comes off in full")
    func figureOnlyCreditNote() {
        // Nothing was handed back, so nothing goes back on the shelf and the
        // whole credit is a real loss. Not an approximation — it is why a
        // figure-only note needs no special case and nothing disclosed.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: 0)
        store.addCreditNote(customerKey: Customer.key(for: "Ahmed"), amount: 30)

        let earnings = store.earningsIn(period())

        // Sold still counts bills and not notes, so it cannot disagree with Home.
        #expect(earnings.sold == 90)
        #expect(earnings.goodsReturned == 0)
        #expect(earnings.costOfGoods == 60)
        #expect(earnings.goodsEarned == 30, "90 sold less 60 cost")
        #expect(earnings.credited == 30)
        #expect(earnings.creditNotes == 1)
        // 30 earned on the goods, 30 given straight back.
        #expect(earnings.kept == 0)
        // Nothing to confess: it is taken off the page rather than listed beside.
        #expect(!earnings.hasGap)
        #expect(earnings.creditNotesBeforeCosts == 0)
    }

    @Test("A credit note with goods on it puts their cost back")
    func itemisedCreditNote() {
        // The pair of the test above, and the reason the two cannot share one
        // rule blindly: 30 credited on goods that cost the shop 20 leaves it 10
        // worse off, not 30. Those pieces are on the shelf again.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: 0)
        store.addCreditNote(
            customerKey: Customer.key(for: "Ahmed"),
            lines: [.init(productUID: padlock.uid, qty: 1, price: 30)]
        )

        let earnings = store.earningsIn(period())

        #expect(earnings.sold == 90, "a return does not unsell the bill")
        #expect(earnings.costOfGoods == 60)
        #expect(earnings.goodsReturned == 20, "one padlock back, at what it cost")
        #expect(earnings.netCostOfGoods == 40)
        #expect(earnings.goodsEarned == 50)
        #expect(earnings.credited == 30)
        // 50 earned, 30 credited: 20 kept, which is the 30 profit on the two
        // still sold less the 10 the returned one was marked up by.
        #expect(earnings.kept == 20)
        #expect(!earnings.hasGap)
    }

    @Test("A return whose product is gone is put back at nothing and said to be")
    func returnBeyondValuing() {
        // The one case that leaves the figure low. Better low than high, and
        // better said than either.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: 0)
        store.addCreditNote(
            customerKey: Customer.key(for: "Ahmed"),
            lines: [.init(productUID: padlock.uid, qty: 1, price: 30)]
        )
        stripCosts(store)
        store.delete(store.product(uid: padlock.uid)!)

        let earnings = store.earningsIn(period())

        #expect(earnings.goodsReturned == 0)
        #expect(earnings.creditNotesBeforeCosts == 1)
        #expect(earnings.hasGap)
    }

    @Test("An old return is valued at today's price, and counted as an estimate")
    func returnEstimated() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: 0)
        store.addCreditNote(
            customerKey: Customer.key(for: "Ahmed"),
            lines: [.init(productUID: padlock.uid, qty: 1, price: 30)]
        )
        stripCosts(store)

        let earnings = store.earningsIn(period())

        #expect(earnings.goodsReturned == 20, "the shelf still knows")
        #expect(earnings.creditNotesEstimated == 1)
        #expect(earnings.creditNotesBeforeCosts == 0)
        #expect(earnings.hasEstimates)
    }

    @Test("Sold matches what Home shows, so the two screens cannot disagree")
    func soldAgreesWithHome() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 3, price: 30)], customer: "Ahmed", paid: nil)
        store.saveBill(customer: "Khalid", paid: nil, amount: 500)

        #expect(store.earningsIn(period()).sold == store.soldIn(period()))
    }

    // MARK: The page

    @Test("A shop that itemises everything reads sold, cost, earned, spent, kept")
    func shortChain() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.addExpense(amount: 45, note: "Petrol")

        let document = page(store)

        #expect(document.lines.map(\.label) == [
            "Sold", "Cost of goods", "What the goods earned", "Expenses", "What the shop kept"
        ])
        #expect(document.lines.map(\.value) == ["SAR 300", "SAR 200", "SAR 100", "SAR 45", "SAR 55"])
        #expect(!document.hasGap)
        #expect(document.gapNote == nil)
    }

    @Test("A credit note is a line in the chain, not a footnote beside it")
    func creditedIsALine() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: 0)
        store.addCreditNote(customerKey: Customer.key(for: "Ahmed"), amount: 60)

        let document = page(store)

        #expect(document.lines.map(\.label) == [
            "Sold", "Cost of goods", "What the goods earned", "Credited", "Expenses", "What the shop kept"
        ])
        // Nothing came back, so the whole 60 comes off: 100 earned, 40 kept.
        #expect(document.lines.map(\.value) == ["SAR 300", "SAR 200", "SAR 100", "SAR 60", "SAR 0", "SAR 40"])
        // The page did the arithmetic, so it has nothing left to confess.
        #expect(!document.hasGap)
        #expect(document.gapNote == nil)
    }

    @Test("A returned padlock takes its cost off the cost of goods")
    func returnedGoodsNetOff() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: 0)
        store.addCreditNote(
            customerKey: Customer.key(for: "Ahmed"),
            lines: [.init(productUID: padlock.uid, qty: 2, price: 30)]
        )

        let document = page(store)

        // 200 of goods went out, 40 came back: the shop parted with 160.
        #expect(document.lines.first { $0.label == "Cost of goods" }?.value == "SAR 160")
        #expect(document.lines.first { $0.label == "What the goods earned" }?.value == "SAR 140")
        #expect(document.lines.first { $0.label == "Credited" }?.value == "SAR 60")
        // 140 earned less 60 credited: 80, which is the 8 padlocks still sold at
        // 10 each. The line the whole change exists to make right.
        #expect(document.lines.last?.value == "SAR 80")
    }

    @Test("A page with no credit note does not draw the line")
    func noCreditedLineWhenNoNote() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)

        #expect(!page(store).lines.contains { $0.label == "Credited" })
    }

    @Test("A return that could not be valued is confessed and explains which way it is wrong")
    func unvaluedReturnIsConfessed() throws {
        // The bill keeps its recorded cost; only the note is beyond valuing, and
        // only its product is gone. Written this way round on purpose — strand
        // the bill as well and the page has a bigger thing to say first, which
        // is right but is not what this test is about.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        let hinge = store.addProduct(name: "Hinge 4in", stock: 100, cost: 5, price: 8)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: 0)
        store.addCreditNote(
            customerKey: Customer.key(for: "Ahmed"),
            lines: [.init(productUID: hinge.uid, qty: 2, price: 8)]
        )
        var document = store.makeBackupDocument()
        document.creditNotes = document.creditNotes.map { note in
            var note = note
            note.lines = note.lines.map { line in
                var line = line
                line.cost = nil
                return line
            }
            return note
        }
        store.replaceEverything(with: document)
        let gone = try #require(store.product(uid: hinge.uid))
        store.delete(gone)

        let rendered = page(store)

        #expect(rendered.gap.map(\.label) == ["1 credit note whose returned goods could not be valued"])
        #expect(rendered.gapNote == Loc.returnsNotValued)
        // Nothing put back, so the 16 credited comes off whole: 300 sold, 200
        // cost, 100 earned, 84 kept.
        #expect(rendered.lines.last?.value == "SAR 84")
    }

    @Test("Takings the page cannot answer for are taken off in front of the reader")
    func longChain() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.saveBill(customer: "Khalid", paid: nil, amount: 500)

        let document = page(store)

        #expect(document.lines.map(\.label) == [
            "Sold", "Not counted", "Counted", "Cost of goods",
            "What the goods earned", "Expenses", "What the shop kept"
        ])
        #expect(document.gap.map(\.label) == ["1 bill entered as a total"])
        #expect(document.gap.map(\.value) == ["SAR 500"])
    }

    @Test("A month that lost money says so with a sign")
    func negativeMonth() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 2, price: 30)], customer: "Ahmed", paid: nil)
        store.addExpense(amount: 400, note: "Rent")

        #expect(page(store).lines.last?.value == "-SAR 380")
    }

    @Test("The page is a summary and never a statement")
    func neverAStatement() {
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 1, price: 30)], customer: "Ahmed", paid: nil)

        let document = page(store)

        #expect(document.shopName == "Al Salam Hardware")
        #expect(document.title == "Earnings Summary")
        #expect(!document.title.lowercased().contains("statement"))
    }

    @Test("A book written before costs existed is costed at today's prices")
    func estimatedFromTheShelf() {
        // Found by the owner on real data the day this shipped. Every bill
        // predates the cost field, so nothing could be costed — and the page ran
        // the chain anyway: earnings of zero, then the month's expenses
        // subtracted from it, landing on "kept -1,150" as though the shop had
        // lost its rent. The shelf still knows what a padlock costs, so the page
        // answers with that and names the guess rather than refusing.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.addExpense(amount: 45, note: "Rent")
        stripCosts(store)

        let earnings = store.earningsIn(period())
        #expect(!earnings.nothingCostable)
        #expect(earnings.counted == 300)
        #expect(earnings.costOfGoods == 200)
        #expect(earnings.goodsEarned == 100)
        #expect(earnings.kept == 55)
        // Counted, and counted out loud.
        #expect(earnings.hasEstimates)
        #expect(earnings.billsEstimated == 1)
        #expect(earnings.soldEstimated == 300)

        let document = page(store)
        #expect(document.lines.map(\.label) ==
            ["Sold", "Cost of goods", "What the goods earned", "Expenses", "What the shop kept"])
        #expect(document.gap.map(\.label) == ["1 bill costed at today's prices"])
        #expect(document.gapNote == Loc.costsEstimated)
    }

    @Test("Nothing is written back, so the estimate follows the shelf")
    func estimateIsNotStored() {
        // The bill's own cost stays absent, because absent is the truth about
        // it. The consequence is deliberate: reprice the product and the
        // estimate moves, which is exactly what an estimate should do and
        // exactly what a recorded cost must never do.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        stripCosts(store)

        #expect(store.earningsIn(period()).goodsEarned == 100)
        store.update(store.product(uid: padlock.uid)!, name: "Padlock 40mm", cost: 25, price: 30)

        #expect(store.earningsIn(period()).goodsEarned == 50)
        // And the line itself still knows nothing, which is what keeps a real
        // recorded cost from ever being overwritten by a guess.
        #expect(store.bills.first?.lines.first?.cost == nil)
    }

    @Test("A recorded cost always wins over today's price")
    func recordedCostWins() {
        // The whole point of the field. An estimate is only ever a fallback, and
        // a bill that carries its own figure must be immune to the shelf.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.update(store.product(uid: padlock.uid)!, name: "Padlock 40mm", cost: 25, price: 30)

        let earnings = store.earningsIn(period())
        #expect(earnings.costOfGoods == 200)
        #expect(!earnings.hasEstimates)
    }

    @Test("A book that cannot even be estimated reports an absence, not a loss")
    func nothingCostable() {
        // The shelf is the last source of a figure for an old bill, so a product
        // deleted since takes it away for good. That bill is set aside whole and
        // the chain stops — an absence is not a loss.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Ahmed", paid: nil)
        store.addExpense(amount: 1150, note: "Rent")
        stripCosts(store)
        store.delete(store.product(uid: padlock.uid)!)

        let earnings = store.earningsIn(period())
        #expect(earnings.nothingCostable)
        #expect(earnings.counted == 0)
        #expect(earnings.billsBeforeCosts == 1)
        #expect(earnings.billsEstimated == 0)

        // What was sold, what could not be costed, and then it stops.
        let document = page(store)
        #expect(document.lines.map(\.label) == ["Sold", "Not counted", "Counted"])
        #expect(document.gap.map(\.label) == ["1 bill written before costs were recorded"])
        #expect(document.gapNote == Loc.nothingCostableYet)
    }

    @Test("The three reasons a bill misses its cost are named apart")
    func threeReasons() {
        // One asks the owner to itemise the next bill; one asks nothing of them
        // at all; and one is answered, but on a price that may have moved since.
        // Telling somebody to itemise a bill they already itemised is how an app
        // earns a reputation.
        let store = makeStore()
        let padlock = store.addProduct(name: "Padlock 40mm", stock: 100, cost: 20, price: 30)
        let hinge = store.addProduct(name: "Hinge 4in", stock: 100, cost: 5, price: 8)
        store.saveBill(lines: [.init(productUID: hinge.uid, qty: 10, price: 8)], customer: "Ahmed", paid: nil)
        store.saveBill(lines: [.init(productUID: padlock.uid, qty: 10, price: 30)], customer: "Saeed", paid: nil)
        stripCosts(store)
        store.delete(store.product(uid: hinge.uid)!)
        store.saveBill(customer: "Khalid", paid: nil, amount: 500)

        let document = page(store)

        #expect(document.gap.map(\.label) == [
            "1 bill entered as a total",
            "1 bill written before costs were recorded",
            "1 bill costed at today's prices"
        ])
        #expect(document.gap.map(\.value) == ["SAR 500", "SAR 80", "SAR 300"])
        // One bill can be costed, so the chain runs its full length — and the
        // caveat that reaches the reader is the one about the figure it printed.
        #expect(document.lines.last?.label == "What the shop kept")
        #expect(document.gapNote == Loc.costsEstimated)
    }

    @Test("A quiet period states that and draws no chain of zeroes")
    func quietPeriod() {
        let document = page(makeStore())

        #expect(document.isEmpty)
        #expect(!document.hasGap)
        #expect(document.emptyLine == "Nothing sold in this period.")
    }
}
