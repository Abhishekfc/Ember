package com.ember.backend.service

import com.ember.backend.model.Photo
import com.ember.backend.model.PhotoRecipient
import com.ember.backend.model.User
import com.ember.backend.repository.PhotoRecipientRepository
import com.ember.backend.repository.PhotoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Just the DB-write portion of a photo upload, deliberately split out of [PhotoService] into
 * its own bean so it can carry its own short `@Transactional` boundary — the R2 upload and FCM
 * push around it in [PhotoService.upload] are both unbounded external I/O with no timeout
 * configured, and used to run *inside* the same transaction as these writes, holding a pooled DB
 * connection for however long that external call took. A `@Transactional` method only actually
 * gets transaction advice applied when invoked *through* the Spring proxy from a different bean —
 * calling it from within the same class (self-invocation) silently skips the transaction
 * entirely — which is why this had to become its own class rather than a private method. */
@Service
class PhotoWriteService(
    private val photoRepository: PhotoRepository,
    private val photoRecipientRepository: PhotoRecipientRepository,
) {
    @Transactional
    fun persist(sender: User, storageKey: String, contentType: String, recipients: List<User>): Pair<Photo, List<PhotoRecipient>> {
        val photo = photoRepository.save(Photo(sender = sender, storageKey = storageKey, contentType = contentType))
        val photoRecipients = photoRecipientRepository.saveAll(
            recipients.map { recipient -> PhotoRecipient(photo = photo, recipient = recipient, deliveredAt = Instant.now()) }
        )
        return photo to photoRecipients
    }
}
