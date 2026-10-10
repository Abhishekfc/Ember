package com.emigo.app.invite

/** What the app remembers between launches about an install that came from an invite link. Small on
 * purpose, and an interface so the logic in [InviteReferral] can be tested without a phone. */
interface InviteStore {
    /** True once Google Play's install referrer has been read and answered (with or without an
     * invite in it), so it isn't read again. */
    suspend fun hasCheckedReferrer(): Boolean

    suspend fun markReferrerChecked()

    /** The username from an invite link that hasn't been dealt with yet, or null. */
    suspend fun pendingInviter(): String?

    suspend fun setPendingInviter(username: String?)
}
