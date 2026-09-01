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
    /// The date and the reference are left-aligned; the three money columns are
    /// right-aligned against the edges below, which is how a column of figures is
    /// read: by the units lining up.
    private static let colDate: CGFloat = 0
    private static let colReference: CGFloat = 0.16
    private static let edgeCharge: CGFloat = 0.62
    private static let edgeSettled: CGFloat = 0.81
    private static let edgeBalance: CGFloat = 1.0

    private static let ink = UIColor(red: 0.078, green: 0.078, blue: 0.110, alpha: 1)
    private static let grey = UIColor(red: 0.420, green: 0.420, blue: 0.463, alpha: 1)
    /// The hairline, darker than it looks like it needs to be: these pages get
    /// photocopied, and a 16%-grey rule is the first thing a copier loses.
    private static let ruleColour = UIColor(red: 0.769, green: 0.769, blue: 0.816, alpha: 1)

    /// The heavy rule the monochrome treatment uses in place of the band.
    private static func heavyRule(from: CGPoint, to: CGPoint) {
        let path = UIBezierPath()
        path.move(to: from)
        path.addLine(to: to)
        path.lineWidth = 1.6
        ink.setStroke()
        path.stroke()
    }

    /// The app's own violet, carried onto the paper.
    ///
    /// Somebody who has seen the phone recognises the page. It also costs
    /// colour: on a mono printer the band becomes a heavy grey slab and the
    /// tinted card goes pale but survives, which is the trade this treatment
    /// makes.
    private static let accent = UIColor(red: 0.361, green: 0.310, blue: 0.769, alpha: 1)
    /// The same violet at a tenth, for the heading row and the totals card.
    private static let accentSoft = UIColor(red: 0.929, green: 0.918, blue: 0.984, alpha: 1)

    private static func reversed(_ size: CGFloat, alpha: CGFloat = 1) -> [NSAttributedString.Key: Any] {
        [.font: UIFont.boldSystemFont(ofSize: size), .foregroundColor: UIColor.white.withAlphaComponent(alpha)]
    }

    private static func accentText(_ size: CGFloat, bold: Bool = false) -> [NSAttributedString.Key: Any] {
        [
            .font: bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size),
            .foregroundColor: accent
        ]
    }

    private static func fill(_ rect: CGRect, _ colour: UIColor) {
        colour.setFill()
        UIBezierPath(rect: rect).fill()
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
    static func write(_ document: StatementDocument, fileName: String) throws -> URL {
        try write([document], fileName: fileName)
    }

    /// Several statements into one file, **each starting on a fresh page**.
    ///
    /// This is the ledger book: every customer's own sheet, one after another, so
    /// a page can be pulled out and filed on its own. Written as a loop over the
    /// single-statement drawing rather than as a second layout — a book whose
    /// pages did not match the statement the customer was handed would be two
    /// documents claiming to be one, and the first correction to either would
    /// separate them for good.
    /// - Parameter colour: whether to spend colour on it. A statement is one
    ///   sheet handed to one customer, and the band is worth its toner there. The
    ///   ledger book is a hundred of them printed at once and filed, so it takes
    ///   the monochrome treatment: the same page, drawn with a rule where the
    ///   band would be and the balance reversed out of black rather than sitting
    ///   in a tint. Same routine, same geometry — a sheet pulled from the book is
    ///   still the statement, just cheaper.
    static func write(_ documents: [StatementDocument], fileName: String, colour: Bool = true) throws -> URL {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)

        try renderer.writePDF(to: url) { context in
            for document in documents {
                draw(document, in: context, colour: colour)
            }
        }

        return url
    }

    /// Draws one statement, starting a fresh page for it.
    private static func draw(_ document: StatementDocument, in context: UIGraphicsPDFRendererContext, colour: Bool) {
        context.beginPage()

        let right = pageSize.width - margin
        let width = right - margin
        var y = margin

        // MARK: The band
        //
        // Full bleed to the paper's edge, not inset to the margin: an inset
        // colour block reads as a box somebody drew, where a band that runs off
        // both sides reads as the head of the page.
        //
        // Sans throughout, deliberately. A serif here would set the shop's own
        // name — which the owner types, and which may be in Arabic or Kannada —
        // in a face whose coverage of those scripts is patchy, and the failure is
        // tofu boxes in the largest text on the page.

        if colour {
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
            drawRight(document.periodValue, rightEdge: right, y: 30, attributes: reversed(11))
            y = bandHeight + 24
        } else {
            document.shopName.draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(titleSize, bold: true))
            drawRight(document.docType, rightEdge: right, y: y + 3, attributes: attributes(13))
            y += titleSize + 6
            document.shopAddressLines.joined(separator: ", ")
                .draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(7.5, muted: true))
            y += 14
            heavyRule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
            y += 18
        }

        // MARK: The two boxed facts — whose account, and over what

        let factsTop = y
        let factsHeight: CGFloat = 46
        let middle = margin + width / 2
        stroke(CGRect(x: margin, y: factsTop, width: width, height: factsHeight))
        rule(from: CGPoint(x: middle, y: factsTop), to: CGPoint(x: middle, y: factsTop + factsHeight))

        let factLabel = colour ? accentText(7.5, bold: true) : attributes(7.5, muted: true)
        document.accountLabel.uppercased().draw(at: CGPoint(x: margin + 10, y: factsTop + 5), withAttributes: factLabel)
        document.partyName.draw(at: CGPoint(x: margin + 10, y: factsTop + 17), withAttributes: attributes(bodySize, bold: true))
        document.partyLines.joined(separator: " · ").draw(at: CGPoint(x: margin + 10, y: factsTop + 30), withAttributes: attributes(8, muted: true))

        document.periodLabel.uppercased().draw(at: CGPoint(x: middle + 10, y: factsTop + 5), withAttributes: factLabel)
        document.periodValue.draw(at: CGPoint(x: middle + 10, y: factsTop + 17), withAttributes: attributes(bodySize, bold: true))
        document.summaryTitle.draw(at: CGPoint(x: middle + 10, y: factsTop + 30), withAttributes: attributes(8, muted: true))

        y = factsTop + factsHeight + 24

        // MARK: The activity table

        document.activityTitle.uppercased().draw(at: CGPoint(x: margin, y: y), withAttributes: attributes(8, muted: true))
        y += 14

        func headings() {
            // Set on a tinted row rather than over a rule: the band at the top
            // has already said this page uses colour, and a second heavy black
            // rule would be a different page's idea.
            if colour { fill(CGRect(x: margin, y: y - 3, width: width, height: 15), accentSoft) }
            let head = attributes(7.5, muted: true)
            document.columnHeadings[0].uppercased().draw(at: CGPoint(x: margin + width * colDate, y: y), withAttributes: head)
            document.columnHeadings[1].uppercased().draw(at: CGPoint(x: margin + width * colReference, y: y), withAttributes: head)
            drawRight(document.columnHeadings[2].uppercased(), rightEdge: margin + width * edgeCharge, y: y, attributes: head)
            drawRight(document.columnHeadings[3].uppercased(), rightEdge: margin + width * edgeSettled, y: y, attributes: head)
            drawRight(document.columnHeadings[4].uppercased(), rightEdge: margin + width * edgeBalance, y: y, attributes: head)
            y += 17
            if !colour { heavyRule(from: CGPoint(x: margin, y: y - 4), to: CGPoint(x: right, y: y - 4)) }
        }
        headings()

        for row in document.activityRows {
            // A break before a row rather than through one, and the headings
            // repeat so a second page stands on its own. The room kept back is
            // for the totals block.
            if y > pageSize.height - margin - 130 {
                context.beginPage()
                y = margin
                headings()
            }

            // Exactly one of the two money columns carries anything, so the empty
            // one draws nothing at all rather than a dash or a zero: an empty cell
            // is unambiguous, and a zero in the Received column is a payment
            // somebody might go looking for.
            let body = attributes(rowSize)
            row.date.draw(at: CGPoint(x: margin + width * colDate, y: y), withAttributes: attributes(rowSize, muted: true))
            row.reference.draw(at: CGPoint(x: margin + width * colReference, y: y), withAttributes: body)
            drawRight(row.charge, rightEdge: margin + width * edgeCharge, y: y, attributes: body)
            drawRight(row.settled, rightEdge: margin + width * edgeSettled, y: y, attributes: body)
            drawRight(row.balance, rightEdge: margin + width * edgeBalance, y: y, attributes: body)
            y += 17
            rule(from: CGPoint(x: margin, y: y), to: CGPoint(x: right, y: y))
            y += 2
        }

        // MARK: The totals, set against the right edge under the money columns

        y += 12
        let totalsLeft = margin + width * 0.52
        let lineHeight: CGFloat = 18
        var totalY = y
        stroke(CGRect(x: totalsLeft, y: totalY, width: right - totalsLeft, height: CGFloat(document.summaryRows.count) * lineHeight))
        for (index, row) in document.summaryRows.enumerated() {
            row.label.draw(at: CGPoint(x: totalsLeft + 9, y: totalY + 4), withAttributes: attributes(bodySize, muted: true))
            drawRight(row.value.bracketed(row.deduction), rightEdge: right - 9, y: totalY + 4, attributes: attributes(bodySize))
            totalY += lineHeight
            if index < document.summaryRows.count - 1 {
                rule(from: CGPoint(x: totalsLeft, y: totalY), to: CGPoint(x: right, y: totalY))
            }
        }

        // The one figure the reader came for, in the accent and on a tint, with a
        // solid bar down its left edge so it still reads as the end of the column
        // when the page is photocopied and the tint goes.
        let dueHeight: CGFloat = 28
        if colour {
            fill(CGRect(x: totalsLeft, y: totalY, width: right - totalsLeft, height: dueHeight), accentSoft)
            fill(CGRect(x: totalsLeft, y: totalY, width: 3.5, height: dueHeight), accent)
            document.closingLabel.uppercased().draw(at: CGPoint(x: totalsLeft + 12, y: totalY + 6), withAttributes: accentText(7.5, bold: true))
            drawRight(document.closingValue, rightEdge: right - 9, y: totalY + 7, attributes: accentText(13, bold: true))
        } else {
            fill(CGRect(x: totalsLeft, y: totalY, width: right - totalsLeft, height: dueHeight), ink)
            document.closingLabel.uppercased().draw(at: CGPoint(x: totalsLeft + 12, y: totalY + 6), withAttributes: reversed(7.5))
            drawRight(document.closingValue, rightEdge: right - 9, y: totalY + 7, attributes: reversed(13))
        }

        // MARK: Footer — the address, and the shop it came from

        let footY = pageSize.height - margin - 10
        rule(from: CGPoint(x: margin, y: footY - 6), to: CGPoint(x: right, y: footY - 6))
        document.partyName.draw(at: CGPoint(x: margin, y: footY), withAttributes: attributes(7.5, muted: true))
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
        path.lineWidth = 0.9
        ruleColour.setStroke()
        path.stroke()
    }

    private static func stroke(_ rect: CGRect) {
        let path = UIBezierPath(rect: rect)
        path.lineWidth = 0.9
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
