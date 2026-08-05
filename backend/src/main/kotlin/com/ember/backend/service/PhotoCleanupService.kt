package com.ember.backend.service

import com.ember.backend.repository.PhotoRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

// Same 24h window PhotoRecipientRepository.findVisibleFeedPhotos uses to decide whether a
// superseded photo is still showing in a feed — this job only ever removes a photo once that
// same window says nobody can see it any more.
private const val PHOTO_GRACE_PERIOD_HOURS = 24L

/** Frees storage for photos nobody explicitly saved and nobody can see any more (see
 * PhotoRepository.findExpiredUnsavedPhotoIds for the exact eligibility rule) — the piece that
 * makes "only saved photos are kept forever" actually reduce storage, rather than just hiding
 * old sent photos from Memories while leaving every one of them sitting in R2 regardless. Runs
 * hourly; 24h is the coarsest anything here needs to be checked at, so an hourly pass is already
 * far more frequent than necessary — cheap insurance against a photo lingering a full extra day
 * before its window is next checked. */
@Service
class PhotoCleanupService(
    private val photoRepository: PhotoRepository,
    private val r2StorageService: R2StorageService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60 * 60 * 1000L)
    fun cleanupExpiredUnsavedPhotos() {
        val graceSince = Instant.now().minus(PHOTO_GRACE_PERIOD_HOURS, ChronoUnit.HOURS)
        val expiredIds = runCatching { photoRepository.findExpiredUnsavedPhotoIds(graceSince) }
            .onFailure { logger.error("Photo cleanup query failed", it) }
            .getOrNull() ?: return
        if (expiredIds.isEmpty()) return

        val photos = photoRepository.findAllById(expiredIds)
        logger.info("Photo cleanup: deleting {} expired, unsaved photo(s)", photos.size)
        // Each row deleted individually (deleteById is its own transaction via Spring Data JPA)
        // rather than one deleteAll for the whole batch — one bad row shouldn't roll back R2
        // deletes that already succeeded for everything processed before it.
        photos.forEach { photo ->
            r2StorageService.delete(photo.storageKey)
            runCatching { photoRepository.deleteById(photo.id) }
                .onFailure { logger.error("Failed to delete expired photo row: photoId={}", photo.id, it) }
        }
    }
}
