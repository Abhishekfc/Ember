import SwiftUI

/// Photos sent in the last 24 hours, with the option to take one back. Opens from the Sent button
/// in the camera header.
struct SentPhotosView: View {
    let model: SentPhotosViewModel

    @Environment(\.theme) private var theme
    @State private var openPhoto: SentPhoto?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.m) {
                Text(Strings.Sent.intro)
                    .font(.system(size: 12))
                    .foregroundStyle(theme.colors.muted)

                if model.showsEmptyState {
                    Text(Strings.Sent.empty)
                        .font(.system(size: 13))
                        .foregroundStyle(theme.colors.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, Spacing.huge)
                }

                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(model.photos) { photo in
                        Button { openPhoto = photo } label: {
                            RemoteImage(url: photo.url, pointSize: 200)
                                .aspectRatio(PhotoBaker.aspectRatio, contentMode: .fit)
                                .overlay {
                                    LinearGradient(
                                        stops: [.init(color: .clear, location: 0.65), .init(color: .black.opacity(0.55), location: 1)],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                }
                                .overlay(alignment: .bottomLeading) {
                                    Text(verbatim: RelativeTime.short(since: photo.createdAt))
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 5)
                                }
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text(String(format: String(localized: Strings.Sent.timeDescription), RelativeTime.short(since: photo.createdAt))))
                    }
                }
            }
            .padding(.horizontal, Spacing.l)
            .padding(.top, Spacing.xs)
            .padding(.bottom, Spacing.xl)
        }
        .background(theme.colors.background.ignoresSafeArea())
        .navigationTitle(Text(Strings.Sent.title))
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await model.load() }
        .task { await model.load() }
        .fullScreenCover(item: $openPhoto) { photo in
            SentPhotoViewer(photo: photo, model: model)
        }
        .alert(model.errorMessage ?? "", isPresented: Binding(
            get: { model.errorMessage != nil },
            set: { if !$0 { model.dismissError() } }
        )) {
            Button(role: .cancel) {} label: { Text(Strings.Common.close) }
        }
    }
}

/// One sent photo, big, with how long is left to take it back.
private struct SentPhotoViewer: View {
    let photo: SentPhoto
    let model: SentPhotosViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var isConfirming = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: Spacing.m) {
                RemoteImage(url: photo.url, pointSize: 1000)
                    .aspectRatio(PhotoBaker.aspectRatio, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                    .padding(.horizontal, Size.cardSidePadding)
                VStack(spacing: 4) {
                    Text(String(format: String(localized: Strings.Sent.timeDescription), RelativeTime.short(since: photo.createdAt)))
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(.white)
                    Text(String(format: String(localized: Strings.Sent.remainingToUnsend), RelativeTime.remaining(until: SentPhotosViewModel.unsendDeadline(for: photo))))
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.6))
                }
            }
        }
        .overlay(alignment: .topLeading) {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .liquidGlass(in: Circle(), interactive: true)
            }
            .accessibilityLabel(Text(Strings.Common.close))
            .padding(Spacing.xs)
        }
        .overlay(alignment: .topTrailing) {
            Menu {
                Button(role: .destructive) { isConfirming = true } label: {
                    Label { Text(Strings.Sent.unsend) } icon: { Image(systemName: "arrow.uturn.backward") }
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .liquidGlass(in: Circle(), interactive: true)
            }
            .accessibilityLabel(Text(Strings.Common.moreOptions))
            .padding(Spacing.xs)
        }
        .confirmationDialog(Text(Strings.Sent.unsendTitle), isPresented: $isConfirming, titleVisibility: .visible) {
            Button(role: .destructive) {
                Task { if await model.unsend(photo) { dismiss() } }
            } label: { Text(Strings.Sent.unsend) }
            Button(role: .cancel) {} label: { Text(Strings.Common.cancel) }
        } message: {
            Text(Strings.Sent.unsendWarning)
        }
        .preferredColorScheme(.dark)
    }
}
