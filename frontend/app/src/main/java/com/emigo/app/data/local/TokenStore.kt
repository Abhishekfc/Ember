package com.emigo.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Caches the signed-in user's display name for instant greeting UI on cold start, before the real
 * profile has been fetched over the network.
 *
 * This used to also hold the session credential itself (an encrypted JWT this backend issued),
 * which is why the class is still named `TokenStore` and why [displayName] still lives in the
 * *un*-encrypted ordinary DataStore alongside everything else in this app, as it always has — see
 * git history for that reasoning if the encrypted file ever needs resurrecting. There's no local
 * credential to store any more: Firebase Authentication owns the session now, persists it in its
 * own storage, and hands back a fresh ID token on demand (see NetworkModule's auth interceptor) —
 * this class has nothing left to do with authentication itself.
 */
class TokenStore(private val context: Context) {

    private val displayNameKey = stringPreferencesKey("display_name")

    val displayName: Flow<String?> = context.emberDataStore.data.map { it[displayNameKey] }

    suspend fun saveDisplayName(displayName: String) {
        context.emberDataStore.edit { it[displayNameKey] = displayName }
    }

    /** Called on sign-out alongside clearing the actual Firebase session
     * ([com.google.firebase.auth.FirebaseAuth.signOut]) — this only ever held this account's own
     * cached display name (and, below, its pending-verification echo), neither of which must leak
     * into whatever's shown before a different account's profile has loaded on this same device. */
    suspend fun clear() {
        context.emberDataStore.edit { it.remove(displayNameKey) }
        clearPendingVerification()
    }

    private val pendingVerificationUidKey = stringPreferencesKey("pending_verification_uid")
    private val pendingVerificationEmailKey = stringPreferencesKey("pending_verification_email")
    private val pendingVerificationDeadlineKey = longPreferencesKey("pending_verification_deadline")

    /** A local echo of the last [com.emigo.app.data.SignInOutcome.NeedsVerification] this device
     * actually saw for a given [PendingVerification.firebaseUid] — written from
     * AuthRepository.checkExistingProfile and AuthRepository.rememberPendingVerification, cleared
     * from AuthRepository.checkExistingProfile and AuthRepository.forgetPendingVerification.
     *
     * Exists purely so MainActivity can decide the *first frame* correctly on a cold start,
     * instead of only finding out after resumeSession's own network round trip (a Firebase reload
     * plus a force-refreshed ID token) completes. Without it, a still-pending account's cold start
     * briefly rendered the full app shell — since hasSavedSession is true for it exactly as it is
     * for anyone else — before that check came back and bounced it out to this same screen a beat
     * later. Whether that's actually still true is re-confirmed by that same network check every
     * time regardless; this is never the final answer, only the best guess available before it.
     */
    data class PendingVerification(val firebaseUid: String, val email: String, val deadlineMillis: Long)

    suspend fun readPendingVerification(): PendingVerification? {
        val prefs = context.emberDataStore.data.first()
        val uid = prefs[pendingVerificationUidKey] ?: return null
        val email = prefs[pendingVerificationEmailKey] ?: return null
        val deadline = prefs[pendingVerificationDeadlineKey] ?: return null
        return PendingVerification(uid, email, deadline)
    }

    suspend fun savePendingVerification(firebaseUid: String, email: String, deadlineMillis: Long) {
        context.emberDataStore.edit {
            it[pendingVerificationUidKey] = firebaseUid
            it[pendingVerificationEmailKey] = email
            it[pendingVerificationDeadlineKey] = deadlineMillis
        }
    }

    /** Called the moment a check comes back [com.emigo.app.data.SignInOutcome.SignedIn] — mirrors
     * FirebaseAuthenticationFilter clearing the same-purpose flag server-side once it sees a
     * verified token, so this local echo can't keep claiming a since-verified account is still
     * pending on some later cold start. Also fine to call on sign-out (see [clear]'s own callers):
     * a stale pending-verification entry belonging to whichever account was signed in must not
     * leak into a different account's cold start on the same device. */
    suspend fun clearPendingVerification() {
        context.emberDataStore.edit {
            it.remove(pendingVerificationUidKey)
            it.remove(pendingVerificationEmailKey)
            it.remove(pendingVerificationDeadlineKey)
        }
    }
}
