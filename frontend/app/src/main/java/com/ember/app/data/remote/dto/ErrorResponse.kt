package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's GlobalExceptionHandler error body shape. */
@Serializable
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String? = null,
)
