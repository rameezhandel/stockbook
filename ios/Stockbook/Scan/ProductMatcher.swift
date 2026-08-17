import Foundation

/// Finding the product a scanned line is talking about.
///
/// Paper does not use the catalogue's spelling. `4in hinge` on a bill has to find
/// `4 inch hinge` in the app, and `cisa` has to find `Cisa lock`, or the feature
/// hands back a list of things the owner has to type anyway.
///
/// Deliberately conservative: a wrong match puts the wrong product on a bill and
/// takes it off the wrong shelf, which is worse than no match at all. Below the
/// threshold it returns nothing and the screen says so.
enum ProductMatcher {

    /// Below this, the guess is not worth making.
    static let threshold = 0.5

    static func match(_ scannedName: String, in products: [Product]) -> Product? {
        let needle = tokens(of: scannedName)
        guard !needle.isEmpty else { return nil }

        var best: (product: Product, score: Double)?
        for product in products {
            let score = score(needle, tokens(of: product.name))
            if score >= threshold, score > (best?.score ?? 0) {
                best = (product, score)
            }
        }
        return best?.product
    }

    /// How alike two token sets are, 0…1.
    ///
    /// Shared tokens over the smaller set, so `cisa` scores 1 against
    /// `cisa lock` — a shopkeeper writing the short form is the normal case, not
    /// a partial match to be penalised. A token that is a prefix of another
    /// counts too, which is what makes `hing` find `hinge`.
    static func score(_ a: Set<String>, _ b: Set<String>) -> Double {
        guard !a.isEmpty, !b.isEmpty else { return 0 }
        var shared = 0
        for token in a where b.contains(where: { $0 == token || $0.hasPrefix(token) || token.hasPrefix($0) }) {
            shared += 1
        }
        return Double(shared) / Double(min(a.count, b.count))
    }

    /// Lowercased, punctuation dropped, digits split off from letters, and the
    /// abbreviations a hardware shop actually writes expanded.
    ///
    /// `4in` becomes `4 inch` because nobody writes the long form on a carbon
    /// copy. This table is a guess until it has been tried against real bills;
    /// it is one place, and adding to it is one line.
    static func tokens(of name: String) -> Set<String> {
        var spaced = ""
        var previous: Character?
        for character in name.lowercased() {
            if let previous, previous.isNumber != character.isNumber,
               character.isLetter || previous.isLetter {
                spaced.append(" ")
            }
            spaced.append(character.isLetter || character.isNumber ? character : " ")
            previous = character
        }

        let expansions = [
            "in": "inch", "inc": "inch", "\"": "inch",
            "mm": "mm", "cm": "cm",
            "pc": "piece", "pcs": "piece", "nos": "piece",
            "hndl": "handle", "hdl": "handle",
            "lck": "lock", "pdlk": "padlock"
        ]

        return Set(
            spaced.split(separator: " ")
                .map(String.init)
                .filter { $0.count > 1 || $0.allSatisfy(\.isNumber) }
                .map { expansions[$0] ?? $0 }
        )
    }
}
