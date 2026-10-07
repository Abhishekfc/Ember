import XCTest
@testable import Emigo

/// The email-verification rules, ported from Android's `EmailVerificationRulesTest`.
final class AuthRulesTests: XCTestCase {
    private func profile(createdAt: Date?, verificationRequired: Bool) -> UserProfile {
        UserProfile(
            userId: "u", displayName: "A", username: "a", email: "a@b.co",
            createdAt: createdAt, emailVerificationRequired: verificationRequired
        )
    }

    func testTheServerAloneDecidesWhetherVerificationIsNeeded() {
        XCTAssertTrue(needsEmailVerification(profile(createdAt: nil, verificationRequired: true)))
        XCTAssertFalse(needsEmailVerification(profile(createdAt: nil, verificationRequired: false)))
    }

    func testTheDeadlineIsTenMinutesAfterTheAccountWasCreated() {
        let created = Date(timeIntervalSince1970: 1_800_000_000)
        let deadline = verifyByDeadline(for: profile(createdAt: created, verificationRequired: true))
        XCTAssertEqual(deadline.timeIntervalSince(created), 10 * 60, accuracy: 0.001)
    }

    func testReopeningNeverRestartsTheCountdown() {
        let created = Date(timeIntervalSince1970: 1_800_000_000)
        let first = verifyByDeadline(for: profile(createdAt: created, verificationRequired: true), now: created.addingTimeInterval(60))
        let later = verifyByDeadline(for: profile(createdAt: created, verificationRequired: true), now: created.addingTimeInterval(500))
        XCTAssertEqual(first, later)
    }

    func testAMissingCreationDateGivesAFreshWindowFromNow() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let deadline = verifyByDeadline(for: profile(createdAt: nil, verificationRequired: true), now: now)
        XCTAssertEqual(deadline.timeIntervalSince(now), 10 * 60, accuracy: 0.001)
    }

    func testSessionStoreKeepsPendingVerificationUntilCleared() {
        let defaults = UserDefaults(suiteName: "emigo.tests.\(UUID().uuidString)")!
        let store = SessionStore(defaults: defaults)
        XCTAssertNil(store.pendingVerification)

        let pending = PendingVerification(firebaseUid: "uid", email: "a@b.co", deadline: Date(timeIntervalSince1970: 1_800_000_000))
        store.savePendingVerification(pending)
        XCTAssertEqual(store.pendingVerification, pending)

        store.displayName = "Ann"
        store.clear()
        XCTAssertNil(store.pendingVerification)
        XCTAssertNil(store.displayName)
    }
}

final class ProfilePhotoEncoderTests: XCTestCase {
    func testAWidePictureBecomesASquareOfTheRequestedSize() throws {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 600, height: 300))
        let source = renderer.jpegData(withCompressionQuality: 1) { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 600, height: 300))
        }

        let encoded = try XCTUnwrap(ProfilePhotoEncoder.jpegData(from: source, side: 200))
        let image = try XCTUnwrap(UIImage(data: encoded))
        XCTAssertEqual(image.size.width * image.scale, 200, accuracy: 0.5)
        XCTAssertEqual(image.size.height * image.scale, 200, accuracy: 0.5)
    }

    func testGarbageIsRejected() {
        XCTAssertNil(ProfilePhotoEncoder.jpegData(from: Data([0, 1, 2, 3])))
    }
}
