import UIKit

/// The letterhead every printed page starts with: the shop's name and address
/// reversed out of a violet band, and on the right what the page is and which
/// days it covers.
///
/// **One drawer rather than one per writer.** The statement had this and nothing
/// else did, so eight of the app's pages printed with no letterhead at all — a
/// sheet on a desk that did not say whose shop it came from. Copying the band
/// into three more writers would have been three more sets of constants to drift
/// apart, which is already how this app ended up with two greys for one hairline.
///
/// **The ledger book does not use it, on purpose.** That is a hundred pages
/// printed at once and filed; `StatementPDF` draws it with a rule where the band
/// would be. Everything else in the app is a sheet or three, where a band costs
/// nothing worth counting.
enum PageBand {

    /// Full bleed, top of the page. The same 74pt the statement has always used.
    static let height: CGFloat = 74

    /// Where a page's own content starts, band included.
    static let contentTop: CGFloat = height + 24

    private static let fill = UIColor(red: 0.361, green: 0.310, blue: 0.769, alpha: 1)

    private static func reversed(_ size: CGFloat, bold: Bool = false, alpha: CGFloat = 1)
        -> [NSAttributedString.Key: Any] {
        [
            .font: bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size),
            .foregroundColor: UIColor.white.withAlphaComponent(alpha),
        ]
    }

    /// Draws the band across the top of the current page.
    ///
    /// - Parameters:
    ///   - docType: what the page is, set small and uppercased — it labels the
    ///     figure beside it rather than competing with the shop's name.
    ///   - dateLine: the day or the span. A page without it is a page somebody
    ///     files and later mistakes for this morning's.
    static func draw(
        pageWidth: CGFloat,
        margin: CGFloat,
        shopName: String,
        addressLines: [String],
        docType: String,
        dateLine: String
    ) {
        fill.setFill()
        UIRectFill(CGRect(x: 0, y: 0, width: pageWidth, height: height))

        // `draw(at:)` takes the top-left corner where Android's `drawText` takes
        // a baseline, so every y here sits about a line's ascent above the
        // Kotlin twin's number for the same line.
        shopName.draw(at: CGPoint(x: margin, y: 17), withAttributes: reversed(15, bold: true))

        // Held back from white so the name above stays the first thing read.
        // Two or three lines fit; a longer address runs under the band and is
        // clipped by it, which is the honest failure — the name and the page's
        // own title are what the sheet has to establish.
        let address = reversed(7.5, alpha: 0.78)
        for (index, line) in addressLines.prefix(3).enumerated() {
            line.draw(at: CGPoint(x: margin, y: 37 + CGFloat(index) * 10), withAttributes: address)
        }

        let right = pageWidth - margin
        drawRight(docType.uppercased(), rightEdge: right, y: 18, attributes: reversed(8, alpha: 0.82))
        drawRight(dateLine, rightEdge: right, y: 29, attributes: reversed(11))
    }

    private static func drawRight(
        _ text: String,
        rightEdge: CGFloat,
        y: CGFloat,
        attributes: [NSAttributedString.Key: Any]
    ) {
        let width = (text as NSString).size(withAttributes: attributes).width
        text.draw(at: CGPoint(x: rightEdge - width, y: y), withAttributes: attributes)
    }
}
