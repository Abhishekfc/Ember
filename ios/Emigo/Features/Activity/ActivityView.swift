import SwiftUI

/// Everything that's happened: photos received, requests, streaks. Opened from the bell on Home.
struct ActivityView: View {
    let model: ActivityViewModel

    @Environment(\.theme) private var theme

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(model.days) { day in
                    dayHeader(day.day)
                    ForEach(day.events) { event in
                        ActivityRow(event: event)
                    }
                }
            }
            .padding(.horizontal, Spacing.l)
            .padding(.bottom, Spacing.xl)
        }
        .background(theme.colors.background.ignoresSafeArea())
        .overlay {
            if model.showsEmptyState {
                EmptyPlaceholder(symbol: "bell", title: Strings.Activity.emptyTitle, detail: Strings.Activity.emptyDetail)
            }
        }
        .navigationTitle(Text(Strings.Activity.title))
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await model.load(forceRefresh: true) }
        .task {
            await model.load()
            await model.markSeen()
        }
    }

    private func dayHeader(_ day: Date) -> some View {
        Group {
            if Calendar.current.isDateInToday(day) {
                SectionCaption(Strings.Activity.today)
            } else if Calendar.current.isDateInYesterday(day) {
                SectionCaption(Strings.Activity.yesterday)
            } else {
                SectionCaption(verbatim: day.formatted(.dateTime.month(.abbreviated).day()))
            }
        }
        .padding(.top, Spacing.l)
        .padding(.bottom, Spacing.xs)
    }
}

private struct ActivityRow: View {
    let event: ActivityEvent

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: Spacing.m) {
            RingedAvatar(
                name: event.actorDisplayName,
                photoURL: event.actorProfilePhotoUrl.flatMap { URL(string: $0) },
                size: 54
            )
            .overlay(alignment: .bottomTrailing) { typeBadge.offset(x: 2, y: 2) }

            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: event.message)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(event.warn ? EmigoFixedColors.errorText : theme.colors.cream)
                    .fixedSize(horizontal: false, vertical: true)
                Text(verbatim: RelativeTime.short(since: event.createdAt))
                    .font(.system(size: 12.5))
                    .foregroundStyle(theme.colors.muted)
            }
            Spacer(minLength: Spacing.xs)

            if let url = event.photoUrl.flatMap({ URL(string: $0) }) {
                RemoteImage(url: url, pointSize: 60)
                    .frame(width: 54, height: 60)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
        .padding(.vertical, Spacing.xs)
        .accessibilityElement(children: .combine)
    }

    private var typeBadge: some View {
        Image(systemName: symbol)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: 20, height: 20)
            .background(theme.colors.accent2, in: Circle())
            .overlay(Circle().strokeBorder(theme.colors.backgroundTop, lineWidth: 2))
    }

    private var symbol: String {
        switch event.type {
        case .photoReceived: "photo.fill"
        case .streakExpiring: "flame.fill"
        case .streakBroken: "flame"
        case .requestAccepted: "person.fill.checkmark"
        case .requestIncoming: "person.fill.badge.plus"
        case .unknown: "bell.fill"
        }
    }
}
