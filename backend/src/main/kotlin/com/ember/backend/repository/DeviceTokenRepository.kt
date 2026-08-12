package com.ember.backend.repository

import com.ember.backend.model.DeviceToken
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import java.util.UUID

interface DeviceTokenRepository : JpaRepository<DeviceToken, UUID> {
    fun findByFcmToken(fcmToken: String): DeviceToken?
    fun findAllByUserId(userId: UUID): List<DeviceToken>
    fun findAllByUserIdIn(userIds: Collection<UUID>): List<DeviceToken>

    /** Removes tokens FCM has reported as permanently dead — see
     * [com.ember.backend.service.PushNotificationService.recordSendResult]. Annotated
     * `@Transactional` here rather than relying on the caller: this is a derived *delete* query,
     * and Hibernate throws `TransactionRequiredException` executing one outside a transaction —
     * the exact trap that silently broke `markSeen` once before (see PROJECT_CONTEXT). The caller
     * is a fire-and-forget push path with no transaction of its own, so it has to be declared
     * where the query lives. */
    @Modifying
    @Transactional
    fun deleteByFcmTokenIn(fcmTokens: Collection<String>)
}
