package com.emigo.app.ui.camera

import com.emigo.app.data.remote.dto.FriendSummaryDto

/** What tapping Send does when nobody is chosen, once the friend list has been checked again. (The
 * button itself is always live: with someone chosen it simply sends.) */
internal enum class NoRecipientOutcome {
    /** No friends yet: the sheet with "Find friends" and "Invite friends". */
    SHOW_INVITE_SHEET,

    /** They do have friends (the camera's list was behind): open the picker to choose. */
    OPEN_PICKER,
}

internal fun outcomeWhenNobodyChosen(friendsAfterCheck: List<FriendSummaryDto>): NoRecipientOutcome =
    if (friendsAfterCheck.isEmpty()) NoRecipientOutcome.SHOW_INVITE_SHEET else NoRecipientOutcome.OPEN_PICKER

/** True when somebody in [chosenIds] isn't in [friends]. Send resolves the chosen ids through the
 * camera's own friend list, so an id it doesn't know counts as nobody chosen and Send stays grey. */
internal fun hasUnknownRecipient(friends: List<FriendSummaryDto>, chosenIds: Set<String>): Boolean {
    if (chosenIds.isEmpty()) return false
    val known = friends.mapTo(HashSet()) { it.friendId }
    return !known.containsAll(chosenIds)
}

/** The friend list the camera should hold once [chosenIds] have been picked. The camera loads its
 * list once and can be a friend behind (someone added after it looked), while the picker reloads
 * every time it opens. So when the camera's list is missing someone who was chosen and the
 * picker's list has them all, the picker's list is used; in every other case the camera's own list
 * is left exactly as it was. */
internal fun friendsAfterRecipientChoice(
    current: List<FriendSummaryDto>,
    fromPicker: List<FriendSummaryDto>,
    chosenIds: Set<String>,
): List<FriendSummaryDto> {
    if (!hasUnknownRecipient(current, chosenIds)) return current
    return if (hasUnknownRecipient(fromPicker, chosenIds)) current else fromPicker
}
