package com.emigo.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenRequestDto(
    val fcmToken: String,
)
