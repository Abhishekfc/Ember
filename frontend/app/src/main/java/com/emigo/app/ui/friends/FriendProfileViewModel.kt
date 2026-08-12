package com.emigo.app.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.FriendRepository
import com.emigo.app.data.SafetyRepository
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.data.remote.dto.ReportReason
import kotlinx.coroutines.launch

// Matches RECIPIENT_PICKER_FRIENDS_LIMIT (CameraViewModel/RecipientPickerViewModel) — same
// "generously high ceiling, not a real pagination limit" reasoning, needed here to reliably find
// one specific friend by id in a single call rather than paging through 30 at a time.
private const val FULL_FRIENDS_LOOKUP_LIMIT = 500

class FriendProfileViewModel(
    private val repository: FriendRepository,
    private val safetyRepository: SafetyRepository,
    initialSubject: ProfileSubject,
) : ViewModel() {

    var subject by mutableStateOf(initialSubject)
        private set
    var isUpdatingPin by mutableStateOf(false)
        private set
    var isRemoving by mutableStateOf(false)
        private set
    var isAccepting by mutableStateOf(false)
        private set
    var isRejecting by mutableStateOf(false)
        private set
    var isSendingRequest by mutableStateOf(false)
        private set
    var isCancelling by mutableStateOf(false)
        private set
    var isBlocking by mutableStateOf(false)
        private set
    var isReporting by mutableStateOf(false)
        private set
    /** Set only on a successful report — the screen shows a brief confirmation off this rather
     * than a toast, then the caller clears it once shown. Separate from [errorMessage] since
     * a report failure and a report success both need their own distinct, one-shot message. */
    var reportSubmitted by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        // A search result that's already a friend (found again by name/username, not through the
        // Friends tab) carries none of the real friend data — no photo, no streak, no pin state —
        // and isn't this user's to Add/Accept/Decline. Resolving it to the exact same
        // ProfileSubject.Friend a Friends-tab visit would produce is what makes this genuinely
        // one profile page rather than a second, degraded one for whichever entry point happened
        // to be used.
        val result = (initialSubject as? ProfileSubject.SearchResult)?.result
        if (result != null && result.requested && !result.isPendingFromMe && !result.isPendingFromThem) {
            resolveExistingFriend(result.userId)
        }
    }

    private fun resolveExistingFriend(userId: String) {
        viewModelScope.launch {
            repository.getFriends(limit = FULL_FRIENDS_LOOKUP_LIMIT).onSuccess { page ->
                page.items.find { it.friendId == userId }?.let { subject = ProfileSubject.Friend(it) }
            }
        }
    }

    /** [onUpdated] hands the caller the exact fresh [FriendSummaryDto] the moment this succeeds —
     * the Friends tab merges it straight into its own list on the spot (see
     * FriendsViewModel.applyUpdatedFriend), so pinning shows up there instantly with no refetch,
     * instead of only on the next time that screen happens to reload. */
    fun togglePin(onUpdated: (FriendSummaryDto) -> Unit = {}) {
        val friend = (subject as? ProfileSubject.Friend)?.summary ?: return
        viewModelScope.launch {
            isUpdatingPin = true
            errorMessage = null
            repository.setPinned(friend.friendshipId, pinned = !friend.pinnedByMe).fold(
                onSuccess = {
                    // The pin endpoint's own response hardcodes streak/lastActivityAt (it isn't
                    // the query that computes those) — copying only the field it actually updated
                    // onto what's already known-correct, rather than taking its response as-is,
                    // is what keeps this from clobbering a real streak with a bogus zero.
                    val updated = friend.copy(pinnedByMe = it.pinnedByMe)
                    subject = ProfileSubject.Friend(updated)
                    onUpdated(updated)
                },
                onFailure = { errorMessage = it.message ?: "Couldn't update pin" },
            )
            isUpdatingPin = false
        }
    }

    fun removeFriend(onRemoved: () -> Unit) {
        val friendshipId = subject.friendshipId ?: return
        viewModelScope.launch {
            isRemoving = true
            errorMessage = null
            repository.removeFriend(friendshipId).fold(
                onSuccess = { onRemoved() },
                onFailure = { errorMessage = it.message ?: "Couldn't remove friend" },
            )
            isRemoving = false
        }
    }

    /** [onAccepted] hands the caller the freshly-created [FriendSummaryDto] — the Friends tab
     * adds it straight to its own list on the spot (see FriendsViewModel.addFriendLocally), the
     * same instant-update reasoning as [togglePin]'s [onUpdated]. */
    fun acceptRequest(onAccepted: (FriendSummaryDto) -> Unit) {
        val friendshipId = subject.friendshipId ?: return
        viewModelScope.launch {
            isAccepting = true
            errorMessage = null
            repository.acceptFriendRequest(friendshipId).fold(
                onSuccess = { onAccepted(it) },
                onFailure = { errorMessage = it.message ?: "Couldn't accept request" },
            )
            isAccepting = false
        }
    }

    /** No dedicated "decline" endpoint exists server-side — same DELETE /friends/{friendshipId}
     * call "remove friend" already uses (see FriendsViewModel.rejectRequest for the identical
     * reasoning), which the backend already allows regardless of the friendship's status as long
     * as the caller is part of it. */
    fun rejectRequest(onRejected: () -> Unit) {
        val friendshipId = subject.friendshipId ?: return
        viewModelScope.launch {
            isRejecting = true
            errorMessage = null
            repository.removeFriend(friendshipId).fold(
                onSuccess = { onRejected() },
                onFailure = { errorMessage = it.message ?: "Couldn't decline request" },
            )
            isRejecting = false
        }
    }

    /** Stays on the profile rather than taking a completion callback like the other three — the
     * button just needs to flip from Add to a Requested/cancel state in place, not leave the
     * screen the way accepting/declining/removing all do. */
    fun sendRequest() {
        val result = (subject as? ProfileSubject.SearchResult)?.result ?: return
        viewModelScope.launch {
            isSendingRequest = true
            errorMessage = null
            repository.sendFriendRequest(result.userId).fold(
                onSuccess = { sent ->
                    subject = ProfileSubject.SearchResult(
                        result.copy(requested = true, friendshipId = sent.friendshipId, isPendingFromMe = true),
                    )
                },
                onFailure = { errorMessage = it.message ?: "Couldn't send request" },
            )
            isSendingRequest = false
        }
    }

    /** Mirrors [FindPeopleViewModel.cancelRequest] for the same case reached from a profile page
     * instead of the search list row. */
    fun cancelRequest() {
        val result = (subject as? ProfileSubject.SearchResult)?.result ?: return
        val friendshipId = result.friendshipId ?: return
        viewModelScope.launch {
            isCancelling = true
            errorMessage = null
            repository.removeFriend(friendshipId).fold(
                onSuccess = {
                    subject = ProfileSubject.SearchResult(
                        result.copy(requested = false, friendshipId = null, isPendingFromMe = false),
                    )
                },
                onFailure = { errorMessage = it.message ?: "Couldn't cancel request" },
            )
            isCancelling = false
        }
    }

    /** Blocking always leaves the profile — there's nothing left to show once it succeeds (the
     * backend also removes any friendship/pending request between the two, see BlockService's
     * own doc comment), so [onBlocked] mirrors [removeFriend]'s own "leave the screen" callback
     * rather than updating [subject] in place the way [togglePin] does. */
    fun blockUser(onBlocked: () -> Unit) {
        viewModelScope.launch {
            isBlocking = true
            errorMessage = null
            safetyRepository.blockUser(subject.userId).fold(
                onSuccess = { onBlocked() },
                onFailure = { errorMessage = it.message ?: "Couldn't block this person" },
            )
            isBlocking = false
        }
    }

    /** Stays on the profile, unlike [blockUser] — reporting doesn't change the relationship at
     * all, it's purely a moderation record (see ReportService's own doc comment), so there's
     * nothing here that needs the screen to close. [reportSubmitted] drives a brief confirmation
     * the screen shows and then clears via [dismissReportConfirmation]. */
    fun reportUser(reason: ReportReason, details: String? = null) {
        viewModelScope.launch {
            isReporting = true
            errorMessage = null
            safetyRepository.reportUser(subject.userId, reason, details).fold(
                onSuccess = { reportSubmitted = true },
                onFailure = { errorMessage = it.message ?: "Couldn't submit report" },
            )
            isReporting = false
        }
    }

    fun dismissReportConfirmation() {
        reportSubmitted = false
    }
}
