package com.emigo.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorToneTest {

    private val tolerance = 0.01f

    private fun assertColorEquals(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, tolerance)
        assertEquals(expected.green, actual.green, tolerance)
        assertEquals(expected.blue, actual.blue, tolerance)
    }

    @Test
    fun convertingToHslAndBackKeepsTheColor() {
        val samples = listOf(
            Color(0xFFEDEAE0), Color(0xFF7B61FF), Color(0xFFFF2EC4),
            Color(0xFF121212), Color(0xFFCCE7FF), Color(0xFF4FE3C1),
        )
        for (color in samples) {
            assertColorEquals(color, color.toHsl().toColor())
        }
    }

    @Test
    fun aGrayHasNoSaturation() {
        val hsl = Color(0xFF808080).toHsl()
        assertEquals(0f, hsl.saturation, 0f)
        assertEquals(0.5f, hsl.lightness, tolerance)
    }

    @Test
    fun pureBlackAndWhiteAreTheLightnessExtremes() {
        assertEquals(0f, Color.Black.toHsl().lightness, 0f)
        assertEquals(1f, Color.White.toHsl().lightness, 0f)
    }

    @Test
    fun theSurfaceSitsBetweenTheBackgroundAndThePanel() {
        val background = Color(0xFF121212)
        val panel = Color(0xFF424242)
        val ladder = deriveSurfaceLadder(background, panel, accent = Color(0xFFEDEAE0))
        val surface = ladder.surface.toHsl().lightness
        assertTrue(surface > background.toHsl().lightness)
        assertTrue(surface < panel.toHsl().lightness)
    }

    @Test
    fun eachHigherTierIsLighterThanTheOneBelow() {
        val panel = Color(0xFF424242)
        val ladder = deriveSurfaceLadder(Color(0xFF121212), panel, accent = Color(0xFFEDEAE0))
        val elevated = ladder.elevatedPanel.toHsl().lightness
        val overlay = ladder.overlayPanel.toHsl().lightness
        assertTrue(elevated > panel.toHsl().lightness)
        assertTrue(overlay > elevated)
    }

    @Test
    fun theTiersNeverClipToPureWhiteOnAnAlreadyLightPanel() {
        val ladder = deriveSurfaceLadder(Color(0xFFF2F2F2), Color(0xFFF8F8F8), accent = Color(0xFFEDEAE0))
        assertTrue(ladder.elevatedPanel.toHsl().lightness <= 1f)
        assertTrue(ladder.overlayPanel.toHsl().lightness <= 1f)
    }

    @Test
    fun aColorTooCloseToTheReferenceIsPushedLighter() {
        val panel = Color(0xFF424242)
        val tooClose = Color(0xFF454545)
        val fixed = tooClose.ensureLightnessGap(panel, minGap = 0.26f, awayFromWhite = false)
        assertTrue(fixed.toHsl().lightness >= panel.toHsl().lightness + 0.26f - tolerance)
    }

    @Test
    fun aColorTooCloseToTheReferenceIsPushedDarkerWhenMovingAwayFromWhite() {
        val panel = Color(0xFFE0E0E0)
        val tooClose = Color(0xFFDCDCDC)
        val fixed = tooClose.ensureLightnessGap(panel, minGap = 0.26f, awayFromWhite = true)
        assertTrue(fixed.toHsl().lightness <= panel.toHsl().lightness - 0.26f + tolerance)
    }

    @Test
    fun aColorThatAlreadyHasEnoughGapIsLeftAlone() {
        val panel = Color(0xFF424242)
        val farAway = Color(0xFFCCCCCC)
        assertColorEquals(farAway, farAway.ensureLightnessGap(panel, minGap = 0.26f, awayFromWhite = false))
    }

    @Test
    fun enforcingTheGapKeepsTheHue() {
        val panel = Color(0xFF342D3A)
        val purple = Color(0xFF5A4880)
        val fixed = purple.ensureLightnessGap(panel, minGap = 0.4f, awayFromWhite = false)
        assertEquals(purple.toHsl().hue, fixed.toHsl().hue, tolerance)
        assertEquals(purple.toHsl().saturation, fixed.toHsl().saturation, tolerance)
    }
}
