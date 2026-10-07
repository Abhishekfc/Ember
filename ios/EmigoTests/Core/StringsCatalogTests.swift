import XCTest
@testable import Emigo

/// Keeps `Strings.swift` and `Localizable.xcstrings` in step: every key the code asks for must
/// exist, and no key may sit in the catalog unused.
final class StringsCatalogTests: XCTestCase {
    private var projectRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Core
            .deletingLastPathComponent() // EmigoTests
            .deletingLastPathComponent() // ios
    }

    private func keysUsedInCode() throws -> Set<String> {
        let source = try String(contentsOf: projectRoot.appendingPathComponent("Emigo/Core/Localization/Strings.swift"), encoding: .utf8)
        let regex = try NSRegularExpression(pattern: #"LocalizedStringResource\("([^"]+)"\)"#)
        let range = NSRange(source.startIndex..., in: source)
        return Set(regex.matches(in: source, range: range).compactMap {
            Range($0.range(at: 1), in: source).map { String(source[$0]) }
        })
    }

    private func keysInCatalog() throws -> Set<String> {
        let data = try Data(contentsOf: projectRoot.appendingPathComponent("Emigo/Resources/Localizable.xcstrings"))
        let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(json["strings"] as? [String: Any])
        return Set(strings.keys)
    }

    func testEveryKeyUsedInCodeIsInTheCatalog() throws {
        let missing = try keysUsedInCode().subtracting(keysInCatalog())
        XCTAssertTrue(missing.isEmpty, "Missing from Localizable.xcstrings: \(missing.sorted())")
    }

    func testNoCatalogKeyIsUnused() throws {
        let unused = try keysInCatalog().subtracting(keysUsedInCode())
        XCTAssertTrue(unused.isEmpty, "Unused in Strings.swift: \(unused.sorted())")
    }

    func testCompiledCatalogReturnsTheEnglishText() {
        XCTAssertEqual(String(localized: Strings.Welcome.tagline), "Your people. Always close.")
        XCTAssertEqual(String(localized: Strings.Login.errorBadCredentials), "Incorrect email or password")
        XCTAssertEqual(String(format: String(localized: Strings.Verify.expiresIn), 9, 5), "Expires in 9:05")
    }
}
