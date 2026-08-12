package com.emigo.app.ui.camera

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
import com.emigo.app.data.FriendRepository
import com.emigo.app.data.PhotoRepository
import com.emigo.app.data.SubscriptionRepository
import com.emigo.app.data.local.LocalListCache
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.ui.home.FEATURED_CARD_ASPECT_RATIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/** How long the outbox button's filled-checkmark state holds before fading back to idle — see
 * [CameraViewModel.markSendComplete]. */
private const val SEND_ANIM_COMPLETE_HOLD_MS = 1200L

/** Drives the Camera outbox button's own send animation (see CameraScreen's OutboxButton) —
 * SENDING from the moment [CameraViewModel.sendCaptured] queues the upload, flipped to COMPLETE by
 * [CameraViewModel.markSendComplete] once the real upload has actually landed (MainActivity
 * collects EmberApplication.photoSendCompletedEvents for this — see its own doc comment), then
 * automatically fades back to IDLE a moment later. Deliberately not tied to [isQueuingSend]
 * (which only covers the brief local queuing step) — the real upload can take much longer,
 * especially offline, and this is meant to reflect that whole span. */
enum class SendAnimState { IDLE, SENDING, COMPLETE }

class CameraViewModel(
    private val friendRepository: FriendRepository,
    private val photoRepository: PhotoRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val localCache: LocalListCache,
) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set
    var selectedRecipientIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** True only for the brief local step (baking the caption in, moving the file into durable
     * storage) between tapping Send and it actually being handed to [PendingSendWorker] — purely
     * a double-tap guard for that short window, not a "network in flight" flag any more. The
     * actual upload happens in the background regardless of whether this screen is even open,
     * with no on-screen indicator of its own any more (see CameraScreen — the top-right corner
     * that used to show "Sending…"/"Sent" is the bookmark button's spot now). */
    var isQueuingSend by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** See [SendAnimState]'s own doc comment. Not reset per-capture like [isSaved]/[capturedFile]
     * — a send animation should keep playing through to completion even if the user retakes or
     * leaves Camera mid-flight, since it's tracking the outbox upload itself, not this specific
     * review session. */
    var sendAnimState by mutableStateOf(SendAnimState.IDLE)
        private set

    /** Called once the real upload this ViewModel most recently queued has actually landed (see
     * [SendAnimState]'s own doc comment for where that signal comes from) — a no-op unless a send
     * animation is actually in flight, so a completion echoing in from an unrelated older retry
     * can't restart an already-finished (or never-started) animation. */
    fun markSendComplete() {
        if (sendAnimState != SendAnimState.SENDING) return
        sendAnimState = SendAnimState.COMPLETE
        viewModelScope.launch {
            delay(SEND_ANIM_COMPLETE_HOLD_MS)
            sendAnimState = SendAnimState.IDLE
        }
        // The photo that was just sent is now the outbox's own newest entry — refresh so the
        // button's thumbnail updates to it once the fill/checkmark fades back out.
        refreshLastSentPhoto()
    }

    /** The thumbnail the outbox button itself shows — this account's own single most recent
     * unsaved send (the same list [SentPhotosScreen] shows, just the first entry of it), fetched
     * independently of whether that screen has ever actually been opened this session. Silent on
     * failure/empty, same as every other background refresh in this class — worst case the
     * button just keeps showing whatever it last had (or its empty fallback), not a user-facing
     * error over a purely decorative thumbnail. */
    var lastSentPhotoUrl by mutableStateOf<String?>(null)
        private set

    private fun refreshLastSentPhoto() {
        viewModelScope.launch {
            photoRepository.getSentPhotos().onSuccess { photos -> lastSentPhotoUrl = photos.firstOrNull()?.photoUrl }
        }
    }

    /** True once the current capture has been saved to Memories (see [saveToMemories]) — drives
     * the bookmark button switching from outline to filled. Per-capture, not per-session: reset
     * back to false by every place a new [capturedFile] gets set (a fresh capture, a retake, a
     * gallery pick), same as [captionText] already is. Saving is one-way from this screen —
     * there's no un-save button here, only Memories' own delete (see MemoriesScreen). */
    var isSaved by mutableStateOf(false)
        private set
    /** True only for the brief local step (baking the caption in, moving the file into durable
     * storage) between tapping the bookmark and it actually being handed to [PendingSendWorker] —
     * same purpose as [isQueuingSend], just for the independent save action rather than send. */
    var isSavingToMemories by mutableStateOf(false)
        private set

    /** True once *either* Save or Send has queued the real upload for this capture — the other
     * one, if it's also tapped, chains onto that same upload (see [PendingSendWorker.
     * enqueueMarkSaved]/[PendingSendWorker.enqueueAddRecipients]) instead of uploading the file a
     * second time. Reset alongside [isSaved] on every new capture. */
    private var hasQueuedUpload = false
    /** The stable identity WorkManager's unique-work chain for this capture is keyed on — set
     * once, from the freshly-captured file's own name, the moment [capturedFile] is assigned.
     * Deliberately NOT re-derived from whatever the actually-uploaded file ends up named later
     * (baking a caption in, or Save's own copy, both rename it — see [saveToMemories]), since the
     * second action needs to target the exact same WorkManager unique-work name the first one
     * used, and that name has to survive those renames unchanged to do it. */
    private var uploadWorkName: String? = null

    /** Gallery picking is an Ember Gold perk; free accounts are limited to the live camera.
     * Defaults to false (not Gold) until the subscription check resolves, so the upsell never
     * flashes a free feature open before snapping shut. */
    var isGoldMember by mutableStateOf(false)
        private set
    var showGoldUpsell by mutableStateOf(false)
        private set

    /** Captured (or gallery-picked) photo waiting on the preview stage — nothing is sent
     * until the user reviews it and taps Send. */
    var capturedFile by mutableStateOf<File?>(null)
        private set
    var captionText by mutableStateOf("")
        private set

    /** False only for the brief window between [onPreviewSnapshotCaptured] (an instant frozen
     * frame of the live viewfinder, shown the moment the shutter is tapped so capture feels
     * immediate — see capturePhoto in CameraScreen.kt) and the real hardware capture actually
     * landing via [onPhotoCaptured]. Gallery picks skip the snapshot stage entirely and go
     * straight to [onPhotoCaptured], so this is true immediately for that path. Send gates on
     * this so a very fast tap-then-send can never upload the temporary frame instead of the real
     * photo.
     *
     * A first attempt at this exact idea was reverted after two real bugs: the page-level
     * transition was a `Crossfade` keyed on the file value itself, so the snapshot→real swap
     * retriggered a second fade that exposed the photo card's black background mid-transition;
     * and the swap was slow/visible enough that the temporary frame's lower fidelity read as an
     * obvious quality dip. This time: the live↔reviewing boundary in CameraScreen.kt is a plain
     * `if/else` (no Crossfade at all, so no fade-through-black is even possible), and the
     * snapshot→real swap happens through the same already-crossfade-disabled AsyncImage request
     * (see CapturedPreview) — an instant pixel swap, not a fade, over two frames that show
     * nearly the same scene a fraction of a second apart. The real capture itself is still full,
     * unbounded quality — only the fleeting placeholder is viewfinder-resolution. */
    var isRealCaptureReady by mutableStateOf(true)
        private set

    /** The in-memory bitmap backing the instant preview stage — an already-decoded `Bitmap`
     * (from `previewView.bitmap`), not a second file-based image, so it draws the same frame
     * it's set with no async decode gap. Kept alive for this capture's whole review (not cleared
     * the moment the real photo lands) purely as a defensive fallback layer — see
     * `CapturedPreview` in CameraScreen.kt, which layers the real file's AsyncImage on top of
     * this so the real photo's own (Coil, EXIF-aware) decode always has something already on
     * screen to sit over instead of the card's bare black background. */
    var previewBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // The snapshot file backing capturedFile while isRealCaptureReady is false — tracked
    // separately (not just re-derived from capturedFile) purely so it can be deleted once the
    // real file supersedes it, or if a retake happens before that ever occurs.
    private var pendingSnapshotFile: File? = null

    init {
        refreshLastSentPhoto()
        // Deliberately NOT loadFriends() here — this ViewModel now lives for the whole app
        // session (Camera is a pager page, not a screen only created on demand), so anything
        // fired from init runs on every single cold start whether or not Camera is ever opened
        // that session. loadFriends() is a limit=500 "give me everyone" fetch for the recipient
        // picker specifically — MainActivity calls it once, lazily, the first time the user
        // actually reaches the Camera page (see its own comment for why), not here.
        //
        // The *local* cache read below is a different matter and does belong here: it's plain
        // disk I/O, not a network call, and without it the recipient badge had no data at all
        // until that lazy fetch landed — so opening Camera after a restart showed the empty
        // "Friends" state first and only then popped in the pinned/last-used friend's avatar. The
        // fetch above still runs on arrival and refreshes this; this just means the badge starts
        // out already correct instead of visibly correcting itself a moment later.
        viewModelScope.launch {
            localCache.read<FriendSummaryDto>(LocalListCache.KEY_FRIENDS)?.let { cached ->
                // Guarded on friends still being empty — if the real fetch somehow beat this read,
                // its fresher (and complete, limit=500 rather than the Friends tab's own 30) list
                // must not be replaced by this snapshot.
                if (friends.isEmpty()) applyFriends(cached)
            }
            isGoldMember = subscriptionRepository.isGoldMemberOrLastKnown()
        }
    }

    // Ordered by selectedRecipientIds itself, not by filtering `friends` — Set's own + / - here
    // (see toggleSelected/setSelectedRecipients) already preserve tap order as a LinkedHashSet
    // under the hood, but filtering `friends` against that set would've thrown that away and
    // shown them back in the friends list's own order instead of the order actually picked.
    val selectedFriends: List<FriendSummaryDto>
        get() {
            val friendsById = friends.associateBy { it.friendId }
            return selectedRecipientIds.mapNotNull { friendsById[it] }
        }

    // Always the plain word "Friends" now, never a specific name — the avatar stack (plus its
    // own "+N" circle once there's more than a couple) already shows exactly who's selected, so
    // naming them again in text was saying the same thing twice.
    val recipientLabel: String = "Friends"

    val hasPinnedSelected: Boolean
        get() = friends.any { it.friendId in selectedRecipientIds && it.pinnedByMe }

    /** Reuses a friend list Friends' own tab has already fetched, when it's known to already be
     * complete (see MainActivity — only used when FriendsViewModel has loaded everything, i.e.
     * `hasMore == false`), instead of this ViewModel firing its own separate, mostly-redundant
     * network call for what's very often the exact same data. [loadFriends] below remains the
     * fallback for whenever that isn't the case (Friends hasn't loaded yet, or genuinely has more
     * than a page of friends). */
    fun provideFriends(list: List<FriendSummaryDto>) {
        viewModelScope.launch { applyFriends(list) }
    }

    fun loadFriends() {
        viewModelScope.launch {
            // The recipient picker needs every friend to choose from, not a scrollable page of
            // them — RECIPIENT_PICKER_FRIENDS_LIMIT is a generously high ceiling, not a real
            // pagination boundary.
            friendRepository.getFriends(limit = RECIPIENT_PICKER_FRIENDS_LIMIT).fold(
                onSuccess = { page -> applyFriends(page.items) },
                onFailure = { errorMessage = it.message ?: "Couldn't load your friends" },
            )
        }
    }

    // Shared by provideFriends and loadFriends so both paths apply the exact same default-
    // recipient rule below — never to "everyone" with no explicit choice. A silent reply-all
    // default is exactly the kind of invisible behavior that makes a send flow feel unsafe rather
    // than just unpolished: nothing should leave the device to a friend the user never actually
    // picked. A pinned best-friend always wins as that default; failing that, this falls back to
    // whoever the last real send actually went to (see sendCaptured), so a restart remembers "who
    // I was sending to" instead of resetting to nobody every time nothing is pinned.
    //
    // The default is fully computed *before* either `friends` or `selectedRecipientIds` is
    // written, specifically so both land in the same recomposition. The first version of this
    // wrote `friends = list` immediately and only resolved the default afterward — harmless for
    // the pinned case (no suspension in between), but the local-cache read in the non-pinned path
    // is a real suspend point, so Compose recomposed once with friends populated and the
    // selection still empty (the badge briefly showing "Friends" / nobody picked), then again a
    // moment later once the read completed — a visible flash on every cold start with no pinned
    // friend that the user explicitly didn't want.
    private suspend fun applyFriends(list: List<FriendSummaryDto>) {
        val resolvedSelection = if (selectedRecipientIds.isEmpty()) {
            val pinnedIds = list.filter { it.pinnedByMe }.map { it.friendId }.toSet()
            if (pinnedIds.isNotEmpty()) {
                pinnedIds
            } else {
                val lastUsedIds = localCache.read<String>(LocalListCache.KEY_LAST_RECIPIENT_IDS).orEmpty().toSet()
                // Filtered against the freshly-loaded list, not trusted blindly — a friend from a
                // past send could since have been unfriended/removed.
                lastUsedIds.filterTo(mutableSetOf()) { id -> list.any { friend -> friend.friendId == id } }
            }
        } else {
            selectedRecipientIds
        }
        friends = list
        selectedRecipientIds = resolvedSelection
    }

    fun setSelectedRecipients(ids: Set<String>) {
        selectedRecipientIds = ids
    }

    /** Entry point for the gallery button: opens the picker for Gold members, otherwise shows
     * the upsell instead — the caller never launches the picker directly. */
    fun onGalleryClick(launchPicker: () -> Unit) {
        if (isGoldMember) launchPicker() else showGoldUpsell = true
    }

    fun dismissGoldUpsell() {
        showGoldUpsell = false
    }

    fun captureFailed(message: String) {
        errorMessage = message
    }

    /** The instant, pre-real-capture frame — see [isRealCaptureReady]'s own doc comment. Never
     * called for a gallery pick, only the live-camera path. */
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

    /** The real, final photo — either the hardware capture landing (superseding whatever
     * snapshot [onPreviewSnapshotCaptured] showed a moment earlier) or a gallery pick, which has
     * no snapshot stage and is already "real" the instant it's chosen. */
    /** [isFrontCamera] captures get their pixels mirrored to match the mirrored preview the shot
     * was actually framed against — see takePhoto's own comment in CameraScreen for why this is
     * done to the pixels here rather than via ImageCapture's EXIF-only isReversedHorizontal flag.
     * The flip runs off the main thread; until it lands, the instant preview snapshot already on
     * screen keeps showing (isRealCaptureReady stays false), which is the same handoff a
     * back-camera capture already goes through, just a beat longer. */
    fun onPhotoCaptured(file: File, isFrontCamera: Boolean = false) {
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
    }

    /** Queues the captured photo for background sending and returns immediately — this no
     * longer waits on (or even needs) a live network connection, and never blocks the screen.
     * [PendingSendWorker] does the actual upload whenever the device next has connectivity, even
     * if the app is later closed. [context] is used transiently (moving a file, enqueuing
     * WorkManager) and never retained — the caller passes `context.applicationContext`, not an
     * Activity Context, since this can outlive the screen that called it.
     *
     * If [saveToMemories] already queued the real upload for this exact capture, this doesn't
     * upload the file again — it chains a lightweight "add these recipients" request onto that
     * same upload instead (see [PendingSendWorker.enqueueAddRecipients]). Tapping both Save and
     * Send on one capture used to mean two full, independent uploads of the same file; this is
     * the fix for that. */
    fun sendCaptured(context: Context, onQueued: () -> Unit) {
        // Guards the top of the function itself, not just the button's own `enabled` — enabled
        // only takes effect once Compose recomposes after isQueuingSend flips true, so a fast
        // double-tap landing inside that window could otherwise queue the same photo twice.
        // isRealCaptureReady is the same idea for the instant preview-snapshot stage — without
        // it, a send fired in the brief window before the real capture lands would queue the
        // temporary frame instead.
        if (isQueuingSend || !isRealCaptureReady) return
        val file = capturedFile ?: return
        val workName = uploadWorkName ?: file.name
        if (selectedRecipientIds.isEmpty()) {
            errorMessage = "Select at least one friend first"
            return
        }
        val recipientIds = selectedRecipientIds.toList()
        viewModelScope.launch {
            isQueuingSend = true
            errorMessage = null
            sendAnimState = SendAnimState.SENDING
            if (hasQueuedUpload) {
                // Save already uploaded a *copy* of this capture (see saveToMemories), leaving
                // the original file — still sitting right here — untouched on purpose in case
                // Send followed. Now that it has, and nothing else needs it, it can finally go.
                withContext(Dispatchers.IO) { file.delete() }
                PendingSendWorker.enqueueAddRecipients(context, workName, recipientIds)
            } else {
                val baked = withContext(Dispatchers.Default) {
                    runCatching { bakeCaptionIntoPhoto(file, captionText) }.getOrDefault(file)
                }
                val queuedFile = withContext(Dispatchers.IO) {
                    runCatching { moveToPendingSendStorage(context, baked) }.getOrNull()
                }
                // The captioned copy (if there was one) supersedes the original the instant
                // baking succeeds, same as the old inline-upload path did — nothing else still
                // needs it.
                if (baked != file) file.delete()
                if (queuedFile == null) {
                    errorMessage = "Couldn't queue your photo — please try again"
                    isQueuingSend = false
                    sendAnimState = SendAnimState.IDLE
                    return@launch
                }
                PendingSendWorker.enqueuePrimary(context, workName, queuedFile, recipientIds, save = isSaved)
                hasQueuedUpload = true
            }
            // Only persisted once a send actually goes out — not on every tap in the picker —
            // so a selection made and then backed out of never overwrites "who I last sent to".
            localCache.write(LocalListCache.KEY_LAST_RECIPIENT_IDS, recipientIds)
            capturedFile = null
            captionText = ""
            isQueuingSend = false
            onQueued()
        }
    }

    /** Saves the current capture to Memories, independent of [sendCaptured] — you can tap this
     * alone, with nobody selected to send to, and it still saves. If Send *also* gets tapped for
     * the very same capture (in either order), the two share one real upload instead of each
     * independently uploading the same file — see [sendCaptured]'s own doc comment and
     * [PendingSendWorker.enqueueMarkSaved].
     *
     * Bakes the caption onto a *copy* of [capturedFile] when this is the first of the two actions
     * to run, never the original — unlike [sendCaptured], this can't consume/delete the file
     * still backing the live preview, since the user might still go on to tap Send (or Retake)
     * afterward. */
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
                errorMessage = "Couldn't save your photo — please try again"
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
                errorMessage = "Couldn't save your photo — please try again"
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

/** Moves [source] out of the cache dir (which the OS can clear at any moment) into a durable
 * spot under [Context.getFilesDir] that survives until [PendingSendWorker] actually uploads and
 * deletes it — a photo waiting on connectivity, possibly for a long time, can't be left
 * somewhere the system is free to reclaim. Copy-then-delete rather than `File.renameTo`, which
 * isn't guaranteed to work across different storage areas on every Android version/device. */
private fun moveToPendingSendStorage(context: Context, source: File): File {
    val dir = File(context.filesDir, "pending_sends").apply { mkdirs() }
    val dest = File(dir, source.name)
    source.copyTo(dest, overwrite = true)
    source.delete()
    return dest
}

/** Rewrites [file] horizontally mirrored, so a front-camera capture matches the mirrored preview
 * it was framed against. Any EXIF rotation is baked into the pixels at the same time and the
 * output carries no orientation metadata of its own — that keeps this from fighting
 * [bakeCaptionIntoPhoto], which reads EXIF itself and would otherwise re-apply a rotation that's
 * already been applied here. Returns the original [file] untouched if it can't be decoded. */
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
    // One matrix doing both at once — rotating upright first and mirroring second in two separate
    // createBitmap passes would allocate a second full-resolution intermediate for no benefit.
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

/** Draws the caption onto the photo itself so recipients see it everywhere (feed, widget) with
 * no backend support for captions needed. Returns the original file untouched for a blank
 * caption; otherwise decodes (honoring EXIF rotation, which would be lost by re-encoding), crops
 * to the same [FEATURED_CARD_ASPECT_RATIO] every card displays photos at (see [cropToAspectRatio]
 * for why that step has to happen before baking, not just at display time), paints a
 * Snapchat-style dark bar + centered white text, and writes a new JPEG. */
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
    // A capture is a full-resolution bitmap (tens of MB decoded) — up to four can transiently
    // exist here (decoded, upright, sourceForBake, bitmap) if left to GC alone, and retake/
    // re-caption repeats this every time in one Camera session. Recycling each intermediate the
    // moment it's superseded keeps at most two full-resolution bitmaps live at once instead of
    // four.
    if (upright !== decoded) decoded.recycle()

    // The camera's own raw capture is whatever native aspect ratio that device's sensor defaults
    // to (varies by phone) — never the featured card's 0.8 ratio the live preview constrained
    // itself to while framing/captioning. Cropping here, before baking, is what makes the
    // caption's Y-fraction below land in the same relative spot the preview showed *and* the same
    // spot the featured card will later display — without it, a different crop applied only at
    // display time shifts where the caption ends up, sometimes into the name/streak bar at the
    // very bottom of the card, and how far it shifts depends on that device's own native capture
    // ratio, which is why this only ever showed up on some recipients' devices and not others.
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

/** Center-crops [source] down to [targetRatio] (width/height), the same way [ContentScale.Crop]
 * would at display time — trims the sides if [source] is relatively wider than [targetRatio],
 * or the top/bottom if it's relatively taller. Returns [source] itself, unmodified, if it's
 * already at (or extremely close to) that ratio. */
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
