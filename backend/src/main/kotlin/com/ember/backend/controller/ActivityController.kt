package com.ember.backend.controller

import com.ember.backend.dto.ActivityEvent
import com.ember.backend.dto.ActivityLastSeen
import com.ember.backend.dto.Page
import com.ember.backend.security.AuthenticatedUser
import com.ember.backend.service.ActivityService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
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

    /** Backs the Activity tab's nav-dock badge dot — a real per-account value (not on-device
     * storage) so it survives a reinstall. See [ActivityService.getLastSeen]. */
    @GetMapping("/seen")
    fun getLastSeen(@AuthenticationPrincipal me: AuthenticatedUser): ActivityLastSeen = activityService.getLastSeen(me.id)

    @PostMapping("/seen")
    fun markSeen(@AuthenticationPrincipal me: AuthenticatedUser): ActivityLastSeen = activityService.markSeen(me.id)
}
