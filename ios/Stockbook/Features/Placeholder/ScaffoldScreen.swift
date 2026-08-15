import SwiftUI

/// A screen that has architecture behind it but no UI yet.
///
/// This is scaffolding for the foundations pass: rather than a blank view, each
/// unbuilt screen states what the handoff specifies for it, so the checklist
/// lives next to the code that has to satisfy it. Every one of these is deleted
/// as its screen lands — nothing here ships.
struct ScaffoldScreen: View {
    let title: String
    var subtitle: String?
    /// The requirements from `design_handoff_stockbook/README.md`.
    let requirements: [String]
    /// Where the data and rules this screen needs already live.
    var readyPieces: [String] = []

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: title, subtitle: subtitle)

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    section("Still to build", items: requirements, tint: Nocturne.neutral500)
                    if !readyPieces.isEmpty {
                        section("Already wired up", items: readyPieces, tint: Nocturne.accent400)
                    }
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 24)
            }
        }
    }

    private func section(_ heading: String, items: [String], tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Kicker(heading)
            ForEach(items, id: \.self) { item in
                HStack(alignment: .top, spacing: 9) {
                    Circle()
                        .fill(tint)
                        .frame(width: 4, height: 4)
                        .padding(.top, 7)
                    Text(item)
                        .nocturneText(.body)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(14)
        .nocturneCard()
    }
}
