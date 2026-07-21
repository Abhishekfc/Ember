package com.ember.backend.controller

import com.ember.backend.dto.FeedItem
import com.ember.backend.dto.PhotoUploadResponse
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.PhotoService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/photos")
class PhotosController(private val photoService: PhotoService) {

    @PostMapping(consumes = ["multipart/form-data"])
    fun upload(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestPart("file") file: MultipartFile,
        @RequestParam("recipientIds") recipientIds: List<UUID>,
    ): ResponseEntity<PhotoUploadResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(photoService.upload(me.id, file, recipientIds))

    @GetMapping("/feed")
    fun feed(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam(defaultValue = "false") refresh: Boolean,
    ): List<FeedItem> = photoService.getFeed(me.id, forceRefresh = refresh)
}
