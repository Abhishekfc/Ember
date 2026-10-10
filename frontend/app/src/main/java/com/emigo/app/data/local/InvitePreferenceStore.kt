package com.emigo.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.emigo.app.invite.InviteStore
import kotlinx.coroutines.flow.first

/** [InviteStore] kept in the app's shared DataStore. */
class InvitePreferenceStore(private val context: Context) : InviteStore {

    private val checkedKey = booleanPreferencesKey("invite_referrer_checked")
    private val inviterKey = stringPreferencesKey("invite_pending_inviter")

    override suspend fun hasCheckedReferrer(): Boolean = context.emberDataStore.data.first()[checkedKey] ?: false

    override suspend fun markReferrerChecked() {
        context.emberDataStore.edit { it[checkedKey] = true }
    }

    override suspend fun pendingInviter(): String? = context.emberDataStore.data.first()[inviterKey]

    override suspend fun setPendingInviter(username: String?) {
        context.emberDataStore.edit {
            if (username == null) it.remove(inviterKey) else it[inviterKey] = username
        }
    }

    /** Called on sign-out with the other per-account preferences: an invite not yet dealt with
     * belongs to the account that was signed in, not to whoever signs in next. The "checked" flag
     * stays, because the install itself, and what it came from, doesn't change. */
    suspend fun clearPending() {
        context.emberDataStore.edit { it.remove(inviterKey) }
    }
}
