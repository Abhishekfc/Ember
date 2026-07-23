package com.ember.app.ui.camera

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
import com.ember.app.data.FriendRepository
import com.ember.app.data.PhotoRepository
import com.ember.app.data.SubscriptionRepository
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.PhotoUploadResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Where the caption sits vertically, as a fraction of image height — must match where the
 * preview overlay draws it so what you see is what your friend gets. */
internal const val CAPTION_Y_FRACTION = 0.72f

/** A generously high ceiling for "give me every friend to choose a recipient from" — not a real
 * pagination page size, just far above any real user's friend count. */
private const val RECIPIENT_PICKER_FRIENDS_LIMIT = 500

class CameraViewModel(
    private val friendRepository: FriendRepository,
    private val photoRepository: PhotoRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set
    var selectedRecipientIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var isSending by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

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

    init {
        // Deliberately NOT loadFriends() here — this ViewModel now lives for the whole app
        // session (Camera is a pager page, not a screen only created on demand), so anything
        // fired from init runs on every single cold start whether or not Camera is ever opened
        // that session. loadFriends() is a limit=500 "give me everyone" fetch for the recipient
        // picker specifically — MainActivity calls it once, lazily, the first time the user
        // actually reaches the Camera page (see its own comment for why), not here.
        viewModelScope.launch {
            subscriptionRepository.getStatus().onSuccess { isGoldMember = it.isActive }
        }
    }

    val selectedFriends: List<FriendSummaryDto>
        get() = friends.filter { it.friendId in selectedRecipientIds }

    val recipientLabel: String
        get() {
            val selected = selectedFriends
            return when {
                selected.isEmpty() -> "Choose recipients"
                selected.size == 1 -> selected.first().displayName
                else -> "${selected.size} people"
            }
        }

    val hasPinnedSelected: Boolean
        get() = friends.any { it.friendId in selectedRecipientIds && it.pinnedByMe }

    /** Reuses a friend list Friends' own tab has already fetched, when it's known to already be
     * complete (see MainActivity — only used when FriendsViewModel has loaded everything, i.e.
     * `hasMore == false`), instead of this ViewModel firing its own separate, mostly-redundant
     * network call for what's very often the exact same data. [loadFriends] below remains the
     * fallback for whenever that isn't the case (Friends hasn't loaded yet, or genuinely has more
     * than a page of friends). */
    fun provideFriends(list: List<FriendSummaryDto>) {
        applyFriends(list)
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

    // Shared by provideFriends and loadFriends so both paths apply the exact same "default to the
    // pinned partner" rule below — never to "everyone" with no explicit choice. A silent
    // reply-all default is exactly the kind of invisible behavior that makes a send flow feel
    // unsafe rather than just unpolished: nothing should leave the device to a friend the user
    // never actually picked.
    private fun applyFriends(list: List<FriendSummaryDto>) {
        friends = list
        if (selectedRecipientIds.isEmpty()) {
            selectedRecipientIds = list.filter { it.pinnedByMe }.map { it.friendId }.toSet()
        }
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

    fun onPhotoCaptured(file: File) {
        capturedFile = file
        captionText = ""
        errorMessage = null
    }

    fun onCaptionChange(value: String) {
        captionText = value
    }

    fun discardCapture() {
        capturedFile?.delete()
        capturedFile = null
        captionText = ""
    }

    fun sendCaptured(onSuccess: (PhotoUploadResponseDto) -> Unit) {
        // Guards the top of the function itself, not just the button's own `enabled` — enabled
        // only takes effect once Compose recomposes after isSending flips true, so a fast
        // double-tap landing inside that window could otherwise launch this twice and send the
        // same photo to every recipient twice over.
        if (isSending) return
        val file = capturedFile ?: return
        if (selectedRecipientIds.isEmpty()) {
            errorMessage = "Select at least one friend first"
            return
        }
        viewModelScope.launch {
            isSending = true
            errorMessage = null
            val toSend = withContext(Dispatchers.Default) {
                runCatching { bakeCaptionIntoPhoto(file, captionText) }.getOrDefault(file)
            }
            photoRepository.uploadPhoto(toSend, selectedRecipientIds.toList()).fold(
                onSuccess = { response ->
                    // Cleans up the cache file(s) now that they've actually been sent — this
                    // used to only drop the in-memory reference, leaving every sent photo (plus
                    // its captioned copy, when there was a caption) on disk forever.
                    file.delete()
                    if (toSend != file) toSend.delete()
                    capturedFile = null
                    captionText = ""
                    onSuccess(response)
                },
                onFailure = { errorMessage = it.message ?: "Couldn't send your photo" },
            )
            isSending = false
        }
    }
}

/** Draws the caption onto the photo itself so recipients see it everywhere (feed, widget) with
 * no backend support for captions needed. Returns the original file untouched for a blank
 * caption; otherwise decodes (honoring EXIF rotation, which would be lost by re-encoding),
 * paints a Snapchat-style dark bar + centered white text, and writes a new JPEG. */
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
    // A capture is a full-resolution bitmap (tens of MB decoded) — up to three can transiently
    // exist here (decoded, upright, bitmap) if left to GC alone, and retake/re-caption repeats
    // this every time in one Camera session. Recycling each intermediate the moment it's
    // superseded keeps at most two full-resolution bitmaps live at once instead of three.
    if (upright !== decoded) decoded.recycle()

    val bitmap = if (upright.isMutable) upright else upright.copy(Bitmap.Config.ARGB_8888, true)
    if (bitmap !== upright) upright.recycle()
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
