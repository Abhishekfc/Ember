import SwiftUI
import UIKit

/// The apps Find people offers for inviting someone who isn't on Emigo yet.
enum InviteTarget: CaseIterable, Identifiable {
    case instagram
    case snapchat
    case whatsapp

    var id: Self { self }

    var displayName: String {
        switch self {
        case .instagram: "Instagram"
        case .snapchat: "Snapchat"
        case .whatsapp: "WhatsApp"
        }
    }

    var imageName: String {
        switch self {
        case .instagram: "Invite-instagram"
        case .snapchat: "Invite-snapchat"
        case .whatsapp: "Invite-whatsapp"
        }
    }

    /// Where to send the person. WhatsApp takes the message in the link itself; Instagram and
    /// Snapchat don't, so the message is copied first and they paste it.
    func url(message: String) -> URL? {
        switch self {
        case .whatsapp:
            var components = URLComponents(string: "https://wa.me/")
            components?.queryItems = [URLQueryItem(name: "text", value: message)]
            return components?.url
        case .instagram:
            return URL(string: "instagram://direct-inbox")
        case .snapchat:
            return URL(string: "snapchat://")
        }
    }

    var needsMessageCopied: Bool { self != .whatsapp }

    /// Sends the invite through this app: copies the message first where the app can't take it in
    /// a link, then opens the app. If the app isn't installed, `fallback` shows the system share sheet.
    @MainActor
    func send(message: String, openURL: OpenURLAction, fallback: @escaping () -> Void) {
        if needsMessageCopied { UIPasteboard.general.string = message }
        guard let url = url(message: message) else {
            fallback()
            return
        }
        openURL(url) { accepted in
            if !accepted { fallback() }
        }
    }
}

enum InviteMessage {
    /// "Come add me on Emigo, I'm @name…", or the version without a name when it isn't known.
    static func text(username: String?) -> String {
        if let username, !username.isEmpty {
            return String(format: String(localized: Strings.FindPeople.inviteMessageWithUsername), username)
        }
        return String(localized: Strings.FindPeople.inviteMessage)
    }
}
