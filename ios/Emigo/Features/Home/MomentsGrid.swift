import SwiftUI

/// Every photo in the feed as big tiles, newest first, each labelled with whose it is. Tapping one
/// opens it on Home.
struct MomentsGrid: View {
    let model: HomeViewModel

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 11), count: 2)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 11) {
            ForEach(model.moments) { entry in
                Button {
                    model.showOnHome(photoId: entry.photo.photoId)
                } label: {
                    RemoteImage(url: entry.photo.url, pointSize: 400)
                        .aspectRatio(0.8, contentMode: .fit)
                        .overlay {
                            LinearGradient(
                                stops: [.init(color: .clear, location: 0.6), .init(color: .black.opacity(0.55), location: 1)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        }
                        .overlay(alignment: .bottomLeading) {
                            Text(verbatim: entry.displayName)
                                .displayFont(15, weight: .medium, relativeTo: .subheadline)
                                .foregroundStyle(EmigoFixedColors.onPhotoText)
                                .lineLimit(1)
                                .padding(Spacing.m)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: entry.displayName))
            }
        }
        .padding(.horizontal, Spacing.l)
    }
}
