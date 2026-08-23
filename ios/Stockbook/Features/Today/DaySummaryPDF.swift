import UIKit

/// Draws a `DaySummaryDocument` onto A4 pages.
///
/// `SummaryPDF`'s taller cousin, and deliberately the same geometry — A4 at
/// 72dpi, the same margin, the same rules, the same page-break rule — so every
/// page the shop prints looks like it came out of the same shop. What it adds is
/// depth: a heading and a subtotal per section, and the products of an itemised
/// bill indented under the row they were sold on.
///
/// **Black on white**, like the statement, and for a different reason. That one
/// is handed to a customer; this one is never handed to anyone — but it is
/// printed, and a dark page prints badly whoever is reading it.
///
/// The layout decides nothing about wording. Every string it draws came from
/// `DaySummaryDocument`, which is shared with the Android build and tested.
enum DaySummaryPDF {

    /// A4 at 72dpi, which is the unit `UIGraphicsPDFRenderer` works in.
    private static let pageSize = CGSize(width: 595, height: 842)
    private static let margin: CGFloat = 44

    private static let titleSize: CGFloat = 15
    private static let bodySize: CGFloat = 9.5
    private static let rowSize: CGFloat = 10
    private static let line: CGFloat = 13

    /// How much of the writable width a name may take before it is cut short.
    private static let nameFraction: CGFloat = 0.62

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
    static func write(_ document: DaySummaryDocument, fileName: String) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)

        try renderer.writePDF(to: url) { context in
            context.beginPage()

            let right = pageSize.width - margin
            let width = right - margin
            var y = margin

            /// A page break before a row rather than through one.
            func room(_ needed: CGFloat) {
                guard y > pageSize.height - margin - needed else { return }
                context.beginPage()
                y = margin
            }

            // MARK: Whose day this is, and which one

            document.shopName.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
            y += line + 4
            document.title.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(titleSize, bold: true))
            y += titleSize + 6
            document.onDate.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
            y += line + 18

            guard !document.isEmpty else {
                document.emptyLine.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize))
                return
            }

            // MARK: What happened, a section at a time

            for section in document.sections {
                // The heading goes over at least one row or not at all: a section
                // title alone at the foot of a page is a promise the page does
                // not keep.
                room(60)
                section.heading.uppercased()
                    .draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, bold: true))
                y += 14
                rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                y += 8

                for row in section.rows {
                    room(40)
                    // The name is truncated to the space before the figure rather
                    // than drawn over it. A long name running under the amount is
                    // how a page becomes unreadable at exactly the row that
                    // matters most.
                    draw(row.name, at: CGPoint(x: margin, y: y), maxWidth: width * nameFraction, attributes: attributes(rowSize))
                    drawRight(row.amount, rightEdge: right, y: y, attributes: attributes(rowSize))
                    y += 14

                    // The paper's number, and what is still owed on it. Grey and
                    // under the name, because it qualifies the row rather than
                    // competing with the figure.
                    if let detail = row.detail {
                        detail.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
                        y += 12
                    }

                    // The products, indented under the row they were sold on, so
                    // a bill with four things on it reads as one bill.
                    for item in row.items {
                        room(24)
                        draw(
                            item.text,
                            at: CGPoint(x: margin + 14, y: y),
                            maxWidth: width * nameFraction,
                            attributes: attributes(bodySize, muted: true)
                        )
                        drawRight(item.amount, rightEdge: right, y: y, attributes: attributes(bodySize, muted: true))
                        y += 12
                    }

                    y += 4
                    rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                    y += 6
                }

                room(30)
                y += 2
                section.subtotalLabel.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(rowSize, bold: true))
                drawRight(section.subtotalValue, rightEdge: right, y: y, attributes: attributes(rowSize, bold: true))
                y += 26
            }

            // MARK: What the day did to the cash box

            room(30 + CGFloat(document.cash.count) * 16)
            rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
            y += 12
            for cashLine in document.cash {
                let style = attributes(rowSize, bold: cashLine.isNet)
                cashLine.label.draw(at: CGPoint(x: margin, y: y), withAttributes: style)
                drawRight(cashLine.value, rightEdge: right, y: y, attributes: style)
                y += 16
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
