package com.emigo.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRegistryTest {

    @Test
    fun everyThemeKeyHasADefinitionThatPointsBackAtIt() {
        for (key in ThemeKey.values()) {
            assertEquals(key, emberThemeDefinition(key).key)
        }
    }

    @Test
    fun theDefaultThemeIsFree() {
        // ThemeViewModel falls back to the default when a subscription lapses, so a locked default
        // would be overridden at once.
        assertFalse(ThemeKey.DEFAULT.locked)
    }

    @Test
    fun savedThemeNamesAreNeverRenamed() {
        // ThemePreferenceStore saves a theme by enum name. Renaming a constant would reset every
        // device that had it selected back to the default.
        val expected = setOf("EMBER", "EMBER_NEW", "BLAZE", "NOIR", "AURORA", "CYBER", "BOTANICA", "CITRUS", "FROST")
        assertEquals(expected, ThemeKey.values().map { it.name }.toSet())
    }

    @Test
    fun atLeastOneThemeIsFreeAndOneIsGold() {
        assertTrue(ThemeKey.values().any { !it.locked })
        assertTrue(ThemeKey.values().any { it.locked })
    }

    @Test
    fun softSecondaryTextKeepsItsContrastAgainstThePanelInEveryTheme() {
        // mutedDim is enforced to sit at least 0.26 lighter than the panel on dark themes.
        for (key in ThemeKey.values()) {
            val colors = emberThemeDefinition(key).colors
            if (colors.isLight) continue
            val gap = colors.mutedDim.toHsl().lightness - colors.panel.toHsl().lightness
            assertTrue("$key mutedDim gap is only $gap", gap >= 0.25f)
        }
    }

    @Test
    fun theSurfaceTiersOfEveryThemeStepUpInLightness() {
        for (key in ThemeKey.values()) {
            val colors = emberThemeDefinition(key).colors
            val panel = colors.panel.toHsl().lightness
            val elevated = colors.elevatedPanel.toHsl().lightness
            val overlay = colors.overlayPanel.toHsl().lightness
            assertTrue("$key elevated is not lighter than panel", elevated > panel)
            assertTrue("$key overlay is not lighter than elevated", overlay > elevated)
        }
    }
}
