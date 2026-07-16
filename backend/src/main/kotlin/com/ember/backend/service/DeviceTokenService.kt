package com.ember.backend.service

import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.DeviceToken
import com.ember.backend.repository.DeviceTokenRepository
import com.ember.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun registerToken(userId: UUID, fcmToken: String) {
        val existing = deviceTokenRepository.findByFcmToken(fcmToken)
        if (existing != null) {
            if (existing.user.id != userId) {
                existing.user = userRepository.findById(userId)
                    .orElseThrow { ResourceNotFoundException("User not found") }
                deviceTokenRepository.save(existing)
            }
            return
        }
        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("User not found") }
        deviceTokenRepository.save(DeviceToken(user = user, fcmToken = fcmToken))
    }
}
