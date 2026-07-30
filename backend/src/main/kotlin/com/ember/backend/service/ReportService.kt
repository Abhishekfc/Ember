package com.ember.backend.service

import com.ember.backend.dto.ReportUserRequest
import com.ember.backend.exception.InvalidSafetyActionException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.UserReport
import com.ember.backend.repository.UserReportRepository
import com.ember.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ReportService(
    private val userReportRepository: UserReportRepository,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Purely a moderation record today — nothing here automatically actions the reported
     * account (no auto-suspend, no auto-hide, blocking is a separate deliberate action); it's
     * logged for a human to review later. Rate-limited at the controller layer (same pattern as
     * FriendsController's own friend-request limit), so this can't be used to spam either a
     * specific account or the moderation queue as a whole. */
    fun report(reporterId: UUID, reportedUserId: UUID, request: ReportUserRequest) {
        if (reporterId == reportedUserId) throw InvalidSafetyActionException("You can't report yourself")
        val reporter = userRepository.findById(reporterId).orElseThrow { ResourceNotFoundException("User not found") }
        val reportedUser = userRepository.findById(reportedUserId).orElseThrow { ResourceNotFoundException("User not found") }

        userReportRepository.save(
            UserReport(
                reporter = reporter,
                reportedUser = reportedUser,
                reason = request.reason,
                details = request.details?.trim()?.take(500)?.ifBlank { null },
            ),
        )
        logger.info("User reported: reporterId={} reportedUserId={} reason={}", reporterId, reportedUserId, request.reason)
    }
}
