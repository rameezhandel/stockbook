import SwiftUI
import UniformTypeIdentifiers

/// Settings, and the export/import handoff that is the app's only route onto a
/// new phone.
///
/// The prototype faked the file layer. This does not: export writes a real dated
/// JSON file through the document exporter, Share hands the same file to the OS
/// share sheet, and import reads a file the owner picked, **validates it before
/// asking anything**, and only then offers to replace the database.
struct SettingsScreen: View {
    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    @State private var ownerName = ""
    @State private var seeded = false

    // Export
    @State private var exportDocument: BackupFile?
    @State private var isExporting = false
    @State private var exportChip: (name: String, detail: String)?
    @State private var shareURL: URL?

    // Import
    @State private var isImporting = false
    @State private var importFlow = ImportFlow()

    private var settings: Settings { store.settings }
    private var products: [Product] { store.products }
    private var bills: [Bill] { store.bills }
    private var liveBills: [Bill] { store.liveBills }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "Settings") {
                Button("Done") { router.showingSettings = false }
                    .buttonStyle(GhostButtonStyle(fontSize: 12.5))
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    thisPhone
                    moveToAnotherPhone
                    startAgain
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

    // MARK: This phone

    private var thisPhone: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker("This phone").padding(.bottom, 8)

            VStack(alignment: .leading, spacing: 10) {
                NocturneField(
                    label: "Business owner",
                    placeholder: "Business owner name",
                    text: $ownerName
                )
                .onChange(of: ownerName) { _, new in
                    store.setOwnerName(new)
                }

                HStack(spacing: 10) {
                    stat("Products", products.count)
                    stat("Bills", liveBills.count)
                    stat("Customers", store.customers().count)
                }
            }
            .padding(12)
            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
            .padding(.bottom, 20)
        }
    }

    private func stat(_ label: String, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label)
                .font(NocturneType.inter(11))
                .foregroundStyle(Nocturne.neutral500)
            Text(String(value)).font(NocturneType.inter(17))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Move to another phone

    private var moveToAnotherPhone: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker("Move to another phone").padding(.bottom, 8)

            Text("Stockbook never uploads anything, so a new phone gets your shop from a file you carry across. Export here, then import on the other phone.")
                .font(NocturneType.inter(12.5))
                .foregroundStyle(Nocturne.neutral500)
                .lineSpacing(3)
                .padding(.bottom, 10)

            exportCard.padding(.bottom, 10)
            importCard.padding(.bottom, 20)
        }
    }

    private var exportCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            cardHeading(icon: Icon.export, title: "Export everything")

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
                Button(settings.hasBackup ? "Write a fresh file" : "Create backup file") {
                    exportDocument = BackupFile(document: store.makeBackupDocument())
                    isExporting = true
                }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))

                if let shareURL {
                    ShareLink(item: shareURL) {
                        Label("Share", systemImage: Icon.share)
                    }
                    .buttonStyle(SecondaryButtonStyle(height: 42, fontSize: 13.5))
                }
            }
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
    }

    private var importCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            cardHeading(icon: Icon.importFile, title: "Import a backup file")

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

                    Text(document.summaryLine)
                        .font(NocturneType.inter(11.5))
                        .foregroundStyle(Nocturne.neutral500)
                        .lineSpacing(4)

                    // Naming what is about to be lost, in the owner's own
                    // numbers. This is the last thing standing between a tap and
                    // an unrecoverable swap.
                    Text("This replaces the \(replacementSummary) already on this phone. It cannot be undone.")
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
                    Button("Cancel") { importFlow.cancel() }
                        .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
                    Button("Replace everything") {
                        // Only ever acts on what confirm() hands back.
                        guard let confirmed = importFlow.confirm() else { return }
                        store.replaceEverything(with: confirmed)
                        seeded = false
                        seed()
                        refreshExportChip()
                    }
                    .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
                }
            } else {
                Button { isImporting = true } label: {
                    Label("Choose a file", systemImage: Icon.folder)
                }
                .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
            }
        }
        .padding(13)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
    }

    // MARK: Start again

    private var startAgain: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kicker("Start again").padding(.bottom, 8)

            Text("Clears every product, price and bill on this phone and runs setup from the beginning.")
                .font(NocturneType.inter(12.5))
                .foregroundStyle(Nocturne.neutral500)
                .lineSpacing(3)
                .padding(.bottom, 10)

            Button("Start over") {
                store.startOver()
                router.closeOverlays()
                router.showingSettings = false
                router.tab = .today
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 42, fontSize: 13.5))
        }
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
        settings.hasBackup
            ? "Written to Files. Send it to the other phone however you like — AirDrop, WhatsApp, a memory card."
            : "Writes one file with every product, price, stock count and bill."
    }

    private var importNote: String {
        switch importFlow.stage {
        case .imported:
            "Imported. Everything from that file is now on this phone."
        case .failed(let message):
            message
        default:
            "Pick a file exported from another phone. Its contents take over from what is here now."
        }
    }

    /// `1 product and 0 bills`
    private var replacementSummary: String {
        "\(Copy.count(products.count, "product")) and \(Copy.count(bills.count, "bill"))"
    }

    // MARK: Actions

    private func seed() {
        guard !seeded else { return }
        seeded = true
        ownerName = settings.ownerName
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
            detail: "\(Copy.count(document.products.count, "product")) · \(Copy.count(document.bills.count, "bill")) · \(BackupService.sizeLabel(for: document))"
        )
        // Share hands the OS a real file, so one has to exist on disk.
        shareURL = try? BackupService.writeToTemporaryFile(document)
    }

}
