import SwiftUI

/// A date the owner can change: a label, the date itself, and a calendar behind
/// a tap.
///
/// This replaced `DatePicker(.compact)` on the three forms that record a
/// document — the bill, the receipt, the credit note. The system control draws
/// its own label, in whatever shape the phone's region settings ask for, so the
/// same bill read `13/08/2026` on one phone, `8/13/2026` on another, and
/// `2026/08/13` on a third. None of them is wrong and none of them is the app.
/// The text here is ours, from `Loc.pickedDate`, which is the same function the
/// Android build calls — so both apps show `Aug 13, 2026` and neither asks the
/// phone what a date looks like.
///
/// The calendar is still Apple's, presented as a popover anchored to the chip.
/// `.presentationCompactAdaptation(.popover)` is what keeps it a popover on a
/// phone; without it SwiftUI would slide a full sheet up over a form the owner
/// is halfway through typing.
struct NocturneDateField: View {
    let label: String
    @Binding var date: Date
    /// Matched to the field it stands beside, which is 40 on three of the four
    /// forms and `Metrics.inputHeight` on the delivery sheet.
    var height: CGFloat = 40
    /// So UI tests can find a field whose visible label is a separate view.
    var identifier: String?

    @State private var picking = false

    var body: some View {
        // Read once, here, rather than inside the button's builder: `Loc` is
        // main-actor isolated and the enclosing `body` is the one place that is
        // certainly on it.
        let shown = Loc.pickedDate(date)

        VStack(alignment: .leading, spacing: 5) {
            Text(label).nocturneText(.fieldLabel)

            Button {
                picking = true
            } label: {
                Text(shown)
                    .font(NocturneType.inter(13))
                    .foregroundStyle(Nocturne.accent)
                    .lineLimit(1)
                    // A long month name in Kannada should shrink rather than
                    // truncate: half a date is worse than a small one.
                    .minimumScaleFactor(0.75)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 10)
                    // The same box `NocturneField` draws, because this sits
                    // beside one on all four forms and a bare line of text next
                    // to a bordered box reads as a label rather than something
                    // that can be tapped.
                    .frame(height: height)
                    .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                    .hairline(Nocturne.neutral800, radius: Metrics.controlRadius)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(label)
            .accessibilityValue(shown)
            .accessibilityIdentifier(identifier ?? "")
            .popover(isPresented: $picking) {
                DatePicker("", selection: $date, displayedComponents: .date)
                    .labelsHidden()
                    .datePickerStyle(.graphical)
                    .tint(Nocturne.accent)
                    .padding(12)
                    .frame(minWidth: 320, minHeight: 340)
                    .presentationCompactAdaptation(.popover)
            }
        }
    }
}
