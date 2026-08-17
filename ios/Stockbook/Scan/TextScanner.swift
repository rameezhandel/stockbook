import Foundation
import Vision
import UIKit

/// Reading text off a photograph, on this device and nowhere else.
///
/// `VNRecognizeTextRequest` runs entirely on the phone: no request leaves it, no
/// model is fetched, and it works in airplane mode. That is the only reason this
/// feature can exist in an app whose first rule is that nothing is ever sent
/// anywhere.
enum TextScanner {

    /// Everything the camera could read, in no particular order.
    ///
    /// `.accurate` and `usesLanguageCorrection` are both deliberate. Handwriting
    /// is where the fast path falls apart, and language correction is what turns
    /// a scrawled "Podlock" into "Padlock" — at the cost of occasionally
    /// correcting a product name into an English word that was never there,
    /// which is why nothing it produces is saved without the owner seeing it.
    static func read(_ image: UIImage) throws -> [ScannedText] {
        guard let cgImage = image.cgImage else { return [] }

        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true
        request.recognitionLanguages = ["en-US"]

        let handler = VNImageRequestHandler(cgImage: cgImage, orientation: image.cgOrientation, options: [:])
        try handler.perform([request])

        return (request.results ?? []).compactMap { observation in
            guard let candidate = observation.topCandidates(1).first else { return nil }
            let box = observation.boundingBox
            return ScannedText(
                text: candidate.string,
                midY: Double(box.midY),
                minX: Double(box.minX),
                confidence: Double(candidate.confidence)
            )
        }
    }
}

private extension UIImage {
    /// Vision wants the orientation as EXIF, not as `UIImage.Orientation`. Getting
    /// this wrong rotates the page and turns every row into a column.
    var cgOrientation: CGImagePropertyOrientation {
        switch imageOrientation {
        case .up: .up
        case .down: .down
        case .left: .left
        case .right: .right
        case .upMirrored: .upMirrored
        case .downMirrored: .downMirrored
        case .leftMirrored: .leftMirrored
        case .rightMirrored: .rightMirrored
        @unknown default: .up
        }
    }
}
