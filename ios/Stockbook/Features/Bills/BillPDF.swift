import SwiftUI
import UIKit

/// Draws a `BillDocument` onto A4 pages.
///
/// The **Counter** treatment, same as the statement and the payment receipt:
/// this is a single sheet handed to one person, which is where the band is worth
/// its toner. Drawn on the same grid as those two — same band, same facts row,
/// same tinted card — so every piece of paper a customer is handed obviously
/// comes from the same shop.
///
/// **It breaks across pages.** A bill is usually one line or none, but a load of
/// fittings can run to forty, and a break through a row would leave a quantity
/// on one sheet and its price on the next. The break comes before a row, and the
/// total block is never orphaned onto a page of its own.
///
/// The layout decides nothing about wording: every string here came from
/// `BillDocument`, which is shared with the Android build and tested. This file
/// is only geometry.
enum BillPDF {

    /// A4 at 72dpi, which is the unit `UIGraphicsPDFRenderer` works in.
    private static let pageSize = CGSize(width: 595, height: 842)
    private static let margin: CGFloat = 44

    private static let titleSize: CGFloat = 15
    private static let bodySize: CGFloat = 9.5
    private static let rowSize: CGFloat = 10

    private static let ink = UIColor(red: 0.078, green: 0.078, blue: 0.110, alpha: 1)
    private static let grey = UIColor(red: 0.420, green: 0.420, blue: 0.463, alpha: 1)
    /// The hairline, darker than it looks like it needs to be: these pages get
    /// photocopied, and a 16%-grey rule is the first thing a copier loses.
    private static let ruleColour = UIColor(red: 0.769, green: 0.769, blue: 0.816, alpha: 1)

    /// The app's own violet, carried onto the paper.
    private static let accent = UIColor(red: 0.361, green: 0.310, blue: 0.769, alpha: 1)
    /// The same violet at a tenth, for the total card.
    private static let accentSoft = UIColor(red: 0.929, green: 0.918, blue: 0.984, alpha: 1)

    /// Renders the bill and returns the file it was written to.
    ///
    /// Written into the app's own temporary directory, which is where a file
    /// goes when the only thing that will read it is the share sheet.
    static func write(_ document: BillDocument, fileName: String) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try renderer.writePDF(to: url) { context in
            draw(document, in: context)
        }
        return url
    }

    private static func draw(_ document: BillDocument, in context: UIGraphicsPDFRendererContext) {
        context.beginPage()

        let right = pageSize.width - margin
        let width = right - margin

        // MARK: The band
        //
        // Full bleed to the paper's edge, not inset to the margin: an inset
        // colour block reads as a box somebody drew, where a band that runs off
        // both sides reads as the head of the page. Sans throughout, because the
        // shop's own name may be in Arabic or Kannada and a serif's coverage of
        // those is patchy — the failure being tofu boxes in the largest text on
        // the page.
        let bandHeight: CGFloat = 74
        fill(CGRect(x: 0, y: 0, width: pageSize.width, height: bandHeight), accent)
        document.shopName.draw(at: CGPoint(x: margin, y: 18), withAttributes: reversed(titleSize))
        for (index, addressLine) in document.shopAddressLines.enumerated() {
            addressLine.draw(
                at: CGPoint(x: margin, y: 38 + CGFloat(index) * 10),
                withAttributes: reversed(7.5, alpha: 0.8)
            )
        }
        drawRight(document.docType.uppercased(), rightEdge: right, y: 18, attributes: reversed(8, alpha: 0.82))
        drawRight(document.reference, rightEdge: right, y: 32, attributes: reversed(13))
        var y = bandHeight + 24

        // MARK: Who it is for, and when it was written
        let factsTop = y
        let factsHeight: CGFloat = 46
        let middle = margin + width / 2
        let factLabel = accentText(7.5, bold: true)

        document.addressedToLabel.uppercased()
            .draw(at: CGPoint(x: margin, y: factsTop + 4), withAttributes: factLabel)
        document.partyName
            .draw(at: CGPoint(x: margin, y: factsTop + 16), withAttributes: attributes(11, bold: true))
        document.partyLines.joined(separator: " · ")
            .draw(at: CGPoint(x: margin, y: factsTop + 31), withAttributes: attributes(8, muted: true))

        document.dateLabel.uppercased()
            .draw(at: CGPoint(x: middle, y: factsTop + 4), withAttributes: factLabel)
        document.dateValue
            .draw(at: CGPoint(x: middle, y: factsTop + 16), withAttributes: attributes(11, bold: true))

        y = factsTop + factsHeight + 20

        // MARK: What was sold, where the bill says
        //
        // Nothing at all on a bill entered as a figure, and no empty heading
        // either: a table head over no rows is a question the reader has to
        // answer for themselves.
        if document.isItemised {
            rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
            y += 14

            for line in document.lines {
                // A break before a row rather than through one — a quantity on
                // one sheet and its price on the next is worse than a short
                // page. The room kept back is for the totals block.
                if y + 26 > pageSize.height - margin - 150 {
                    context.beginPage()
                    y = margin
                }

                line.name.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(rowSize))
                drawRight(line.amount, rightEdge: right, y: y, attributes: attributes(rowSize))
                line.detail.draw(at: CGPoint(x: margin, y: y + 12), withAttributes: attributes(bodySize, muted: true))
                y += 28
            }
            y += 2
            rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
            y += 16
        }

        // MARK: Subtotal and discount, where one was given
        let totalsLeft = margin + width * 0.52
        for row in document.summaryRows {
            row.label.draw(at: CGPoint(x: totalsLeft, y: y), withAttributes: attributes(bodySize, muted: true))
            drawRight(
                row.value.deducted(row.deduction),
                rightEdge: right,
                y: y,
                attributes: attributes(bodySize)
            )
            y += 15
        }
        if !document.summaryRows.isEmpty { y += 4 }

        // MARK: The figure the page exists to state
        //
        // Set large and alone in its own card, because this is the one thing the
        // person holding the bill is checking.
        let cardHeight: CGFloat = 58
        fill(CGRect(x: totalsLeft, y: y, width: right - totalsLeft, height: cardHeight), accentSoft)
        fill(CGRect(x: totalsLeft, y: y, width: 3.5, height: cardHeight), accent)
        document.totalLabel.uppercased()
            .draw(at: CGPoint(x: totalsLeft + 14, y: y + 12), withAttributes: factLabel)
        drawRight(document.totalValue, rightEdge: right - 14, y: y + 24, attributes: accentText(24, bold: true))
        y += cardHeight + 16

        // MARK: Settled, or what is left and who owes it
        document.paymentNote.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(bodySize))
    }

    // MARK: Drawing helpers

    private static func reversed(_ size: CGFloat, alpha: CGFloat = 1) -> [NSAttributedString.Key: Any] {
        [.font: UIFont.boldSystemFont(ofSize: size), .foregroundColor: UIColor.white.withAlphaComponent(alpha)]
    }

    private static func accentText(_ size: CGFloat, bold: Bool = false) -> [NSAttributedString.Key: Any] {
        [
            .font: bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size),
            .foregroundColor: accent
        ]
    }

    private static func attributes(
        _ size: CGFloat,
        bold: Bool = false,
        muted: Bool = false
    ) -> [NSAttributedString.Key: Any] {
        [
            .font: bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size),
            .foregroundColor: muted ? grey : ink
        ]
    }

    private static func fill(_ rect: CGRect, _ colour: UIColor) {
        colour.setFill()
        UIBezierPath(rect: rect).fill()
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

    private static func rule(from: CGPoint, to: CGPoint) {
        let path = UIBezierPath()
        path.move(to: from)
        path.addLine(to: to)
        path.lineWidth = 0.9
        ruleColour.setStroke()
        path.stroke()
    }
}

private extension String {
    /// `− SAR 20` — a discount reads as something taken off rather than as a
    /// second figure to add. Brackets are the statement's convention for a
    /// deduction inside a column of positives; here the line sits alone above a
    /// total, where a minus is plainer.
    func deducted(_ deduction: Bool) -> String { deduction ? "− \(self)" : self }
}

/// One bill, rendered and ready for the share sheet.
///
/// Both ways a bill is shared go through here — the confirmation straight after
/// saving, and the sheet a bill is opened on later — so the copy a customer is
/// given at the counter and the copy they ask for a week afterwards are the same
/// document.
///
/// The customer is looked up rather than taken from the bill: `Bill.who` is the
/// name typed at the counter and is all a bill carries, while the place and
/// phone that go under it live on the roster.
///
/// A failure hands back nil and nothing opens, which is the honest outcome the
/// other printed pages already settled on.
@MainActor
enum BillFile {
    static func make(_ bill: Bill, in store: StockbookStore) -> StatementFile? {
        let document = BillDocument.make(
            bill: bill,
            settings: store.settings,
            strings: Loc,
            customer: store.customer(key: Customer.key(for: bill.who))
        )
        let name = document.reference
            .replacingOccurrences(of: "[^A-Za-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
            .lowercased()
        guard let url = try? BillPDF.write(
            document,
            fileName: Loc.billFileName(name, Copy.fileDate(bill.createdAt))
        ) else { return nil }
        return StatementFile(url: url)
    }
}
