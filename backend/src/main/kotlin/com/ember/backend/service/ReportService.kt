package com.ember.backend.service

import com.ember.backend.config.ModerationProperties
import com.ember.backend.dto.ReportUserRequest
import com.ember.backend.exception.InvalidSafetyActionException
import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.User
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
    private val emailService: EmailService,
    private val moderationProperties: ModerationProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** A moderation record, plus (unlike before) a best-effort email alert — nothing here still
     * automatically actions the reported account (no auto-suspend, no auto-hide, blocking is a
     * separate deliberate action); a human decides what to do, this just makes sure a human
     * actually sees it instead of it sitting silently in a DB table. Rate-limited at the
     * controller layer (same pattern as FriendsController's own friend-request limit), so this
     * can't be used to spam either a specific account or the moderation queue as a whole. */
    fun report(reporterId: UUID, reportedUserId: UUID, request: ReportUserRequest) {
        if (reporterId == reportedUserId) throw InvalidSafetyActionException("You can't report yourself")
        val reporter = userRepository.findById(reporterId).orElseThrow { ResourceNotFoundException("User not found") }
        val reportedUser = userRepository.findById(reportedUserId).orElseThrow { ResourceNotFoundException("User not found") }

        val saved = userReportRepository.save(
            UserReport(
                reporter = reporter,
                reportedUser = reportedUser,
                reason = request.reason,
                details = request.details?.trim()?.take(500)?.ifBlank { null },
            ),
        )
        logger.info("User reported: reporterId={} reportedUserId={} reason={}", reporterId, reportedUserId, request.reason)
        sendAlertEmail(saved, reporter, reportedUser)
    }

    /** [ModerationProperties.alertEmail] blank means this is simply unconfigured (nothing set up
     * in Railway yet) — [EmailService.send] itself already no-ops the same way when Resend isn't
     * configured, and never throws either way, so there's nothing to guard here beyond that. */
    private fun sendAlertEmail(report: UserReport, reporter: User, reportedUser: User) {
        if (moderationProperties.alertEmail.isBlank()) return
        emailService.send(
            to = moderationProperties.alertEmail,
            subject = "Emigo report: ${report.reason} — ${reportedUser.username}",
            body = """
                Reported user: ${reportedUser.displayName} (@${reportedUser.username}, ${reportedUser.email}, id=${reportedUser.id})
                Reported by:   ${reporter.displayName} (@${reporter.username}, ${reporter.email}, id=${reporter.id})
                Reason:        ${report.reason}
                Details:       ${report.details ?: "(none provided)"}
                Reported at:   ${report.createdAt}
                Report id:     ${report.id}
            """.trimIndent(),
        )
    }
}
