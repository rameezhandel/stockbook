import SwiftUI
import UIKit

/// Draws a `StatementDocument` onto A4 pages.
///
/// **Black on white**, unlike every other surface in this app. The screen is a
/// dark instrument the owner reads at a counter; this is a document they hand to
/// a customer, forward on WhatsApp, or print — and a dark page does none of those
/// well. Nothing here reads `Nocturne`.
///
/// The layout decides nothing about wording: every string it draws came from
/// `StatementDocument`, which is shared with the Android build and tested. This
/// file is only geometry, and it is deliberately the same geometry — A4 at 72dpi,
/// the same column fractions, the same page-break rule.
enum StatementPDF {

    /// A4 at 72dpi, which is the unit `UIGraphicsPDFRenderer` works in.
    private static let pageSize = CGSize(width: 595, height: 842)
    private static let margin: CGFloat = 44

    private static let titleSize: CGFloat = 15
    private static let bodySize: CGFloat = 9.5
    private static let rowSize: CGFloat = 9
    private static let line: CGFloat = 13

    /// Where each column sits, as fractions of the writable width.
    ///
    /// The details column is left-aligned and wide, because it now carries what a
    /// row is *and* when it happened — two columns' worth in one. The three money
    /// columns are right-aligned against the edges below, which is how a column
    /// of figures is read: by the units lining up.
    private static let colDetails: CGFloat = 0
    private static let edgeCharge: CGFloat = 0.62
    private static let edgeSettled: CGFloat = 0.81
    private static let edgeBalance: CGFloat = 1.0

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
    static func write(_ document: StatementDocument, fileName: String) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)

        try renderer.writePDF(to: url) { context in
            context.beginPage()

            let right = pageSize.width - margin
            let width = right - margin
            var y = margin

            // MARK: Who it is from, and who it is for

            document.shopName.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(titleSize, bold: true))
            var leftY = y + titleSize + line - 4
            for addressLine in document.shopAddressLines {
                addressLine.draw(at: CGPoint(x: margin, y: leftY), withAttributes: attributes(bodySize))
                leftY += line
            }

            // The right-hand block is right-aligned against the margin, so a long
            // shop name on the left cannot push into it.
            var rightY = y
            drawRight(document.addressedToLabel, rightEdge: right, y: rightY, attributes: attributes(bodySize, muted: true))
            rightY += line + 2
            drawRight(document.partyName, rightEdge: right, y: rightY, attributes: attributes(bodySize, bold: true))
            rightY += line
            for partyLine in document.partyLines {
                drawRight(partyLine, rightEdge: right, y: rightY, attributes: attributes(bodySize))
                rightY += line
            }

            y = max(leftY, rightY) + 26

            // MARK: The summary box

            let boxTop = y
            let boxHeight = CGFloat(document.summaryRows.count + 2) * 22
            stroke(CGRect(x: margin, y: boxTop, width: width, height: boxHeight))

            var rowY = boxTop
            document.summaryTitle.draw(at: CGPoint(x: margin + 10, y: rowY + 6), withAttributes: attributes(bodySize, bold: true))
            rowY += 22
            rule(from: CGPoint(x: margin, y: rowY), to: CGPoint(x: right, y: rowY))

            for row in document.summaryRows {
                row.label.draw(at: CGPoint(x: margin + 10, y: rowY + 6), withAttributes: attributes(bodySize))
                drawRight(row.value.bracketed(row.deduction), rightEdge: right - 10, y: rowY + 6, attributes: attributes(bodySize))
                rowY += 22
                rule(from: CGPoint(x: margin, y: rowY), to: CGPoint(x: right, y: rowY))
            }

            document.closingLabel.draw(at: CGPoint(x: margin + 10, y: rowY + 6), withAttributes: attributes(bodySize, bold: true))
            drawRight(document.closingValue, rightEdge: right - 10, y: rowY + 6, attributes: attributes(bodySize, bold: true))

            y = boxTop + boxHeight + 30

            // MARK: The activity table

            document.activityTitle.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(12, bold: true))
            y += 20

            func drawHeadings() {
                let heads = document.columnHeadings
                let bold = attributes(rowSize, bold: true)
                heads[0].draw(at: CGPoint(x: margin + width * colDetails, y: y), withAttributes: bold)
                drawRight(heads[1], rightEdge: margin + width * edgeCharge, y: y, attributes: bold)
                drawRight(heads[2], rightEdge: margin + width * edgeSettled, y: y, attributes: bold)
                drawRight(heads[3], rightEdge: margin + width * edgeBalance, y: y, attributes: bold)
                y += 16
                rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                y += 5
            }
            drawHeadings()

            for row in document.activityRows {
                // A page break before a row rather than through one, and the
                // headings repeat: a second page whose columns are unlabelled is
                // a page nobody can read on its own.
                if y > pageSize.height - margin - 60 {
                    context.beginPage()
                    y = margin
                    drawHeadings()
                }

                // Exactly one of the two money columns carries anything, so the
                // empty one draws nothing at all rather than a dash or a zero:
                // an empty cell is unambiguous, and a zero in the Received column
                // is a payment somebody might go looking for.
                let body = attributes(rowSize)
                row.details.draw(at: CGPoint(x: margin + width * colDetails, y: y), withAttributes: body)
                drawRight(row.charge, rightEdge: margin + width * edgeCharge, y: y, attributes: body)
                drawRight(row.settled, rightEdge: margin + width * edgeSettled, y: y, attributes: body)
                drawRight(row.balance, rightEdge: margin + width * edgeBalance, y: y, attributes: body)
                y += 16
                rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                y += 5
            }

            // The figure the document exists to state, repeated where the eye
            // stops.
            y += 4
            let bold = attributes(rowSize, bold: true)
            document.closingLabel.draw(at: CGPoint(x: margin + width * colDetails, y: y), withAttributes: bold)
            drawRight(document.closingValue, rightEdge: margin + width * edgeBalance, y: y, attributes: bold)
        }

        return url
    }

    // MARK: Drawing helpers

    private static func drawRight(_ text: String, rightEdge: CGFloat, y: CGFloat, attributes: [NSAttributedString.Key: Any]) {
        let width = (text as NSString).size(withAttributes: attributes).width
        text.draw(at: CGPoint(x: rightEdge - width, y: y), withAttributes: attributes)
    }

    private static func rule(from: CGPoint, to: CGPoint) {
        let path = UIBezierPath()
        path.move(to: from)
        path.addLine(to: to)
        path.lineWidth = 0.8
        ruleColour.setStroke()
        path.stroke()
    }

    private static func stroke(_ rect: CGRect) {
        let path = UIBezierPath(rect: rect)
        path.lineWidth = 0.8
        ruleColour.setStroke()
        path.stroke()
    }
}

private extension String {
    /// `(SAR 530.00)` — accounting brackets, the convention the shop's own
    /// supplier statements use. A bare minus in front of a currency symbol reads
    /// as a typo on paper.
    func bracketed(_ deduction: Bool) -> String { deduction ? "(\(self))" : self }
}

/// Hands a file to the system share sheet.
///
/// `UIActivityViewController` rather than `ShareLink`, because the URL is
/// produced by a tap rather than known when the view is built — `ShareLink`
/// wants its item up front, and a statement is rendered for whichever period is
/// on screen at the moment the button is pressed.
struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// A rendered statement waiting to be handed out.
///
/// A wrapper rather than a retroactive `Identifiable` on `URL`: conforming a
/// Foundation type to a Foundation protocol from here is the kind of thing that
/// breaks the day the standard library does it too, and `sheet(item:)` only
/// needs *something* identifiable.
struct StatementFile: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}
