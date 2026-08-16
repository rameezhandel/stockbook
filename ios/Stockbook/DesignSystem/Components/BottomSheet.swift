import SwiftUI

/// Bottom sheets, drawn by the app rather than by `.sheet`.
///
/// The design is specific in ways the system sheet does not expose: an
/// `rgba(16,17,28,0.74)` scrim, 18px rounding on the **top corners only**, a
/// `0 -16px 40px rgba(0,0,0,0.65)` shadow, a 38×4 grab handle, and a max height
/// of 84%. Presenting it as an overlay inside the app's own root stack gives us
/// all of that and keeps the tab bar visible behind the scrim, which is what the
/// prototype does.
struct BottomSheetContainer<Content: View>: View {
    let onDismiss: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        ZStack(alignment: .bottom) {
            Nocturne.scrim
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: onDismiss)
                .transition(.opacity)

            VStack(spacing: 0) {
                Capsule()
                    .fill(Nocturne.neutral800)
                    .frame(width: 38, height: 4)
                    .padding(.top, 10)
                    .padding(.bottom, 12)

                ScrollView {
                    content
                        .padding(.horizontal, Metrics.screenPadding)
                        .padding(.bottom, 32)
                }
            }
            .frame(maxWidth: .infinity)
            .background(Nocturne.surface)
            .clipShape(TopRoundedRectangle(radius: Metrics.sheetRadius))
            .shadow(color: .black.opacity(0.65), radius: 20, x: 0, y: -16)
            .transition(.move(edge: .bottom))
        }
        .frame(maxHeight: .infinity, alignment: .bottom)
    }
}

/// Rounds the top two corners only — bottom sheets sit flush against the
/// bottom edge.
struct TopRoundedRectangle: Shape {
    let radius: CGFloat

    func path(in rect: CGRect) -> Path {
        Path(
            roundedRect: rect,
            cornerRadii: RectangleCornerRadii(topLeading: radius, bottomLeading: 0, bottomTrailing: 0, topTrailing: radius),
            style: .continuous
        )
    }
}

/// The title row every sheet opens with: heading, optional sub-line, close `x`.
struct SheetHeader: View {
    let title: String
    var subtitle: String?
    let onClose: () -> Void

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).nocturneText(.sheetTitle)
                if let subtitle {
                    Text(subtitle).nocturneText(.meta)
                }
            }
            Spacer(minLength: 12)
            Button(action: onClose) {
                Glyph(Icon.close, size: 16)
                    .foregroundStyle(Nocturne.neutral500)
                    .minimumTouchTarget()
            }
            .buttonStyle(.plain)
        }
        .padding(.bottom, 14)
    }
}

extension View {
    /// Presents `content` as a Nocturne bottom sheet whenever `item` is non-nil.
    func nocturneSheet<Item: Identifiable, SheetContent: View>(
        item: Binding<Item?>,
        @ViewBuilder content: @escaping (Item) -> SheetContent
    ) -> some View {
        overlay {
            ZStack {
                if let value = item.wrappedValue {
                    BottomSheetContainer(onDismiss: { item.wrappedValue = nil }) {
                        content(value)
                    }
                }
            }
            .animation(Metrics.sheet, value: item.wrappedValue != nil)
        }
    }
}
