package com.ember.app.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import com.ember.app.data.local.LocalListCache
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import kotlinx.coroutines.launch

/** Matches the backend's own default `limit` for this endpoint — kept as one named constant,
 * used for every page fetched (first and subsequent), rather than repeated as a bare number. */
private const val PAGE_SIZE = 30

class FriendsViewModel(
    private val repository: FriendRepository,
    private val localCache: LocalListCache,
    // Lets other long-lived ViewModels with their own separate copy of the friend list (Camera's
    // recipient picker, mainly) find out a request was just accepted here, without this ViewModel
    // needing any reference back to them — see EmberApplication.friendsChangedEvents.
    private val onFriendsChanged: () -> Unit = {},
) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set
    var pendingRequests by mutableStateOf<List<PendingFriendRequestDto>>(emptyList())
        private set
    var acceptingRequestIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var isLoading by mutableStateOf(true)
        private set

    /** True only while a manual pull-to-refresh gesture is driving the reload — kept separate
     * from [isLoading] so automatic reloads don't pop the pull-refresh spinner at the top when
     * the user never actually pulled down. Same split (and same reasoning) HomeViewModel already
     * makes for its own feed: [isLoading] covers *any* load, including the one this ViewModel
     * fires from init on every app start, which — since a cache hit means there's already content
     * on screen by then — otherwise read as "refreshing" the moment the Friends tab was opened
     * after a restart. */
    var isPullRefreshing by mutableStateOf(false)
        private set

    // Separate from isLoading (which starts `true` purely to show a spinner before the very
    // first fetch runs) — using isLoading itself as loadFriends' re-entrancy guard meant the
    // very first call from init saw it already `true` and returned immediately, before ever
    // reaching the line that sets it back to `false`. isLoading got stuck `true` forever, and
    // every future loadFriends call (pull-to-refresh, after pinning a friend, anything) was
    // silently dropped for the rest of the app's session. This one starts `false` and exists
    // purely to prevent overlapping fetches, never to drive UI.
    private var isFetchingFriends = false
    /** True only while fetching the *next* page — kept separate from [isLoading] so scrolling
     * near the end of an already-loaded list shows a small inline spinner at the bottom, not
     * the full-screen one meant for the very first load. */
    var isLoadingMore by mutableStateOf(false)
        private set
    /** Whether another page exists beyond what's already in [friends] — checked (and not just
     * inferred from list size) so a page that happens to come back exactly [PAGE_SIZE] long
     * doesn't get mistaken for the last one. */
    var hasMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var searchQuery by mutableStateOf("")
        private set

    val filteredFriends: List<FriendSummaryDto>
        get() = if (searchQuery.isBlank()) {
            friends
        } else {
            friends.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.username.contains(searchQuery, ignoreCase = true)
            }
        }

    init {
        // Instagram/Snapchat-style instant-on-reopen — see HomeViewModel's init for the fuller
        // explanation. Read must complete first (not just be launched first) so there's no race
        // between it and the fresh fetch for who sets `friends`/`pendingRequests` first. Pending
        // requests specifically used to have no cache backing at all — only ever set once
        // loadFriends' own network call landed — which is what made the Friends nav-dock badge
        // visibly pop in about a second after the app opened instead of being there immediately.
        viewModelScope.launch {
            localCache.read<FriendSummaryDto>(LocalListCache.KEY_FRIENDS)?.let { friends = it }
            localCache.read<PendingFriendRequestDto>(LocalListCache.KEY_PENDING_FRIEND_REQUESTS)?.let { pendingRequests = it }
            loadFriends()
        }
    }

    fun onSearchQueryChange(value: String) {
        searchQuery = value
    }

    /** The one place [pendingRequests] is ever written — every call site below funnels through
     * this instead of assigning the property directly, so the on-disk cache can never drift out
     * of sync with what's actually showing (which a direct assignment at each of the half-dozen
     * mutation sites below would risk one of them quietly forgetting to do). */
    private fun updatePendingRequests(updated: List<PendingFriendRequestDto>) {
        pendingRequests = updated
        viewModelScope.launch { localCache.write(LocalListCache.KEY_PENDING_FRIEND_REQUESTS, updated) }
    }

    fun loadFriends(isPullRefresh: Boolean = false) {
        // init's own call and a pull-to-refresh landing close together could otherwise overlap —
        // whichever completes last wins regardless of which started later.
        if (isFetchingFriends) return
        viewModelScope.launch {
            isFetchingFriends = true
            isLoading = true
            if (isPullRefresh) isPullRefreshing = true
            errorMessage = null
            repository.getFriends(forceRefresh = isPullRefresh, limit = PAGE_SIZE).fold(
                onSuccess = { page ->
                    friends = page.items
                    hasMore = page.hasMore
                    localCache.write(LocalListCache.KEY_FRIENDS, page.items)
                },
                onFailure = { errorMessage = it.message ?: "Couldn't load your friends" },
            )
            // Requests load is best-effort: a failure here shouldn't blank the friends list.
            repository.getPendingRequests().onSuccess { updatePendingRequests(it) }
            isLoading = false
            isPullRefreshing = false
            isFetchingFriends = false
        }
    }

    /** Same fetch as the first page of [loadFriends], for the moments a photo (received, or just
     * sent) means some friend's streak/last-activity could have changed — but deliberately never
     * touches [isLoading], which drives the pull-to-refresh spinner (see FriendsScreen). Without
     * this split, refreshing on every incoming/outgoing photo would flash that spinner in at the
     * top of the screen whenever one happened to land while this tab was open, for a refresh
     * nobody asked for — the same "silent unless promoted" reasoning HomeViewModel already
     * applies to its own feed, adapted to a screen that has no separate synced-vs-visible split
     * of its own to promote into. Silent on failure, same as [loadMoreFriends]: a background
     * refresh nobody asked for shouldn't surface an error either if it doesn't work. */
    fun refreshSilently() {
        if (isFetchingFriends) return
        viewModelScope.launch {
            isFetchingFriends = true
            repository.getFriends(limit = PAGE_SIZE).fold(
                onSuccess = { page ->
                    friends = page.items
                    hasMore = page.hasMore
                    localCache.write(LocalListCache.KEY_FRIENDS, page.items)
                },
                onFailure = {},
            )
            isFetchingFriends = false
        }
    }

    /** Fetches the next page and appends it — called as the list scrolls near its end. A no-op
     * if there's nothing more, or a fetch (first-load or another loadMore) is already in
     * flight, so a fast scroll can't fire overlapping requests for the same page. */
    fun loadMoreFriends() {
        if (!hasMore || isLoading || isLoadingMore) return
        viewModelScope.launch {
            isLoadingMore = true
            repository.getFriends(offset = friends.size, limit = PAGE_SIZE).fold(
                onSuccess = { page -> friends = friends + page.items; hasMore = page.hasMore },
                // Silent on failure — a failed background continuation shouldn't blank the
                // list that's already showing or throw a scary error over it. The user can
                // just scroll again (or pull to refresh) to retry.
                onFailure = {},
            )
            isLoadingMore = false
        }
    }

    /** Applied the moment a mutation succeeds on the friend-profile screen (pin/unpin, remove,
     * accept, decline) — that screen already has the exact, fresh result of its own action in
     * hand, so merging it straight into [friends]/[pendingRequests] here is instant and needs no
     * network round trip of its own. Reaching for a full [loadFriends] on every return from that
     * screen would work too, but would mean a visible refresh (spinner, brief stale-then-fresh
     * flash) for a change this screen already knows the exact outcome of. */
    fun applyUpdatedFriend(updated: FriendSummaryDto) {
        friends = friends.map { if (it.friendshipId == updated.friendshipId) updated else it }
    }

    fun removeFriendLocally(friendshipId: String) {
        friends = friends.filterNot { it.friendshipId == friendshipId }
    }

    fun removePendingRequestLocally(friendshipId: String) {
        updatePendingRequests(pendingRequests.filterNot { it.friendshipId == friendshipId })
    }

    /** Companion to [removePendingRequestLocally] for the other half of accepting a request —
     * shares the same de-dupe-on-friendshipId reasoning as [acceptRequest] below in case this
     * races with an in-flight [loadFriends] that already picked the new friend up. */
    fun addFriendLocally(newFriend: FriendSummaryDto) {
        if (friends.none { it.friendshipId == newFriend.friendshipId }) {
            friends = friends + newFriend
        }
        updatePendingRequests(pendingRequests.filterNot { it.friendshipId == newFriend.friendshipId })
    }

    fun acceptRequest(request: PendingFriendRequestDto) {
        if (request.friendshipId in acceptingRequestIds) return
        viewModelScope.launch {
            acceptingRequestIds = acceptingRequestIds + request.friendshipId
            repository.acceptFriendRequest(request.friendshipId).fold(
                onSuccess = { newFriend ->
                    updatePendingRequests(pendingRequests.filterNot { it.friendshipId == request.friendshipId })
                    friends = friends + newFriend
                    onFriendsChanged()
                },
                onFailure = { errorMessage = it.message ?: "Couldn't accept request" },
            )
            acceptingRequestIds = acceptingRequestIds - request.friendshipId
        }
    }

    /** No dedicated "decline" endpoint exists server-side — this reuses the same
     * DELETE /friends/{friendshipId} call "remove friend" already uses (see
     * FriendRepository.removeFriend), which the backend already allows regardless of the
     * friendship's status as long as the caller is part of it. Tracked in the same
     * [acceptingRequestIds] set as accepting — the chip only ever has one action in flight for a
     * given request at a time, so one busy-set covers both. */
    fun rejectRequest(request: PendingFriendRequestDto) {
        if (request.friendshipId in acceptingRequestIds) return
        viewModelScope.launch {
            acceptingRequestIds = acceptingRequestIds + request.friendshipId
            repository.removeFriend(request.friendshipId).fold(
                onSuccess = { updatePendingRequests(pendingRequests.filterNot { it.friendshipId == request.friendshipId }) },
                onFailure = { errorMessage = it.message ?: "Couldn't decline request" },
            )
            acceptingRequestIds = acceptingRequestIds - request.friendshipId
        }
    }
}
