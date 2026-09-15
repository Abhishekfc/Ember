package com.ember.backend.service

import com.ember.backend.config.ResendProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Thin wrapper around Resend's HTTP API (resend.com) — one POST request with a JSON body, not
 * worth pulling in a whole SDK for the single call site this has today (ReportService's
 * moderation alert). No Gmail/SMTP involved — Resend needs only an API key, no 2FA/app-password
 * setup on anyone's actual Google account. */
@Service
class EmailService(
    private val resendProperties: ResendProperties,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newHttpClient()

    /** Best-effort by design — never throws. [ReportService] (and any future caller) can fire
     * this without its own try/catch; a failed or unconfigured send just logs a warning and
     * returns, so an email problem can never turn into a 500 for whatever real action triggered
     * it. [ResendProperties.apiKey] blank means this is simply unconfigured — the common state
     * before Resend is actually set up. */
    fun send(to: String, subject: String, body: String) {
        if (resendProperties.apiKey.isBlank()) return
        try {
            val payload = objectMapper.writeValueAsString(
                mapOf(
                    "from" to resendProperties.fromEmail,
                    "to" to listOf(to),
                    "subject" to subject,
                    "text" to body,
                ),
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer ${resendProperties.apiKey}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                logger.warn("Resend email failed: status={} body={}", response.statusCode(), response.body())
            }
        } catch (ex: Exception) {
            logger.warn("Could not send email via Resend", ex)
        }
    }
}
