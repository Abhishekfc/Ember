package com.ember.app.ui.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.FriendRepository
import com.ember.app.data.local.LocalListCache
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.RecipientListDto
import kotlinx.coroutines.launch

/** A generously high ceiling for "give me every friend to choose a recipient from" — not a real
 * pagination page size, just far above any real user's friend count. */
private const val RECIPIENT_PICKER_FRIENDS_LIMIT = 500

class RecipientPickerViewModel(
    private val repository: FriendRepository,
    private val localCache: LocalListCache,
    initialSelectedFriendIds: Set<String>,
) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var selectedFriendIds by mutableStateOf(initialSelectedFriendIds)
        private set

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

    /** The rows the list actually shows. Only a saved custom list actually narrows this — Recent
     * still shows every friend (with just the recent ones checked), since the point of tapping
     * Recent is "start from who I sent to last" while still being free to add or drop people, not
     * "I only ever want to see these people again." A custom list is the opposite: the whole
     * reason to save one is to jump straight to that fixed group without the rest of the list in
     * the way. */
    val visibleFriends: List<FriendSummaryDto>
        get() {
            val filterIds = customLists.firstOrNull { it.id == activeFilterId }?.friendIds?.toSet() ?: return friends
            return friends.filter { it.friendId in filterIds }
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
            localCache.read<FriendSummaryDto>(LocalListCache.KEY_FRIENDS)?.let { friends = it }
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

    fun loadFriends() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.getFriends(limit = RECIPIENT_PICKER_FRIENDS_LIMIT).fold(
                onSuccess = { page -> friends = page.items },
                // A failed refresh must not wipe out whatever the cache (or a previous successful
                // fetch this session) already populated — only the error banner reflects it.
                onFailure = { errorMessage = it.message ?: "Couldn't load your friends" },
            )
            isLoading = false
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
