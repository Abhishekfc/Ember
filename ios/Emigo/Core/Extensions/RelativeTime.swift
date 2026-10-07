import Foundation

/// Short, friendly times: "5m ago", "yesterday", "18h left". Worded like Android's helpers.
enum RelativeTime {
    static func short(since date: Date, now: Date = Date()) -> String {
        let seconds = max(0, now.timeIntervalSince(date))
        let minutes = Int(seconds / 60)
        let hours = Int(seconds / 3600)
        let days = Int(seconds / 86400)

        switch (minutes, hours, days) {
        case (..<1, _, _):
            return String(localized: Strings.Time.justNow)
        case (..<60, _, _):
            return String(format: String(localized: Strings.Time.minutesAgo), minutes)
        case (_, ..<24, _):
            return String(format: String(localized: Strings.Time.hoursAgo), hours)
        case (_, _, 1):
            return String(localized: Strings.Time.yesterday)
        default:
            return String(format: String(localized: Strings.Time.daysAgo), days)
        }
    }

    /// Time left until `expiry`, never negative: a photo whose window just closed reads "expiring".
    static func remaining(until expiry: Date, now: Date = Date()) -> String {
        let minutesLeft = Int(expiry.timeIntervalSince(now) / 60)
        if minutesLeft <= 0 { return String(localized: Strings.Time.expiring) }
        let hoursLeft = minutesLeft / 60
        if hoursLeft < 1 { return String(format: String(localized: Strings.Time.minutesLeft), minutesLeft) }
        return String(format: String(localized: Strings.Time.hoursLeft), hoursLeft)
    }
}
