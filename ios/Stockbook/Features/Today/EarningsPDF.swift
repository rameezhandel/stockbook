import UIKit

/// Draws an `EarningsDocument` onto one A4 page.
///
/// `SummaryPDF`'s geometry — A4 at 72dpi, the same margin, the same rules — so
/// every page the shop prints looks like it came out of the same shop. One page
/// always: the chain is six lines and the confession is two, and a summary that
/// ran over would not be one.
///
/// **Black on white**, like the statement, and for a different reason. That one
/// is handed to a customer; this one is never handed to anyone — but it is
/// printed, and a dark page prints badly whoever is reading it.
///
/// The layout decides nothing about wording. Every string it draws came from
/// `EarningsDocument`, which is shared with the Android build and tested.
enum EarningsPDF {

    private static let pageSize = CGSize(width: 595, height: 842)
    private static let margin: CGFloat = 44

    private static let titleSize: CGFloat = 15
    private static let bodySize: CGFloat = 9.5
    private static let rowSize: CGFloat = 10
    private static let line: CGFloat = 13
    private static let bandHeight: CGFloat = 19

    private static let ink = UIColor(red: 0.078, green: 0.078, blue: 0.110, alpha: 1)
    private static let grey = UIColor(red: 0.420, green: 0.420, blue: 0.463, alpha: 1)
    private static let ruleColour = UIColor(red: 0.839, green: 0.839, blue: 0.871, alpha: 1)
    private static let bandColour = UIColor(red: 0.929, green: 0.929, blue: 0.949, alpha: 1)

    private static func attributes(_ size: CGFloat, bold: Bool = false, muted: Bool = false) -> [NSAttributedString.Key: Any] {
        [
            .font: bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size),
            .foregroundColor: muted ? grey : ink
        ]
    }

    static func write(_ document: EarningsDocument, fileName: String) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)

        try renderer.writePDF(to: url) { context in
            context.beginPage()

            let right = pageSize.width - margin
            var y = margin

            // MARK: Whose figures these are, and which days

            document.shopName.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
            y += line + 4
            document.title.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(titleSize, bold: true))
            y += titleSize + 6
            document.onDate.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
            y += line + 20

            guard !document.isEmpty else {
                document.emptyLine.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize))
                return
            }

            // MARK: Takings down to what was kept

            for figure in document.lines {
                let isTotal = figure.weight == .total
                let isMinus = figure.weight == .minus

                // A total is what the lines above it come to, so the rule goes
                // over it — the same place a subtotal's rule goes on every other
                // page this shop prints.
                if isTotal {
                    y += 4
                    rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
                    y += 9
                }

                let style = attributes(rowSize, bold: isTotal, muted: isMinus && !isTotal)
                figure.label.draw(at: CGPoint(x: margin, y: y), withAttributes: style)
                drawRight(isMinus ? "− \(figure.value)" : figure.value, rightEdge: right, y: y, attributes: style)
                y += isTotal ? 20 : 16
            }

            // MARK: What the page could not account for

            if document.hasGap {
                y += 14
                bandColour.setFill()
                UIBezierPath(rect: CGRect(x: margin, y: y, width: right - margin, height: bandHeight)).fill()
                document.gapHeading.uppercased()
                    .draw(at: CGPoint(x: margin + 7, y: y + 4), withAttributes: attributes(bodySize, bold: true))
                y += bandHeight + 10

                for entry in document.gap {
                    entry.label.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
                    drawRight(entry.value, rightEdge: right, y: y, attributes: attributes(bodySize, muted: true))
                    y += 14
                }

                if let note = document.gapNote {
                    y += 4
                    note.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize, muted: true))
                }
            }
        }

        return url
    }

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
}
