package com.ember.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.PhotoRepository
import com.ember.app.data.UserRepository
import com.ember.app.data.local.LocalListCache
import com.ember.app.data.local.TokenStore
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.MemoryPhotoDto
import com.ember.app.data.remote.dto.UserProfileDto
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

/** How far back "previous month" navigation is allowed to go — a plain sanity backstop (not a
 * data-aware limit; see [HomeViewModel.canGoToPreviousMonth]) so an account with zero photos
 * ever can't be clicked back through decades of guaranteed-empty months. */
private const val MEMORIES_HISTORY_LIMIT_YEARS = 5L

// Must match PhotoService.FEED_WINDOW_HOURS on the backend — see [dropExpiredPhotos] for why the
// client re-applies this same cutoff to data it already has, instead of only ever trusting
// whatever the server excluded at the moment of the last successful fetch.
private const val FEED_FRESHNESS_HOURS = 24L

/** The backend already excludes anything older than [FEED_FRESHNESS_HOURS] from every real
 * getFeed() response — but that filtering only happens *at fetch time*. A phone that goes
 * offline (or simply never gets a chance to sync again — no data connection, backgrounded with
 * no push, etc.) keeps showing whatever was cached from its last successful fetch, verbatim,
 * with nothing to notice that real time has since carried those same photos past the same
 * 24-hour cutoff the server would now enforce. This re-runs that cutoff against the device's own
 * clock on data already in memory/on disk, so a photo ages off Home even when no new network
 * call ever happens — dropping a friend's card entirely once none of their photos are left. */
private fun dropExpiredPhotos(items: List<FeedItem>): List<FeedItem> {
    val cutoff = Instant.now().minus(FEED_FRESHNESS_HOURS, ChronoUnit.HOURS)
    return items.mapNotNull { item ->
        val freshPhotos = item.photos.filter { photo ->
            val createdAt = runCatching { Instant.parse(photo.createdAt) }.getOrNull()
            createdAt == null || createdAt.isAfter(cutoff)
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

    /** One calendar month's worth of this user's own sent photos, keyed by month — backs the
     * Memories grid, which now shows exactly one month at a time (see [selectedMonth]) rather
     * than an ever-growing scroll through every month at once. Populated lazily: a month is only
     * ever fetched the first time it's navigated to, then kept here for the rest of the session
     * so navigating back to an already-visited month is instant, no refetch. Seeded from
     * [initialCache] by re-grouping its (unpartitioned) cached photos by their own real month,
     * not dumped wholesale into the current month's bucket — that cache holds whatever the old
     * single-page fetch last returned, which could span more than one month. */
    private val memoriesByMonth = mutableStateMapOf<YearMonth, List<MemoryPhotoDto>>().apply {
        initialCache.memories
            .mapNotNull { memory ->
                runCatching { Instant.parse(memory.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .getOrNull()
                    ?.let { YearMonth.from(it) to memory }
            }
            .groupBy({ it.first }, { it.second })
            .forEach { (month, items) -> put(month, items) }
    }

    /** Which month the Memories grid currently shows — always starts on the real current month
     * (never persisted/restored to wherever the user last left off), matching how Home itself
     * always opens on "now" rather than resuming mid-browse. */
    var selectedMonth by mutableStateOf(YearMonth.now())
        private set

    /** [selectedMonth]'s photos, or empty while that month hasn't loaded yet (see
     * [isLoadingSelectedMonth]). The Memories grid always renders this as a complete calendar —
     * every day of [selectedMonth], empty placeholders included — never just the days that
     * happen to have a photo, whether or not this is the real current month. */
    val memories: List<MemoryPhotoDto>
        get() = memoriesByMonth[selectedMonth] ?: emptyList()

    /** True only while fetching whichever month is currently [selectedMonth] — a month already
     * in [memoriesByMonth] never sets this, so navigating back to an already-visited month never
     * flashes a spinner over content that's already there. */
    var isLoadingSelectedMonth by mutableStateOf(false)
        private set

    private val monthsCurrentlyLoading = mutableSetOf<YearMonth>()

    /** The month this account was created — the real, correct limit for how far back "previous
     * month" should go (there's nothing to show before an account existed). Null until the
     * profile fetch in [init] lands; [canGoToPreviousMonth] falls back to a plain sanity backstop
     * for that brief window (or if the fetch never succeeds) rather than blocking navigation on
     * it entirely. */
    private var accountCreatedMonth by mutableStateOf(initialCache.profile?.createdAt?.toYearMonthOrNull())

    /** Never allowed past the real current month — there's nothing to browse in the future. */
    val canGoToNextMonth: Boolean
        get() = selectedMonth < YearMonth.now()

    /** Bounded by [accountCreatedMonth] once known — there's genuinely nothing before the month
     * this account was created, so that's the real, correct limit rather than a guess. Falls back
     * to a plain time-based sanity backstop ([MEMORIES_HISTORY_LIMIT_YEARS]) only until that
     * profile fetch lands (or on the off chance it never does), so a zero-photo account can't be
     * clicked back through decades of guaranteed-empty months in the meantime. */
    val canGoToPreviousMonth: Boolean
        get() {
            val joined = accountCreatedMonth
            return if (joined != null) selectedMonth > joined else selectedMonth > YearMonth.now().minusYears(MEMORIES_HISTORY_LIMIT_YEARS)
        }

    fun goToNextMonth() {
        if (!canGoToNextMonth) return
        selectedMonth = selectedMonth.plusMonths(1)
        ensureMonthLoaded(selectedMonth)
    }

    fun goToPreviousMonth() {
        if (!canGoToPreviousMonth) return
        selectedMonth = selectedMonth.minusMonths(1)
        ensureMonthLoaded(selectedMonth)
    }

    /** Fetches [month] if it isn't already cached (or already in flight) — the network round
     * trip that both [goToPreviousMonth]/[goToNextMonth] and the initial [loadMemories] rely on.
     * Silent on failure, same reasoning as the old loadMoreMemories: a failed background fetch
     * shouldn't blank whatever's already showing or throw a scary error over it — it just means
     * that month stays empty (indistinguishable from "really has no photos") until the user
     * navigates there again. */
    private fun ensureMonthLoaded(month: YearMonth) {
        if (month in memoriesByMonth || month in monthsCurrentlyLoading) return
        monthsCurrentlyLoading += month
        if (selectedMonth == month) isLoadingSelectedMonth = true
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val start = month.atDay(1).atStartOfDay(zone).toInstant()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
            repository.getMemoriesForRange(start, end).onSuccess { items ->
                memoriesByMonth[month] = items
                // Only the real current month is what a cold start (before any real fetch has
                // landed) should show instantly — see InitialHomeCache's own doc comment for why
                // that hydration has to be synchronous, at construction, rather than awaited here.
                if (month == YearMonth.now()) {
                    localCache.write(LocalListCache.KEY_MEMORIES, items)
                }
            }
            monthsCurrentlyLoading -= month
            if (selectedMonth == month) isLoadingSelectedMonth = false
        }
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

    /** Called at init, and again right after a successful send (see MainActivity's `onSent`) so
     * a just-sent photo shows up in the grid immediately rather than waiting for the next time
     * Memories happens to be reopened. Always jumps back to the real current month and
     * force-refetches it — a fresh send or reopen should show the latest state from the top, not
     * silently stay on whatever past month was last being browsed. Every other cached month is
     * untouched: a send can only ever add a photo to the current month. */
    fun loadMemories() {
        // init and a just-sent photo's onSent callback can both fire close together — without
        // this, the slower of two overlapping calls could win and overwrite the newer one.
        if (isLoadingMemoriesRefresh) return
        val month = YearMonth.now()
        selectedMonth = month
        viewModelScope.launch {
            isLoadingMemoriesRefresh = true
            isLoadingSelectedMonth = true
            val zone = ZoneId.systemDefault()
            val start = month.atDay(1).atStartOfDay(zone).toInstant()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
            repository.getMemoriesForRange(start, end).onSuccess { items ->
                memoriesByMonth[month] = items
                localCache.write(LocalListCache.KEY_MEMORIES, items)
            }
            isLoadingMemoriesRefresh = false
            isLoadingSelectedMonth = false
        }
    }

}

/** Parses an ISO-8601 instant string into the [YearMonth] it falls in, in local time — null for
 * anything absent/unparseable (an old cached profile predating this field, say) rather than
 * throwing. */
private fun String?.toYearMonthOrNull(): YearMonth? =
    this?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
        ?.let { YearMonth.from(it) }
