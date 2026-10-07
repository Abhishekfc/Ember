import XCTest
@testable import Emigo

final class ColorToneTests: XCTestCase {
    func testHslRoundTripKeepsTheColor() {
        for hex: UInt32 in [0xF5D90A, 0xFF7A1A, 0x353535, 0x7B61FF, 0x111111] {
            let original = RGBColor(hex: hex)
            let back = RGBColor(original.hsl)
            XCTAssertEqual(back.red, original.red, accuracy: 1e-9)
            XCTAssertEqual(back.green, original.green, accuracy: 1e-9)
            XCTAssertEqual(back.blue, original.blue, accuracy: 1e-9)
        }
    }

    func testGreyHasNoSaturation() {
        XCTAssertEqual(RGBColor(hex: 0x353535).hsl.saturation, 0)
    }

    func testSurfaceLadderStepsUpFromTheBackground() {
        let background = RGBColor(hex: 0x111111)
        let panel = RGBColor(hex: 0x353535)
        let ladder = deriveSurfaceLadder(backgroundBase: background, panel: panel, accent: RGBColor(hex: 0xF5D90A))

        let backgroundL = background.hsl.lightness
        let surfaceL = ladder.surface.hsl.lightness
        let panelL = panel.hsl.lightness
        let elevatedL = ladder.elevatedPanel.hsl.lightness
        let overlayL = ladder.overlayPanel.hsl.lightness

        XCTAssertGreaterThan(surfaceL, backgroundL)
        XCTAssertLessThan(surfaceL, panelL)
        XCTAssertGreaterThan(elevatedL, panelL)
        XCTAssertGreaterThan(overlayL, elevatedL)
    }

    func testLightnessGapIsEnforcedOnDarkThemes() {
        let panel = RGBColor(hex: 0x353535)
        let dim = RGBColor(hex: 0x5C5C5C).ensuringLightnessGap(from: panel, minGap: 0.26, awayFromWhite: false)
        XCTAssertGreaterThanOrEqual(dim.hsl.lightness, panel.hsl.lightness + 0.26 - 1e-9)
    }

    func testLightnessGapNeverMovesAColorThatAlreadyHasIt() {
        let panel = RGBColor(hex: 0x353535)
        let bright = RGBColor(hex: 0xEEEEEE)
        XCTAssertEqual(bright.ensuringLightnessGap(from: panel, minGap: 0.26, awayFromWhite: false).hsl.lightness, bright.hsl.lightness, accuracy: 1e-9)
    }
}
