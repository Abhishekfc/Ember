package com.emigo.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeyondViewportPagesTest {

    @Test
    fun homeStaysComposedFromEveryPage() {
        // The bug: Home was rebuilt from scratch when a nav-dock tap jumped back to it from
        // Friends or Settings, because it was out of the pager's composed range there.
        for (page in 0 until PAGE_COUNT) {
            val reach = beyondViewportPagesFor(page)
            assertTrue("Home must be composed while on page $page", PAGE_HOME in (page - reach)..(page + reach))
        }
    }

    @Test
    fun theOpeningPageComposesOnlyItsNeighboursSoLaunchIsUnchanged() {
        assertEquals(1, beyondViewportPagesFor(PAGE_CAMERA))
    }

    @Test
    fun theNeighboursAreAlwaysComposed() {
        for (page in 0 until PAGE_COUNT) {
            assertTrue(beyondViewportPagesFor(page) >= 1)
        }
    }

    @Test
    fun pagesNearHomeAreUnchanged() {
        assertEquals(1, beyondViewportPagesFor(PAGE_MEMORIES))
        assertEquals(1, beyondViewportPagesFor(PAGE_HOME))
        assertEquals(1, beyondViewportPagesFor(PAGE_CAMERA))
    }

    @Test
    fun friendsAndSettingsReachBackToHome() {
        assertEquals(2, beyondViewportPagesFor(PAGE_FRIENDS))
        assertEquals(3, beyondViewportPagesFor(PAGE_SETTINGS))
    }
}
