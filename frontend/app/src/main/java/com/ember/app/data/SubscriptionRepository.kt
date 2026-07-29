package com.ember.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ember.app.data.local.emberDataStore
import com.ember.app.data.remote.EmberApi
import com.ember.app.data.remote.dto.ErrorResponse
import com.ember.app.data.remote.dto.SubscriptionStatusDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class SubscriptionRepository(
    private val api: EmberApi,
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lastKnownIsActiveKey = booleanPreferencesKey("subscription_last_known_is_active")

    // Not mirroring a backend Redis TTL the way PhotoRepository's feedCache does (see TtlCache's
    // own doc comment) — subscription status has no server-side cache to match, since it changes
    // far less often than a feed. This cache exists purely to collapse the handful of
    // near-simultaneous callers around app open (CameraViewModel, ThemeViewModel, and the widget's
    // own one-shot check all independently ask "is this account Gold?" within the same moment)
    // into a single network round trip, not to avoid ever rechecking again — anything that needs
    // a fresher answer later just calls getStatus(forceRefresh = true).
    private val statusCache = TtlCache<Unit, SubscriptionStatusDto>(ttlMillis = 60_000)
    private val statusSingleFlight = SingleFlight<Unit, Result<SubscriptionStatusDto>>()

    /** Called on sign-out — this repository is a process-wide singleton that outlives any one
     * signed-in account (see PhotoRepository's own equivalent), so a different account signing in
     * within the cache window could otherwise be served the previous account's status. Doesn't
     * touch [lastKnownIsActive] itself — that's persisted to disk, not this in-memory cache, and
     * gets its own explicit clear from the same sign-out path (see MainActivity's onSignOut). */
    fun clearCache() {
        statusCache.invalidateAll()
    }

    suspend fun getStatus(forceRefresh: Boolean = false): Result<SubscriptionStatusDto> {
        if (!forceRefresh) {
            statusCache.get(Unit)?.let { return Result.success(it) }
        }
        return statusSingleFlight.run(Unit) {
            safeCall {
                val response = api.getSubscriptionStatus()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Result.success(body)
                } else {
                    val message = response.errorBody()?.string()?.let {
                        runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
                    } ?: "Couldn't check subscription status (${response.code()})"
                    Result.failure(Exception(message))
                }
            }.onSuccess {
                statusCache.put(Unit, it)
                // Persisted to disk (not just the in-memory cache above), specifically so
                // isGoldMemberOrLastKnown below still has a real answer across a full app
                // restart with no connectivity at all, not just within one still-running process.
                context.emberDataStore.edit { prefs -> prefs[lastKnownIsActiveKey] = it.isActive }
            }
        }
    }

    /** The one call every Gold-gated screen should actually use to decide `isGoldMember`, rather
     * than defaulting straight to "not Gold" the moment [getStatus] fails. A live check failing
     * means "couldn't reach the server," not "not subscribed" — the two look identical from a
     * plain `Result.failure`, and treating every offline moment as if the subscription had been
     * cancelled would lock a genuine subscriber out of their own paid features (themes, gallery
     * picking) the instant they lost signal. Falls back to whatever the last *successful* check
     * confirmed, persisted to disk so this survives a full app restart while still offline, not
     * just a network blip mid-session. */
    suspend fun isGoldMemberOrLastKnown(): Boolean =
        getStatus().fold(
            onSuccess = { it.isActive },
            onFailure = { context.emberDataStore.data.first()[lastKnownIsActiveKey] ?: false },
        )

    /** Called on sign-out alongside [clearCache] — a different account signing in on the same
     * device must never inherit the previous account's last-known subscription status. */
    suspend fun clearLastKnownStatus() {
        context.emberDataStore.edit { it.remove(lastKnownIsActiveKey) }
    }
}
