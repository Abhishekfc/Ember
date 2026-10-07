import Foundation

/// Web links and contact addresses, in one place. Same values as Android's `AppLinks`.
enum AppLinks {
    static let termsOfService = URL(string: "https://emigo.live/terms-of-service")!
    static let privacyPolicy = URL(string: "https://emigo.live/privacy-policy")!
    static let supportEmail = "emigohq@gmail.com"
    /// Where Emigo Gold is managed or cancelled: it is billed by Google Play.
    static let manageSubscription = URL(string: "https://play.google.com/store/account/subscriptions")!

    /// A `mailto:` link to support, so tapping "Help & support" opens the Mail app.
    static func supportMailURL(subject: String) -> URL {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = supportEmail
        components.queryItems = [URLQueryItem(name: "subject", value: subject)]
        return components.url ?? URL(string: "mailto:\(supportEmail)")!
    }
}
