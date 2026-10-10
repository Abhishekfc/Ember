package com.emigo.app.ui.camera

import com.emigo.app.R
import com.emigo.app.core.StringProvider

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.ads.GalleryAdResult
import com.emigo.app.ads.GalleryUnlock
import com.emigo.app.ads.WatchAdForGallery
import com.emigo.app.data.repository.FriendRepository
import com.emigo.app.data.repository.PhotoRepository
import com.emigo.app.data.repository.SubscriptionRepository
import com.emigo.app.data.local.CameraHintPreferenceStore
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.ui.home.FEATURED_CARD_ASPECT_RATIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** Where the caption sits vertically, as a fraction of image height — must match where the
 * preview overlay draws it so what you see is what your friend gets. */
internal const val CAPTION_Y_FRACTION = 0.72f

/** A generously high ceiling for "give me every friend to choose a recipient from" — not a real
 * pagination page size, just far above any real user's friend count. */
private const val RECIPIENT_PICKER_FRIENDS_LIMIT = 500

/** How long the outbox button holds its filled-checkmark state before fading back to idle (see
 * [CameraViewModel.markSendComplete]). */
private const val SEND_ANIM_COMPLETE_HOLD_MS = 1200L

/** Debug switch to always show the swipe hint. Confirmed good on-device (2026-09-16); kept as a
 * named constant so re-testing a hint change is a one-line flip. */
private const val SWIPE_HINT_ALWAYS_SHOW_FOR_TESTING = false

/** Drives the outbox button's send animation (see CameraScreen's OutboxButton): SENDING from when
 * [CameraViewModel.sendCaptured] queues the upload, COMPLETE once [CameraViewModel.markSendComplete]
 * reports the real upload landed (SignedInShell collects EmberApplication.photoSendCompletedEvents),
 * then back to IDLE shortly after. Not tied to [isQueuingSend], which covers only the brief local
 * queuing step; the real upload can take much longer, especially offline, and this reflects the
 * whole span. */
enum class SendAnimState { IDLE, SENDING, COMPLETE }

class CameraViewModel(
    private val strings: StringProvider,
    private val friendRepository: FriendRepository,
    private val photoRepository: PhotoRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val localCache: LocalListCache,
    private val cameraHintPreferenceStore: CameraHintPreferenceStore,
    // Without Gold, two watched ads open the gallery for one photo (see GalleryUnlock).
    private val galleryUnlock: GalleryUnlock,
    private val watchAdForGallery: WatchAdForGallery,
) : ViewModel() {

    // Seeded synchronously (see CameraHintPreferenceStore) so a returning user who dismissed it never
    // sees it flash for a frame. Only goes true -> false, once, per account on this device (see
    // dismissSwipeHint).
    var showSwipeHint by mutableStateOf(SWIPE_HINT_ALWAYS_SHOW_FOR_TESTING || !cameraHintPreferenceStore.isDismissed())
        private set

    /** Called the first time the user navigates away from Camera (SignedInShell's settledPage
     * effect); hides the hint permanently on this device. A no-op after the first call: a cheap
     * early-out that also avoids rewriting the same true -> false transition to disk on every page
     * change. */
    fun dismissSwipeHint() {
        if (SWIPE_HINT_ALWAYS_SHOW_FOR_TESTING) return
        if (!showSwipeHint) return
        showSwipeHint = false
        cameraHintPreferenceStore.dismiss()
    }

    // Friends and selection are seeded together, synchronously, from LocalListCache's synchronous
    // mirror (readSync), not the empty defaults this used while the suspend read and network fetch
    // were in flight. Resolved with applyFriends' pinned-first-else-last-sent priority, computed once
    // here so both land on frame one instead of friends appearing on one recomposition and the
    // recipient badge catching up later (see applyFriends for why landing together matters).
    private val initialFriendsAndSelection: Pair<List<FriendSummaryDto>, Set<String>> = run {
        val cachedFriends = localCache.readSync<FriendSummaryDto>(LocalListCache.KEY_FRIENDS).orEmpty()
        val pinnedIds = cachedFriends.filter { it.pinnedByMe }.map { it.friendId }.toSet()
        val selection = pinnedIds.ifEmpty {
            val lastUsedIds = localCache.readSync<String>(LocalListCache.KEY_LAST_RECIPIENT_IDS).orEmpty().toSet()
            lastUsedIds.filterTo(mutableSetOf()) { id -> cachedFriends.any { friend -> friend.friendId == id } }
        }
        cachedFriends to selection
    }

    var friends by mutableStateOf(initialFriendsAndSelection.first)
        private set
    var selectedRecipientIds by mutableStateOf(initialFriendsAndSelection.second)
        private set

    /** True while Send, tapped with nobody chosen, asks the server once whether there are friends
     * after all. A double-tap guard for that short moment. */
    var isCheckingFriends by mutableStateOf(false)
        private set

    /** True only for the brief local step (baking the caption in, moving the file into durable
     * storage) between tapping Send and handing the upload to [PendingSendWorker]. A double-tap
     * guard for that window, not a "network in flight" flag; the upload runs in the background
     * whether or not this screen is open, with no on-screen indicator of its own (the top-right
     * corner is the bookmark button's spot). */
    var isQueuingSend by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** See [SendAnimState]. Not reset per capture like [isSaved] and [capturedFile]: the animation
     * tracks the outbox upload itself, so it keeps playing even if the user retakes or leaves Camera
     * mid-flight. */
    var sendAnimState by mutableStateOf(SendAnimState.IDLE)
        private set

    /** Called once the real upload this ViewModel last queued has landed (see [SendAnimState] for
     * where that comes from). A no-op unless an animation is in flight, so a completion echoing in
     * from an unrelated older retry can't restart a finished or never-started animation. */
    fun markSendComplete() {
        if (sendAnimState != SendAnimState.SENDING) return
        sendAnimState = SendAnimState.COMPLETE
        viewModelScope.launch {
            delay(SEND_ANIM_COMPLETE_HOLD_MS)
            sendAnimState = SendAnimState.IDLE
        }
        // The sent photo is now the outbox's newest entry; refresh so the thumbnail updates once the
        // checkmark fades out.
        refreshLastSentPhoto()
    }

    /** The outbox button's thumbnail: the account's most recent unsaved send (the first entry of
     * [SentPhotosScreen]'s list), fetched whether or not that screen was opened this session.
     * Silent on failure or empty, like other background refreshes here: the worst case is the
     * button keeps its last thumbnail or empty fallback, not an error over a decorative image. */
    var lastSentPhotoUrl by mutableStateOf<String?>(null)
        private set

    private fun refreshLastSentPhoto() {
        viewModelScope.launch {
            photoRepository.getSentPhotos().onSuccess { photos -> lastSentPhotoUrl = photos.firstOrNull()?.photoUrl }
        }
    }

    /** True once the current capture was saved to Memories (see [saveToMemories]); switches the
     * bookmark from outline to filled. Per capture, not per session: every place that sets
     * [capturedFile] (new capture, retake, gallery pick) resets it, like [captionText]. Saving is
     * one-way here; un-saving is only Memories' own delete (see MemoriesScreen). */
    var isSaved by mutableStateOf(false)
        private set
    /** True only for the brief local step (baking the caption in, moving the file into durable
     * storage) between tapping the bookmark and handing the upload to [PendingSendWorker]. Same
     * purpose as [isQueuingSend], for save instead of send. */
    var isSavingToMemories by mutableStateOf(false)
        private set

    /** True once either Save or Send has queued the real upload for this capture. The other, if
     * also tapped, chains onto that upload (see [PendingSendWorker.enqueueMarkSaved] and
     * [PendingSendWorker.enqueueAddRecipients]) instead of uploading the file twice. Reset with
     * [isSaved] on every new capture. */
    private var hasQueuedUpload = false
    /** The key of WorkManager's unique-work chain for this capture, set once from the captured
     * file's name when [capturedFile] is assigned. Deliberately not re-derived from the uploaded
     * file's later name (baking a caption, or Save's copy, both rename it, see [saveToMemories]):
     * the second action must target the same unique-work name the first used, which has to survive
     * those renames. */
    private var uploadWorkName: String? = null

    /** Gallery picking is an Ember Gold perk; free accounts get only the live camera. */
    // Seeded synchronously from the last resolved value (see SubscriptionRepository.isGoldMemberSync)
    // instead of a hardcoded false: the real check below is a suspend call with a gap, and defaulting
    // to false flashed the gallery button's lock badge over a real subscriber's unlocked feature on
    // every cold start.
    var isGoldMember by mutableStateOf(subscriptionRepository.isGoldMemberSync())
        private set
    var showGoldUpsell by mutableStateOf(false)
        private set

    /** An ad-earned gallery photo is waiting to be used. */
    var hasGalleryPass by mutableStateOf(galleryUnlock.hasPass)
        private set

    /** Ads watched so far toward the next gallery photo (fewer than [galleryAdsNeeded]). */
    var galleryAdsWatched by mutableStateOf(galleryUnlock.adsWatched)
        private set
    var isWatchingGalleryAd by mutableStateOf(false)
        private set

    /** True while the photo on the review stage came from the gallery picker, so sending it is
     * what uses up a gallery pass. */
    private var capturedFromGallery = false

    /** A captured (or gallery-picked) photo waiting on the preview stage; nothing is sent until the
     * user reviews it and taps Send. */
    var capturedFile by mutableStateOf<File?>(null)
        private set
    var captionText by mutableStateOf("")
        private set

    /** False only between [onPreviewSnapshotCaptured] (an instant frozen frame of the viewfinder,
     * shown on shutter tap so capture feels immediate; see capturePhoto in CameraScreen.kt) and the
     * real hardware capture landing via [onPhotoCaptured]. Gallery picks skip the snapshot and go
     * straight to [onPhotoCaptured], so it's true at once for them. Send gates on this so a fast
     * tap-then-send can't upload the temporary frame instead of the real photo.
     *
     * A first attempt at this was reverted after two bugs: the page transition was a `Crossfade`
     * keyed on the file value, so the snapshot-to-real swap retriggered a second fade that exposed
     * the card's black background; and the swap was slow and visible enough that the temporary
     * frame's lower fidelity read as a quality dip. Now the live/reviewing boundary in
     * CameraScreen.kt is a plain overlay (no Crossfade, so no fade through black), and the
     * snapshot-to-real swap goes through the crossfade-disabled AsyncImage request (see
     * CapturedPreview): an instant pixel swap between two frames of nearly the same scene. The real
     * capture is still full, unbounded quality; only the fleeting placeholder is viewfinder
     * resolution. */
    var isRealCaptureReady by mutableStateOf(true)
        private set

    /** The in-memory bitmap behind the instant preview stage: an already-decoded `Bitmap` (from
     * `previewView.bitmap`), not a second file-based image, so it draws the same frame it's set
     * with no async decode gap. Kept for the whole review (not cleared when the real photo lands)
     * as a fallback layer: `CapturedPreview` in CameraScreen.kt layers the real file's AsyncImage
     * (Coil, EXIF-aware decode) on top, so that decode always has something on screen to sit over
     * instead of the card's black background. */
    var previewBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // The snapshot file backing capturedFile while isRealCaptureReady is false. Tracked separately
    // (not re-derived from capturedFile) so it can be deleted once the real file supersedes it, or on
    // a retake before that happens.
    private var pendingSnapshotFile: File? = null

    init {
        refreshLastSentPhoto()
        // Deliberately NOT loadFriends() here: this ViewModel lives for the whole session (Camera is a
        // pager page, not created on demand), so anything fired from init runs on every cold start
        // whether or not Camera is opened. loadFriends() is a limit=500 fetch for the recipient
        // picker; SignedInShell calls it once, lazily, the first time the user reaches Camera.
        //
        // The local cache read below does belong here: it's disk I/O, not a network call, and without
        // it the recipient badge had no data until the lazy fetch landed, so opening Camera after a
        // restart showed the empty "Friends" state and then popped in the pinned or last-used friend's
        // avatar. The fetch still runs on arrival and refreshes this; this just starts the badge
        // correct.
        viewModelScope.launch {
            localCache.read<FriendSummaryDto>(LocalListCache.KEY_FRIENDS)?.let { cached ->
                // Guarded on friends still being empty: if the real fetch beat this read, its
                // fresher and complete list (limit=500, not the Friends tab's 30) must not be
                // replaced by this snapshot.
                if (friends.isEmpty()) applyFriends(cached)
            }
            isGoldMember = subscriptionRepository.isGoldMemberOrLastKnown()
        }
        // This instance lives for the whole session, so without this it would never see a purchase
        // made later on the Ember Gold screen until a full restart. See
        // SubscriptionRepository.isGoldMemberFlow.
        viewModelScope.launch {
            subscriptionRepository.isGoldMemberFlow.collect { isGoldMember = it }
        }
    }

    // Ordered by selectedRecipientIds, not by filtering `friends`: Set + / - (see
    // toggleSelected/setSelectedRecipients) preserve tap order as a LinkedHashSet, and filtering
    // `friends` would show the friends list's order instead of the order picked.
    val selectedFriends: List<FriendSummaryDto>
        get() {
            val friendsById = friends.associateBy { it.friendId }
            return selectedRecipientIds.mapNotNull { friendsById[it] }
        }

    // Always the plain word "Friends", never a specific name: the avatar stack (and its "+N" circle)
    // already shows who's selected, so naming them again said the same thing twice.
    val recipientLabel: String = strings.get(R.string.friends_title)

    val hasPinnedSelected: Boolean
        get() = friends.any { it.friendId in selectedRecipientIds && it.pinnedByMe }

    /** Reuses a friend list the Friends tab already fetched, when it's known complete (see
     * SignedInShell: only when FriendsViewModel has loaded everything, `hasMore == false`), instead
     * of a separate, mostly redundant network call for the same data. [loadFriends] is the fallback
     * when that isn't the case (Friends not loaded yet, or more than a page of friends). */
    fun provideFriends(list: List<FriendSummaryDto>) {
        viewModelScope.launch { applyFriends(list) }
    }

    fun loadFriends() {
        viewModelScope.launch {
            // The recipient picker needs every friend, not a scrollable page;
            // RECIPIENT_PICKER_FRIENDS_LIMIT is a generous ceiling, not a pagination boundary.
            friendRepository.getFriends(limit = RECIPIENT_PICKER_FRIENDS_LIMIT).fold(
                onSuccess = { page -> applyFriends(page.items) },
                onFailure = { errorMessage = it.message ?: strings.get(R.string.error_load_friends) },
            )
        }
    }

    // Shared by provideFriends and loadFriends so both apply the same default-recipient rule: never
    // "everyone" with no explicit choice. A silent reply-all default makes a send flow feel unsafe:
    // nothing should go to a friend the user never picked. A pinned best friend always wins; failing
    // that, it falls back to whoever the last real send went to (see sendCaptured), so a restart
    // remembers who I was sending to instead of resetting to nobody.
    //
    // The default is computed before either `friends` or `selectedRecipientIds` is written, so both
    // land in the same recomposition. The first version wrote `friends = list` first and resolved the
    // default afterward: harmless when pinned (no suspension), but the local-cache read in the
    // non-pinned path is a real suspend point, so Compose recomposed once with friends populated and
    // no selection (the badge briefly showing nobody picked), then again once the read finished: a
    // visible flash on every cold start with no pinned friend.
    private suspend fun applyFriends(list: List<FriendSummaryDto>) {
        val resolvedSelection = if (selectedRecipientIds.isEmpty()) {
            val pinnedIds = list.filter { it.pinnedByMe }.map { it.friendId }.toSet()
            if (pinnedIds.isNotEmpty()) {
                pinnedIds
            } else {
                val lastUsedIds = localCache.read<String>(LocalListCache.KEY_LAST_RECIPIENT_IDS).orEmpty().toSet()
                // Filtered against the freshly loaded list, not trusted blindly: a friend from a past
                // send could since have been unfriended.
                lastUsedIds.filterTo(mutableSetOf()) { id -> list.any { friend -> friend.friendId == id } }
            }
        } else {
            selectedRecipientIds
        }
        friends = list
        selectedRecipientIds = resolvedSelection
    }

    /** The Send button's tap; the button is always live. With someone chosen it sends. With nobody
     * chosen it first asks the server once for the friend list, since the camera's copy can be a
     * friend behind (someone added or accepted while the app was in the background): a person who
     * really has no friends gets [onNoFriends] (the sheet explaining how to add or invite someone),
     * anyone else gets [onPickRecipients] (the friend picker). A default picked by that refresh
     * (a pinned or last-used friend) is shown in the picker, never sent to unseen. The photo is
     * untouched throughout. */
    fun onSendTapped(
        context: android.content.Context,
        onSent: () -> Unit,
        onNoFriends: () -> Unit,
        onPickRecipients: () -> Unit,
    ) {
        if (selectedFriends.isNotEmpty()) {
            sendCaptured(context, onSent)
            return
        }
        if (isCheckingFriends) return
        viewModelScope.launch {
            isCheckingFriends = true
            friendRepository.getFriends(limit = RECIPIENT_PICKER_FRIENDS_LIMIT).onSuccess { applyFriends(it.items) }
            isCheckingFriends = false
            when (outcomeWhenNobodyChosen(friends)) {
                NoRecipientOutcome.SHOW_INVITE_SHEET -> onNoFriends()
                NoRecipientOutcome.OPEN_PICKER -> onPickRecipients()
            }
        }
    }

    /** Picks who the photo goes to. [knownFriends] is a friend list the caller has just loaded (the
     * recipient picker's), used when this ViewModel's own list is missing someone chosen: a friend
     * added after it loaded its list was chosen but unknown here, so Send read it as nobody picked
     * and stayed disabled until the app restarted. If the choice is still unknown after that (the
     * profile route has no list to hand over), the list is reloaded once. */
    fun setSelectedRecipients(ids: Set<String>, knownFriends: List<FriendSummaryDto> = emptyList()) {
        friends = friendsAfterRecipientChoice(friends, knownFriends, ids)
        selectedRecipientIds = ids
        if (hasUnknownRecipient(friends, ids)) loadFriends()
    }

    /** Entry point for the gallery button: opens the picker for Gold members and for anyone holding
     * an ad-earned pass, otherwise shows the upsell. The caller never launches the picker
     * directly. */
    fun onGalleryClick(launchPicker: () -> Unit) {
        if (isGoldMember || hasGalleryPass) {
            launchPicker()
        } else {
            // Decided when the sheet opens, so it can say so up front instead of letting someone
            // tap a button that can't work. The rules are read now too (they can change from the
            // Firebase console), so the sheet shows the same numbers the unlock will use.
            galleryAdsEnabled = galleryUnlock.adsEnabled
            galleryAdsNeeded = galleryUnlock.adsPerPhoto
            galleryUnlocksPerDay = galleryUnlock.unlocksPerDay
            isGalleryLimitReached = galleryUnlock.unlocksLeftToday == 0
            showGoldUpsell = true
        }
    }

    /** Today's free gallery photos are all used, so the sheet offers only Gold. */
    var isGalleryLimitReached by mutableStateOf(false)
        private set

    /** What the gallery sheet is showing, read from the current ad rules when it opens: whether ads
     * are on at all (the safety switch; off means Gold only), how many ads one photo costs, and how
     * many photos can be unlocked a day. */
    var galleryAdsEnabled by mutableStateOf(galleryUnlock.adsEnabled)
        private set
    var galleryAdsNeeded by mutableStateOf(galleryUnlock.adsPerPhoto)
        private set
    var galleryUnlocksPerDay by mutableStateOf(galleryUnlock.unlocksPerDay)
        private set

    /** Shows one ad toward a gallery photo. The last one needed opens the picker, through
     * [launchPicker], exactly as a tap would for a Gold member. */
    fun watchGalleryAd(activity: Activity, launchPicker: () -> Unit) {
        if (isWatchingGalleryAd) return
        viewModelScope.launch {
            isWatchingGalleryAd = true
            when (val result = watchAdForGallery.watchOne(activity)) {
                GalleryAdResult.Unlocked -> {
                    hasGalleryPass = true
                    galleryAdsWatched = 0
                    showGoldUpsell = false
                    launchPicker()
                }
                is GalleryAdResult.Progress -> galleryAdsWatched = result.watched
                GalleryAdResult.AdUnavailable -> adNotice = strings.get(R.string.ads_unavailable)
                GalleryAdResult.AdClosedEarly -> adNotice = strings.get(R.string.ads_closed_early)
                GalleryAdResult.DailyLimitReached -> adNotice = strings.get(R.string.ads_gallery_daily_limit)
            }
            isWatchingGalleryAd = false
        }
    }

    /** A short message for the screen to show once (a toast) about an ad. */
    var adNotice by mutableStateOf<String?>(null)
        private set

    fun clearAdNotice() {
        adNotice = null
    }

    fun dismissGoldUpsell() {
        showGoldUpsell = false
    }

    fun captureFailed(message: String) {
        errorMessage = message
    }

    /** The instant, pre-real-capture frame (see [isRealCaptureReady]). Called only on the
     * live-camera path, never for a gallery pick. */
    fun onPreviewSnapshotCaptured(file: File, bitmap: Bitmap) {
        capturedFile = file
        pendingSnapshotFile = file
        previewBitmap = bitmap
        isRealCaptureReady = false
        captionText = ""
        errorMessage = null
        isSaved = false
        hasQueuedUpload = false
        uploadWorkName = file.name
    }

    /** The real, final photo: either the hardware capture landing (superseding the snapshot
     * [onPreviewSnapshotCaptured] showed a moment earlier) or a gallery pick, which has no snapshot
     * stage and is real as soon as it's chosen.
     *
     * [isFrontCamera] captures get their pixels mirrored to match the mirrored preview they were
     * framed against (see capturePhoto in CameraScreen for why this is done to pixels, not via
     * ImageCapture's EXIF-only isReversedHorizontal). The flip runs off the main thread; until it
     * lands, the instant snapshot keeps showing (isRealCaptureReady stays false), the same handoff
     * a back-camera capture goes through, just a beat longer. */
    fun onPhotoCaptured(file: File, isFrontCamera: Boolean = false, fromGallery: Boolean = false) {
        capturedFromGallery = fromGallery
        if (!isFrontCamera) {
            applyCapturedFile(file)
            return
        }
        viewModelScope.launch {
            val mirrored = withContext(Dispatchers.IO) {
                runCatching { mirrorHorizontally(file) }.getOrDefault(file)
            }
            applyCapturedFile(mirrored)
        }
    }

    private fun applyCapturedFile(file: File) {
        val staleSnapshot = pendingSnapshotFile
        capturedFile = file
        isRealCaptureReady = true
        pendingSnapshotFile = null
        if (staleSnapshot != null && staleSnapshot != file) staleSnapshot.delete()
        captionText = ""
        errorMessage = null
        isSaved = false
        hasQueuedUpload = false
        uploadWorkName = file.name
    }

    fun onCaptionChange(value: String) {
        captionText = value
    }

    fun discardCapture() {
        capturedFile?.delete()
        pendingSnapshotFile?.let { if (it != capturedFile) it.delete() }
        pendingSnapshotFile = null
        capturedFile = null
        previewBitmap = null
        isRealCaptureReady = true
        captionText = ""
        isSaved = false
        hasQueuedUpload = false
        uploadWorkName = null
        // The pass is kept: backing out of a photo doesn't use it up.
        capturedFromGallery = false
    }

    /** Queues the captured photo for background sending and returns at once; it doesn't wait on (or
     * need) a network connection and never blocks the screen. [PendingSendWorker] uploads whenever
     * the device next has connectivity, even if the app is closed. [context] is used transiently
     * (moving a file, enqueuing WorkManager) and never retained; the caller passes
     * `context.applicationContext`, not an Activity, since this can outlive the screen.
     *
     * If [saveToMemories] already queued the real upload for this capture, this doesn't upload the
     * file again: it chains a lightweight "add these recipients" request onto that upload (see
     * [PendingSendWorker.enqueueAddRecipients]). Tapping both Save and Send used to mean two full
     * uploads of the same file. */
    fun sendCaptured(context: Context, onQueued: () -> Unit) {
        // Guards the top of the function, not just the button's `enabled`: enabled applies only after
        // Compose recomposes once isQueuingSend flips true, so a fast double-tap in that window could
        // queue the photo twice. isRealCaptureReady is the same idea for the instant snapshot stage:
        // without it, a send fired before the real capture lands would queue the temporary frame.
        if (isQueuingSend || !isRealCaptureReady) return
        val file = capturedFile ?: return
        val workName = uploadWorkName ?: file.name
        if (selectedRecipientIds.isEmpty()) {
            errorMessage = strings.get(R.string.camera_error_select_friend)
            return
        }
        val recipientIds = selectedRecipientIds.toList()
        viewModelScope.launch {
            isQueuingSend = true
            errorMessage = null
            sendAnimState = SendAnimState.SENDING
            if (hasQueuedUpload) {
                // Save already uploaded a copy of this capture (see saveToMemories), leaving the
                // original untouched in case Send followed. Now that it has and nothing else needs
                // it, it can go.
                withContext(Dispatchers.IO) { file.delete() }
                PendingSendWorker.enqueueAddRecipients(context, workName, recipientIds)
            } else {
                val baked = withContext(Dispatchers.Default) {
                    runCatching { bakeCaptionIntoPhoto(file, captionText) }.getOrDefault(file)
                }
                val queuedFile = withContext(Dispatchers.IO) {
                    runCatching { moveToPendingSendStorage(context, baked) }.getOrNull()
                }
                // The captioned copy supersedes the original once baking succeeds, as in the old
                // inline-upload path.
                if (baked != file) file.delete()
                if (queuedFile == null) {
                    errorMessage = strings.get(R.string.camera_error_queue)
                    isQueuingSend = false
                    sendAnimState = SendAnimState.IDLE
                    return@launch
                }
                PendingSendWorker.enqueuePrimary(context, workName, queuedFile, recipientIds, save = isSaved)
                hasQueuedUpload = true
            }
            // Persisted only once a send goes out, not on every picker tap, so a selection made and
            // backed out of never overwrites "who I last sent to".
            localCache.write(LocalListCache.KEY_LAST_RECIPIENT_IDS, recipientIds)
            // A gallery photo going out is what a pass is for. Gold members never held one.
            if (capturedFromGallery && hasGalleryPass) {
                galleryUnlock.usePass()
                hasGalleryPass = false
            }
            capturedFromGallery = false
            capturedFile = null
            captionText = ""
            isQueuingSend = false
            onQueued()
        }
    }

    /** Saves the current capture to Memories, independent of [sendCaptured]: you can tap this alone
     * with nobody selected and it still saves. If Send is also tapped for the same capture (in
     * either order), the two share one real upload (see [sendCaptured] and
     * [PendingSendWorker.enqueueMarkSaved]).
     *
     * When it's the first of the two to run it bakes the caption onto a copy of [capturedFile],
     * never the original: unlike [sendCaptured] it can't consume the file still backing the live
     * preview, since the user might still tap Send or Retake. */
    fun saveToMemories(context: Context) {
        if (isSaved || isSavingToMemories || !isRealCaptureReady) return
        val file = capturedFile ?: return
        val workName = uploadWorkName ?: file.name
        viewModelScope.launch {
            isSavingToMemories = true
            errorMessage = null
            if (hasQueuedUpload) {
                PendingSendWorker.enqueueMarkSaved(context, workName)
                isSaved = true
                isSavingToMemories = false
                return@launch
            }
            val sourceCopy = withContext(Dispatchers.IO) {
                runCatching { File(file.parentFile, "save_${file.name}").also { file.copyTo(it, overwrite = true) } }.getOrNull()
            }
            if (sourceCopy == null) {
                errorMessage = strings.get(R.string.camera_error_save)
                isSavingToMemories = false
                return@launch
            }
            val baked = withContext(Dispatchers.Default) {
                runCatching { bakeCaptionIntoPhoto(sourceCopy, captionText) }.getOrDefault(sourceCopy)
            }
            if (baked != sourceCopy) sourceCopy.delete()
            val queuedFile = withContext(Dispatchers.IO) {
                runCatching { moveToPendingSendStorage(context, baked) }.getOrNull()
            }
            if (queuedFile == null) {
                errorMessage = strings.get(R.string.camera_error_save)
                isSavingToMemories = false
                return@launch
            }
            PendingSendWorker.enqueuePrimary(context, workName, queuedFile, recipientIds = emptyList(), save = true)
            hasQueuedUpload = true
            isSaved = true
            isSavingToMemories = false
        }
    }
}

/** Moves [source] out of the cache dir (which the OS can clear at any time) into a durable spot
 * under [Context.getFilesDir] that lasts until [PendingSendWorker] uploads and deletes it; a photo
 * waiting on connectivity can't sit somewhere the system may reclaim. Copy-then-delete, not
 * `File.renameTo`, which isn't guaranteed to work across storage areas on every Android version or
 * device. */
private fun moveToPendingSendStorage(context: Context, source: File): File {
    val dir = File(context.filesDir, "pending_sends").apply { mkdirs() }
    val dest = File(dir, source.name)
    source.copyTo(dest, overwrite = true)
    source.delete()
    return dest
}

/** Rewrites [file] horizontally mirrored so a front-camera capture matches the mirrored preview it
 * was framed against. Any EXIF rotation is baked into the pixels at the same time and the output
 * carries no orientation metadata, so it doesn't fight [bakeCaptionIntoPhoto], which reads EXIF and
 * would re-apply a rotation already applied here. Returns the original [file] untouched if it can't
 * be decoded. */
private fun mirrorHorizontally(file: File): File {
    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return file
    val rotationDegrees = when (
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    // One matrix does both at once; rotating then mirroring in two createBitmap passes would
    // allocate a second full-resolution intermediate for no benefit.
    val matrix = Matrix().apply {
        postRotate(rotationDegrees)
        postScale(-1f, 1f)
    }
    val mirrored = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (mirrored !== decoded) decoded.recycle()

    val output = File(file.parentFile, "mirrored_${file.name}")
    FileOutputStream(output).use { mirrored.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    mirrored.recycle()
    file.delete()
    return output
}

/** Draws the caption onto the photo so recipients see it everywhere (feed, widget) with no backend
 * caption support. Returns the original file for a blank caption; otherwise decodes (honoring EXIF
 * rotation, which re-encoding would lose), crops to the [FEATURED_CARD_ASPECT_RATIO] every card
 * uses (see [cropToAspectRatio] for why this must happen before baking, not just at display),
 * paints a Snapchat-style dark bar with centered white text, and writes a new JPEG. */
private fun bakeCaptionIntoPhoto(file: File, caption: String): File {
    if (caption.isBlank()) return file

    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return file
    val rotationDegrees = when (
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val upright = if (rotationDegrees != 0f) {
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } else {
        decoded
    }
    // A capture is a full-resolution bitmap (tens of MB decoded); up to four (decoded, upright,
    // sourceForBake, bitmap) could exist at once if left to GC, and retake or re-caption repeats this
    // in one Camera session. Recycling each intermediate once superseded keeps at most two live.
    if (upright !== decoded) decoded.recycle()

    // The camera's raw capture has whatever native aspect ratio the sensor defaults to (varies by
    // phone), not the featured card's 0.8 that the live preview framed against. Cropping before
    // baking makes the caption's Y-fraction land in the same relative spot the preview showed and the
    // featured card will display. Without it, a crop applied only at display shifted the caption,
    // sometimes into the name/streak bar at the card's bottom, by an amount that depends on the
    // device's native ratio; that's why it only showed on some recipients' devices.
    val sourceForBake = cropToAspectRatio(upright, FEATURED_CARD_ASPECT_RATIO)
    if (sourceForBake !== upright) upright.recycle()

    val bitmap = if (sourceForBake.isMutable) sourceForBake else sourceForBake.copy(Bitmap.Config.ARGB_8888, true)
    if (bitmap !== sourceForBake) sourceForBake.recycle()
    val canvas = Canvas(bitmap)

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = bitmap.width * 0.045f
    }
    val layout = StaticLayout.Builder
        .obtain(caption.trim(), 0, caption.trim().length, textPaint, (bitmap.width * 0.86f).toInt())
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()

    val barPadding = bitmap.width * 0.03f
    val barTop = bitmap.height * CAPTION_Y_FRACTION - layout.height / 2f - barPadding
    canvas.drawRect(
        0f,
        barTop,
        bitmap.width.toFloat(),
        barTop + layout.height + barPadding * 2,
        Paint().apply { color = Color.argb(150, 0, 0, 0) },
    )
    canvas.save()
    canvas.translate((bitmap.width - layout.width) / 2f, barTop + barPadding)
    layout.draw(canvas)
    canvas.restore()

    val output = File(file.parentFile, "captioned_${file.name}")
    FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return output
}

/** Center-crops [source] to [targetRatio] (width/height), as [ContentScale.Crop] would at display:
 * trims the sides if [source] is relatively wider, or top and bottom if relatively taller. Returns
 * [source] itself if it's already at (or very near) that ratio. */
private fun cropToAspectRatio(source: Bitmap, targetRatio: Float): Bitmap {
    val sourceRatio = source.width.toFloat() / source.height.toFloat()
    if (kotlin.math.abs(sourceRatio - targetRatio) < 0.001f) return source

    return if (sourceRatio > targetRatio) {
        val newWidth = (source.height * targetRatio).roundToInt().coerceIn(1, source.width)
        val x = (source.width - newWidth) / 2
        Bitmap.createBitmap(source, x, 0, newWidth, source.height)
    } else {
        val newHeight = (source.width / targetRatio).roundToInt().coerceIn(1, source.height)
        val y = (source.height - newHeight) / 2
        Bitmap.createBitmap(source, 0, y, source.width, newHeight)
    }
}
