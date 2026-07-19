package com.ember.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.app.data.PhotoRepository
import com.ember.app.data.UserRepository
import com.ember.app.data.local.SeenPhotoStore
import com.ember.app.data.local.TokenStore
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.UserProfileDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class HomeViewModel(
    private val repository: PhotoRepository,
    private val tokenStore: TokenStore,
    private val userRepository: UserRepository,
    private val seenPhotoStore: SeenPhotoStore,
    // Lets the widget piggyback on the feed loads the app is already doing, rather than
    // fetching anything of its own — see WidgetPhotoSync.
    private val onFeedLoaded: (List<FeedItem>) -> Unit = {},
) : ViewModel() {

    var feedItems by mutableStateOf<List<FeedItem>>(emptyList())
        private set

    /** The signed-in user's display name, for the greeting header. Saved at login, so it can
     * be null for sessions that predate that (falls back to a plain greeting). */
    var userName by mutableStateOf<String?>(null)
        private set

    /** The signed-in user's own profile photo, for the header chip — fetched once at init,
     * piggybacking on the same "fetch when the screen normally would" principle as the feed. */
    var profilePhotoUrl by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** The friend whose photos are currently shown in the featured card. */
    var selectedFriendId by mutableStateOf<String?>(null)
        private set

    // Each friend keeps their own carousel position, so switching away and back
    // reopens the same photo instead of resetting to the first one.
    private val photoIndexByFriend = mutableStateMapOf<String, Int>()

    // Latest photoId the user has actually seen per friend. A friend whose newest
    // photo differs from this is "unseen" (gradient avatar ring); a later upload
    // changes their latest photoId and automatically flips them back to unseen.
    private val seenLatestPhotoByFriend = mutableStateMapOf<String, String>()

    val selectedItem: FeedItem?
        get() = feedItems.firstOrNull { it.friendId == selectedFriendId }

    fun selectFriend(friendId: String) {
        selectedFriendId = friendId
    }

    fun photoIndexFor(friendId: String): Int = photoIndexByFriend[friendId] ?: 0

    fun setPhotoIndex(friendId: String, index: Int) {
        photoIndexByFriend[friendId] = index
    }

    fun markLatestSeen(friendId: String, photoId: String) {
        if (seenLatestPhotoByFriend[friendId] == photoId) return
        seenLatestPhotoByFriend[friendId] = photoId
        viewModelScope.launch { seenPhotoStore.save(seenLatestPhotoByFriend.toMap()) }
    }

    fun hasUnseenPhoto(item: FeedItem): Boolean =
        seenLatestPhotoByFriend[item.friendId] != item.photos.last().photoId

    /** Called after editing on the (separate) MyProfileScreen so the header greeting and chip
     * reflect changes immediately, without this ViewModel needing its own copy of the edit flow. */
    fun applyProfileUpdate(profile: UserProfileDto) {
        userName = profile.displayName
        profilePhotoUrl = profile.profilePhotoUrl
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
        loadFeed()
        viewModelScope.launch { userName = tokenStore.displayName.first() }
        viewModelScope.launch { userRepository.getMyProfile().onSuccess { profilePhotoUrl = it.profilePhotoUrl } }
        viewModelScope.launch { seenLatestPhotoByFriend.putAll(seenPhotoStore.current()) }
    }

    fun loadFeed() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.getFeed().fold(
                onSuccess = { items ->
                    feedItems = items
                    if (items.none { it.friendId == selectedFriendId }) {
                        selectedFriendId = items.firstOrNull()?.friendId
                    }
                    items.forEach { item ->
                        val saved = photoIndexByFriend[item.friendId] ?: return@forEach
                        photoIndexByFriend[item.friendId] = saved.coerceAtMost(item.photos.lastIndex)
                    }
                    onFeedLoaded(items)
                },
                onFailure = { errorMessage = it.message ?: "Couldn't load your feed" },
            )
            isLoading = false
        }
    }
}
