import SwiftUI
import PhotosUI

/// The strip of photographs under a bill.
///
/// Thumbnails rather than a list of file names, because the whole point of a
/// photograph is that it is recognised at a glance. Tapping one opens it full
/// screen; there is no second level of navigation for a picture of a receipt.
struct PhotoStrip: View {
    let ids: [String]
    let onOpen: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(ids, id: \.self) { id in
                    PhotoThumbnail(id: id)
                        .onTapGesture { onOpen(id) }
                }
            }
        }
    }
}

private struct PhotoThumbnail: View {
    let id: String

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous)
                .fill(Nocturne.surface)

            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                // A book can arrive on a phone ahead of its pictures. Saying so
                // is the honest answer; an empty square is one the owner has to
                // guess at.
                MissingPhoto()
            }
        }
        .frame(width: 76, height: 92)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(radius: Metrics.controlRadius)
        .task(id: id) { image = await PhotoLoader.load(id: id, edge: 320) }
    }
}

private struct MissingPhoto: View {
    var body: some View {
        VStack(spacing: 4) {
            Glyph(Icon.items, size: 14).foregroundStyle(Nocturne.neutral500)
            Text(Loc.photoNotOnThisPhone)
                .nocturneText(.meta)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 6)
    }
}

/// One photograph, filling the sheet.
///
/// Pinch to zoom and drag to move, because the reason to open a picture of a bill
/// is almost always to read something small on it. Nothing else: no captions, no
/// editing, no filters. It is a photograph of a piece of paper.
struct PhotoViewer: View {
    let id: String
    let onRemove: () -> Void
    let onClose: () -> Void

    @State private var image: UIImage?
    @State private var scale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var confirmingRemoval = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(Loc.billPhotos)
                    .font(NocturneType.inter(15))
                    .foregroundStyle(Nocturne.text)
                Spacer()
                // The file itself, not a rendering of it. Whatever the owner
                // picks in the share sheet receives a JPEG of the paper bill,
                // and the app makes no network call to send it.
                ShareLink(item: PhotoStore().url(id: id)) { Glyph(Icon.share, size: 16) }
                    .accessibilityLabel(Loc.share)
                Button(action: onClose) { Glyph(Icon.close, size: 16) }
                    .buttonStyle(.plain)
                    .padding(.leading, 12)
                    .accessibilityLabel(Loc.done)
            }
            .foregroundStyle(Nocturne.text)
            .padding(.bottom, 10)

            ZStack {
                Color.black
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .scaleEffect(scale)
                        .offset(offset)
                        .gesture(zoom.simultaneously(with: drag))
                } else {
                    MissingPhoto()
                }
            }
            .frame(height: 420)
            .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))

            Button(confirmingRemoval ? Loc.tapAgainToRemove : Loc.removePhoto) {
                if confirmingRemoval { onRemove() } else { confirmingRemoval = true }
            }
            .buttonStyle(GhostButtonStyle(
                fontSize: 12.5,
                tint: confirmingRemoval ? Nocturne.accent400 : Nocturne.neutral500
            ))
            .frame(maxWidth: .infinity)
            .padding(.top, 10)
        }
        .task(id: id) { image = await PhotoLoader.load(id: id) }
    }

    /// Floored at 1: a photograph smaller than its own frame is a picture nobody
    /// asked for. Capped at 5, past which a stored photograph has no more detail
    /// to give.
    private var zoom: some Gesture {
        MagnifyGesture()
            .onChanged { scale = max(1, min(5, $0.magnification)) }
            .onEnded { _ in
                if scale <= 1 {
                    // Back to the middle on the way out, so the next pinch does
                    // not start somewhere the owner did not leave it.
                    scale = 1
                    offset = .zero
                }
            }
    }

    private var drag: some Gesture {
        DragGesture()
            .onChanged { value in
                guard scale > 1 else { return }
                offset = value.translation
            }
    }
}

/// Reads a stored photograph off the main thread.
///
/// `edge` is what the caller is about to draw into. A row of thumbnails asking
/// for full-size images would hold tens of megabytes to draw a strip of postage
/// stamps, so the decoder is told what is actually wanted and does the throwing
/// away itself.
enum PhotoLoader {
    static func load(id: String, edge: Int = PhotoPolicy.maxEdge) async -> UIImage? {
        let url = PhotoStore().url(id: id)
        return await Task.detached(priority: .userInitiated) {
            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: edge
            ]
            guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
                return nil
            }
            return UIImage(cgImage: cg)
        }.value
    }
}

/// The camera, wrapped just enough to hand back one photograph.
///
/// `PhotosPicker` covers the gallery natively; the camera has no SwiftUI
/// equivalent, so this is the one place a `UIViewController` shows through. It
/// needs `NSCameraUsageDescription`, which is the single thing this feature asks
/// the phone for on either platform.
struct CameraSheet: UIViewControllerRepresentable {
    let onCaptured: (Data?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onCaptured: onCaptured) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let onCaptured: (Data?) -> Void

        init(onCaptured: @escaping (Data?) -> Void) { self.onCaptured = onCaptured }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            let image = info[.originalImage] as? UIImage
            // Full quality here on purpose: `PhotoStore` is what decides how
            // large a kept photograph is, and re-compressing on the way in would
            // throw detail away twice.
            onCaptured(image?.jpegData(compressionQuality: 1))
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onCaptured(nil)
        }
    }

    /// Whether this phone has one at all. A simulator does not.
    static var isAvailable: Bool {
        UIImagePickerController.isSourceTypeAvailable(.camera)
    }
}
