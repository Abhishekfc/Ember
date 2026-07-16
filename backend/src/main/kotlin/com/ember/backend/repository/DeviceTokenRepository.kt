package com.ember.backend.repository

import com.ember.backend.model.DeviceToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceTokenRepository : JpaRepository<DeviceToken, UUID> {
    fun findByFcmToken(fcmToken: String): DeviceToken?
    fun findAllByUserId(userId: UUID): List<DeviceToken>
    fun findAllByUserIdIn(userIds: Collection<UUID>): List<DeviceToken>
}
