import UIKit

/// Draws an `SummaryDocument` onto A4 pages.
///
/// `StatementPDF`'s smaller cousin, and deliberately the same geometry — A4 at
/// 72dpi, the same margin, the same rules and the same page-break rule — so the
/// two pages look like they came out of the same shop. Two columns instead of
/// four, and no running balance: every row is a name and a figure.
///
/// **Black on white**, like the statement, and for a different reason. That one
/// is handed to a customer; this one is never handed to anyone — but it is
/// printed, and a dark page prints badly whoever is reading it.
///
/// The layout decides nothing about wording. Every string it draws came from
/// `SummaryDocument`, which is shared with the Android build and tested.
enum SummaryPDF {

    /// A4 at 72dpi, which is the unit `UIGraphicsPDFRenderer` works in.
    private static let pageSize = CGSize(width: 595, height: 842)
    private static let margin: CGFloat = 44

    private static let titleSize: CGFloat = 15
    private static let bodySize: CGFloat = 9.5
    private static let rowSize: CGFloat = 10
    private static let line: CGFloat = 13

    private static let ink = UIColor(red: 0.078, green: 0.078, blue: 0.110, alpha: 1)
    private static let grey = UIColor(red: 0.420, green: 0.420, blue: 0.463, alpha: 1)
    private static let ruleColour = UIColor(red: 0.839, green: 0.839, blue: 0.871, alpha: 1)

    private static func attributes(_ size: CGFloat, bold: Bool = false, muted: Bool = false) -> [NSAttributedString.Key: Any] {
        [
            .font: bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size),
            .foregroundColor: muted ? grey : ink
        ]
    }

    /// Renders the document and returns the file it was written to.
    ///
    /// Written into the app's own temporary directory, which is where a file goes
    /// when the only thing that will read it is the share sheet.
    static func write(_ document: SummaryDocument, fileName: String) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)

        try renderer.writePDF(to: url) { context in
            context.beginPage()

            let right = pageSize.width - margin
            let width = right - margin
            var y = margin

            // MARK: Whose list this is, and when it was true

            document.shopName.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
            y += line + 4
            document.title.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(titleSize, bold: true))
            y += titleSize + 6
            document.asOf.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
            y += line + 18

            // MARK: The list

            guard !document.isEmpty else {
                document.emptyLine.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize))
                return
            }

            func drawHeadings() {
                document.columnHeadings[0].draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(rowSize, bold: true))
                drawRight(document.columnHeadings[1], rightEdge: right, y: y, attributes: attributes(rowSize, bold: true))
                y += 16
                rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                y += 6
            }
            drawHeadings()

            for row in document.rows {
                // A page break before a row rather than through one, and the
                // headings repeat: a second page whose columns are unlabelled is
                // a page nobody can read on its own.
                if y > pageSize.height - margin - 60 {
                    context.beginPage()
                    y = margin
                    drawHeadings()
                }

                // The name is truncated to the space before the figure rather
                // than drawn over it. A long name running under the amount is
                // how a chasing list becomes unreadable at exactly the row that
                // matters most.
                let nameWidth = width * 0.68
                draw(row.name, at: CGPoint(x: margin, y: y), maxWidth: nameWidth, attributes: attributes(rowSize))
                // The aside, where a row has one: how often something was bought,
                // set grey and between the two so it never competes with the
                // figure. A debtor has none — they are behind by an amount, not
                // by a count of anything.
                if let detail = row.detail {
                    drawRight(detail, rightEdge: margin + width * 0.86, y: y, attributes: attributes(bodySize, muted: true))
                }
                drawRight(row.amount, rightEdge: right, y: y, attributes: attributes(rowSize))
                y += 17
                rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                y += 6
            }

            // The figure the page exists to state, where the eye stops.
            y += 6
            document.totalLabel.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(rowSize, bold: true))
            drawRight(document.totalValue, rightEdge: right, y: y, attributes: attributes(rowSize, bold: true))

            // The fact the column could not carry, set small and grey under the
            // total so it is plainly not another row. Only the payments page has
            // one: what the shop paid out over the same days, which belongs on the
            // page but not in a column of money coming in.
            if let footnote = document.footnote {
                y += 17
                footnote.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
            }
        }

        return url
    }

    // MARK: Drawing helpers

    private static func drawRight(_ text: String, rightEdge: CGFloat, y: CGFloat, attributes: [NSAttributedString.Key: Any]) {
        let width = (text as NSString).size(withAttributes: attributes).width
        text.draw(at: CGPoint(x: rightEdge - width, y: y), withAttributes: attributes)
    }

    /// One line, clipped to `maxWidth` with an ellipsis rather than wrapped: a
    /// row on this page is a name and a figure, and a name that wraps takes the
    /// figure beside it out of line with every other row.
    private static func draw(
        _ text: String,
        at point: CGPoint,
        maxWidth: CGFloat,
        attributes: [NSAttributedString.Key: Any]
    ) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineBreakMode = .byTruncatingTail
        var attributes = attributes
        attributes[.paragraphStyle] = paragraph
        (text as NSString).draw(
            with: CGRect(x: point.x, y: point.y, width: maxWidth, height: 20),
            options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine],
            attributes: attributes,
            context: nil
        )
    }

    private static func rule(from: CGPoint, to: CGPoint) {
        let path = UIBezierPath()
        path.move(to: from)
        path.addLine(to: to)
        path.lineWidth = 0.8
        ruleColour.setStroke()
        path.stroke()
    }
}
