package com.ember.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists the JWT issued at login/register so the app stays signed in across launches. */
class TokenStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("jwt_token")

    val token: Flow<String?> = context.emberDataStore.data.map { it[tokenKey] }

    suspend fun currentToken(): String? = token.first()

    suspend fun save(token: String) {
        context.emberDataStore.edit { it[tokenKey] = token }
    }

    suspend fun clear() {
        context.emberDataStore.edit { it.remove(tokenKey) }
    }
}
