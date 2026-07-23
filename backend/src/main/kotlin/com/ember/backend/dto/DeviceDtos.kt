package com.ember.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DeviceTokenRequest(
    @field:NotBlank @field:Size(max = 512) val fcmToken: String,
)
