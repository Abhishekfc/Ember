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
import com.ember.app.data.remote.dto.FriendSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Where the caption sits vertically, as a fraction of image height — must match where the
 * preview overlay draws it so what you see is what your friend gets. */
internal const val CAPTION_Y_FRACTION = 0.72f

class CameraViewModel(
    private val friendRepository: FriendRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

    var friends by mutableStateOf<List<FriendSummaryDto>>(emptyList())
        private set
    var selectedRecipientIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var isSending by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Captured (or gallery-picked) photo waiting on the preview stage — nothing is sent
     * until the user reviews it and taps Send. */
    var capturedFile by mutableStateOf<File?>(null)
        private set
    var captionText by mutableStateOf("")
        private set

    init {
        loadFriends()
    }

    val recipientLabel: String
        get() {
            val selected = friends.filter { it.friendId in selectedRecipientIds }
            return when {
                selected.isEmpty() -> "Select recipients"
                selected.size == 1 -> selected.first().displayName
                else -> "${selected.size} people"
            }
        }

    val hasPinnedSelected: Boolean
        get() = friends.any { it.friendId in selectedRecipientIds && it.pinnedByMe }

    fun loadFriends() {
        viewModelScope.launch {
            friendRepository.getFriends().fold(
                onSuccess = { list ->
                    friends = list
                    // Default: the pinned partner if there is one, otherwise everyone — matches
                    // "pinning makes them your default recipient" without forcing an empty
                    // selection (and an extra tap into the picker) for the common single-friend
                    // testing case.
                    if (selectedRecipientIds.isEmpty()) {
                        val pinned = list.filter { it.pinnedByMe }
                        selectedRecipientIds = (pinned.ifEmpty { list }).map { it.friendId }.toSet()
                    }
                },
                onFailure = { errorMessage = it.message ?: "Couldn't load your friends" },
            )
        }
    }

    fun setSelectedRecipients(ids: Set<String>) {
        selectedRecipientIds = ids
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

    fun sendCaptured(onSuccess: () -> Unit) {
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
                onSuccess = {
                    capturedFile = null
                    captionText = ""
                    onSuccess()
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

    val bitmap = if (upright.isMutable) upright else upright.copy(Bitmap.Config.ARGB_8888, true)
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
