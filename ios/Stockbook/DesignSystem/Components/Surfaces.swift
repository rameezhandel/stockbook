import SwiftUI

/// A small uppercase section label — "RECENT BILLS", "COMMON HARDWARE LINES".
struct Kicker: View {
    let text: String
    var tint: Color?

    init(_ text: String, tint: Color? = nil) {
        self.text = text
        self.tint = tint
    }

    var body: some View {
        Text(text)
            .nocturneText(.kicker)
            .foregroundStyle(tint ?? Nocturne.neutral500)
    }
}

/// The dashed container used for every empty state and the backup nudge.
struct DashedBox<Content: View>: View {
    var padding: EdgeInsets = EdgeInsets(top: 22, leading: 16, bottom: 22, trailing: 16)
    @ViewBuilder var content: Content

    var body: some View {
        content
            .frame(maxWidth: .infinity)
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                    .strokeBorder(
                        Nocturne.neutral800,
                        style: StrokeStyle(lineWidth: 1, dash: [4, 4])
                    )
            )
    }
}

/// The standard empty state: optional icon, a line of copy, one primary action.
struct EmptyStateBox: View {
    var icon: String?
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        DashedBox(padding: EdgeInsets(top: 26, leading: 18, bottom: 26, trailing: 18)) {
            VStack(spacing: 0) {
                if let icon {
                    Glyph(icon, size: 26)
                        .foregroundStyle(Nocturne.neutral600)   // icons only — too dark for text
                        .padding(.bottom, 9)
                }
                Text(message)
                    .nocturneText(.body)
                    .multilineTextAlignment(.center)
                if let actionTitle, let action {
                    Button(action: action) {
                        Label(actionTitle, systemImage: Icon.add)
                            .labelStyle(.titleAndIcon)
                    }
                    .buttonStyle(.primaryCompact)
                    .padding(.top, 12)
                }
            }
        }
    }
}

/// A stat card on Today. The "Sold today" card is the one gradient in the app.
struct StatCard: View {
    let label: String
    let value: String
    var gradient: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(value)
                .nocturneText(.bigNumber(26))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background {
            if gradient {
                Nocturne.soldTodayGradient
            } else {
                Nocturne.surface
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Metrics.statRadius, style: .continuous))
        .hairline(radius: Metrics.statRadius)
    }
}

/// A 1px rule that fades to transparent at both ends.
///
/// This is a Nocturne signature — the handoff calls it out explicitly and asks
/// for it to be preserved. Used on the receipt, between the lines and the total.
struct FadedRule: View {
    var inset: CGFloat = 24

    var body: some View {
        GeometryReader { proxy in
            let width = max(proxy.size.width, 1)
            let stop = min(inset / width, 0.5)
            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0),
                    .init(color: Nocturne.neutral800, location: stop),
                    .init(color: Nocturne.neutral800, location: 1 - stop),
                    .init(color: .clear, location: 1)
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
        }
        .frame(height: 1)
    }
}

/// The header block every screen opens with: a title on the left, an optional
/// kicker above it and a sub-line below, and one action on the right.
struct ScreenHeader<Trailing: View>: View {
    var kicker: String?
    var kickerTint: Color?
    let title: String
    var subtitle: String?
    var bottomPadding: CGFloat = 12
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 0) {
                if let kicker {
                    Kicker(kicker, tint: kickerTint ?? Nocturne.accent)
                        .padding(.bottom, 3)
                }
                Text(title).nocturneText(.screenTitle)
                if let subtitle {
                    Text(subtitle)
                        .nocturneText(.meta)
                        .padding(.top, 2)
                }
            }
            Spacer(minLength: 12)
            trailing
        }
        .padding(.horizontal, Metrics.screenPadding)
        .padding(.bottom, bottomPadding)
        .screenHeaderPadding()
    }
}

extension ScreenHeader where Trailing == EmptyView {
    init(kicker: String? = nil, kickerTint: Color? = nil, title: String, subtitle: String? = nil, bottomPadding: CGFloat = 12) {
        self.init(
            kicker: kicker,
            kickerTint: kickerTint,
            title: title,
            subtitle: subtitle,
            bottomPadding: bottomPadding,
            trailing: { EmptyView() }
        )
    }
}
