package com.ember.backend.controller

import com.ember.backend.dto.AddPhotoRecipientsRequest
import com.ember.backend.dto.FeedItem
import com.ember.backend.dto.MemoryPhoto
import com.ember.backend.dto.PhotoUploadResponse
import com.ember.backend.dto.SentPhoto
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.PhotoService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/photos")
class PhotosController(
    private val photoService: PhotoService,
    // Reaction feature disabled — see PhotoReactionService's own comment.
    // private val photoReactionService: PhotoReactionService,
) {

    @PostMapping(consumes = ["multipart/form-data"])
    fun upload(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestPart("file") file: MultipartFile,
        // Defaults keep this backward-compatible with any client that only ever sent
        // recipientIds — a save-only upload (the camera's bookmark button, no one selected to
        // send to) is what makes recipientIds legitimately empty now.
        @RequestParam(name = "recipientIds", required = false) recipientIds: List<UUID>?,
        @RequestParam(name = "save", defaultValue = "false") save: Boolean,
    ): ResponseEntity<PhotoUploadResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(photoService.upload(me.id, file, recipientIds.orEmpty(), save))

    @DeleteMapping("/{photoId}")
    fun delete(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable photoId: UUID,
    ): ResponseEntity<Void> {
        photoService.delete(me.id, photoId)
        return ResponseEntity.noContent().build()
    }

    /** Lets the camera's bookmark button reuse an already-uploaded photo instead of uploading the
     * same file a second time — see PhotoService.markSaved's own doc comment. */
    @PostMapping("/{photoId}/save")
    fun save(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable photoId: UUID,
    ): ResponseEntity<Void> {
        photoService.markSaved(me.id, photoId)
        return ResponseEntity.noContent().build()
    }

    /** The Send counterpart to [save] above — adds recipients to an already-uploaded photo
     * instead of uploading the same file again (see PhotoService.addRecipients). */
    @PostMapping("/{photoId}/recipients")
    fun addRecipients(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable photoId: UUID,
        @RequestBody request: AddPhotoRecipientsRequest,
    ): ResponseEntity<Void> {
        photoService.addRecipients(me.id, photoId, request.recipientIds)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/feed")
    fun feed(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam(defaultValue = "false") refresh: Boolean,
    ): List<FeedItem> = photoService.getFeed(me.id, forceRefresh = refresh)

    @GetMapping("/memories")
    fun memories(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam start: Instant,
        @RequestParam end: Instant,
    ): List<MemoryPhoto> = photoService.getMemoriesInRange(me.id, start, end)

    /** Backs the Camera outbox — see PhotoService.getRecentSent's own doc comment for why this
     * is a plain 24h-from-send bound rather than the feed's own visibility rule. */
    @GetMapping("/sent")
    fun sent(@AuthenticationPrincipal me: AuthenticatedUser): List<SentPhoto> = photoService.getRecentSent(me.id)

    @PostMapping("/{photoId}/seen")
    fun markSeen(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @PathVariable photoId: UUID,
    ): ResponseEntity<Void> {
        photoService.markSeen(me.id, photoId)
        return ResponseEntity.noContent().build()
    }

    // Reaction endpoint disabled — see PhotoReactionService's own comment.
    // /** PUT, not POST — this sets the caller's *current* reaction to [request.emoji] (toggling
    //  * it off if it's already that), so calling it again with the same body is idempotent,
    //  * unlike upload/markSeen which each represent a one-time event. */
    // @PutMapping("/{photoId}/reaction")
    // fun setReaction(
    //     @AuthenticationPrincipal me: AuthenticatedUser,
    //     @PathVariable photoId: UUID,
    //     @RequestBody request: SetReactionRequest,
    // ): SetReactionResponse =
    //     SetReactionResponse(myReaction = photoReactionService.setReaction(photoId, me.id, request.emoji))
}
