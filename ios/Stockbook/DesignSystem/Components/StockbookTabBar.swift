import SwiftUI

/// The four-tab bar. Hand-built rather than a `TabView` because the design
/// specifies the exact metrics — surface ground, a single top hairline (no
/// blur material), 22pt icons over a 10.5pt label, filled icon plus accent on
/// the active tab. Settings is deliberately *not* a tab.
struct StockbookTabBar: View {
    @Binding var selection: AppTab
    @Environment(\.bottomSafeInset) private var bottomInset

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases) { tab in
                Button {
                    selection = tab
                } label: {
                    VStack(spacing: 3) {
                        Glyph(selection == tab ? tab.activeIcon : tab.icon, size: 22)
                        Text(tab.title).nocturneText(.tabLabel)
                    }
                    .foregroundStyle(selection == tab ? Nocturne.accent : Nocturne.neutral500)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("tab.\(tab.rawValue)")
                .accessibilityAddTraits(selection == tab ? [.isSelected, .isButton] : .isButton)
            }
        }
        .padding(.top, 6)
        // The design asks for 24pt below the labels to clear the home indicator.
        // The device's own bottom inset does that job exactly, so we defer to it
        // and fall back to 24 on hardware without one.
        .padding(.bottom, max(bottomInset, 24))
        .background(Nocturne.surface)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Nocturne.neutral800)
                .frame(height: 1)
        }
    }
}

enum AppTab: String, CaseIterable, Identifiable {
    case today, items, sell, bills

    var id: String { rawValue }

    var title: String {
        switch self {
        case .today: "Today"
        case .items: "Items"
        case .sell: "Sell"
        case .bills: "Bills"
        }
    }

    var icon: String {
        switch self {
        case .today: Icon.today
        case .items: Icon.items
        case .sell: Icon.sell
        case .bills: Icon.bills
        }
    }

    var activeIcon: String {
        switch self {
        case .today: Icon.todayActive
        case .items: Icon.itemsActive
        case .sell: Icon.sellActive
        case .bills: Icon.billsActive
        }
    }
}
