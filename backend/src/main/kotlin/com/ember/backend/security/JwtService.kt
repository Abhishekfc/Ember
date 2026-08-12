package com.ember.backend.security

import com.ember.backend.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(private val jwtProperties: JwtProperties) {

    private val signingKey: SecretKey by lazy {
        val keyBytes = jwtProperties.secret.toByteArray(Charsets.UTF_8)
        require(keyBytes.size >= 32) { "ember.jwt.secret must be at least 32 bytes for HS256" }
        Keys.hmacShaKeyFor(keyBytes)
    }

    fun issueAccessToken(userId: UUID, email: String): String {
        val now = Instant.now()
        val expiry = now.plus(jwtProperties.accessTokenTtlMinutes, ChronoUnit.MINUTES)
        return Jwts.builder()
            .issuer(jwtProperties.issuer)
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact()
    }

    /** A verified token's contents. [issuedAt] is carried alongside the id because authentication
     * now depends on *when* a token was issued, not only who it names — see
     * [com.ember.backend.model.User.tokensValidFrom]. */
    data class VerifiedToken(val userId: UUID, val issuedAt: Instant)

    /** Null for anything this server didn't issue and can't read back as a usable token. The
     * `UUID.fromString` used to sit outside any error handling, so a validly-signed token whose
     * subject wasn't a UUID threw straight out of the authentication filter as an unhandled 500
     * instead of simply failing to authenticate — which is what any unusable token should do.
     *
     * A token with no `iat` is treated as unusable rather than as "issued at the beginning of
     * time": every token this service issues sets one, so a missing claim means the token didn't
     * come from here in a form we can reason about, and defaulting it would make such a token
     * *older* than any cutoff and so silently unrevocable. */
    fun parseToken(token: String): VerifiedToken? {
        val claims = parseClaims(token) ?: return null
        val userId = runCatching { UUID.fromString(claims.subject) }.getOrNull() ?: return null
        val issuedAt = claims.issuedAt?.toInstant() ?: return null
        return VerifiedToken(userId, issuedAt)
    }

    private fun parseClaims(token: String): Claims? = try {
        Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
    } catch (ex: ExpiredJwtException) {
        null
    } catch (ex: JwtException) {
        null
    } catch (ex: IllegalArgumentException) {
        null
    }
}
