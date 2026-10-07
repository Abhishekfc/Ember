import SwiftUI

/// A photo loaded from a URL that fills whatever frame it is given, cropping to fit, with a flat
/// placeholder until it arrives and a short fade once it does.
struct RemoteImage: View {
    let url: URL?
    /// The longest side, in points, this image will be shown at. Decides how far it is shrunk.
    let pointSize: CGFloat
    /// `.fill` crops to the frame (cards, thumbnails); `.fit` shows the whole photo (full screen).
    var contentMode: ContentMode = .fill

    @Environment(\.theme) private var theme
    @Environment(\.imageLoader) private var loader
    @Environment(\.displayScale) private var displayScale
    @State private var image: UIImage?

    var body: some View {
        Color.clear
            .overlay {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                        .transition(.opacity)
                } else if contentMode == .fill {
                    theme.colors.panel
                }
            }
            .clipped()
            .task(id: url) {
                guard let url else {
                    image = nil
                    return
                }
                let loaded = await loader.image(for: url, maxPixelSize: pointSize * displayScale)
                withAnimation(.easeOut(duration: 0.2)) { image = loaded }
            }
    }
}
