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

    /// Where each column starts, as a fraction of the writable width, by how many
    /// columns the page has.
    ///
    /// Three shapes, because three kinds of page come through here. A balance
    /// list is a name and a figure. A register of expenses adds the day. A
    /// register of bills, purchases or receipts adds the number on the paper as
    /// well — and the name gives up the room for it, since a customer's name is
    /// the one thing on the row a reader can still recognise cut short.
    ///
    /// The last column is right-aligned against the margin and takes no start.
    private static let columnStarts: [Int: [CGFloat]] = [
        2: [0],
        3: [0, 0.62],
        4: [0, 0.38, 0.66],
    ]

    /// How much room a column has: up to the next one, less a gutter.
    private static func columnWidth(_ starts: [CGFloat], _ index: Int) -> CGFloat {
        (index + 1 < starts.count ? starts[index + 1] : 0.98) - starts[index] - 0.02
    }

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

            // The letterhead, on every page of every document the app prints but
            // the ledger book. A sheet in a folder has to say whose shop it came
            // from, and until now this one did not.
            func masthead() {
                PageBand.draw(
                    pageWidth: pageSize.width,
                    margin: margin,
                    shopName: document.shopName,
                    addressLines: document.shopAddressLines,
                    docType: document.title,
                    dateLine: document.asOf
                )
                y = PageBand.contentTop
            }
            masthead()

            // MARK: The list

            guard !document.isEmpty else {
                document.emptyLine.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize))
                return
            }

            // Two, three or four, and the last of them is the figure. A page
            // whose headings this does not know about would draw its columns on
            // top of one another, so it falls back to the plainest shape rather
            // than to nothing.
            let starts = Self.columnStarts[document.columnHeadings.count] ?? [0]

            func drawHeadings() {
                for (index, start) in starts.enumerated() {
                    document.columnHeadings[index].draw(
                        at: CGPoint(x: margin + width * start, y: y),
                        withAttributes: attributes(rowSize, bold: true)
                    )
                }
                drawRight(
                    document.columnHeadings[document.columnHeadings.count - 1],
                    rightEdge: right,
                    y: y,
                    attributes: attributes(rowSize, bold: true)
                )
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
                    masthead()
                    drawHeadings()
                }

                // Every cell cut short rather than drawn over its neighbour. A
                // long name running under the number is how a register becomes
                // unreadable at exactly the row somebody is looking for.
                //
                // The middle cells are grey: the row is a name and a figure, and
                // the number and the day are how you find it, not what it says.
                // Positional, not compacted. A receipt written without a number
                // leaves an empty cell; dropping it would slide the date up into
                // the number's column and put the whole row out of step with its
                // heading.
                let cells: [String]
                switch starts.count {
                case 3: cells = [row.name, row.reference ?? "", row.date ?? ""]
                case 2: cells = [row.name, row.date ?? ""]
                default: cells = [row.name]
                }
                for (index, cell) in cells.enumerated() where !cell.isEmpty {
                    draw(
                        cell,
                        at: CGPoint(x: margin + width * starts[index], y: y),
                        maxWidth: width * Self.columnWidth(starts, index),
                        attributes: index == 0
                            ? attributes(rowSize)
                            : attributes(bodySize, muted: true)
                    )
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
