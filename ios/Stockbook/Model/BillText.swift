import Foundation

/// A bill as something you can send somebody.
///
/// Plain text, for the same reason a statement is plain text: it goes into
/// WhatsApp, which every customer here already has, and it survives being pasted
/// anywhere. A PDF would look more like a document and be worse at being one —
/// it needs an app to open, a file to keep, and it cannot be read in the message
/// list.
///
/// The same figures the screen shows, in the same order, taken from the same
/// snapshot: what a customer is sent must not be able to disagree with what the
/// owner is looking at.
enum BillText {

    static func plainText(_ bill: Bill, shopName: String, currency: Currency, strings: Strings) -> String {
        var lines: [String] = []

        if !shopName.isBlank { lines.append(shopName) }
        lines.append(bill.reference(strings))
        lines.append(strings.billWhen(date: strings.longDate(bill.createdAt), time: strings.time(bill.createdAt)))
        if !bill.who.isBlank { lines.append(strings.billedTo(bill.who)) }
        lines.append("")

        for line in bill.lines {
            // The arithmetic stays visible, as it does on the document: a query
            // about a total is nearly always a query about one line.
            let quantity = strings.quantityAtPrice(quantity: line.qty, price: Money.text(line.price, in: currency))
            lines.append("\(line.name)  \(quantity)  \(Money.text(line.lineTotal, in: currency))")
        }

        lines.append("")
        lines.append("\(strings.total): \(Money.text(bill.total, in: currency))")

        if bill.voided {
            // A voided bill owes nothing, and says so before it says anything
            // about money — otherwise sending one reads as a demand.
            lines.append(strings.voidedNote)
        } else if let paid = bill.paid {
            lines.append(strings.partPaidNote(
                paid: Money.text(paid, in: currency),
                who: bill.who,
                balance: Money.text(bill.balance, in: currency)
            ))
        } else {
            lines.append(strings.paidInFullCash)
        }

        return lines.joined(separator: "\n")
    }
}
