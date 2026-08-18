import SwiftUI

/// The setup-time twin of the import card on the Settings backup screen.
///
/// A shop moving to a new phone should not have to re-type its name, currency,
/// stock and bills by hand just because it happens to be starting fresh — the
/// file it already has is the fastest way through setup there is. Confirming
/// here calls the same `StockbookStore.replaceEverything` the Settings screen
/// does, which — as a fresh, never-set-up store — carries the owner straight
/// past the rest of these screens: `replaceEverything` marks setup complete as
/// part of rebuilding `Settings`, and `RootView` is watching that flag.
///
/// No "this replaces what's here" warning, unlike the Settings version: at this
/// point in setup there is nothing yet to lose.
struct SetupImportSheet: View {
    let importFlow: ImportFlow
    let onChooseFile: () -> Void
    let onClose: () -> Void

    @Environment(StockbookStore.self) private var store

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(title: Loc.importABackupFile, onClose: onClose)

            Text(importNote)
                .font(NocturneType.inter(12))
                .foregroundStyle(importFlow.stage.isFailure ? Nocturne.accent300 : Nocturne.neutral500)
                .lineSpacing(3)
                .padding(.bottom, 11)

            if case .picked(let document, let filename) = importFlow.stage {
                VStack(alignment: .leading, spacing: 0) {
                    Text(filename)
                        .font(NocturneType.inter(13))
                        .lineLimit(1)
                        .truncationMode(.middle)
                        .padding(.bottom, 7)

                    Text(document.summaryLine(Loc))
                        .font(NocturneType.inter(11.5))
                        .foregroundStyle(Nocturne.neutral500)
                        .lineSpacing(4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(11)
                .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(Nocturne.accent, radius: Metrics.controlRadius)
                .padding(.bottom, 10)

                HStack(spacing: 8) {
                    Button(Loc.cancel) { importFlow.cancel() }
                        .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
                    Button(Loc.useThisBackup) {
                        // Only ever acts on what confirm() hands back.
                        guard let confirmed = importFlow.confirm() else { return }
                        store.replaceEverything(with: confirmed)
                    }
                    .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
                }
            } else {
                Button { onChooseFile() } label: {
                    Label(Loc.chooseAFile, systemImage: Icon.folder)
                }
                .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var importNote: String {
        switch importFlow.stage {
        case .imported: Loc.importNoteDone
        case .failed(let error): Loc.backupError(error)
        default: Loc.importNoteIdle
        }
    }
}
