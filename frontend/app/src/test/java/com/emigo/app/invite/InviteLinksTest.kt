package com.emigo.app.invite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteLinksTest {

    @Test
    fun aUsernameBecomesAPersonalInviteLink() {
        assertEquals("https://emigo.live/i/ann", inviteLinkFor("ann"))
        assertEquals("https://emigo.live/i/Ann_Lee.99", inviteLinkFor("Ann_Lee.99"))
    }

    @Test
    fun surroundingSpacesAreIgnored() {
        assertEquals("https://emigo.live/i/ann", inviteLinkFor("  ann "))
    }

    @Test
    fun noUsernameFallsBackToTheStorePage() {
        assertEquals(PLAY_STORE_URL, inviteLinkFor(null))
        assertEquals(PLAY_STORE_URL, inviteLinkFor(""))
        assertEquals(PLAY_STORE_URL, inviteLinkFor("   "))
    }

    @Test
    fun anOddUsernameIsNeverPutInALink() {
        assertEquals(PLAY_STORE_URL, inviteLinkFor("an n"))
        assertEquals(PLAY_STORE_URL, inviteLinkFor("ann/../x"))
        assertEquals(PLAY_STORE_URL, inviteLinkFor("ann?x=1"))
        assertEquals(PLAY_STORE_URL, inviteLinkFor("<script>"))
        assertEquals(PLAY_STORE_URL, inviteLinkFor("a".repeat(65)))
    }

    @Test
    fun usernamesFollowTheServersRule() {
        assertTrue(isValidUsername("a"))
        assertTrue(isValidUsername("a.b_c9"))
        assertTrue(isValidUsername("a".repeat(64)))
        assertFalse(isValidUsername(""))
        assertFalse(isValidUsername(null))
        assertFalse(isValidUsername("ann-lee"))
        assertFalse(isValidUsername("ann lee"))
    }

    @Test
    fun theReferrerGivesBackTheInviter() {
        assertEquals("ann", usernameFromReferrer("invite=ann"))
        assertEquals("Ann_Lee.99", usernameFromReferrer("invite=Ann_Lee.99"))
    }

    @Test
    fun theReferrerMayCarryOtherPartsAndEncoding() {
        assertEquals("ann", usernameFromReferrer("utm_source=x&invite=ann&utm_medium=y"))
        assertEquals("ann", usernameFromReferrer("invite%3Dann".replace("%3D", "=")))
        assertEquals("ann", usernameFromReferrer("invite=%61nn"))
    }

    @Test
    fun noReferrerOrNoInviteMeansNobody() {
        assertNull(usernameFromReferrer(null))
        assertNull(usernameFromReferrer(""))
        assertNull(usernameFromReferrer("utm_source=google-play&utm_medium=organic"))
        assertNull(usernameFromReferrer("invite="))
        assertNull(usernameFromReferrer("invite"))
        assertNull(usernameFromReferrer("notinvite=ann"))
    }

    @Test
    fun aDamagedOrHostileReferrerIsRejected() {
        assertNull(usernameFromReferrer("invite=ann%20lee"))
        assertNull(usernameFromReferrer("invite=../etc"))
        assertNull(usernameFromReferrer("invite=<script>alert(1)</script>"))
        assertNull(usernameFromReferrer("invite=" + "a".repeat(65)))
        assertNull(usernameFromReferrer("invite=%ZZ"))
    }

    @Test
    fun theFirstInviteWins() {
        assertEquals("ann", usernameFromReferrer("invite=ann&invite=bob"))
    }
}
