package com.ember.backend.controller

import com.ember.backend.dto.CreateRecipientListRequest
import com.ember.backend.dto.RecipientListSummary
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.RecipientListService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/recipient-lists")
class RecipientListController(private val recipientListService: RecipientListService) {

    @GetMapping
    fun getLists(@AuthenticationPrincipal me: AuthenticatedUser): List<RecipientListSummary> =
        recipientListService.getLists(me.id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createList(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: CreateRecipientListRequest,
    ): RecipientListSummary = recipientListService.createList(me.id, request)

    @DeleteMapping("/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteList(@AuthenticationPrincipal me: AuthenticatedUser, @PathVariable listId: UUID) {
        recipientListService.deleteList(me.id, listId)
    }
}
