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
import org.springframework.web.util.HtmlUtils
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
     * configured, and never throws either way, so there's nothing to guard here beyond that.
     *
     * Every user-controlled field ([User.displayName]/[User.username], and especially
     * [UserReport.details] — free text someone else typed) goes through [HtmlUtils.htmlEscape]
     * before landing in [html] below. Without it, a report whose details happened to contain
     * `<`/`>`/`&` would render broken (or, worse, someone could deliberately craft details
     * containing real HTML/links to make the alert email itself look like something it isn't —
     * this email already carries real authority, since it's what decides whether a human goes
     * looks at the reported account). The plain-text [text] fallback needs no escaping — it's
     * never parsed as markup by anything. */
    private fun sendAlertEmail(report: UserReport, reporter: User, reportedUser: User) {
        if (moderationProperties.alertEmail.isBlank()) return

        fun esc(value: String) = HtmlUtils.htmlEscape(value)
        val detailsHtml = report.details?.let { esc(it) }
            ?: "<span style=\"color:#9ca3af;font-style:italic;\">No details provided</span>"

        val html = """
            <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:480px;margin:0 auto;padding:24px;background:#ffffff;color:#111827;">
              <h2 style="margin:0 0 4px;font-size:18px;color:#111827;">New report on Emigo</h2>
              <p style="margin:0 0 20px;font-size:13px;color:#6b7280;">${esc(report.createdAt.toString())}</p>
              <table style="width:100%;border-collapse:collapse;font-size:14px;">
                <tr>
                  <td style="padding:10px 0;border-top:1px solid #e5e7eb;color:#6b7280;width:110px;vertical-align:top;">Reported</td>
                  <td style="padding:10px 0;border-top:1px solid #e5e7eb;">
                    <div style="font-weight:600;">${esc(reportedUser.displayName)} <span style="font-weight:400;color:#6b7280;">@${esc(reportedUser.username)}</span></div>
                    <div style="color:#6b7280;font-size:12px;">${esc(reportedUser.email)}</div>
                  </td>
                </tr>
                <tr>
                  <td style="padding:10px 0;border-top:1px solid #e5e7eb;color:#6b7280;vertical-align:top;">Reported by</td>
                  <td style="padding:10px 0;border-top:1px solid #e5e7eb;">
                    <div style="font-weight:600;">${esc(reporter.displayName)} <span style="font-weight:400;color:#6b7280;">@${esc(reporter.username)}</span></div>
                    <div style="color:#6b7280;font-size:12px;">${esc(reporter.email)}</div>
                  </td>
                </tr>
                <tr>
                  <td style="padding:10px 0;border-top:1px solid #e5e7eb;color:#6b7280;vertical-align:top;">Reason</td>
                  <td style="padding:10px 0;border-top:1px solid #e5e7eb;font-weight:600;">${esc(report.reason.name)}</td>
                </tr>
                <tr>
                  <td style="padding:10px 0;border-top:1px solid #e5e7eb;color:#6b7280;vertical-align:top;">Details</td>
                  <td style="padding:10px 0;border-top:1px solid #e5e7eb;white-space:pre-wrap;">$detailsHtml</td>
                </tr>
              </table>
              <p style="margin:20px 0 0;font-size:11px;color:#9ca3af;">Report ID ${esc(report.id.toString())}</p>
            </div>
        """.trimIndent()

        val text = """
            New report on Emigo (${report.createdAt})

            Reported: ${reportedUser.displayName} (@${reportedUser.username}, ${reportedUser.email})
            Reported by: ${reporter.displayName} (@${reporter.username}, ${reporter.email})
            Reason: ${report.reason}
            Details: ${report.details ?: "No details provided"}

            Report ID ${report.id}
        """.trimIndent()

        emailService.send(
            to = moderationProperties.alertEmail,
            subject = "Emigo report: ${report.reason} — ${reportedUser.username}",
            html = html,
            text = text,
        )
    }
}
