package com.emigo.app.data

import android.content.Context
import com.emigo.app.data.remote.EmberApi
import com.emigo.app.data.remote.dto.ErrorResponse
import com.emigo.app.data.remote.dto.SubscriptionStatusDto
import com.emigo.app.data.remote.dto.SubscriptionVerifyRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class SubscriptionRepository(
    private val api: EmberApi,
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Plain SharedPreferences, not DataStore — read synchronously so every Gold-gated
    // ViewModel's very first composed frame can already show the right answer. DataStore's own
    // read (the real getStatus() network call below) is a suspend function with a real gap
    // before it resolves; every one of these ViewModels used to seed isGoldMember straight to a
    // hardcoded `false` for that whole gap, which flashed a lock badge/upsell over a genuine
    // subscriber's Gold features for a moment on every cold start before snapping back once the
    // real check landed. Mirrors ThemePreferenceStore's own lastEffectiveThemeSync/
    // saveEffectiveThemeSync — same problem, same fix, already proven elsewhere in this app.
    private val syncPrefs = context.getSharedPreferences("ember_subscription_sync", Context.MODE_PRIVATE)
    private val syncIsGoldMemberKey = "is_gold_member"

    /** The value every Gold-gated ViewModel should seed its own `isGoldMember` state from at
     * construction time, instead of a hardcoded `false`. Defaults to `false` only the very first
     * time this ever runs for an account (nothing saved yet) — from then on it's always the last
     * real answer [getStatus] confirmed, correct across restarts with no network needed. */
    fun isGoldMemberSync(): Boolean = syncPrefs.getBoolean(syncIsGoldMemberKey, false)

    /** Live, app-wide mirror of the same value — every Gold-gated ViewModel (Camera, Theme,
     * AppIcon, WidgetSettings, Friends) collects this alongside seeding from [isGoldMemberSync]
     * above, so the instant a purchase is verified on the Ember Gold screen, every other
     * already-alive screen unlocks immediately too, instead of only picking up the change on its
     * own next cold start. Without this, each ViewModel's `isGoldMember` was a one-shot read that
     * never changed again for the life of that instance — since Camera/Theme/etc. are long-lived,
     * app-session-scoped ViewModels, that meant a full app restart was the only way to see a
     * just-completed purchase reflected anywhere outside the Gold screen itself. */
    private val _isGoldMemberFlow = MutableStateFlow(isGoldMemberSync())
    val isGoldMemberFlow: StateFlow<Boolean> = _isGoldMemberFlow.asStateFlow()

    private fun saveIsGoldMemberSync(isActive: Boolean) {
        syncPrefs.edit().putBoolean(syncIsGoldMemberKey, isActive).apply()
        _isGoldMemberFlow.value = isActive
    }

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
     * touch the synced last-known flag above — that's persisted to disk, not this in-memory
     * cache, and gets its own explicit clear from the same sign-out path (see
     * [clearLastKnownStatus] and MainActivity's own onSignOut). */
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
                // isGoldMemberOrLastKnown/isGoldMemberSync above still have a real answer across a
                // full app restart with no connectivity at all, not just within one still-running
                // process.
                saveIsGoldMemberSync(it.isActive)
            }
        }
    }

    /** Hands a Play purchase token to the backend, which re-verifies it with Google and grants (or
     * doesn't) Gold. On success the fresh status is written straight into the same caches
     * [getStatus] fills — the in-memory TTL one and the disk-persisted last-known flag — so every
     * Gold-gated ViewModel that re-reads after this sees the new answer without its own network
     * call. */
    suspend fun verifyPurchase(productId: String, purchaseToken: String): Result<SubscriptionStatusDto> =
        safeCall {
            val response = api.verifySubscription(
                SubscriptionVerifyRequestDto(purchaseToken = purchaseToken, productId = productId),
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                statusCache.put(Unit, body)
                saveIsGoldMemberSync(body.isActive)
                Result.success(body)
            } else {
                val message = response.errorBody()?.string()?.let {
                    runCatching { json.decodeFromString<ErrorResponse>(it).message }.getOrNull()
                } ?: "Couldn't confirm your purchase (${response.code()})"
                Result.failure(Exception(message))
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
            onFailure = { isGoldMemberSync() },
        )

    /** Called on sign-out alongside [clearCache] — a different account signing in on the same
     * device must never inherit the previous account's last-known subscription status. */
    fun clearLastKnownStatus() {
        syncPrefs.edit().remove(syncIsGoldMemberKey).apply()
        _isGoldMemberFlow.value = false
    }
}
