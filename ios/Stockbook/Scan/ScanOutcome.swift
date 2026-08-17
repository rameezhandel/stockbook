import Foundation

/// What a scan turned into, once the catalogue has had a look at it.
///
/// Split into what can go on the bill and what cannot, because those two need
/// completely different things from the owner: one needs checking, the other
/// needs a decision.
struct ScanOutcome {
    /// Lines matched to a product, ready to become cart lines.
    var matched: [Matched] = []
    /// Lines the catalogue did not recognise. Not silently dropped — the owner
    /// wrote them on the paper and needs to see that the app could not place them.
    var unmatched: [ScannedLine] = []
    /// The customer, if the bill said so in a way worth trusting.
    var customer: String?

    struct Matched {
        let product: Product
        let line: ScannedLine
        /// The quantity to use — what was read, or one when it could not be.
        var quantity: Int { max(1, line.quantity ?? 1) }
        /// The price to use. Falls back to the product's own, so a line whose
        /// figure was unreadable still prices itself correctly rather than at zero.
        func price(fallback: Double) -> Double {
            guard let read = line.unitPrice, read > 0 else { return fallback }
            return read
        }
    }

    var isEmpty: Bool { matched.isEmpty && unmatched.isEmpty }

    /// Runs a scanned page against the catalogue.
    static func from(_ bill: ScannedBill, products: [Product]) -> ScanOutcome {
        var outcome = ScanOutcome(customer: bill.customer)
        for line in bill.lines {
            if let product = ProductMatcher.match(line.name, in: products) {
                outcome.matched.append(Matched(product: product, line: line))
            } else {
                outcome.unmatched.append(line)
            }
        }
        return outcome
    }
}
