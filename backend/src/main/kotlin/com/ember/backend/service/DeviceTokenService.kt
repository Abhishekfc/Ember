package com.ember.backend.service

import com.ember.backend.exception.ResourceNotFoundException
import com.ember.backend.model.DeviceToken
import com.ember.backend.repository.DeviceTokenRepository
import com.ember.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Most devices one account can have registered at once.
 *
 * Nothing bounded this before: `/devices/register` accepted any 512-character string as a token
 * and inserted a row for it, so a single authenticated account could add rows indefinitely. That
 * grows the table without limit and — because every push does a `findAllByUserIdIn` and sends to
 * everything it finds — makes each notification to that account fan out to as many junk tokens as
 * were inserted, turning one photo send into arbitrarily much work.
 *
 * Well above a real person's phone/tablet/spare-device count, so the pruning below only ever
 * touches genuinely stale tokens (an old device, a reinstall — FCM issues a new token each time
 * and never tells us the old one is gone until a send to it fails).
 */
private const val MAX_TOKENS_PER_USER = 10

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun registerToken(userId: UUID, fcmToken: String) {
        val existing = deviceTokenRepository.findByFcmToken(fcmToken)
        if (existing != null) {
            // Reassignment is the legitimate "someone signed into a different account on this
            // same device" case: the token identifies the *device*, so it must follow whoever is
            // now signed in there, or their pushes would keep going to the previous account.
            if (existing.user.id != userId) {
                existing.user = userRepository.findById(userId)
                    .orElseThrow { ResourceNotFoundException("User not found") }
                deviceTokenRepository.save(existing)
            }
            return
        }
        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("User not found") }
        deviceTokenRepository.save(DeviceToken(user = user, fcmToken = fcmToken))

        pruneOldestBeyondCap(userId)
    }

    /** Signing out has to drop this device's token, or the account it just signed out of keeps
     * pushing "<friend> sent you a photo" — with the sender's name — to a phone that is now on the
     * login screen, or in someone else's hands. Scoped to the caller's own token: a token that
     * belongs to a different account is left alone rather than deleted, so this can't be used to
     * silence anyone else's notifications by guessing a token. */
    @Transactional
    fun unregisterToken(userId: UUID, fcmToken: String) {
        val existing = deviceTokenRepository.findByFcmToken(fcmToken) ?: return
        if (existing.user.id != userId) return
        deviceTokenRepository.delete(existing)
    }

    /** Keeps only the [MAX_TOKENS_PER_USER] most recently registered tokens for an account. The
     * newest are kept because a token's age is the only signal available for which device is
     * still real — FCM reports a dead token only when a send to it actually fails (see
     * PushNotificationService.recordSendResult, the other half of keeping this table clean). */
    private fun pruneOldestBeyondCap(userId: UUID) {
        val tokens = deviceTokenRepository.findAllByUserId(userId)
        if (tokens.size <= MAX_TOKENS_PER_USER) return
        val stale = tokens.sortedByDescending { it.createdAt }.drop(MAX_TOKENS_PER_USER)
        deviceTokenRepository.deleteAll(stale)
    }
}
