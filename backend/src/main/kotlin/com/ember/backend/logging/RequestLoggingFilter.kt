package com.ember.backend.logging

import com.ember.backend.security.AuthenticatedUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Logs every request that hits the server: who made it (once JWT auth has resolved the caller),
 * method, path, status, and duration. Registered *after* JwtAuthenticationFilter in the security
 * chain (see SecurityConfig) specifically so the authenticated user is already known by the time
 * this runs.
 *
 * Deliberately never logs request/response bodies — /auth/login and /auth/register bodies
 * contain plaintext passwords, so body logging would put credentials in the log file.
 */
@Component
class RequestLoggingFilter : OncePerRequestFilter() {

    private val requestLogger = LoggerFactory.getLogger("com.ember.backend.http")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val start = System.currentTimeMillis()
        var thrown: Throwable? = null
        try {
            filterChain.doFilter(request, response)
        } catch (ex: Throwable) {
            thrown = ex
            throw ex
        } finally {
            val durationMs = System.currentTimeMillis() - start
            val query = request.queryString?.let { "?$it" } ?: ""
            val user = (SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser)
                ?.let { "user=${it.id}" } ?: "user=anonymous"
            requestLogger.info(
                "{} {}{} -> {} ({} ms) {} from {}",
                request.method,
                request.requestURI,
                query,
                response.status,
                durationMs,
                user,
                request.remoteAddr,
            )
            if (thrown != null) {
                requestLogger.error("Request failed with an uncaught exception", thrown)
            }
        }
    }
}
