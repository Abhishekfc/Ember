package com.ember.backend.exception

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the app reads when a streak restore is refused for lack of watched ads: the 402 carries how
 * many ads it takes and how many are on record, and no other error carries those two fields. */
class AdRewardErrorBodyTest {

    private val handler = GlobalExceptionHandler()
    private val mapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    private fun json(response: org.springframework.http.ResponseEntity<ErrorResponse>): JsonNode =
        mapper.readTree(mapper.writeValueAsString(response.body))

    @Test
    fun `the refusal says how many ads it takes and how many are watched`() {
        val response = handler.handleAdRewardRequired(AdRewardRequiredException(adsRequired = 3, adsWatched = 1))

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.statusCode)
        val body = json(response)
        assertEquals(402, body["status"].asInt())
        assertEquals(3, body["adsRequired"].asInt())
        assertEquals(1, body["adsWatched"].asInt())
        assertTrue(body["message"].asText().contains("3"), "the message names the number")
    }

    @Test
    fun `other errors carry no ad counts`() {
        val response = handler.handleApiException(StreakRestoreNotAvailableException())

        val body = json(response)
        assertFalse(body.has("adsRequired"))
        assertFalse(body.has("adsWatched"))
    }
}
