import Foundation

/// One piece of text the camera read, with only the geometry the parser needs.
///
/// Deliberately not a `VNRecognizedTextObservation`: the parser is the part of
/// this feature that can be proven correct, and it should be testable from a
/// literal list of strings without a camera, an image, or Vision.
struct ScannedText: Equatable {
    let text: String
    /// Vertical centre, normalised 0…1. Rows are found by clustering on this.
    let midY: Double
    /// Left edge, normalised 0…1. Orders the pieces within a row.
    let minX: Double
    /// Vision's own confidence, 0…1.
    let confidence: Double

    init(text: String, midY: Double, minX: Double, confidence: Double = 1) {
        self.text = text
        self.midY = midY
        self.minX = minX
        self.confidence = confidence
    }
}

/// One line the parser thinks it found on the paper.
///
/// Every field is optional except the name, because a handwritten bill read by a
/// camera is a pile of guesses and the screen has to be able to say "I could not
/// read this one" rather than inventing a number.
struct ScannedLine: Equatable {
    var name: String
    var quantity: Int?
    var unitPrice: Double?
    /// The lowest confidence of the pieces this line was built from.
    var confidence: Double
}

struct ScannedBill: Equatable {
    var customer: String?
    var lines: [ScannedLine]

    var isEmpty: Bool { customer == nil && lines.isEmpty }
}

/// Turning what the camera read into something the cart screen can show.
///
/// This exists because the alternative — pushing raw OCR text at the owner — is
/// not a feature. It is also the only part of bill scanning that is testable
/// without a camera, which is why every heuristic in here is a named function
/// with a test rather than a condition buried in a view.
///
/// **It never invents a number.** A field it cannot read confidently is left
/// `nil` so the screen leaves the box empty. A blank box costs a tap; a wrong
/// price costs a wrong bill, and the owner may not notice until the customer
/// does.
enum BillScanParser {

    /// Two pieces belong to the same row when their vertical centres are within
    /// this fraction of the page. Handwriting does not sit on straight lines, so
    /// this is deliberately looser than print would need.
    static let rowTolerance = 0.018

    static func parse(_ pieces: [ScannedText]) -> ScannedBill {
        let rows = rows(from: pieces)
        var lines: [ScannedLine] = []
        var customer: String?

        for row in rows {
            let joined = row.map(\.text).joined(separator: " ").trimmed
            guard !joined.isEmpty else { continue }

            if customer == nil, let found = customerName(in: joined) {
                customer = found
                continue
            }
            if isNotAnItem(joined) { continue }
            if let line = line(from: row) { lines.append(line) }
        }

        return ScannedBill(customer: customer, lines: lines)
    }

    // MARK: Rows

    /// Clusters the pieces into rows, top to bottom, each ordered left to right.
    ///
    /// Vision's y axis runs bottom-up, so a bill reads from the largest `midY`
    /// down. Getting that backwards silently reverses every bill, which is the
    /// sort of thing that looks like bad OCR rather than a bug.
    static func rows(from pieces: [ScannedText]) -> [[ScannedText]] {
        let sorted = pieces.sorted { $0.midY > $1.midY }
        var rows: [[ScannedText]] = []

        for piece in sorted {
            if let last = rows.last, let anchor = last.first,
               abs(anchor.midY - piece.midY) <= rowTolerance {
                rows[rows.count - 1].append(piece)
            } else {
                rows.append([piece])
            }
        }

        return rows.map { $0.sorted { $0.minX < $1.minX } }
    }

    // MARK: One row

    /// `Cisa lock  2  95  190` → name, quantity, unit price.
    ///
    /// The three-number case is the common invoice layout: quantity, rate,
    /// amount. Two numbers is quantity and amount, and the rate is division.
    /// One number is an amount with the quantity left unread rather than assumed
    /// to be one — assuming is how a bill for six becomes a bill for one.
    static func line(from row: [ScannedText]) -> ScannedLine? {
        let words = row.flatMap { piece in
            piece.text.split(separator: " ").map { (token: String($0), confidence: piece.confidence) }
        }
        guard !words.isEmpty else { return nil }

        // Numbers are only numbers at the end of a row. A quantity written in
        // the middle of a product name ("4 inch hinge") is part of the name.
        var trailing: [Double] = []
        var index = words.count - 1
        while index >= 0, let value = number(from: words[index].token) {
            trailing.insert(value, at: 0)
            index -= 1
        }

        // A row with no figure on it is not an item. That one rule throws out
        // the shop's own letterhead, its address, and "Thank you" without any of
        // them needing to be listed as noise — an item on an invoice always has
        // at least a price beside it.
        guard index >= 0, !trailing.isEmpty else { return nil }

        let name = words[0...index].map(\.token).joined(separator: " ").trimmed
        guard !name.isEmpty, name.rangeOfCharacter(from: .letters) != nil else { return nil }

        var quantity: Int?
        var unitPrice: Double?

        switch trailing.count {
        case 1:
            unitPrice = trailing[0]
        case 2:
            quantity = wholeNumber(trailing[0])
            if let quantity, quantity > 0 { unitPrice = trailing[1] / Double(quantity) }
        default:
            // Take the last three and read them as qty, rate, amount. Anything
            // earlier is a serial number or a date the row picked up.
            let tail = Array(trailing.suffix(3))
            quantity = wholeNumber(tail[0])
            unitPrice = tail[1]
        }

        return ScannedLine(
            name: name,
            quantity: quantity,
            unitPrice: unitPrice.map { (($0 * 100).rounded() / 100) },
            confidence: words.map(\.confidence).min() ?? 0
        )
    }

    // MARK: Pieces of a row

    /// A number, allowing for what a camera does to handwriting.
    ///
    /// `O` for zero, `l` and `I` for one, `S` for five: these are the substitutions
    /// OCR actually makes on handwritten digits, and only applied to a token that
    /// is otherwise entirely numeric — so a product called "Loose" is never read
    /// as 100se.
    static func number(from token: String) -> Double? {
        let stripped = token.trimmingCharacters(in: CharacterSet(charactersIn: ".,:;-*x×"))
        guard !stripped.isEmpty else { return nil }

        let corrected = String(stripped.map { character -> Character in
            switch character {
            case "O", "o": "0"
            case "l", "I", "|": "1"
            case "S": "5"
            default: character
            }
        })
        guard corrected.allSatisfy({ $0.isNumber || $0 == "." }) else { return nil }
        guard corrected.contains(where: \.isNumber) else { return nil }

        return Double(corrected.replacingOccurrences(of: ",", with: ""))
    }

    private static func wholeNumber(_ value: Double) -> Int? {
        guard value > 0, value < 10_000, value == value.rounded() else { return nil }
        return Int(value)
    }

    /// `To: Ahmed Contracting` → `Ahmed Contracting`.
    ///
    /// Only a labelled name is taken. Guessing that the topmost line of a bill is
    /// a customer picks up the shop's own letterhead as often as not.
    static func customerName(in row: String) -> String? {
        let labels = ["customer:", "customer", "name:", "to:", "m/s", "bill to:", "billed to:"]
        let lowered = row.lowercased()
        for label in labels where lowered.hasPrefix(label) {
            let name = String(row.dropFirst(label.count)).trimmed
                .trimmingCharacters(in: CharacterSet(charactersIn: ":-. "))
            if name.count >= 2, name.rangeOfCharacter(from: .letters) != nil { return name }
        }
        return nil
    }

    /// Headings, totals and the shop's own furniture. Reading "Grand Total 480"
    /// as an item called "Grand Total" is the most obvious way this feature could
    /// embarrass itself.
    static func isNotAnItem(_ row: String) -> Bool {
        let noise = [
            "total", "subtotal", "sub total", "grand total", "balance", "amount",
            "qty", "quantity", "rate", "price", "item", "description", "sr", "s.no",
            "date", "bill no", "invoice", "gst", "vat", "tax", "signature",
            "thank you", "paid", "cash", "discount"
        ]
        let lowered = row.lowercased().trimmed
        return noise.contains { lowered == $0 || lowered.hasPrefix($0 + " ") || lowered.hasPrefix($0 + ":") }
    }
}
