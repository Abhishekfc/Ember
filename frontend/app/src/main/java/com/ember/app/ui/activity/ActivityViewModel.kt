package com.ember.app.ui.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.ActivityRepository
import com.ember.app.data.local.LocalListCache
import com.ember.app.data.remote.dto.ActivityEventDto
import kotlinx.coroutines.launch

/** Matches the backend's own default `limit` for this endpoint — kept as one named constant,
 * used for every page fetched (first and subsequent), rather than repeated as a bare number. */
private const val PAGE_SIZE = 30

class ActivityViewModel(
    private val repository: ActivityRepository,
    private val localCache: LocalListCache,
) : ViewModel() {

    var events by mutableStateOf<List<ActivityEventDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set

    // Separate from isLoading (which starts `true` purely to show a spinner before the very
    // first fetch runs) — using isLoading itself as loadActivity's re-entrancy guard meant the
    // very first call from init saw it already `true` and returned immediately, before ever
    // reaching the line that sets it back to `false`. isLoading got stuck `true` forever, and
    // every future loadActivity call (pull-to-refresh, anything) was silently dropped for the
    // rest of the app's session. This one starts `false` and exists purely to prevent
    // overlapping fetches, never to drive UI.
    private var isFetchingActivity = false
    /** True only while fetching the *next* page — kept separate from [isLoading] so scrolling
     * near the end of an already-loaded list shows a small inline spinner at the bottom, not
     * the full-screen one meant for the very first load. */
    var isLoadingMore by mutableStateOf(false)
        private set
    /** Whether another page exists beyond what's already in [events] — checked (and not just
     * inferred from list size) so a page that happens to come back exactly [PAGE_SIZE] long
     * doesn't get mistaken for the last one. */
    var hasMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        // Instagram/Snapchat-style instant-on-reopen — see HomeViewModel's init for the fuller
        // explanation. Read must complete first (not just be launched first) so there's no race
        // between it and the fresh fetch for who sets `events` first.
        viewModelScope.launch {
            localCache.read<ActivityEventDto>(LocalListCache.KEY_ACTIVITY)?.let { events = it }
            loadActivity()
        }
    }

    fun loadActivity(isPullRefresh: Boolean = false) {
        // init's own call and a pull-to-refresh landing close together could otherwise overlap —
        // whichever completes last wins regardless of which started later.
        if (isFetchingActivity) return
        viewModelScope.launch {
            isFetchingActivity = true
            isLoading = true
            errorMessage = null
            repository.getActivity(forceRefresh = isPullRefresh, limit = PAGE_SIZE).fold(
                onSuccess = { page ->
                    events = page.items
                    hasMore = page.hasMore
                    localCache.write(LocalListCache.KEY_ACTIVITY, page.items)
                },
                onFailure = { errorMessage = it.message ?: "Couldn't load activity" },
            )
            isLoading = false
            isFetchingActivity = false
        }
    }

    /** Fetches the next page and appends it — called as the list scrolls near its end. A no-op
     * if there's nothing more, or a fetch (first-load or another loadMore) is already in
     * flight, so a fast scroll can't fire overlapping requests for the same page. */
    fun loadMoreActivity() {
        if (!hasMore || isLoading || isLoadingMore) return
        viewModelScope.launch {
            isLoadingMore = true
            repository.getActivity(offset = events.size, limit = PAGE_SIZE).fold(
                onSuccess = { page -> events = events + page.items; hasMore = page.hasMore },
                // Silent on failure — a failed background continuation shouldn't blank the
                // list that's already showing or throw a scary error over it.
                onFailure = {},
            )
            isLoadingMore = false
        }
    }
}
