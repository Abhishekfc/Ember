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

    fun parseUserId(token: String): UUID? = parseClaims(token)?.let { UUID.fromString(it.subject) }

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
