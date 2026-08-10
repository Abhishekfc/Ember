package com.ember.backend.controller

import com.ember.backend.dto.ChangePasswordRequest
import com.ember.backend.dto.UpdateProfileRequest
import com.ember.backend.dto.UsernameAvailability
import com.ember.backend.dto.UserProfile
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.AuthService
import com.ember.backend.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/users/me")
class UserController(
    private val userService: UserService,
    private val authService: AuthService,
) {

    @GetMapping
    fun getProfile(@AuthenticationPrincipal me: AuthenticatedUser): UserProfile =
        userService.getProfile(me.id)

    @PatchMapping
    fun updateProfile(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): UserProfile = userService.updateProfile(me.id, request)

    @GetMapping("/username-availability")
    fun checkUsernameAvailability(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam username: String,
    ): UsernameAvailability = userService.checkUsernameAvailability(me.id, username)

    @PostMapping("/photo", consumes = ["multipart/form-data"])
    fun updatePhoto(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestPart("file") file: MultipartFile,
    ): UserProfile = userService.updateProfilePhoto(me.id, file)

    @PostMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): ResponseEntity<Void> {
        authService.changePassword(me.id, request)
        return ResponseEntity.noContent().build()
    }

    // No re-auth (password re-entry) required here — the client already gates this behind its
    // own "type DELETE to confirm" dialog, and the request itself is already authenticated by
    // the same JWT every other /users/me call requires.
    @DeleteMapping
    fun deleteAccount(@AuthenticationPrincipal me: AuthenticatedUser): ResponseEntity<Void> {
        userService.deleteAccount(me.id)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }
}
