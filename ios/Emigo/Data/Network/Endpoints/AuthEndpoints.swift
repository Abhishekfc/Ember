import Foundation

extension Endpoint where Response == UserProfile {
    /// The one step the backend still owns after Firebase creates an account: attaching a name and username.
    static func completeProfile(displayName: String, username: String) -> Endpoint {
        Endpoint(.post, "auth/complete-profile", body: CompleteProfileRequest(displayName: displayName, username: username))
    }

    /// A 401 here means "signed in to Firebase but no Emigo profile yet", which sign-in handles itself.
    static func myProfile() -> Endpoint {
        Endpoint(.get, "users/me", handlesUnauthorizedItself: true)
    }

    static func updateProfile(displayName: String? = nil, username: String? = nil) -> Endpoint {
        Endpoint(.patch, "users/me", body: UpdateProfileRequest(displayName: displayName, username: username))
    }

    static func uploadProfilePhoto(jpeg: Data) -> Endpoint {
        var form = MultipartForm()
        form.addFile("file", filename: "profile.jpg", mimeType: "image/jpeg", data: jpeg)
        return Endpoint(.post, "users/me/photo", multipart: form)
    }
}

extension Endpoint where Response == UsernameAvailability {
    /// Public (no account yet), used while picking a username during sign-up.
    static func usernameAvailabilityPublic(_ username: String) -> Endpoint {
        Endpoint(.get, "auth/username-availability", query: [URLQueryItem(name: "username", value: username)])
    }

    static func usernameAvailability(_ username: String) -> Endpoint {
        Endpoint(.get, "users/me/username-availability", query: [URLQueryItem(name: "username", value: username)])
    }
}

extension Endpoint where Response == EmailAvailability {
    static func emailAvailabilityPublic(_ email: String) -> Endpoint {
        Endpoint(.get, "auth/email-availability", query: [URLQueryItem(name: "email", value: email)])
    }
}

extension Endpoint where Response == UsernameLoginLookup {
    /// Firebase only knows emails, so a username typed at sign-in is resolved by the backend first.
    static func resolveUsernameForLogin(_ username: String) -> Endpoint {
        Endpoint(.get, "auth/username-login-lookup", query: [URLQueryItem(name: "username", value: username)])
    }
}

extension Endpoint where Response == EmptyResponse {
    static func registerDevice(token: String) -> Endpoint {
        Endpoint(.post, "devices/register", body: DeviceTokenRequest(fcmToken: token))
    }

    /// Expected to 401 when sign-out was itself caused by a dead session, so it never counts as one.
    static func unregisterDevice(token: String) -> Endpoint {
        Endpoint(.post, "devices/unregister", body: DeviceTokenRequest(fcmToken: token), handlesUnauthorizedItself: true)
    }

    static func deleteAccount() -> Endpoint {
        Endpoint(.delete, "users/me")
    }
}
