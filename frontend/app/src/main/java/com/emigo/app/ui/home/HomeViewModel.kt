package com.emigo.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.ALL_FRIENDS_LIMIT
import com.emigo.app.data.FriendRepository
import com.emigo.app.data.PhotoRepository
import com.emigo.app.data.UserRepository
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.local.TokenStore
import com.emigo.app.data.remote.dto.FeedItem
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.data.remote.dto.MemoryPhotoDto
import com.emigo.app.data.remote.dto.UserProfileDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How far back [HomeViewModel.loadMemories] fetches when the account's real creation date isn't
 * known yet — a plain sanity backstop, not a data-aware limit, so a brand-new account isn't left
 * waiting on that profile fetch just to load its own (empty) history. */
private const val MEMORIES_HISTORY_LIMIT_YEARS = 5L

// Must match PhotoService.PHOTO_GRACE_PERIOD_HOURS on the backend — see [dropExpiredPhotos] for
// why the client re-applies this same rule to data it already has, instead of only ever trusting
// whatever the server decided at the moment of the last successful fetch.
private const val PHOTO_GRACE_PERIOD_HOURS = 24L

/** The backend already applies this exact rule — see
 * PhotoRecipientRepository.findVisibleFeedPhotos — but only *at fetch time*. A phone that goes
 * offline (or simply never gets a chance to sync again — no data connection, backgrounded with
 * no push, etc.) keeps showing whatever was cached from its last successful fetch, verbatim, with
 * nothing to notice that real time has since moved a photo past its own grace period. This
 * re-runs that same rule against the device's own clock on data already in memory/on disk, using
 * exactly the logic the backend uses: each friend's own most recent photo (the last entry in
 * their already-sorted-ascending [FeedItem.photos]) never expires on its own; any of their
 * earlier photos expires once its *successor* — not itself — is more than [PHOTO_GRACE_PERIOD_HOURS]
 * old, i.e. once 24 hours have passed since it was superseded by a newer one. */
private fun dropExpiredPhotos(items: List<FeedItem>): List<FeedItem> {
    val cutoff = Instant.now().minus(PHOTO_GRACE_PERIOD_HOURS, ChronoUnit.HOURS)
    return items.mapNotNull { item ->
        val photos = item.photos
        val freshPhotos = photos.filterIndexed { index, _ ->
            if (index == photos.lastIndex) {
                true
            } else {
                val supersededAt = runCatching { Instant.parse(photos[index + 1].createdAt) }.getOrNull()
                supersededAt == null || supersededAt.isAfter(cutoff)
            }
        }
        if (freshPhotos.isEmpty()) null else item.copy(photos = freshPhotos)
    }
}

/** Read synchronously (see MainActivity.onCreate) and handed straight to HomeViewModel's
 * constructor, rather than read asynchronously inside its own init — a coroutine launched
 * inside init, however fast, cannot possibly resolve before Compose's very first composition,
 * so feedItems/memories/profile fields would sit at empty/null defaults for at least one real
 * frame no matter how quick the underlying read was. Handing already-read values straight to
 * the constructor means the very first frame is already correct. */
data class InitialHomeCache(
    val feedItems: List<FeedItem> = emptyList(),
    val memories: List<MemoryPhotoDto> = emptyList(),
    val profile: UserProfileDto? = null,
)

class HomeViewModel(
    private val repository: PhotoRepository,
    private val tokenStore: TokenStore,
    private val userRepository: UserRepository,
    // Same shared instance (and its own 30s TTL cache) Camera's recipient picker and the Friends
    // tab already read through — reused here rather than fetched independently, purely to look up
    // each feed friend's own real profile photo for the avatar row below the card (see
    // FriendAvatarRow's own call site) instead of duplicating that data or the network call.
    private val friendRepository: FriendRepository,
    private val localCache: LocalListCache,
    initialCache: InitialHomeCache = InitialHomeCache(),
    // Lets the widget piggyback on the feed loads the app is already doing, rather than
    // fetching anything of its own — see WidgetPhotoSync.
    private val onFeedLoaded: (List<FeedItem>) -> Unit = {},
) : ViewModel() {

    // feedItems is the frozen "current browsing session" the pager/avatar row actually render.
    // syncedFeedItems is the latest the backend has actually reported, updated by every fetch —
    // foreground or background — the instant it completes. These are deliberately kept separate:
    // without the split, a silent background sync could rewrite feedItems (and therefore the
    // flattened carousel + pager position, see HomeScreen's buildHomeCarousel) out from under
    // someone mid-swipe, since feed entries are sorted by most-recently-active friend and a
    // single new photo re-sorts the whole list. See promoteSyncedFeed for the only paths allowed
    // to copy synced into the visible session.
    var feedItems by mutableStateOf(dropExpiredPhotos(initialCache.feedItems))
        private set
    var syncedFeedItems by mutableStateOf(dropExpiredPhotos(initialCache.feedItems))
        private set

    // Purely for FriendAvatarRow's own profile-photo lookup by friendId (see its call site) —
    // FeedItem itself deliberately doesn't carry this (a friend's real profile photo, versus what
    // they've actually sent, doesn't belong on a *feed* row). Loaded once via the shared
    // FriendRepository cache, same as Camera/Friends already do, not refetched on every recompose.
    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set

    // Which of Home's two views (single-friend carousel vs. the Moments grid) is showing — lives
    // here rather than as HomeScreen's own local Compose state so it survives swiping away to
    // another tab and back. This ViewModel is hoisted in MainActivity and outlives that; the
    // composable itself is torn down and rebuilt on every return visit, which is what silently
    // reset the selection back to HOME every time before this moved here.
    internal var homeViewMode by mutableStateOf(HomeViewMode.HOME)
        private set

    internal fun setHomeViewMode(mode: HomeViewMode) {
        homeViewMode = mode
    }

    // Set once the very first real fetch (not the synchronous cache hydration at construction)
    // completes — see loadFeed, which always promotes that one fetch regardless of isPullRefresh
    // since there's no established session yet to protect. Publicly readable (and a real Compose
    // state, not a plain var) so HomeScreen's skeleton-loader condition can tell "we've never
    // gotten a real answer yet" apart from "we already know this feed is empty, this is just a
    // refresh of that same known-empty state" — showing a skeleton for the latter only to have it
    // resolve right back to the empty state a moment later reads as a loading bug, not a refresh.
    var hasCompletedFirstSync by mutableStateOf(false)
        private set

    /** True once [syncedFeedItems] contains a photo [feedItems] doesn't — real new content has
     * synced in the background while the current session is still showing older content. Drives
     * the "New memories available" indicator; nothing clears it except [promoteSyncedFeed]. */
    val hasNewFeedAvailable: Boolean
        get() {
            val knownPhotoIds = feedItems.flatMapTo(mutableSetOf()) { item -> item.photos.map { it.photoId } }
            return syncedFeedItems.any { item -> item.photos.any { it.photoId !in knownPhotoIds } }
        }

    /** Every saved Memories photo this account has, newest first — one flat list covering the
     * account's whole history rather than one calendar month at a time (see [loadMemories]'s own
     * doc comment for why). Seeded synchronously from [initialCache] so the grid has something to
     * show before the real fetch lands. */
    var memories by mutableStateOf(initialCache.memories.sortedByDescending { it.createdAt })
        private set

    /** True only while the very first fetch (or a just-sent-photo refresh) is in flight — see
     * [loadMemories]. */
    var isLoadingMemories by mutableStateOf(false)
        private set

    /** The month this account was created — the real start of the range [loadMemories] fetches;
     * there's nothing to show before it. Null until the profile fetch in [init] lands, in which
     * case a plain time-based sanity backstop ([MEMORIES_HISTORY_LIMIT_YEARS]) is used instead so
     * a brand-new account isn't left waiting on that fetch just to load its own (empty) history. */
    private var accountCreatedMonth by mutableStateOf(initialCache.profile?.createdAt?.toYearMonthOrNull())

    /** Deletes a saved Memories photo for real (see PhotoService.delete on the backend — this
     * isn't a soft "unsave", the storage is actually freed). Removes it from the in-memory list on
     * success so the grid reflects it immediately without a full re-fetch; a failure leaves the
     * list untouched. */
    suspend fun deleteMemoryPhoto(photoId: String): Result<Unit> =
        repository.deletePhoto(photoId).onSuccess {
            memories = memories.filterNot { it.photoId == photoId }
        }

    /** The signed-in user's display name, for the greeting header. Saved at login, so it can
     * be null for sessions that predate that (falls back to a plain greeting). */
    var userName by mutableStateOf<String?>(null)
        private set

    /** The signed-in user's own profile photo, for the header chip — hydrated from
     * [initialCache] if a cached profile exists, then refreshed for real below. */
    var profilePhotoUrl by mutableStateOf(initialCache.profile?.profilePhotoUrl)
        private set

    /** The signed-in user's @handle — fetched alongside [profilePhotoUrl] from the same
     * getMyProfile() call, reused as-is by Settings' profile header so it doesn't need its own
     * network round-trip just to show who's signed in. */
    var username by mutableStateOf(initialCache.profile?.username)
        private set
    var isLoading by mutableStateOf(true)
        private set

    // Separate from isLoading, which starts `true` so the very first composition shows a
    // spinner before any fetch has actually run yet — using isLoading itself as loadFeed's
    // re-entrancy guard meant the very first call from init saw it already `true` and returned
    // immediately, *before* ever reaching the code that sets it back to `false`. isLoading got
    // stuck `true` forever, and every future loadFeed call (pull-to-refresh, after sending a
    // photo, anything) was silently dropped for the rest of the app's session. This one starts
    // `false` and exists purely to prevent overlapping fetches, never to drive UI.
    private var isFetchingFeed = false

    /** True only while a manual pull-to-refresh gesture is driving the reload — kept separate
     * from [isLoading] so background reloads (e.g. right after sending a photo) don't pop the
     * pull-refresh spinner at the top when the user never actually pulled down. */
    var isPullRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** The friend whose photos are currently shown in the featured card. */
    var selectedFriendId by mutableStateOf<String?>(null)
        private set

    // Each friend keeps their own carousel position, so switching away and back
    // reopens the same photo instead of resetting to the first one.
    private val photoIndexByFriend = mutableStateMapOf<String, Int>()

    val selectedItem: FeedItem?
        get() = feedItems.firstOrNull { it.friendId == selectedFriendId }

    fun selectFriend(friendId: String) {
        selectedFriendId = friendId
    }

    // The carousel itself displays newest-first (see FeaturedPhotoCard), so page 0 is always
    // the latest photo — a plain 0 default is correct without needing per-friend photo counts.
    fun photoIndexFor(friendId: String): Int = photoIndexByFriend[friendId] ?: 0

    fun setPhotoIndex(friendId: String, index: Int) {
        photoIndexByFriend[friendId] = index
    }

    // Seen state itself lives on PhotoEntryDto.seen now — the backend is the source of truth
    // (scoped per photo *and* recipient, see PhotoEntryDto's own doc comment), not something
    // tracked locally on this ViewModel any more.
    fun hasUnseenPhoto(item: FeedItem): Boolean = item.photos.any { !it.seen }

    /** Marks one specific photo seen for this user — updates [feedItems] optimistically first
     * (so the ring/dots react immediately) and pushes the real change to the backend in the
     * background; a failed push just means the next real feed fetch reports it unseen again,
     * not a user-facing error. */
    fun markPhotoSeen(friendId: String, photoId: String) {
        val item = feedItems.firstOrNull { it.friendId == friendId } ?: return
        val photo = item.photos.firstOrNull { it.photoId == photoId } ?: return
        if (photo.seen) return
        val markSeen: (List<FeedItem>) -> List<FeedItem> = { items ->
            items.map { fi ->
                if (fi.friendId != friendId) {
                    fi
                } else {
                    fi.copy(photos = fi.photos.map { p -> if (p.photoId == photoId) p.copy(seen = true) else p })
                }
            }
        }
        // Applied to both, not just the visible session — otherwise a promotion landing before
        // the backend confirms this (see promoteSyncedFeed) could regress this exact photo back
        // to unseen, since syncedFeedItems would still reflect the pre-mark-seen snapshot.
        feedItems = markSeen(feedItems)
        syncedFeedItems = markSeen(syncedFeedItems)
        viewModelScope.launch { repository.markPhotoSeen(photoId) }
    }

    /** Copies [syncedFeedItems] into the visible [feedItems] session — the only thing allowed to
     * change what the pager/avatar row actually render. Never called by a silent background sync
     * on its own; only by [loadFeed]'s very first fetch (nothing to protect yet), an explicit
     * pull-to-refresh, [onHomeSessionStart], or [revealNewFeed] — see each for why. */
    private fun promoteSyncedFeed() {
        val items = syncedFeedItems
        feedItems = items
        if (items.none { it.friendId == selectedFriendId }) {
            selectedFriendId = items.firstOrNull()?.friendId
        }
        items.forEach { item ->
            val saved = photoIndexByFriend[item.friendId] ?: return@forEach
            photoIndexByFriend[item.friendId] = saved.coerceAtMost(item.photos.lastIndex)
        }
    }

    /** Called whenever Home becomes the active/settled page again (see MainActivity/HomeScreen's
     * isActive) — leaving Home and coming back is one of the two moments background-synced
     * content is allowed to become visible, the other being an explicit pull-to-refresh (handled
     * directly in loadFeed). A no-op whenever nothing has actually diverged. */
    fun onHomeSessionStart() {
        // Re-validates against the device's own clock every time Home becomes active again —
        // see [dropExpiredPhotos] — so a photo that's simply aged past the freshness window
        // while the app sat elsewhere (or offline) disappears the moment you come back to look
        // at it, not only once some future network fetch happens to succeed.
        feedItems = dropExpiredPhotos(feedItems)
        syncedFeedItems = dropExpiredPhotos(syncedFeedItems)
        if (hasNewFeedAvailable) promoteSyncedFeed()
    }

    /** Called from the "New memories available" indicator itself — a deliberate, explicit user
     * action to reveal what's already synced, same intent as pull-to-refresh. */
    fun revealNewFeed() {
        if (hasNewFeedAvailable) promoteSyncedFeed()
    }

    /** Called after editing on the (separate) MyProfileScreen so the header greeting and chip
     * reflect changes immediately, without this ViewModel needing its own copy of the edit flow. */
    fun applyProfileUpdate(profile: UserProfileDto) {
        userName = profile.displayName
        profilePhotoUrl = profile.profilePhotoUrl
        username = profile.username
    }

    val greeting: String = run {
        val hour = LocalDateTime.now().hour
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val dateText: String = run {
        val now = LocalDateTime.now()
        val day = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val month = now.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "$day, $month ${now.dayOfMonth}"
    }

    init {
        // No async cache read here any more — see InitialHomeCache's doc comment for why that
        // moved to a synchronous read before this ViewModel is even constructed. This is just
        // the real, fresh fetch, same as it would be on any other load.
        loadFeed()
        loadMemories()
        // Seeded from the same LocalListCache.KEY_FRIENDS entry FriendsViewModel already reads
        // and writes — this ViewModel never wrote to it before, so on an offline cold start
        // `friends` stayed empty for this whole session and FriendAvatarRow fell back to initials
        // even though the Friends tab (reading the same key) still had real photos to show. No
        // new cache needed: same key, same underlying DataStore file, just read here too.
        viewModelScope.launch { localCache.read<FriendSummaryDto>(LocalListCache.KEY_FRIENDS)?.let { friends = it } }
        viewModelScope.launch { friendRepository.getFriends(limit = ALL_FRIENDS_LIMIT).onSuccess { friends = it.items } }
        viewModelScope.launch { userName = tokenStore.displayName.first() }
        viewModelScope.launch {
            userRepository.getMyProfile().onSuccess { profile ->
                profilePhotoUrl = profile.profilePhotoUrl
                username = profile.username
                accountCreatedMonth = profile.createdAt.toYearMonthOrNull()
                localCache.writeObject(LocalListCache.KEY_PROFILE, profile)
            }
        }
    }

    fun loadFeed(isPullRefresh: Boolean = false) {
        // Without this, a pull-to-refresh landing while the initial load (or a prior refresh) is
        // still in flight launches a second overlapping call — whichever happens to *complete*
        // last then wins and overwrites feedItems, regardless of which one was actually started
        // more recently, and each sets isLoading/isPullRefreshing independently so the spinner
        // could flip off mid-refresh while the other call is still running.
        if (isFetchingFeed) return
        viewModelScope.launch {
            isFetchingFeed = true
            isLoading = true
            if (isPullRefresh) isPullRefreshing = true
            errorMessage = null
            repository.getFeed(forceRefresh = isPullRefresh).fold(
                onSuccess = { items ->
                    // The server already excludes anything stale as of the moment this request
                    // was answered — filtered again here (cheap, and normally a no-op) purely so
                    // this assignment can never be the one place that skips the same rule
                    // [dropExpiredPhotos] enforces everywhere else this list gets set.
                    syncedFeedItems = dropExpiredPhotos(items)
                    // The very first real fetch always promotes immediately — there's no
                    // established browsing session yet to protect, this is just the initial
                    // cache (or empty state) settling to ground truth. Every fetch after that
                    // only promotes if it's an explicit pull-to-refresh; a silent background
                    // sync instead just updates syncedFeedItems and leaves hasNewFeedAvailable/
                    // the "New memories available" indicator to surface it non-intrusively.
                    if (!hasCompletedFirstSync || isPullRefresh) {
                        promoteSyncedFeed()
                    }
                    hasCompletedFirstSync = true
                    onFeedLoaded(items)
                    viewModelScope.launch { localCache.write(LocalListCache.KEY_FEED, items) }
                },
                onFailure = { errorMessage = it.message ?: "Couldn't load your feed" },
            )
            isLoading = false
            isPullRefreshing = false
            isFetchingFeed = false
        }
    }

    private var isLoadingMemoriesRefresh = false

    /** Called at init, and again right after a successful send (see MainActivity's `onSent`) so a
     * just-sent photo shows up in the grid immediately rather than waiting for the next time
     * Memories happens to be reopened. Always re-fetches the account's *entire* history in one
     * range — Memories is a single continuous grid now (grouped by recency, see
     * MemoriesScreen.kt's own section-building logic), not something paged one calendar month at
     * a time, so there's no separate "which month" bookkeeping left to preserve across a refresh. */
    fun loadMemories() {
        // init and a just-sent photo's onSent callback can both fire close together — without
        // this, the slower of two overlapping calls could win and overwrite the newer one.
        if (isLoadingMemoriesRefresh) return
        viewModelScope.launch {
            isLoadingMemoriesRefresh = true
            isLoadingMemories = true
            val zone = ZoneId.systemDefault()
            val startMonth = accountCreatedMonth ?: YearMonth.now().minusYears(MEMORIES_HISTORY_LIMIT_YEARS)
            val start = startMonth.atDay(1).atStartOfDay(zone).toInstant()
            repository.getMemoriesForRange(start, Instant.now()).onSuccess { items ->
                val sorted = items.sortedByDescending { it.createdAt }
                memories = sorted
                localCache.write(LocalListCache.KEY_MEMORIES, sorted)
            }
            isLoadingMemoriesRefresh = false
            isLoadingMemories = false
        }
    }

}

/** Parses an ISO-8601 instant string into the [YearMonth] it falls in, in local time — null for
 * anything absent/unparseable (an old cached profile predating this field, say) rather than
 * throwing. */
private fun String?.toYearMonthOrNull(): YearMonth? =
    this?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
        ?.let { YearMonth.from(it) }
