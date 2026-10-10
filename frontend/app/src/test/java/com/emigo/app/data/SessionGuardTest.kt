package com.emigo.app.data

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Firebase's error classes check their message with Android's text utilities in the constructor,
 * which a plain unit test doesn't have. The code under test only looks at the error's type, so a
 * bare instance made without running the constructor is all these tests need. */
private fun <T : Throwable> bare(type: Class<T>): T {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    @Suppress("UNCHECKED_CAST")
    return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
}

/** Decides whether a push for an account should still update the widget. After a password change
 * Firebase ends the account's sessions; only a definite "this session is over" may switch the
 * widget off. Network trouble must never wipe someone's widget. */
class SessionGuardTest {

    @Test
    fun aRevokedOrDeletedOrDisabledAccountIsOver() {
        // What Firebase answers on the next refresh after a password change (token expired), or
        // when the account is gone or disabled: all one exception type.
        assertTrue(SessionGuard.isSessionEnded(bare(FirebaseAuthInvalidUserException::class.java)))
    }

    @Test
    fun invalidCredentialsAreOver() {
        assertTrue(SessionGuard.isSessionEnded(bare(FirebaseAuthInvalidCredentialsException::class.java)))
    }

    @Test
    fun noConnectionIsNotTheEndOfASession() {
        assertFalse(SessionGuard.isSessionEnded(bare(FirebaseNetworkException::class.java)))
        assertFalse(SessionGuard.isSessionEnded(IOException("timeout")))
    }

    @Test
    fun otherErrorsAreNotTheEndOfASession() {
        assertFalse(SessionGuard.isSessionEnded(bare(FirebaseException::class.java)))
        assertFalse(SessionGuard.isSessionEnded(RuntimeException("boom")))
        assertFalse(SessionGuard.isSessionEnded(IllegalStateException("not signed in yet")))
    }

    @Test
    fun aRecentConfirmationIsTrustedForTenMinutes() {
        val confirmedAt = 1_000_000L
        assertFalse(SessionGuard.shouldRecheck(nowMillis = confirmedAt + 1, lastConfirmedAtMillis = confirmedAt))
        assertFalse(SessionGuard.shouldRecheck(nowMillis = confirmedAt + 9 * 60 * 1000, lastConfirmedAtMillis = confirmedAt))
    }

    @Test
    fun anOldConfirmationIsCheckedAgain() {
        val confirmedAt = 1_000_000L
        assertTrue(SessionGuard.shouldRecheck(nowMillis = confirmedAt + SessionGuard.RECHECK_AFTER_MILLIS, lastConfirmedAtMillis = confirmedAt))
        assertTrue(SessionGuard.shouldRecheck(nowMillis = confirmedAt + 60 * 60 * 1000, lastConfirmedAtMillis = confirmedAt))
    }

    @Test
    fun theVeryFirstCheckAfterTheProcessStartsAlwaysAsksFirebase() {
        // Nothing has been confirmed yet in this process (last = 0), so a push can never be trusted
        // on an old answer: the first one asks.
        assertTrue(SessionGuard.shouldRecheck(nowMillis = System.currentTimeMillis(), lastConfirmedAtMillis = 0L))
    }
}
