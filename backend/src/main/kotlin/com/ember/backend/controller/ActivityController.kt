package com.ember.backend.controller

import com.ember.backend.dto.ActivityEvent
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.ActivityService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/activity")
class ActivityController(private val activityService: ActivityService) {

    @GetMapping
    fun getActivity(@AuthenticationPrincipal me: AuthenticatedUser): List<ActivityEvent> =
        activityService.getActivity(me.id)
}
