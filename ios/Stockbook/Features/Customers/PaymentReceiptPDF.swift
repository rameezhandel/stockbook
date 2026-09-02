import SwiftUI
import UIKit

/// Draws a `PaymentReceiptDocument` onto one A4 page.
///
/// The **Counter** treatment, same as the statement: this is a single sheet
/// handed to one person, which is where the band is worth its toner. It is drawn
/// on the same grid as the statement, with the same band, the same facts row and
/// the same tinted card, so the two pieces of paper a customer is handed
/// obviously come from the same shop.
///
/// **Half a page, and it says so.** A receipt is four figures; padding it down an
/// A4 sheet would make it look like a form somebody failed to fill in.
/// Everything sits in the top half and a dashed rule closes it, which is what a
/// counter does with a receipt anyway.
///
/// The layout decides nothing about wording: every string here came from
/// `PaymentReceiptDocument`, which is shared with the Android build and tested.
/// This file is only geometry.
enum PaymentReceiptPDF {

    /// A4 at 72dpi, which is the unit `UIGraphicsPDFRenderer` works in.
    private static let pageSize = CGSize(width: 595, height: 842)
    private static let margin: CGFloat = 44

    private static let titleSize: CGFloat = 15
    private static let bodySize: CGFloat = 9.5

    private static let ink = UIColor(red: 0.078, green: 0.078, blue: 0.110, alpha: 1)
    private static let grey = UIColor(red: 0.420, green: 0.420, blue: 0.463, alpha: 1)
    /// The hairline, darker than it looks like it needs to be: these pages get
    /// photocopied, and a 16%-grey rule is the first thing a copier loses.
    private static let ruleColour = UIColor(red: 0.769, green: 0.769, blue: 0.816, alpha: 1)

    /// The app's own violet, carried onto the paper.
    private static let accent = UIColor(red: 0.361, green: 0.310, blue: 0.769, alpha: 1)
    /// The same violet at a tenth, for the amount card and the summary card.
    private static let accentSoft = UIColor(red: 0.929, green: 0.918, blue: 0.984, alpha: 1)

    /// Renders the receipt and returns the file it was written to.
    ///
    /// Written into the app's own temporary directory, which is where a file goes
    /// when the only thing that will read it is the share sheet.
    static func write(_ document: PaymentReceiptDocument, fileName: String) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try renderer.writePDF(to: url) { context in
            draw(document, in: context)
        }
        return url
    }

    private static func draw(_ document: PaymentReceiptDocument, in context: UIGraphicsPDFRendererContext) {
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
        drawRight(document.dateValue, rightEdge: right, y: 30, attributes: reversed(11))
        var y = bandHeight + 24

        // MARK: Who it was, and which slip
        //
        // The statement's facts row exactly: label, the fact, then the smaller
        // one under it. No box round it — the accent labels already group the two
        // halves, and a hairline as well is one device too many.
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

        document.receiptLabel.uppercased()
            .draw(at: CGPoint(x: middle, y: factsTop + 4), withAttributes: factLabel)
        document.receiptValue
            .draw(at: CGPoint(x: middle, y: factsTop + 16), withAttributes: attributes(11, bold: true))
        "\(document.dateLabel) \(document.dateValue)"
            .draw(at: CGPoint(x: middle, y: factsTop + 31), withAttributes: attributes(8, muted: true))

        y = factsTop + factsHeight + 24

        // MARK: The figure the page exists to state
        //
        // Set large and alone in its own card, because this is the one thing the
        // person holding the slip is checking. Everything else on the page is
        // context for it.
        let amountHeight: CGFloat = 58
        fill(CGRect(x: margin, y: y, width: width, height: amountHeight), accentSoft)
        fill(CGRect(x: margin, y: y, width: 3.5, height: amountHeight), accent)
        document.amountLabel.uppercased()
            .draw(at: CGPoint(x: margin + 14, y: y + 12), withAttributes: factLabel)
        drawRight(document.amountValue, rightEdge: right - 14, y: y + 22, attributes: accentText(26, bold: true))
        y += amountHeight + 22

        // MARK: The owner's own note, where there is one
        if let noteLabel = document.noteLabel {
            noteLabel.uppercased().draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(7.5, muted: true))
            (document.noteValue ?? "").draw(at: CGPoint(x: margin, y: y + 12), withAttributes: attributes(bodySize))
            y += 30
        }

        // MARK: Where the account stands now
        //
        // Against the right edge under the figure above, in the same card the
        // statement's totals sit in: previous balance, this receipt coming off
        // it, and the line the reader checks last.
        document.summaryTitle.uppercased()
            .draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(8, muted: true))
        y += 14

        let cardLeft = margin + width * 0.52
        let lineHeight: CGFloat = 18
        let closingHeight: CGFloat = 30
        let cardHeight = CGFloat(document.summaryRows.count) * lineHeight + closingHeight
        fill(CGRect(x: cardLeft, y: y, width: right - cardLeft, height: cardHeight), accentSoft)
        fill(CGRect(x: cardLeft, y: y, width: 3.5, height: cardHeight), accent)

        var rowY = y
        for row in document.summaryRows {
            row.label.draw(at: CGPoint(x: cardLeft + 12, y: rowY + 4), withAttributes: attributes(bodySize, muted: true))
            drawRight(row.value.bracketed(row.deduction), rightEdge: right - 9, y: rowY + 4, attributes: attributes(bodySize))
            rowY += lineHeight
        }
        rule(from: CGPoint(x: cardLeft + 12, y: rowY), to: CGPoint(x: right - 9, y: rowY))
        document.closingLabel.uppercased()
            .draw(at: CGPoint(x: cardLeft + 12, y: rowY + 7), withAttributes: factLabel)
        drawRight(document.closingValue, rightEdge: right - 9, y: rowY + 7, attributes: accentText(15, bold: true))
        y = rowY + closingHeight + 22

        // MARK: The one thing a customer might otherwise get wrong
        document.footnote.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(7.5, muted: true))
        y += 26

        cut(at: y)
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

    /// The line the slip is torn along.
    ///
    /// Dashed rather than solid, and running the full width of the paper: a
    /// solid rule across a page is a divider between two things, and there is
    /// nothing below this one.
    private static func cut(at y: CGFloat) {
        let path = UIBezierPath()
        path.move(to: CGPoint(x: 0, y: y))
        path.addLine(to: CGPoint(x: pageSize.width, y: y))
        path.lineWidth = 0.9
        path.setLineDash([4, 4], count: 2, phase: 0)
        ruleColour.setStroke()
        path.stroke()
    }
}

private extension String {
    /// `(SAR 300)` — accounting brackets, the convention the shop's own supplier
    /// statements use. A bare minus in front of a currency symbol reads as a typo
    /// on paper.
    ///
    /// Its own copy rather than the statement's: that one is file-private, and
    /// widening it so two files can share four characters would make a helper
    /// nobody can move.
    func bracketed(_ deduction: Bool) -> String { deduction ? "(\(self))" : self }
}
