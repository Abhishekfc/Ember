package com.emigo.app.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's GlobalExceptionHandler error body shape. [adsRequired] and [adsWatched]
 * come only with the "watch ads to restore a streak" refusal (402). */
@Serializable
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String? = null,
    val adsRequired: Int? = null,
    val adsWatched: Int? = null,
)
