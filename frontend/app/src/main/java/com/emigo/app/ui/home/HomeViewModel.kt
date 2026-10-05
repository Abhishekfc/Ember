package com.emigo.app.ui.home

import com.emigo.app.R
import com.emigo.app.StringProvider

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

/** How far back [HomeViewModel.loadMemories] fetches when the account's creation date isn't known
 * yet: a sanity backstop, so a brand-new account isn't left waiting on the profile fetch to load
 * its own (empty) history. */
private const val MEMORIES_HISTORY_LIMIT_YEARS = 5L

// Must match PhotoService.PHOTO_GRACE_PERIOD_HOURS on the backend (see [dropExpiredPhotos] for why
// the client re-applies the rule to data it already has).
private const val PHOTO_GRACE_PERIOD_HOURS = 24L

/** The backend applies this rule (see PhotoRecipientRepository.findVisibleFeedPhotos) only at fetch
 * time. A phone that goes offline, or just never syncs again, keeps showing its last fetched feed
 * with nothing to notice that time has moved a photo past its grace period. This re-runs the same
 * rule on the device's clock against data already in memory or on disk: a friend's most recent
 * photo (the last entry in their ascending-sorted [FeedItem.photos]) never expires; an earlier
 * photo expires once its successor, not itself, is more than [PHOTO_GRACE_PERIOD_HOURS] old, i.e.
 * 24 hours after it was superseded. */
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

/** Read synchronously (see MainActivity.onCreate) and passed to HomeViewModel's constructor instead
 * of read in its init: a coroutine launched in init can't resolve before the first composition, so
 * feedItems, memories and profile would sit at empty defaults for at least one frame. Passing
 * already-read values makes the first frame correct. */
data class InitialHomeCache(
    val feedItems: List<FeedItem> = emptyList(),
    val memories: List<MemoryPhotoDto> = emptyList(),
    val profile: UserProfileDto? = null,
)

class HomeViewModel(
    private val strings: StringProvider,
    private val repository: PhotoRepository,
    private val tokenStore: TokenStore,
    private val userRepository: UserRepository,
    // The shared instance (and its 30s TTL cache) that Camera's recipient picker and the Friends tab
    // read through, reused only to look up each feed friend's real profile photo for the avatar row
    // (see FriendAvatarRow) instead of duplicating the data or the call.
    private val friendRepository: FriendRepository,
    private val localCache: LocalListCache,
    initialCache: InitialHomeCache = InitialHomeCache(),
    // Lets the widget reuse the feed loads the app already does instead of fetching its own (see
    // WidgetPhotoSync).
    private val onFeedLoaded: (List<FeedItem>) -> Unit = {},
) : ViewModel() {

    // feedItems is the frozen "current browsing session" the pager and avatar row render.
    // syncedFeedItems is the latest the backend reported, updated by every fetch (foreground or
    // background) as it completes. They're kept separate because otherwise a silent background sync
    // could rewrite feedItems (and so the flattened carousel and pager position, see HomeScreen's
    // buildHomeCarousel) under someone mid-swipe: feed entries sort by most recently active friend,
    // so one new photo re-sorts the whole list. See promoteSyncedFeed for the only paths allowed to
    // copy synced into the visible session.
    var feedItems by mutableStateOf(dropExpiredPhotos(initialCache.feedItems))
        private set
    var syncedFeedItems by mutableStateOf(dropExpiredPhotos(initialCache.feedItems))
        private set

    // Only for FriendAvatarRow's profile-photo lookup by friendId. FeedItem deliberately doesn't
    // carry it (a friend's profile photo isn't what they sent, and doesn't belong on a feed row).
    // Loaded once via the shared FriendRepository cache, like Camera and Friends, not refetched on
    // every recompose.
    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set

    // Which Home view (carousel or Moments grid) is showing. Held here, not as HomeScreen's local
    // state, so it survives swiping to another tab and back: this ViewModel is hoisted in
    // MainActivity and outlives the composable, which is rebuilt on every return and used to reset
    // the selection to HOME.
    internal var homeViewMode by mutableStateOf(HomeViewMode.HOME)
        private set

    internal fun setHomeViewMode(mode: HomeViewMode) {
        homeViewMode = mode
    }

    /**
     * Home's chosen spacing scale, held here for the same reason as [homeViewMode]: the composable is
     * rebuilt on every return visit and shouldn't re-decide from scratch.
     *
     * That re-decision was visible. The scale comes from how much vertical room is left once the
     * header and nav dock are measured (see homeFoldMetricsFor); on the frame after Home is rebuilt
     * the header measures zero, so the calculation sees a header's worth of extra room, picks the
     * roomy scale, and corrects itself when the real measurement lands. The view-mode pill appeared
     * large and shrank every time you came back from a nested screen.
     *
     * Kept as the last confidently measured answer so a rebuild reuses it instead of guessing from
     * half-measured values. Null only until the first real measurement of a session.
     */
    internal var foldMetrics by mutableStateOf<HomeFoldMetrics?>(null)
        private set

    internal fun setFoldMetrics(metrics: HomeFoldMetrics) {
        foldMetrics = metrics
    }

    // Set once the first real fetch (not the synchronous cache hydration at construction) completes;
    // loadFeed always promotes that one fetch, since there's no session yet to protect. A public
    // Compose state so HomeScreen's skeleton condition can tell "never got a real answer" from
    // "known empty, this is just a refresh": showing a skeleton for the latter, then resolving back
    // to the empty state, reads as a loading bug.
    var hasCompletedFirstSync by mutableStateOf(false)
        private set

    /** True once [syncedFeedItems] has a photo [feedItems] doesn't: new content synced in the
     * background while the session still shows older content. Drives the "New memories available"
     * indicator; only [promoteSyncedFeed] clears it. */
    val hasNewFeedAvailable: Boolean
        get() {
            val knownPhotoIds = feedItems.flatMapTo(mutableSetOf()) { item -> item.photos.map { it.photoId } }
            return syncedFeedItems.any { item -> item.photos.any { it.photoId !in knownPhotoIds } }
        }

    /** Every saved Memories photo, newest first: one flat list of the whole history, not a calendar
     * month at a time (see [loadMemories]). Seeded synchronously from [initialCache] so the grid has
     * something before the real fetch lands. */
    var memories by mutableStateOf(initialCache.memories.sortedByDescending { it.createdAt })
        private set

    /** True only while the first fetch (or a just-sent-photo refresh) is in flight; see
     * [loadMemories]. */
    var isLoadingMemories by mutableStateOf(false)
        private set

    /** The month this account was created: the real start of the range [loadMemories] fetches, since
     * there's nothing before it. Null until the profile fetch in [init] lands; then a time-based
     * backstop ([MEMORIES_HISTORY_LIMIT_YEARS]) is used so a new account isn't left waiting on that
     * fetch. */
    private var accountCreatedMonth by mutableStateOf(initialCache.profile?.createdAt?.toYearMonthOrNull())

    /** Deletes a saved Memories photo for real (see PhotoService.delete on the backend: not a soft
     * "unsave", the storage is freed). On success it's removed from the in-memory list so the grid
     * updates without a re-fetch; a failure leaves the list untouched. */
    suspend fun deleteMemoryPhoto(photoId: String): Result<Unit> =
        repository.deletePhoto(photoId).onSuccess {
            memories = memories.filterNot { it.photoId == photoId }
        }

    /** The signed-in user's display name for the greeting header. Saved at login, so it can be null
     * for sessions that predate that (falls back to a plain greeting). */
    var userName by mutableStateOf<String?>(null)
        private set

    /** The signed-in user's profile photo for the header chip: hydrated from [initialCache] if
     * cached, then refreshed below. */
    var profilePhotoUrl by mutableStateOf(initialCache.profile?.profilePhotoUrl)
        private set

    /** The signed-in user's @handle, from the same getMyProfile() call as [profilePhotoUrl], reused
     * by Settings' profile header so it needs no round trip of its own. */
    var username by mutableStateOf(initialCache.profile?.username)
        private set
    var isLoading by mutableStateOf(true)
        private set

    // Separate from isLoading, which starts true so the first composition shows a spinner before any
    // fetch ran. Using isLoading as loadFeed's re-entrancy guard meant the first call from init saw
    // it already true and returned before ever setting it back to false: isLoading stuck true
    // forever, and every later loadFeed (pull-to-refresh, after a send, anything) was silently
    // dropped for the session. This starts false and only prevents overlapping fetches, never
    // driving UI.
    private var isFetchingFeed = false

    /** True only while a manual pull-to-refresh is driving the reload, separate from [isLoading] so
     * background reloads (e.g. right after sending a photo) don't pop the pull-refresh spinner when
     * the user never pulled. */
    var isPullRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** The friend whose photos are currently shown in the featured card. */
    var selectedFriendId by mutableStateOf<String?>(null)
        private set

    // Each friend keeps their own carousel position, so switching away and back reopens the same
    // photo instead of the first.
    private val photoIndexByFriend = mutableStateMapOf<String, Int>()

    val selectedItem: FeedItem?
        get() = feedItems.firstOrNull { it.friendId == selectedFriendId }

    fun selectFriend(friendId: String) {
        selectedFriendId = friendId
    }

    // The carousel displays newest first (see FeaturedPhotoCard), so page 0 is always the latest and
    // a plain 0 default is right without per-friend photo counts.
    fun photoIndexFor(friendId: String): Int = photoIndexByFriend[friendId] ?: 0

    fun setPhotoIndex(friendId: String, index: Int) {
        photoIndexByFriend[friendId] = index
    }

    // Seen state lives on PhotoEntryDto.seen: the backend is the source of truth (scoped per photo and
    // recipient, see PhotoEntryDto), no longer tracked locally here.
    fun hasUnseenPhoto(item: FeedItem): Boolean = item.photos.any { !it.seen }

    /** Marks one photo seen for this user: updates [feedItems] optimistically first (so the ring and
     * dots react at once) and pushes the change to the backend in the background. A failed push just
     * means the next real fetch reports it unseen again, not a user-facing error. */
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
        // Applied to both, not just the visible session: otherwise a promotion landing before the
        // backend confirms this (see promoteSyncedFeed) could regress this photo to unseen, since
        // syncedFeedItems would still hold the pre-mark snapshot.
        feedItems = markSeen(feedItems)
        syncedFeedItems = markSeen(syncedFeedItems)
        viewModelScope.launch { repository.markPhotoSeen(photoId) }
    }

    /** Copies [syncedFeedItems] into the visible [feedItems] session, the only thing allowed to
     * change what the pager and avatar row render. Never called by a silent background sync on its
     * own; only by [loadFeed]'s first fetch (nothing to protect yet), an explicit pull-to-refresh,
     * [onHomeSessionStart] or [revealNewFeed]. */
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

    /** Called whenever Home becomes the active, settled page again (see MainActivity and HomeScreen's
     * isActive). Returning to Home is one of two moments background-synced content may become
     * visible; the other is an explicit pull-to-refresh (handled in loadFeed). A no-op if nothing
     * diverged. */
    fun onHomeSessionStart() {
        // Re-validates against the device clock every time Home becomes active (see
        // [dropExpiredPhotos]), so a photo that aged past the freshness window while the app sat
        // elsewhere or offline disappears when you return, not only after some later network fetch
        // succeeds.
        feedItems = dropExpiredPhotos(feedItems)
        syncedFeedItems = dropExpiredPhotos(syncedFeedItems)
        if (hasNewFeedAvailable) promoteSyncedFeed()
    }

    /** Called from the "New memories available" indicator: a deliberate user action to reveal what's
     * already synced, with the same intent as pull-to-refresh. */
    fun revealNewFeed() {
        if (hasNewFeedAvailable) promoteSyncedFeed()
    }

    /** Called after editing on MyProfileScreen so the header greeting and chip update at once,
     * without this ViewModel needing its own copy of the edit flow. */
    fun applyProfileUpdate(profile: UserProfileDto) {
        userName = profile.displayName
        profilePhotoUrl = profile.profilePhotoUrl
        username = profile.username
    }

    val greeting: String = run {
        val hour = LocalDateTime.now().hour
        when {
            hour < 12 -> strings.get(R.string.home_greeting_morning)
            hour < 17 -> strings.get(R.string.home_greeting_afternoon)
            else -> strings.get(R.string.home_greeting_evening)
        }
    }

    val dateText: String = run {
        val now = LocalDateTime.now()
        val day = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val month = now.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "$day, $month ${now.dayOfMonth}"
    }

    init {
        // No async cache read here: it moved to a synchronous read before this ViewModel is
        // constructed (see InitialHomeCache). This is just the real, fresh fetch.
        loadFeed()
        loadMemories()
        // Seeded from the same LocalListCache.KEY_FRIENDS entry FriendsViewModel reads and writes.
        // This ViewModel never read it before, so on an offline cold start `friends` stayed empty for
        // the whole session and FriendAvatarRow fell back to initials even though the Friends tab
        // still had real photos from the same key.
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
        // Without this, a pull-to-refresh landing while the initial load (or a prior refresh) is in
        // flight launches an overlapping call: whichever completes last wins and overwrites
        // feedItems regardless of which started more recently, and each sets
        // isLoading/isPullRefreshing on its own, so the spinner could flip off mid-refresh.
        if (isFetchingFeed) return
        // friends (and FriendAvatarRow's pictures) is otherwise fetched once, in init. Pull-to-refresh
        // is this screen's one explicit "check for anything new" gesture, so it re-fetches friends
        // too instead of leaving a friend's updated profile photo stale for the session.
        if (isPullRefresh) {
            viewModelScope.launch {
                friendRepository.getFriends(forceRefresh = true, limit = ALL_FRIENDS_LIMIT).onSuccess { friends = it.items }
            }
        }
        viewModelScope.launch {
            isFetchingFeed = true
            isLoading = true
            if (isPullRefresh) isPullRefreshing = true
            errorMessage = null
            repository.getFeed(forceRefresh = isPullRefresh).fold(
                onSuccess = { items ->
                    // The server already excludes anything stale as of this response; filtering again
                    // (cheap, normally a no-op) so this assignment can't be the one place that skips
                    // the rule [dropExpiredPhotos] enforces everywhere else.
                    syncedFeedItems = dropExpiredPhotos(items)
                    // The first real fetch always promotes: there's no browsing session yet to
                    // protect, it's just the initial cache (or empty state) settling to ground truth.
                    // Later fetches promote only on an explicit pull-to-refresh; a silent background
                    // sync updates syncedFeedItems and lets hasNewFeedAvailable and the "New memories
                    // available" indicator surface it non-intrusively.
                    if (!hasCompletedFirstSync || isPullRefresh) {
                        promoteSyncedFeed()
                    }
                    hasCompletedFirstSync = true
                    onFeedLoaded(items)
                    viewModelScope.launch { localCache.write(LocalListCache.KEY_FEED, items) }
                },
                onFailure = { errorMessage = it.message ?: strings.get(R.string.error_load_feed) },
            )
            isLoading = false
            isPullRefreshing = false
            isFetchingFeed = false
        }
    }

    private var isLoadingMemoriesRefresh = false

    /** Called at init and again right after a successful send (see MainActivity's `onSent`) so a
     * just-sent photo shows in the grid at once instead of the next time Memories is reopened.
     * Always re-fetches the account's whole history in one range: Memories is one continuous grid
     * (grouped by recency, see MemoriesScreen.kt), not paged by calendar month, so there's no
     * per-month bookkeeping to preserve across a refresh. */
    fun loadMemories() {
        // init and a just-sent photo's onSent callback can fire close together; without this the
        // slower of two overlapping calls could win and overwrite the newer one.
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

/** Parses an ISO-8601 instant into the [YearMonth] it falls in, in local time; null for anything
 * absent or unparseable (e.g. an old cached profile predating the field) instead of throwing. */
private fun String?.toYearMonthOrNull(): YearMonth? =
    this?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
        ?.let { YearMonth.from(it) }
