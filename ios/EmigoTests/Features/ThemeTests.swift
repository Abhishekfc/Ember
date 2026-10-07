import XCTest
@testable import Emigo

private final class FixedToken: AccessTokenProviding {
    func idToken() async -> String? { "token" }
}

final class ThemeRegistryTests: XCTestCase {
    private var projectRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Features
            .deletingLastPathComponent() // EmigoTests
            .deletingLastPathComponent() // ios
    }

    func testEveryThemeIsDefinedAndKnowsItsKey() {
        for key in ThemeKey.allCases {
            XCTAssertEqual(EmigoTheme.theme(for: key).key, key)
        }
        XCTAssertEqual(ThemeKey.allCases.count, 9)
    }

    func testTheDefaultIsEmberAndComesFirst() {
        XCTAssertEqual(ThemeKey.defaultKey, .citrus)
        XCTAssertEqual(EmigoTheme.default.key, .citrus)
        XCTAssertEqual(ThemeKey.displayOrder.first, .citrus)
        XCTAssertEqual(Set(ThemeKey.displayOrder), Set(ThemeKey.allCases))
    }

    func testOnlyTheFourGoldThemesAreLocked() {
        let locked = Set(ThemeKey.allCases.filter(\.isLocked))
        XCTAssertEqual(locked, [.aurora, .cyber, .botanica, .frost])
        XCTAssertFalse(ThemeKey.defaultKey.isLocked)
    }

    func testSavedNamesNeverChange() {
        // These raw values are written to the device, so renaming one would reset people's choice.
        XCTAssertEqual(ThemeKey.allCases.map(\.rawValue).sorted(),
                       ["AURORA", "BLAZE", "BOTANICA", "CITRUS", "CYBER", "EMBER", "EMBER_NEW", "FROST", "NOIR"])
    }

    func testNamesMatchAndroid() {
        XCTAssertEqual(String(localized: ThemeKey.ember.displayName), "Cream")
        XCTAssertEqual(String(localized: ThemeKey.emberNew.displayName), "Dusk")
        XCTAssertEqual(String(localized: ThemeKey.citrus.displayName), "Ember")
        XCTAssertEqual(String(localized: ThemeKey.frost.displayName), "Frost")
    }

    func testPictureThemesPointAtRealPictures() {
        let withPictures = ThemeKey.allCases.filter { EmigoTheme.theme(for: $0).colors.backgroundImageName != nil }
        XCTAssertEqual(Set(withPictures), [.aurora, .cyber, .botanica, .frost])
        for key in withPictures {
            let name = EmigoTheme.theme(for: key).colors.backgroundImageName ?? ""
            let folder = projectRoot.appendingPathComponent("Emigo/Resources/Assets.xcassets/\(name).imageset")
            XCTAssertTrue(FileManager.default.fileExists(atPath: folder.path), "\(key) is missing \(name).imageset")
        }
    }

    func testDisplayFontsMatchAndroid() {
        let serif: Set<ThemeKey> = [.ember, .emberNew, .blaze, .botanica]
        for key in ThemeKey.allCases {
            let expected: DisplayFontFamily = serif.contains(key) ? .fraunces : .spaceGrotesk
            XCTAssertEqual(EmigoTheme.theme(for: key).displayFont, expected, "\(key)")
        }
    }
}

@MainActor
final class ThemeStoreTests: XCTestCase {
    private var defaults: UserDefaults!
    private var store: SessionStore!
    private var subscription: SubscriptionRepository!
    private var themes: ThemeStore!
    /// What the stubbed server answers; read from the URL loading thread, so it isn't main-actor state.
    private final class StatusBox: @unchecked Sendable {
        var value = "FREE"
    }
    private let statusBox = StatusBox()

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "ThemeStoreTests-\(UUID().uuidString)")
        store = SessionStore(defaults: defaults)
        let api = APIClient(
            baseURL: URL(string: "https://api.example/")!,
            tokenProvider: FixedToken(),
            session: APIClient.makeSession(protocolClasses: [StubURLProtocol.self])
        )
        let status = statusBox
        StubURLProtocol.handler = { _ in
            (200, Data(#"{"status":"\#(status.value)","plan":null,"expiresAt":null}"#.utf8))
        }
        subscription = SubscriptionRepository(api: api, store: store)
        themes = ThemeStore(store: store, subscription: subscription)
    }

    override func tearDown() async throws {
        StubURLProtocol.handler = nil
    }

    private func setGold(_ isGold: Bool) async {
        statusBox.value = isGold ? "ACTIVE" : "FREE"
        await subscription.refreshIsGoldMember(force: true)
    }

    func testStartsOnTheDefault() {
        XCTAssertEqual(themes.appliedKey, .citrus)
        XCTAssertEqual(themes.activeTheme.key, .citrus)
    }

    func testApplyingAFreeThemeSticksAndIsSaved() {
        themes.apply(.noir)
        XCTAssertEqual(themes.appliedKey, .noir)
        XCTAssertEqual(store.selectedTheme, "NOIR")

        let nextLaunch = ThemeStore(store: store, subscription: subscription)
        XCTAssertEqual(nextLaunch.appliedKey, .noir)
    }

    func testAGoldThemeCannotBeAppliedWithoutGold() {
        themes.apply(.aurora)
        XCTAssertEqual(themes.appliedKey, .citrus)
        XCTAssertNil(store.selectedTheme)
        XCTAssertFalse(themes.canApply(.aurora))
        XCTAssertTrue(themes.canApply(.blaze))
    }

    func testGoldMembersCanApplyGoldThemes() async {
        await setGold(true)
        XCTAssertTrue(themes.canApply(.frost))
        themes.apply(.frost)
        XCTAssertEqual(themes.activeTheme.key, .frost)
    }

    func testLosingGoldFallsBackToTheDefaultAndGettingItBackRestoresTheChoice() async {
        await setGold(true)
        themes.apply(.cyber)
        await setGold(false)
        XCTAssertEqual(themes.appliedKey, .citrus, "a lapsed subscription never leaves a Gold theme on")
        await setGold(true)
        XCTAssertEqual(themes.appliedKey, .cyber)
    }

    func testPreviewingWearsAThemeUntilItEnds() {
        themes.apply(.blaze)
        themes.preview(.aurora)   // anyone may try a Gold theme on
        XCTAssertEqual(themes.activeTheme.key, .aurora)
        XCTAssertEqual(themes.appliedKey, .blaze)
        XCTAssertEqual(store.selectedTheme, "BLAZE", "a preview is never saved")

        themes.endPreview()
        XCTAssertEqual(themes.activeTheme.key, .blaze)
    }

    func testApplyingEndsThePreview() {
        themes.preview(.noir)
        themes.apply(.noir)
        XCTAssertEqual(themes.activeTheme.key, .noir)
        themes.endPreview()
        XCTAssertEqual(themes.activeTheme.key, .noir)
    }

    func testResetGoesBackToTheDefault() {
        themes.apply(.blaze)
        themes.reset()
        XCTAssertEqual(themes.appliedKey, .citrus)
    }

    func testSigningOutForgetsTheChoice() {
        themes.apply(.blaze)
        store.clear()
        XCTAssertNil(store.selectedTheme)
    }

    func testAnUnknownSavedThemeFallsBackToTheDefault() {
        store.selectedTheme = "NOT_A_THEME"
        XCTAssertEqual(ThemeStore(store: store, subscription: subscription).appliedKey, .citrus)
    }
}
