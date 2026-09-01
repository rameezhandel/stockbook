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
    /// Darker and heavier than it looks like it needs: these pages photocopy.
    private static let ruleColour = UIColor(red: 0.769, green: 0.769, blue: 0.816, alpha: 1)

    /// The band behind every other row, at 10% rather than the 6% it looks like
    /// it wants.
    ///
    /// A mono laser cannot print grey — it halftones into a dot screen, and below
    /// roughly 8% that screen comes out patchy or not at all, which would stripe
    /// some pages and not others. Ten per cent survives, and on a hundred-row
    /// roll-call the banding is what keeps the eye on one line.
    private static let bandColour = UIColor(red: 0.902, green: 0.902, blue: 0.925, alpha: 1)

    /// The app's own violet, as on the statement.
    private static let accent = UIColor(red: 0.361, green: 0.310, blue: 0.769, alpha: 1)
    /// The same violet at a tenth, behind the column headings.
    private static let accentSoft = UIColor(red: 0.929, green: 0.918, blue: 0.984, alpha: 1)

    private static func reversed(_ size: CGFloat, alpha: CGFloat = 1) -> [NSAttributedString.Key: Any] {
        [.font: UIFont.boldSystemFont(ofSize: size), .foregroundColor: UIColor.white.withAlphaComponent(alpha)]
    }

    private static func fill(_ rect: CGRect, _ colour: UIColor) {
        colour.setFill()
        UIBezierPath(rect: rect).fill()
    }

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

            // The same band the statement carries, full bleed, so a folder of
            // these reads as one shop's paperwork.
            let bandHeight: CGFloat = 58
            fill(CGRect(x: 0, y: 0, width: pageSize.width, height: bandHeight), accent)
            document.shopName.draw(at: CGPoint(x: margin, y: 16), withAttributes: reversed(titleSize))
            drawRight(document.title.uppercased(), rightEdge: right, y: 16, attributes: reversed(8, alpha: 0.82))
            drawRight(document.onDate, rightEdge: right, y: 30, attributes: reversed(11))
            y = bandHeight + 18

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
                fill(CGRect(x: margin, y: y - 3, width: width, height: 15), accentSoft)
                let head = attributes(7.5, muted: true)
                document.columnHeadings[0].uppercased().draw(at: CGPoint(x: margin, y: y), withAttributes: head)
                drawRight(document.columnHeadings[1].uppercased(), rightEdge: margin + width * edgeInvoiced, y: y, attributes: head)
                drawRight(document.columnHeadings[2].uppercased(), rightEdge: margin + width * edgeReceived, y: y, attributes: head)
                drawRight(document.columnHeadings[3].uppercased(), rightEdge: margin + width * edgeOld, y: y, attributes: head)
                drawRight(document.columnHeadings[4].uppercased(), rightEdge: margin + width * edgeCurrent, y: y, attributes: head)
                y += 17
            }
            headings()

            let body = attributes(rowSize)
            let noteInk = attributes(noteSize, muted: true)
            var striped = false
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

                // Every other row banded, which is the whole reason a hundred-line
                // roll-call can be read across five columns without losing the line.
                striped.toggle()
                if striped {
                    bandColour.setFill()
                    UIBezierPath(rect: CGRect(x: margin, y: y - 2, width: width, height: height)).fill()
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
            // Reversed out of solid black, like the statement's balance due: the
            // line the whole page adds up to, and the one a reader checks first.
            y += 4
            fill(CGRect(x: margin, y: y, width: width, height: 22), accent)
            let total = reversed(rowSize)
            document.totalLabel.draw(at: CGPoint(x: margin + 6, y: y + 5), withAttributes: total)
            drawRight(document.totals[0], rightEdge: margin + width * edgeInvoiced - 2, y: y + 5, attributes: total)
            drawRight(document.totals[1], rightEdge: margin + width * edgeReceived - 2, y: y + 5, attributes: total)
            drawRight(document.totals[2], rightEdge: margin + width * edgeOld - 2, y: y + 5, attributes: total)
            drawRight(document.totals[3], rightEdge: margin + width * edgeCurrent - 2, y: y + 5, attributes: total)
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
        path.lineWidth = 0.9
        ruleColour.setStroke()
        path.stroke()
    }

}
