import SwiftUI

/// Photos you've saved, grouped by month.
struct MemoriesView: View {
    let model: MemoriesViewModel

    @Environment(\.theme) private var theme
    @State private var openPhoto: MemoryPhoto?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 5), count: 4)

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Strings.Memories.title)

            if model.showsEmptyState {
                EmptyPlaceholder(symbol: "calendar", title: Strings.Memories.empty, detail: Strings.Memories.emptyDetail)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: Spacing.l) {
                        ForEach(model.months) { month in
                            VStack(alignment: .leading, spacing: Spacing.s) {
                                Text(month.start, format: .dateTime.month(.wide).year())
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(theme.colors.cream)
                                LazyVGrid(columns: columns, spacing: 5) {
                                    ForEach(month.photos) { photo in
                                        thumbnail(photo)
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.l)
                    .padding(.top, Spacing.xs)
                    .padding(.bottom, Size.tabBarHeight + Spacing.xl)
                }
                .refreshable { await model.load() }
            }
        }
        .task { await model.load() }
        .fullScreenCover(item: $openPhoto) { photo in
            PhotoViewer(photo: photo, onDelete: { await model.delete(photo) })
        }
    }

    private func thumbnail(_ photo: MemoryPhoto) -> some View {
        Button { openPhoto = photo } label: {
            RemoteImage(url: photo.url, pointSize: 140)
                .aspectRatio(1, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(Strings.Memories.photoDescription))
    }
}

/// A saved photo, full screen. Swipe down or tap the close button to leave.
private struct PhotoViewer: View {
    let photo: MemoryPhoto
    let onDelete: () async -> Bool

    @Environment(\.dismiss) private var dismiss
    @State private var dragOffset: CGFloat = 0

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            RemoteImage(url: photo.url, pointSize: 1400, contentMode: .fit)
                .offset(y: dragOffset)
                .gesture(
                    DragGesture()
                        .onChanged { dragOffset = max(0, $0.translation.height) }
                        .onEnded { value in
                            if value.translation.height > 120 { dismiss() } else {
                                withAnimation(.spring(duration: 0.3)) { dragOffset = 0 }
                            }
                        }
                )
        }
        .overlay(alignment: .topLeading) {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(EmigoFixedColors.onPhotoText)
                    .frame(width: 44, height: 44)
                    .liquidGlass(in: Circle(), interactive: true)
            }
            .accessibilityLabel(Text(Strings.Common.close))
            .padding(Spacing.xs)
        }
        .overlay(alignment: .bottomLeading) {
            Text(photo.createdAt, format: .dateTime.month(.wide).day().year())
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(EmigoFixedColors.onPhotoText)
                .padding(Spacing.xl)
        }
        .preferredColorScheme(.dark)
    }
}
