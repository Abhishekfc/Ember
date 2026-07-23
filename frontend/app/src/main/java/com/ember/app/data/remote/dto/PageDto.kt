package com.ember.app.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirrors the backend's `Page<T>` — a single page of an offset-paginated list (Friends,
 * Activity, Memories), rather than the whole thing at once. */
@Serializable
data class PageDto<T>(
    val items: List<T>,
    val hasMore: Boolean,
)
