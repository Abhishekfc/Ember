package com.ember.backend.controller

import com.ember.backend.service.AdRewardService
import com.ember.backend.service.RecordOutcome
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The address Google's AdMob servers call when someone finishes a rewarded ad. It has no login
 * (Google can't sign in) and is listed as public in SecurityConfig; what protects it is the
 * signature check in [com.ember.backend.service.AdRewardVerifier]. */
@RestController
@RequestMapping("/ads")
class AdRewardController(private val adRewardService: AdRewardService) {

    @GetMapping("/reward-callback")
    fun rewardCallback(request: HttpServletRequest): ResponseEntity<Void> {
        // The raw query, not parsed parameters: Google signed those exact characters.
        val status = when (adRewardService.record(request.queryString)) {
            RecordOutcome.INVALID -> HttpStatus.FORBIDDEN
            RecordOutcome.RECORDED, RecordOutcome.DUPLICATE, RecordOutcome.IGNORED -> HttpStatus.OK
        }
        return ResponseEntity.status(status).build()
    }
}
