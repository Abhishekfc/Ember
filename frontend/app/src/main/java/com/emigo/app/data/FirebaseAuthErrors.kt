package com.emigo.app.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * Every Firebase Auth SDK call in this app (sign up, sign in, change password, re-authenticate)
 * throws one exception hierarchy for every possible failure — this is the one place that turns it
 * into the same kind of plain, user-facing sentence the rest of the app already shows for a failed
 * network call, rather than each call site pattern-matching Firebase's own exception types by
 * hand. Returns null for anything not specifically recognized, so the caller's own generic
 * fallback message still applies rather than this silently returning a blank string.
 */
fun firebaseErrorMessage(ex: Throwable): String? = when (ex) {
    is FirebaseAuthInvalidUserException -> "No account found with that email"
    is FirebaseAuthUserCollisionException -> "An account with this email already exists"
    is FirebaseAuthWeakPasswordException -> "Choose a stronger password"
    // Covers both "wrong password" and "malformed credential" — the SDK no longer distinguishes
    // these in current versions, and neither would a user benefit from the distinction.
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password"
    is FirebaseAuthRecentLoginRequiredException -> "Please sign in again to continue"
    is FirebaseTooManyRequestsException -> "Too many attempts — please try again later"
    is FirebaseNetworkException -> "Couldn't connect — check your connection"
    else -> null
}
