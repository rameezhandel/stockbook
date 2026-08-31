import UIKit

/// Draws a `DayLedgerDocument` onto A4 pages.
///
/// The twin of `DayLedgerPdf.kt`, and the same geometry as every other page the
/// shop prints — A4 at 72dpi, the same margin, the same rules — so a folder of
/// them looks like it came out of one shop.
///
/// **Black on white.** This one is never handed to a customer, but it is printed,
/// and a dark page prints badly whoever is reading it.
///
/// **Five columns and often a hundred rows.** The page breaks between rows, never
/// through one, and the column headings repeat at the top of each new page: a
/// second sheet whose columns are unlabelled is a sheet nobody can read on its
/// own — least of all beside a paper book, which is what this exists for.
///
/// The layout decides nothing about wording. Every string it draws came from
/// `DayLedgerDocument`, which is shared with the Android build and tested.
enum DayLedgerPDF {

    /// A4 at 72dpi, which is the unit `UIGraphicsPDFRenderer` works in.
    private static let pageSize = CGSize(width: 595, height: 842)
    private static let margin: CGFloat = 40

    private static let titleSize: CGFloat = 15
    private static let bodySize: CGFloat = 9.5
    private static let rowSize: CGFloat = 8.5
    private static let noteSize: CGFloat = 7.5

    /// Where each column ends, as fractions of the writable width. The name is
    /// left-aligned and takes what is left; the four money columns are
    /// right-aligned against these edges, which is how a column of figures is
    /// read — by the units lining up under each other.
    private static let edgeInvoiced: CGFloat = 0.55
    private static let edgeReceived: CGFloat = 0.70
    private static let edgeOld: CGFloat = 0.85
    private static let edgeCurrent: CGFloat = 1.0

    private static let ink = UIColor(red: 0.078, green: 0.078, blue: 0.110, alpha: 1)
    private static let grey = UIColor(red: 0.420, green: 0.420, blue: 0.463, alpha: 1)
    private static let ruleColour = UIColor(red: 0.839, green: 0.839, blue: 0.871, alpha: 1)

    private static func attributes(
        _ size: CGFloat,
        bold: Bool = false,
        muted: Bool = false
    ) -> [NSAttributedString.Key: Any] {
        [
            .font: bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size),
            .foregroundColor: muted ? grey : ink,
        ]
    }

    static func write(_ document: DayLedgerDocument, named fileName: String) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)

        try renderer.writePDF(to: url) { context in
            context.beginPage()

            let right = pageSize.width - margin
            let width = right - margin
            var y = margin

            document.shopName.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(titleSize, bold: true))
            y += titleSize + 10
            document.title.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, bold: true))
            drawRight(document.onDate, rightEdge: right, y: y, attributes: attributes(bodySize))
            y += bodySize + 6

            // Only on a narrowed page, and said before the figures rather than
            // after: somebody reading the totals has to already know what they
            // are the total of.
            if let note = document.filterNote {
                note.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(noteSize, muted: true))
                y += noteSize + 6
            }
            y += 6

            guard !document.isEmpty else {
                document.emptyLine.draw(
                    at: CGPoint(x: margin, y: y),
                    withAttributes: attributes(bodySize, muted: true)
                )
                return
            }

            func headings() {
                let bold = attributes(rowSize, bold: true)
                document.columnHeadings[0].draw(at: CGPoint(x: margin, y: y), withAttributes: bold)
                drawRight(document.columnHeadings[1], rightEdge: margin + width * edgeInvoiced, y: y, attributes: bold)
                drawRight(document.columnHeadings[2], rightEdge: margin + width * edgeReceived, y: y, attributes: bold)
                drawRight(document.columnHeadings[3], rightEdge: margin + width * edgeOld, y: y, attributes: bold)
                drawRight(document.columnHeadings[4], rightEdge: margin + width * edgeCurrent, y: y, attributes: bold)
                y += 15
                rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                y += 2
            }
            headings()

            let body = attributes(rowSize)
            let noteInk = attributes(noteSize, muted: true)
            for row in document.rows {
                let height: CGFloat = row.note == nil ? 15 : 24
                // A break before a row rather than through one, and the headings
                // repeat so the second page stands on its own. The totals need
                // room too, which is why this is more than one row.
                if y + height > pageSize.height - margin - 30 {
                    context.beginPage()
                    y = margin
                    headings()
                }

                draw(row.name, at: CGPoint(x: margin, y: y), maxWidth: width * edgeInvoiced - 50, attributes: body)
                drawRight(row.invoiced, rightEdge: margin + width * edgeInvoiced, y: y, attributes: body)
                drawRight(row.received, rightEdge: margin + width * edgeReceived, y: y, attributes: body)
                drawRight(row.oldBalance, rightEdge: margin + width * edgeOld, y: y, attributes: body)
                drawRight(row.currentBalance, rightEdge: margin + width * edgeCurrent, y: y, attributes: body)
                if let note = row.note {
                    note.draw(at: CGPoint(x: margin + 8, y: y + 11), withAttributes: noteInk)
                }
                y += height
                rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                y += 2
            }

            // The columns added up, and they are the columns above rather than
            // the whole book — see `DayLedger.movedOnly()`.
            let total = attributes(rowSize, bold: true)
            y += 4
            document.totalLabel.draw(at: CGPoint(x: margin, y: y), withAttributes: total)
            drawRight(document.totals[0], rightEdge: margin + width * edgeInvoiced, y: y, attributes: total)
            drawRight(document.totals[1], rightEdge: margin + width * edgeReceived, y: y, attributes: total)
            drawRight(document.totals[2], rightEdge: margin + width * edgeOld, y: y, attributes: total)
            drawRight(document.totals[3], rightEdge: margin + width * edgeCurrent, y: y, attributes: total)
        }

        return url
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

    /// One line, clipped with an ellipsis rather than wrapped: a name that wraps
    /// takes the four figures beside it out of line with every other row, which
    /// is the one thing that makes a table of numbers unreadable.
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
            with: CGRect(x: point.x, y: point.y, width: maxWidth, height: 16),
            options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine],
            attributes: attributes,
            context: nil
        )
    }

    private static func rule(from: CGPoint, to: CGPoint) {
        let path = UIBezierPath()
        path.move(to: from)
        path.addLine(to: to)
        path.lineWidth = 0.6
        ruleColour.setStroke()
        path.stroke()
    }
}
