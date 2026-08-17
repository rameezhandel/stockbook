import Testing
import Foundation
@testable import Stockbook

/// The only part of bill scanning that can be checked without a camera — which
/// is exactly why every heuristic lives in `BillScanParser` as a named function
/// rather than a condition inside a view.
@Suite("Bill scanning")
struct BillScanTests {

    /// Builds a page from rows of text, the way a bill actually reads: top first.
    /// Vision's y axis runs bottom-up, so row 0 gets the *largest* midY.
    private func page(_ rows: [[String]], confidence: Double = 0.9) -> [ScannedText] {
        rows.enumerated().flatMap { rowIndex, row in
            row.enumerated().map { columnIndex, text in
                ScannedText(
                    text: text,
                    midY: 0.9 - Double(rowIndex) * 0.08,
                    minX: Double(columnIndex) * 0.2,
                    confidence: confidence
                )
            }
        }
    }

    // MARK: Rows

    @Test("A bill reads top to bottom, whatever order the camera found it in")
    func rowOrder() {
        let pieces = [
            ScannedText(text: "second", midY: 0.5, minX: 0.1),
            ScannedText(text: "first", midY: 0.8, minX: 0.1),
            ScannedText(text: "third", midY: 0.2, minX: 0.1)
        ]
        let rows = BillScanParser.rows(from: pieces)
        #expect(rows.map { $0.first?.text } == ["first", "second", "third"])
    }

    @Test("Pieces on the same line join left to right")
    func rowGrouping() {
        let pieces = [
            ScannedText(text: "95", midY: 0.802, minX: 0.7),
            ScannedText(text: "Cisa lock", midY: 0.800, minX: 0.1),
            ScannedText(text: "2", midY: 0.798, minX: 0.5)
        ]
        let rows = BillScanParser.rows(from: pieces)
        #expect(rows.count == 1, "handwriting does not sit straight; a hair of drift is one row")
        #expect(rows[0].map(\.text) == ["Cisa lock", "2", "95"])
    }

    // MARK: One row

    @Test("Quantity, rate and amount read as an invoice reads")
    func threeNumbers() {
        let line = BillScanParser.line(from: [
            ScannedText(text: "Cisa lock", midY: 0.5, minX: 0.1),
            ScannedText(text: "2", midY: 0.5, minX: 0.5),
            ScannedText(text: "95", midY: 0.5, minX: 0.7),
            ScannedText(text: "190", midY: 0.5, minX: 0.9)
        ])
        #expect(line?.name == "Cisa lock")
        #expect(line?.quantity == 2)
        #expect(line?.unitPrice == 95)
    }

    @Test("Quantity and amount give the rate by division")
    func twoNumbers() {
        let line = BillScanParser.line(from: [
            ScannedText(text: "Padlock", midY: 0.5, minX: 0.1),
            ScannedText(text: "4", midY: 0.5, minX: 0.6),
            ScannedText(text: "100", midY: 0.5, minX: 0.9)
        ])
        #expect(line?.quantity == 4)
        #expect(line?.unitPrice == 25)
    }

    @Test("A row without a figure is not an item at all")
    func noFigure() {
        // The shop's letterhead, its address, "Thank you" — all rows, none items.
        #expect(BillScanParser.line(from: [
            ScannedText(text: "Khalid Hardware Store", midY: 0.9, minX: 0.1)
        ]) == nil)
    }

    @Test("One number is a price, and the quantity is left unread")
    func oneNumber() {
        let line = BillScanParser.line(from: [
            ScannedText(text: "Deadbolt", midY: 0.5, minX: 0.1),
            ScannedText(text: "45", midY: 0.5, minX: 0.9)
        ])
        // Not assumed to be one. Assuming is how a bill for six becomes a bill
        // for one, and the owner has no reason to look twice at a filled box.
        #expect(line?.quantity == nil)
        #expect(line?.unitPrice == 45)
    }

    @Test("A number inside a product name stays part of the name")
    func numbersInNames() {
        let line = BillScanParser.line(from: [
            ScannedText(text: "4 inch hinge", midY: 0.5, minX: 0.1),
            ScannedText(text: "6", midY: 0.5, minX: 0.6),
            ScannedText(text: "120", midY: 0.5, minX: 0.9)
        ])
        #expect(line?.name == "4 inch hinge")
        #expect(line?.quantity == 6)
        #expect(line?.unitPrice == 20)
    }

    @Test("A row with no letters is not an item")
    func numbersOnly() {
        #expect(BillScanParser.line(from: [
            ScannedText(text: "12", midY: 0.5, minX: 0.1),
            ScannedText(text: "480", midY: 0.5, minX: 0.9)
        ]) == nil)
    }

    // MARK: Digits, as a camera reads handwriting

    @Test("The substitutions OCR actually makes on handwritten digits")
    func digitCorrection() {
        #expect(BillScanParser.number(from: "l0") == 10)
        #expect(BillScanParser.number(from: "9S") == 95)
        #expect(BillScanParser.number(from: "O") == 0)
        #expect(BillScanParser.number(from: "19O.5") == 190.5)
        #expect(BillScanParser.number(from: "1,240") == 1240)
    }

    @Test("A word is never bent into a number")
    func wordsAreNotNumbers() {
        // "Loose" would become 100se if the substitution were applied to any
        // token rather than only to one that is otherwise entirely numeric.
        #expect(BillScanParser.number(from: "Loose") == nil)
        #expect(BillScanParser.number(from: "lock") == nil)
        #expect(BillScanParser.number(from: "Sam") == nil)
        #expect(BillScanParser.number(from: "") == nil)
    }

    // MARK: The furniture on a bill

    @Test("Totals and headings are not items")
    func noise() {
        for row in ["Total 480", "GRAND TOTAL", "Qty", "Rate", "Date: 11/08/26", "Bill No 42", "Signature"] {
            #expect(BillScanParser.isNotAnItem(row), "\(row)")
        }
        for row in ["Cisa lock", "Total lock set", "Padlock 4"] {
            #expect(!BillScanParser.isNotAnItem(row), "“\(row)” is a product, not a heading")
        }
    }

    @Test("Only a labelled customer name is taken")
    func customer() {
        #expect(BillScanParser.customerName(in: "To: Ahmed Contracting") == "Ahmed Contracting")
        #expect(BillScanParser.customerName(in: "Customer: Sami") == "Sami")
        // A dash is as good a separator as a colon; a shopkeeper uses whichever.
        #expect(BillScanParser.customerName(in: "Customer - Sami") == "Sami")
        // The label has to open the row. Mentioned mid-sentence it is prose, and
        // taking a name out of prose is how the shop's own strapline ends up in
        // the customer box.
        #expect(BillScanParser.customerName(in: "Sold to the customer: Sami") == nil)
        #expect(BillScanParser.customerName(in: "M/S Al-Amri Trading") == "Al-Amri Trading")
        // Guessing that the top line is the customer picks up the shop's own
        // letterhead as often as not.
        #expect(BillScanParser.customerName(in: "Khalid Hardware Store") == nil)
    }

    // MARK: A whole bill

    @Test("A plausible handwritten bill comes out the far side")
    func wholeBill() {
        let bill = BillScanParser.parse(page([
            ["Khalid Hardware"],
            ["To: Ahmed Contracting"],
            ["Item", "Qty", "Rate", "Amount"],
            ["Cisa lock", "2", "95", "190"],
            ["4 inch hinge", "6", "20", "120"],
            ["Padlock", "1", "45", "45"],
            ["Total", "355"]
        ]))

        #expect(bill.customer == "Ahmed Contracting")
        // The letterhead is gone without being named as noise: it carries no
        // figure, and an item on an invoice always does.
        #expect(bill.lines.map(\.name) == ["Cisa lock", "4 inch hinge", "Padlock"])
        #expect(bill.lines.map(\.quantity) == [2, 6, 1])
        #expect(bill.lines.map(\.unitPrice) == [95, 20, 45])
    }

    @Test("An unreadable page yields nothing rather than nonsense")
    func garbage() {
        let bill = BillScanParser.parse(page([["~~~"], ["***", "###"]]))
        #expect(bill.lines.isEmpty)
        #expect(bill.customer == nil)
    }
}

@Suite("Matching a scanned name to the catalogue")
struct ProductMatcherTests {

    private let catalogue = [
        Product(name: "Cisa lock", stock: 10, cost: 60, price: 95),
        Product(name: "4 inch hinge", stock: 40, cost: 12, price: 20),
        Product(name: "Padlock", stock: 8, cost: 30, price: 45),
        Product(name: "Lever Handle Lock", stock: 5, cost: 80, price: 140)
    ]

    @Test("Paper spelling finds catalogue spelling")
    func abbreviations() {
        // Nobody writes the long form on a carbon copy.
        #expect(ProductMatcher.match("4in hinge", in: catalogue)?.name == "4 inch hinge")
        #expect(ProductMatcher.match("cisa", in: catalogue)?.name == "Cisa lock")
        #expect(ProductMatcher.match("PADLOCK", in: catalogue)?.name == "Padlock")
        #expect(ProductMatcher.match("lever handle", in: catalogue)?.name == "Lever Handle Lock")
    }

    @Test("A name it does not know matches nothing")
    func noMatch() {
        // A wrong match puts the wrong product on a bill and takes it off the
        // wrong shelf, which is worse than no match at all.
        #expect(ProductMatcher.match("cement", in: catalogue) == nil)
        #expect(ProductMatcher.match("", in: catalogue) == nil)
        #expect(ProductMatcher.match("~~~", in: catalogue) == nil)
    }

    @Test("The better of two candidates wins")
    func bestWins() {
        #expect(ProductMatcher.match("lever handle lock", in: catalogue)?.name == "Lever Handle Lock")
        #expect(ProductMatcher.match("pad lock", in: catalogue)?.name == "Padlock")
    }
}
