package com.ember.backend.service

import com.ember.backend.config.AdProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.UUID

/** Stands in for Google in tests: its own signing key, and callbacks signed the way Google signs
 * them (ECDSA over SHA-256 of the query, URL-safe base64, `signature` then `key_id` last). */
class AdCallbackFixture(val adUnit: String = "5224354917", val keyId: String = "3335741209") {

    val keyPair: KeyPair = newKeyPair()

    /** Knows only this fixture's key, like Google's published list would know only Google's. */
    val keyProvider: AdMobKeyProvider = object : AdMobKeyProvider {
        override fun publicKey(keyId: String): PublicKey? = if (keyId == this@AdCallbackFixture.keyId) keyPair.public else null
    }

    fun verifier(rewardAdUnitIds: String = adUnit) =
        AdRewardVerifier(keyProvider, AdProperties(rewardAdUnitIds = rewardAdUnitIds))

    /** The callback's query as Google would send it, signed unless [signWith] says otherwise. */
    fun query(
        userId: UUID,
        friendshipId: UUID,
        timestampMillis: Long,
        transactionId: String = "tx-1",
        adUnit: String = this.adUnit,
        extra: String = "",
        signWith: PrivateKey = keyPair.private,
        keyId: String = this.keyId,
    ): String {
        val content = "ad_network=5450213213286189855&ad_unit=$adUnit&custom_data=$friendshipId" +
            "&reward_amount=1&reward_item=streak&timestamp=$timestampMillis" +
            "&transaction_id=$transactionId&user_id=$userId$extra"
        return "$content&signature=${sign(content, signWith)}&key_id=$keyId"
    }

    fun sign(content: String, key: PrivateKey = keyPair.private): String {
        val bytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(content.toByteArray(Charsets.UTF_8))
            sign()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    }
}
