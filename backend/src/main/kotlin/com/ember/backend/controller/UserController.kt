package com.ember.backend.controller

import com.ember.backend.dto.UpdateProfileRequest
import com.ember.backend.dto.UserProfile
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.UserService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/users/me")
class UserController(private val userService: UserService) {

    @GetMapping
    fun getProfile(@AuthenticationPrincipal me: AuthenticatedUser): UserProfile =
        userService.getProfile(me.id)

    @PatchMapping
    fun updateProfile(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestBody request: UpdateProfileRequest,
    ): UserProfile = userService.updateProfile(me.id, request)

    @PostMapping("/photo", consumes = ["multipart/form-data"])
    fun updatePhoto(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestPart("file") file: MultipartFile,
    ): UserProfile = userService.updateProfilePhoto(me.id, file)
}
