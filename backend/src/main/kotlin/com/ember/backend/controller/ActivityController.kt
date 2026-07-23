package com.ember.backend.controller

import com.ember.backend.dto.ActivityEvent
import com.ember.backend.dto.Page
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.ActivityService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/activity")
class ActivityController(private val activityService: ActivityService) {

    @GetMapping
    fun getActivity(
        @AuthenticationPrincipal me: AuthenticatedUser,
        @RequestParam(defaultValue = "false") refresh: Boolean,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "30") limit: Int,
    ): Page<ActivityEvent> = activityService.getActivity(me.id, offset = offset, limit = limit, forceRefresh = refresh)
}
