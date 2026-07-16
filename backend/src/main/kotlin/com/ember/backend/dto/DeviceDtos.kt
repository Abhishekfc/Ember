package com.ember.backend.dto

import jakarta.validation.constraints.NotBlank

data class DeviceTokenRequest(
    @field:NotBlank val fcmToken: String,
)
