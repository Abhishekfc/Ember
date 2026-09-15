package com.emigo.app.ui.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.ALL_FRIENDS_LIMIT
import com.emigo.app.data.FriendRepository
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.data.remote.dto.RecipientListDto
import kotlinx.coroutines.launch

class RecipientPickerViewModel(
    private val repository: FriendRepository,
    private val localCache: LocalListCache,
    initialSelectedFriendIds: Set<String>,
    // CameraViewModel's own already-known friend list — fetched once, lazily, the first time the
    // Camera page is ever visited, well before Send is ever tapped. Seeding straight from this
    // instead of an empty list means a brand-new account with genuinely zero friends can show
    // "Find friends" the instant this screen opens, with no spinner-then-content flash: the
    // answer was already known in memory, this just reuses it instead of asking the server again
    // for the exact same thing. loadFriends() below still re-checks in the background regardless
    // (see its own doc comment), so this is purely about what the very first frame shows.
    initialFriends: List<FriendSummaryDto> = emptyList(),
) : ViewModel() {

    var friends by mutableStateOf(initialFriends)
        private set
    var isLoading by mutableStateOf(initialFriends.isEmpty())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var selectedFriendIds by mutableStateOf(initialSelectedFriendIds)
        private set

    /** What [visibleFriends] actually sorts by — a frozen snapshot of [selectedFriendIds], not
     * that live property itself. Refreshed once per picker *open* (see [refreshSortSnapshot],
     * called from MainActivity's own per-open LaunchedEffect, right alongside loadFriends()), and
     * never again until the next open — this ViewModel is a single long-lived instance reused
     * across every open (same store as CameraViewModel), so without this the "float selected rows
     * to the top" behavior would live-react to every tap and every badge switch during a single
     * session, reshuffling the list under the user's finger while they're still mid-selection.
     * Snapchat's own recipient list only reorders between sends, never while you're actively
     * picking — this is the same shape: whoever was selected the moment this screen opened (e.g.
     * carried over from the last real send) sorts to the top, and stays exactly there — new taps
     * this session only toggle a checkmark in place. */
    private var sortSnapshot by mutableStateOf(initialSelectedFriendIds)

    /** Who the last real send actually went to (see CameraViewModel.sendCaptured) — the
     * "Recent" badge's target selection. Empty until a send has ever gone out. */
    var recentIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Saved on the backend (see RecipientListService), not just on-device — a list created on
     * one phone needs to show up after logging into the same account on another, the same way
     * the friends list itself already does. [LocalListCache.KEY_RECIPIENT_LISTS] below is purely
     * a fast, optimistic first paint for this same data — the network calls in [createCustomList]/
     * [deleteCustomList] are what's actually authoritative. */
    var customLists by mutableStateOf<List<RecipientListDto>>(emptyList())
        private set

    /** True only while a create/delete request is actually in flight — guards against a fast
     * double-tap firing the same request twice, and lets the "+" flow show a busy state instead
     * of looking like nothing happened while it waits on the network. */
    var isMutatingLists by mutableStateOf(false)
        private set

    val allFriendIds: Set<String>
        get() = friends.map { it.friendId }.toSet()

    /** Which badge (if any) is the active *view* — set explicitly by tapping a badge (or once,
     * at load, to seed the initial view — see init below), not re-derived from the current
     * selection on every read. A derived-from-selection version was tried first, but it broke
     * the moment a single row got toggled off inside an already-filtered view: the selection no
     * longer exactly matched the badge's full membership, so the "which badge is active" check
     * failed and the view snapped back to showing every friend — exactly the moment the user is
     * mid-way through fine-tuning one specific group, not asking to see everyone again. */
    var activeFilterId by mutableStateOf<String?>(null)
        private set

    /** Typed into the search box below the badge row — narrows [visibleFriends] on top of
     * whatever badge/list is already active, rather than replacing that filter, since the point
     * of searching here is "find someone within what I'm already looking at", not "start over
     * from everyone" (unlike Friends' own search, which has no badge/list layer to sit under). */
    var searchQuery by mutableStateOf("")
        private set

    fun onSearchQueryChange(value: String) {
        searchQuery = value
    }

    /** The rows the list actually shows. Only a saved custom list actually narrows this — Recent
     * still shows every friend (with just the recent ones checked), since the point of tapping
     * Recent is "start from who I sent to last" while still being free to add or drop people, not
     * "I only ever want to see these people again." A custom list is the opposite: the whole
     * reason to save one is to jump straight to that fixed group without the rest of the list in
     * the way. [searchQuery] then narrows whichever of those this resolves to even further.
     *
     * Whatever that resolves to, rows selected as of [sortSnapshot] (not the live selection —
     * see its own doc comment) float to the top — Recent in particular can pre-check several
     * people scattered anywhere in a long alphabetical-ish list, and without this you'd have to
     * scroll the whole thing just to see who you're actually about to send to. `sortedByDescending`
     * is a stable sort, so it only ever moves those rows up as a group; it never reorders anything
     * within either group on its own. */
    val visibleFriends: List<FriendSummaryDto>
        get() {
            val filterIds = customLists.firstOrNull { it.id == activeFilterId }?.friendIds?.toSet()
            val badgeFiltered = if (filterIds == null) friends else friends.filter { it.friendId in filterIds }
            val searched = if (searchQuery.isBlank()) {
                badgeFiltered
            } else {
                badgeFiltered.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                        it.username.contains(searchQuery, ignoreCase = true)
                }
            }
            return searched.sortedByDescending { it.friendId in sortSnapshot }
        }

    /** Called once per picker open (see [sortSnapshot]'s own doc comment) — captures whatever's
     * selected right now as the order [visibleFriends] sorts by for this whole session, so later
     * taps and badge switches this same session can't reshuffle the list further. */
    fun refreshSortSnapshot() {
        sortSnapshot = selectedFriendIds
    }

    init {
        // Same instant-on-reopen cache Friends' own tab already reads (LocalListCache.KEY_FRIENDS)
        // — without this, opening the picker while offline showed nothing (and the misleading
        // "Add friends first" empty state) even though the friend list was known and already
        // sitting on disk from the last successful fetch. customLists gets the same treatment:
        // read from disk first for an instant first paint, then the real network fetch below
        // corrects it — which is what makes a list created on a *different* device actually show
        // up here instead of only ever reflecting whatever this one phone last saved locally.
        viewModelScope.launch {
            // Guarded on friends still being empty — if the constructor's own initialFriends
            // already seeded a real (possibly empty-for-real-reasons) list, this on-disk snapshot
            // must not clobber it with something potentially staler.
            if (friends.isEmpty()) {
                localCache.read<FriendSummaryDto>(LocalListCache.KEY_FRIENDS)?.let { friends = it }
            }
            recentIds = localCache.read<String>(LocalListCache.KEY_LAST_RECIPIENT_IDS).orEmpty().toSet()
            localCache.read<RecipientListDto>(LocalListCache.KEY_RECIPIENT_LISTS)?.let { customLists = it }
            // Seeds the initial view to match whatever CameraViewModel actually defaulted the
            // selection to before this screen ever opened (pinned friend, last-sent recipients,
            // or a saved list that happens to match) — one-time, at load, not a standing rule.
            activeFilterId = when {
                recentIds.isNotEmpty() && initialSelectedFriendIds == recentIds -> RECENT_BADGE_ID
                friends.isNotEmpty() && initialSelectedFriendIds == friends.map { it.friendId }.toSet() -> EVERYONE_BADGE_ID
                else -> customLists.firstOrNull { it.friendIds.toSet() == initialSelectedFriendIds }?.id
            }
            loadFriends()
            repository.getRecipientLists().onSuccess {
                customLists = it
                localCache.write(LocalListCache.KEY_RECIPIENT_LISTS, it)
            }
        }
    }

    /** Called on every picker open (see MainActivity's own doc comment on why), which used to mean
     * a visible spinner-then-content flash every single time, even when the list already had
     * perfectly good data on screen from the last open. Now mirrors FriendsViewModel's own
     * refreshSilently: the loading/error UI is reserved for the genuine first load (nothing to
     * show yet); once there's already a list on screen, this just quietly replaces it in the
     * background, and a failure leaves that existing list alone rather than interrupting it with
     * an error banner over data that's still perfectly usable. */
    fun loadFriends() {
        val isFirstLoad = friends.isEmpty()
        viewModelScope.launch {
            if (isFirstLoad) {
                isLoading = true
                errorMessage = null
            }
            repository.getFriends(limit = ALL_FRIENDS_LIMIT).fold(
                onSuccess = { page -> friends = page.items },
                onFailure = { if (isFirstLoad) errorMessage = it.message ?: "Couldn't load your friends" },
            )
            if (isFirstLoad) isLoading = false
        }
    }

    fun toggleSelected(friendId: String) {
        selectedFriendIds = if (friendId in selectedFriendIds) {
            selectedFriendIds - friendId
        } else {
            selectedFriendIds + friendId
        }
    }

    fun setSelection(ids: Set<String>) {
        selectedFriendIds = ids
    }

    fun selectRecent() {
        activeFilterId = RECENT_BADGE_ID
        setSelection(recentIds)
    }

    fun selectEveryone() {
        activeFilterId = EVERYONE_BADGE_ID
        setSelection(allFriendIds)
    }

    fun selectCustomList(list: RecipientListDto) {
        activeFilterId = list.id
        setSelection(list.friendIds.toSet())
    }

    /** Saves whatever's currently checked as a new named badge — a no-op on a blank name, an
     * empty selection, or while another list mutation is already in flight. The new list's real
     * id comes back from the server (not generated here), since every other device needs to agree
     * on the same id for this same list. */
    fun createCustomList(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || selectedFriendIds.isEmpty() || isMutatingLists) return
        viewModelScope.launch {
            isMutatingLists = true
            errorMessage = null
            repository.createRecipientList(trimmed, selectedFriendIds.toList()).fold(
                onSuccess = { created ->
                    val updated = customLists + created
                    customLists = updated
                    localCache.write(LocalListCache.KEY_RECIPIENT_LISTS, updated)
                },
                onFailure = { errorMessage = it.message ?: "Couldn't save that list" },
            )
            isMutatingLists = false
        }
    }

    /** The screen itself gates this behind a confirmation dialog on long-press — deleting here is
     * the actual, final removal once that's confirmed. Removes optimistically (falling back to
     * showing every friend again if the list being deleted was the one currently filtering the
     * view), then rolls back if the server call actually fails — a lost network connection
     * shouldn't silently leave the list looking gone on this device while it still exists on
     * every other one. */
    fun deleteCustomList(id: String) {
        if (isMutatingLists) return
        val previous = customLists
        val previousFilterId = activeFilterId
        val updated = previous.filterNot { it.id == id }
        customLists = updated
        if (activeFilterId == id) activeFilterId = null
        viewModelScope.launch {
            isMutatingLists = true
            errorMessage = null
            localCache.write(LocalListCache.KEY_RECIPIENT_LISTS, updated)
            repository.deleteRecipientList(id).onFailure {
                customLists = previous
                activeFilterId = previousFilterId
                localCache.write(LocalListCache.KEY_RECIPIENT_LISTS, previous)
                errorMessage = it.message ?: "Couldn't delete that list"
            }
            isMutatingLists = false
        }
    }

    companion object {
        const val RECENT_BADGE_ID = "recent"
        const val EVERYONE_BADGE_ID = "everyone"
    }
}
