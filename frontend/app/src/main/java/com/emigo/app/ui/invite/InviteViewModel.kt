package com.emigo.app.ui.invite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.R
import com.emigo.app.core.StringProvider
import com.emigo.app.invite.InviteReferral
import com.emigo.app.invite.Inviter
import kotlinx.coroutines.launch

/** The "Add @ann?" offer shown once to someone who installed Emigo from ann's invite link. */
class InviteViewModel(
    private val strings: StringProvider,
    private val referral: InviteReferral,
    /** Told after a request goes out, so the friend lists can refresh quietly. */
    private val onRequestSent: () -> Unit = {},
) : ViewModel() {

    /** Who to offer to add, or null when there is nothing to offer (the usual case). */
    var inviter by mutableStateOf<Inviter?>(null)
        private set
    var isSending by mutableStateOf(false)
        private set

    /** A short message to show once (a toast). */
    var notice by mutableStateOf<String?>(null)
        private set

    private var hasChecked = false

    /** Looks for an invite, once per app launch. Never shows an error: no invite just means no offer. */
    fun check() {
        if (hasChecked) return
        hasChecked = true
        viewModelScope.launch { inviter = runCatching { referral.inviterToOffer() }.getOrNull() }
    }

    fun accept() {
        val current = inviter ?: return
        if (isSending) return
        viewModelScope.launch {
            isSending = true
            referral.accept(current).fold(
                onSuccess = {
                    notice = strings.get(R.string.invite_prompt_sent, current.displayName)
                    inviter = null
                    onRequestSent()
                },
                onFailure = {
                    // The invite is forgotten either way: they can still search for the person.
                    notice = strings.get(R.string.invite_prompt_failed)
                    referral.dismiss()
                    inviter = null
                },
            )
            isSending = false
        }
    }

    fun dismiss() {
        if (isSending) return
        inviter = null
        viewModelScope.launch { referral.dismiss() }
    }

    fun clearNotice() {
        notice = null
    }
}
