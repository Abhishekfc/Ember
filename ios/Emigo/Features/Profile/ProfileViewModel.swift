import Foundation
import Observation

@MainActor
@Observable
final class ProfileViewModel {
    private(set) var profile: UserProfile
    private(set) var isUploadingPhoto = false
    private(set) var photoError: String?

    private let users: UserRepository
    private let identity: IdentityProvider
    /// Tells the rest of the app (Home, Settings) the profile changed.
    private let onUpdated: (UserProfile) -> Void

    init(profile: UserProfile, users: UserRepository, identity: IdentityProvider, onUpdated: @escaping (UserProfile) -> Void) {
        self.profile = profile
        self.users = users
        self.identity = identity
        self.onUpdated = onUpdated
    }

    private func apply(_ updated: UserProfile) {
        profile = updated
        onUpdated(updated)
    }

    /// Each of these returns an error message to show, or nil when it worked.

    func saveName(_ text: String) async -> String? {
        let name = text.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return String(localized: Strings.Profile.errorNameEmpty) }
        do {
            apply(try await users.updateName(name))
            return nil
        } catch is CancellationError {
            return ""
        } catch {
            return (error as? APIError)?.errorDescription ?? String(localized: Strings.Failure.saveName)
        }
    }

    func checkUsername(_ username: String) async -> UsernameCheck {
        guard username.count >= minimumUsernameLength, username != profile.username else { return .idle }
        guard let result = try? await users.usernameAvailability(username) else { return .idle }
        return result.available ? .available : .taken(suggestions: result.suggestions)
    }

    func saveUsername(_ username: String) async -> String? {
        do {
            apply(try await users.updateUsername(username))
            return nil
        } catch is CancellationError {
            return ""
        } catch {
            return (error as? APIError)?.errorDescription ?? String(localized: Strings.Failure.saveUsername)
        }
    }

    func changePassword(current: String, new: String, confirm: String) async -> String? {
        guard !current.isEmpty else { return String(localized: Strings.Profile.errorCurrentPassword) }
        guard new.count >= minimumPasswordLength else { return String(localized: Strings.Profile.errorNewPasswordShort) }
        guard new == confirm else { return String(localized: Strings.Profile.errorPasswordMismatch) }
        do {
            try await identity.changePassword(current: current, new: new)
            return nil
        } catch is CancellationError {
            return ""
        } catch {
            return (error as? IdentityError)?.errorDescription ?? String(localized: Strings.Failure.changePassword)
        }
    }

    func uploadPhoto(from data: Data) async {
        isUploadingPhoto = true
        photoError = nil
        defer { isUploadingPhoto = false }
        guard let jpeg = await Task.detached(priority: .userInitiated, operation: { ProfilePhotoEncoder.jpegData(from: data) }).value else {
            photoError = String(localized: Strings.Failure.updatePhoto)
            return
        }
        do {
            apply(try await users.uploadProfilePhoto(jpeg: jpeg))
            Haptics.success()
        } catch is CancellationError {
            return
        } catch {
            photoError = String(localized: Strings.Failure.updatePhoto)
            Haptics.error()
        }
    }
}
