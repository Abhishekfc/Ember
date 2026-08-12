package com.emigo.app.ui.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.ActivityRepository
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.remote.dto.ActivityEventDto
import com.emigo.app.data.remote.dto.ActivityLastSeenDto
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

    /** True only while a manual pull-to-refresh gesture is driving the reload — same split (and
     * same reasoning) HomeViewModel and FriendsViewModel both make: [isLoading] also covers the
     * automatic load fired from init on every app start, which with a warm cache would otherwise
     * show a refresh spinner the user never asked for. */
    var isPullRefreshing by mutableStateOf(false)
        private set

    // Drives the Activity tab's nav-dock badge — now the actual count of unseen events, not
    // just whether any exist, so the badge itself can show a number. `createdAt` is an ISO-8601
    // string (`Instant.toString()` server-side), so plain string comparison already sorts
    // chronologically the same way parsing and comparing Instants would — no need to actually
    // parse it just to count how many are newer. null (nothing seen yet, a fresh install) counts
    // every event as new, same as "seen" being from before all of them.
    var newActivityCount by mutableStateOf(0)
        private set
    val hasNewActivity: Boolean get() = newActivityCount > 0
    private var lastSeenAt: String? = null

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
        // explanation. Both local reads below happen *before* any network call and complete
        // first (not just get launched first), so the badge has its best-known value the instant
        // the app opens — the previous version awaited the real getActivityLastSeen() network
        // call before ever touching the local cache, which meant the whole badge waited on a
        // round trip even though a cached answer was sitting right there on disk.
        viewModelScope.launch {
            lastSeenAt = localCache.readObject<ActivityLastSeenDto>(LocalListCache.KEY_ACTIVITY_LAST_SEEN)?.lastSeenAt
            localCache.read<ActivityEventDto>(LocalListCache.KEY_ACTIVITY)?.let {
                events = it
                recomputeNewActivityCount()
            }
            // The real, authoritative value now follows — a per-account marker from the backend,
            // not just trusted from the local copy above. This is what makes a fresh install (no
            // local cache at all) or opening the app on a second device correct itself instead of
            // being stuck showing already-seen activity as new: the cached read above is only
            // ever an optimistic first paint, this is what's actually trusted. Best-effort: a
            // failed check just leaves the cached/optimistic value in place for this session
            // rather than blocking the rest of the tab from loading over it.
            repository.getActivityLastSeen().onSuccess {
                lastSeenAt = it.lastSeenAt
                localCache.writeObject(LocalListCache.KEY_ACTIVITY_LAST_SEEN, it)
                recomputeNewActivityCount()
            }
            loadActivity()
        }
    }

    private fun recomputeNewActivityCount() {
        val seenAt = lastSeenAt
        newActivityCount = if (seenAt == null) events.size else events.count { it.createdAt > seenAt }
    }

    /** Called once the Activity tab actually becomes the visible page — see MainActivity's own
     * pager-settle effect. Persists "now" as the new "seen" marker server-side, so the dot
     * doesn't come back for events already on screen by the time this fires, and stays cleared
     * even across a reinstall.
     *
     * Always runs, regardless of [hasNewActivity]'s current value — that used to guard this with
     * `if (!hasNewActivity) return`, which raced the very first [loadActivity] call: landing on
     * this tab before that initial fetch resolves means [newActivityCount] is still its default
     * `0`, so the guard skipped marking anything seen; moments later, that fetch's own
     * [recomputeNewActivityCount] would then turn the badge back on for activity the user was
     * already looking at, with no way left to clear it short of leaving and returning. Always
     * marking is a trivial extra request in the common case and the only way to be correct here.
     */
    fun markSeen() {
        newActivityCount = 0
        viewModelScope.launch {
            repository.markActivitySeen().onSuccess {
                lastSeenAt = it.lastSeenAt
                // Kept in step with the server response — without this, restarting the app in
                // the moment right after marking seen (before any other write to this key
                // happens to occur) would read back the stale pre-mark value on the next cold
                // start's optimistic first paint, briefly showing already-seen activity as new.
                localCache.writeObject(LocalListCache.KEY_ACTIVITY_LAST_SEEN, it)
            }
        }
    }

    fun loadActivity(isPullRefresh: Boolean = false) {
        // init's own call and a pull-to-refresh landing close together could otherwise overlap —
        // whichever completes last wins regardless of which started later.
        if (isFetchingActivity) return
        viewModelScope.launch {
            isFetchingActivity = true
            isLoading = true
            if (isPullRefresh) isPullRefreshing = true
            errorMessage = null
            repository.getActivity(forceRefresh = isPullRefresh, limit = PAGE_SIZE).fold(
                onSuccess = { page ->
                    events = page.items
                    hasMore = page.hasMore
                    localCache.write(LocalListCache.KEY_ACTIVITY, page.items)
                    recomputeNewActivityCount()
                },
                onFailure = { errorMessage = it.message ?: "Couldn't load activity" },
            )
            isLoading = false
            isPullRefreshing = false
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
