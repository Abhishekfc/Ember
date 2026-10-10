package com.emigo.app.ui.camera

import com.emigo.app.data.remote.dto.FriendSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private fun friend(id: String) = FriendSummaryDto(
    friendshipId = "fs-$id",
    friendId = id,
    displayName = id,
    username = id,
    profilePhotoUrl = null,
    pinnedByMe = false,
    pinnedByThem = false,
    lastActivityAt = null,
    lastActivityBySelf = null,
    streak = 0,
)

/** A brand-new user adds their first friend, picks them in the picker and presses Continue. The
 * camera loaded its friend list before that friend existed, so it must take the picker's list or
 * Send reads the choice as nobody and stays grey. */
class RecipientChoiceTest {

    @Test
    fun aChosenFriendTheCameraDoesNotKnowIsNoticed() {
        assertTrue(hasUnknownRecipient(friends = emptyList(), chosenIds = setOf("ann")))
        assertTrue(hasUnknownRecipient(friends = listOf(friend("bob")), chosenIds = setOf("bob", "ann")))
    }

    @Test
    fun knownFriendsAndNobodyChosenAreFine() {
        assertFalse(hasUnknownRecipient(friends = listOf(friend("ann")), chosenIds = setOf("ann")))
        assertFalse(hasUnknownRecipient(friends = emptyList(), chosenIds = emptySet()))
    }

    @Test
    fun theFirstFriendIsTakenFromThePickersFreshList() {
        val camera = emptyList<FriendSummaryDto>()
        val picker = listOf(friend("ann"))

        val result = friendsAfterRecipientChoice(camera, picker, chosenIds = setOf("ann"))

        assertEquals(picker, result)
        assertFalse("Send now knows who was chosen", hasUnknownRecipient(result, setOf("ann")))
    }

    @Test
    fun aFriendAddedLaterIsTakenWhenOthersAreAlreadyKnown() {
        val camera = listOf(friend("bob"))
        val picker = listOf(friend("bob"), friend("ann"))

        val result = friendsAfterRecipientChoice(camera, picker, chosenIds = setOf("bob", "ann"))

        assertEquals(picker, result)
    }

    @Test
    fun theCamerasListIsLeftAloneWhenItAlreadyKnowsTheChoice() {
        val camera = listOf(friend("bob"), friend("ann"))
        val picker = listOf(friend("bob"))

        assertSame(camera, friendsAfterRecipientChoice(camera, picker, chosenIds = setOf("bob")))
    }

    @Test
    fun choosingNobodyChangesNothing() {
        val camera = listOf(friend("bob"))

        assertSame(camera, friendsAfterRecipientChoice(camera, emptyList(), chosenIds = emptySet()))
    }

    // Send is always live. With someone chosen it simply sends; these cover what a tap does when
    // nobody is chosen, once the friend list has been checked again with the server.

    @Test
    fun noFriendsAfterTheCheckShowsTheInviteSheet() {
        assertEquals(NoRecipientOutcome.SHOW_INVITE_SHEET, outcomeWhenNobodyChosen(emptyList()))
    }

    @Test
    fun friendsFoundByTheCheckOpenThePickerInstead() {
        // The camera's list was a friend behind: they do have someone, so never tell them they don't.
        assertEquals(NoRecipientOutcome.OPEN_PICKER, outcomeWhenNobodyChosen(listOf(friend("ann"))))
    }

    @Test
    fun aFirstFriendJustAddedFromTheSheetOpensThePickerNotTheSheetAgain() {
        // The reported case: they added their first friend from the sheet and came back. The camera
        // now has that friend but nobody is picked, so Send opens the picker (it used to go grey).
        assertEquals(NoRecipientOutcome.OPEN_PICKER, outcomeWhenNobodyChosen(listOf(friend("newfriend"))))
    }

    @Test
    fun severalFriendsAndNobodyPickedOpenThePicker() {
        assertEquals(NoRecipientOutcome.OPEN_PICKER, outcomeWhenNobodyChosen(listOf(friend("ann"), friend("bob"), friend("cat"))))
    }

    @Test
    fun anOutOfDatePickerListDoesNotReplaceTheCamerasList() {
        // The picker can't vouch for the choice either, so nothing is swapped in (the caller then
        // reloads the camera's own list).
        val camera = listOf(friend("bob"))
        val picker = listOf(friend("cat"))

        val result = friendsAfterRecipientChoice(camera, picker, chosenIds = setOf("ann"))

        assertSame(camera, result)
        assertTrue(hasUnknownRecipient(result, setOf("ann")))
    }
}
