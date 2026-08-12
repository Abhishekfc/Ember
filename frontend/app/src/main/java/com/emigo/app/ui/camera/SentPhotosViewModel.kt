package com.emigo.app.ui.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.PhotoRepository
import com.emigo.app.data.remote.dto.SentPhotoDto
import kotlinx.coroutines.launch

/** Backs the Camera outbox screen — this account's own recent, unsaved sends, with Unsend. Not
 * cached (see PhotoRepository.getSentPhotos' own doc comment): always refetched fresh whenever
 * this screen opens, since the whole list is defined by a moving 24h window, not a stable one. */
class SentPhotosViewModel(
    private val repository: PhotoRepository,
) : ViewModel() {

    var photos by mutableStateOf<List<SentPhotoDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** Non-null only while an unsend is actually in flight for that one photo — lets the
     * full-screen viewer show a spinner on just that photo's own menu button instead of a
     * screen-wide loading state. */
    var unsendingPhotoId by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.getSentPhotos().fold(
                onSuccess = { photos = it },
                onFailure = { errorMessage = it.message ?: "Couldn't load your sent photos" },
            )
            isLoading = false
        }
    }

    /** Unsends [photoId] — removes it from [photos] immediately on success. Nothing else needs to
     * separately account for a photo aging past its 24h window or R2 cleanup having already
     * removed it (see PhotoRepository's own backing query): either one just means a future
     * [load] wouldn't return that photo either, so there's no separate "expire from the list"
     * logic to keep in sync with this. A failure (most commonly the server's own
     * "This photo can no longer be unsent" once the window's passed) leaves [photos] untouched
     * and reports [errorMessage] for the caller to show. */
    suspend fun unsend(photoId: String): Result<Unit> {
        unsendingPhotoId = photoId
        val result = repository.unsendPhoto(photoId).onSuccess {
            photos = photos.filterNot { it.photoId == photoId }
        }.onFailure {
            errorMessage = it.message ?: "Couldn't unsend that photo"
        }
        unsendingPhotoId = null
        return result
    }
}
