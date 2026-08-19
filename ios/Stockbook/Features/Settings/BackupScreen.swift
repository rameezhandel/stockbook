import SwiftUI
import UniformTypeIdentifiers

/// The export/import handoff — the app's only route onto a new phone.
///
/// Lifted out of Settings, which had grown into one long scroll where the two
/// things an owner changes weekly sat above the two they use once a year. This
/// is the once-a-year half, behind a row.
///
/// The prototype faked the file layer. This does not: export writes a real dated
/// JSON file through the document exporter, Share hands the same file to the OS
/// share sheet, and import reads a file the owner picked, **validates it before
/// asking anything**, and only then offers to replace the database.
struct BackupScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    // Export
    @State private var exportDocument: BackupFile?
    @State private var isExporting = false
    @State private var exportChip: (name: String, detail: String)?
    @State private var shareURL: URL?
    @State private var seeded = false

    // Import
    @State private var isImporting = false
    @State private var importFlow = ImportFlow()

    private var settings: Settings { store.settings }
    private var products: [Product] { store.products }
    private var bills: [Bill] { store.bills }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Loc.moveToAnotherPhone) {
                Button(Loc.done) { router.showingBackup = false }
                    .buttonStyle(GhostButtonStyle(fontSize: 12.5))
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(Loc.moveToAnotherPhoneNote)
                        .font(NocturneType.inter(12.5))
                        .foregroundStyle(Nocturne.neutral500)
                        .lineSpacing(3)
                        .padding(.bottom, 14)

                    exportCard.padding(.bottom, 10)
                    importCard
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 18)
            }
        }
        .background(Nocturne.bg.ignoresSafeArea())
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .onAppear(perform: seed)
        .fileExporter(
            isPresented: $isExporting,
            document: exportDocument,
            contentType: .json,
            defaultFilename: exportDocument?.document.suggestedFilename
        ) { result in
            // Only a real write counts. A cancelled save sheet must not claim a
            // backup exists — that is the one lie this screen must never tell.
            if case .success = result {
                store.markExported()
                refreshExportChip()
            }
        }
        .fileImporter(
            isPresented: $isImporting,
            allowedContentTypes: [.json]
        ) { result in
            importFlow.pick(result)
        }
    }

    // MARK: Export

    private var exportCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            cardHeading(icon: Icon.export, title: Loc.exportEverything)

            Text(exportNote)
                .font(NocturneType.inter(12))
                .foregroundStyle(Nocturne.neutral500)
                .lineSpacing(3)
                .padding(.bottom, 11)

            if let chip = exportChip {
                HStack(spacing: 10) {
                    Glyph(Icon.file, size: 19)
                        .foregroundStyle(Nocturne.accent400)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(chip.name)
                            .font(NocturneType.inter(13))
                            .lineLimit(1)
                            .truncationMode(.middle)
                        Text(chip.detail)
                            .font(NocturneType.inter(11))
                            .foregroundStyle(Nocturne.neutral500)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 11)
                .padding(.vertical, 10)
                .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(radius: Metrics.controlRadius)
                .padding(.bottom, 10)
            }

            HStack(spacing: 8) {
                Button(settings.hasBackup ? Loc.writeAFreshFile : Loc.createBackupFile) {
                    exportDocument = BackupFile(document: store.makeBackupDocument())
                    isExporting = true
                }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))

                if let shareURL {
                    ShareLink(item: shareURL) {
                        Label(Loc.share, systemImage: Icon.share)
                    }
                    .buttonStyle(SecondaryButtonStyle(height: 42, fontSize: 13.5))
                }
            }
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
    }

    // MARK: Import

    private var importCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            cardHeading(icon: Icon.importFile, title: Loc.importABackupFile)

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

                    // Naming what is about to be lost, in the owner's own
                    // numbers. This is the last thing standing between a tap and
                    // an unrecoverable swap.
                    Text(Loc.replaceWarning(productCount: products.count, billCount: bills.count))
                        .font(NocturneType.inter(11.5))
                        .foregroundStyle(Nocturne.accent300)
                        .lineSpacing(3)
                        .padding(.top, 8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(11)
                .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(Nocturne.accent, radius: Metrics.controlRadius)
                .padding(.bottom, 10)

                HStack(spacing: 8) {
                    Button(Loc.cancel) { importFlow.cancel() }
                        .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
                    Button(Loc.replaceEverything) {
                        // Only ever acts on what confirm() hands back.
                        guard let confirmed = importFlow.confirm() else { return }
                        store.replaceEverything(with: confirmed)
                        // A swap, not a merge: every photograph this phone was
                        // holding belonged to the book that was just replaced.
                        // Only ids the incoming book names survive — and an id
                        // it names that this phone lacks is left waiting, not
                        // tidied away.
                        PhotoStore().sweep(keeping: store.photoIDsInUse())
                        refreshExportChip()
                    }
                    .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
                }
            } else {
                Button { isImporting = true } label: {
                    Label(Loc.chooseAFile, systemImage: Icon.folder)
                }
                .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
            }
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
    }

    private func cardHeading(icon: String, title: String) -> some View {
        HStack(spacing: 9) {
            Glyph(icon, size: 17).foregroundStyle(Nocturne.accent)
            Text(title).nocturneText(.rowPrimary)
            Spacer(minLength: 0)
        }
        .padding(.bottom, 4)
    }

    // MARK: Copy

    private var exportNote: String {
        settings.hasBackup ? Loc.exportNoteAfterBackup : Loc.exportNoteFirstTime
    }

    private var importNote: String {
        switch importFlow.stage {
        case .imported: Loc.importNoteDone
        case .failed(let error): Loc.backupError(error)
        default: Loc.importNoteIdle
        }
    }

    // MARK: Actions

    private func seed() {
        guard !seeded else { return }
        seeded = true
        refreshExportChip()
    }

    /// Encoding the whole database is cheap at this size but not free, so the
    /// chip is computed on demand rather than in `body`.
    private func refreshExportChip() {
        guard let exportedAt = settings.lastExportAt else {
            exportChip = nil
            shareURL = nil
            return
        }
        let document = store.makeBackupDocument(at: exportedAt)
        exportChip = (
            name: document.suggestedFilename,
            detail: [
                Loc.products(document.products.count),
                Loc.bills(document.bills.count),
                Loc.fileSize(kilobytes: BackupService.sizeInKilobytes(of: document))
            ].joined(separator: " · ")
        )
        // Share hands the OS a real file, so one has to exist on disk.
        shareURL = try? BackupService.writeToTemporaryFile(document)
    }
}
