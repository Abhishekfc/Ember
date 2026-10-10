package com.emigo.app.ads

import android.app.Activity
import com.emigo.app.data.AdRewardRequiredException
import com.emigo.app.data.remote.dto.FriendSummaryDto
import kotlinx.coroutines.delay

/** How a "watch an ad to restore this streak" attempt ended. */
sealed interface AdRestoreResult {
    data class Restored(val friend: FriendSummaryDto) : AdRestoreResult

    /** No ad could be loaded, so nothing was shown and nothing was spent. */
    data object AdUnavailable : AdRestoreResult

    /** The viewer closed the ad before the end. */
    data object AdClosedEarly : AdRestoreResult

    /** The ad was watched but the server hadn't heard from Google when we stopped asking. The
     * watched ad stays good on the server for a while, so asking again will usually work. */
    data object NotConfirmed : AdRestoreResult

    /** The server refused for another reason (the restore window has closed, no connection...).
     * [message] is its explanation when it gave one. */
    data class Failed(val message: String?) : AdRestoreResult
}

/** Which ad of how many is on screen now, for the sheet to show ("Ad 2 of 3"). */
data class AdProgress(val current: Int, val total: Int)

/**
 * Restores a broken streak for someone without Emigo Gold by having them watch ads, one after
 * another. How many it takes is the server's to say (it answers the first request with how many
 * ads it needs and how many it already has), so changing the number needs no app update.
 *
 * The app never decides an ad was watched. Google tells the server (see the backend's
 * AdRewardController), and the server restores the streak only when it has that word for every ad,
 * so this class just shows the ads and then asks the server to restore until it agrees. Asking
 * first, before any ad, also means a restore that can't happen (its window closed) never costs the
 * viewer an ad, and ads watched earlier (even by a viewer who stopped halfway) are not asked for
 * again.
 */
class RestoreStreakWithAd(
    private val ads: RewardedAds,
    private val adUnitId: String,
    private val myUserId: suspend () -> Result<String>,
    private val restore: suspend (friendshipId: String) -> Result<FriendSummaryDto>,
    private val retryDelayMillis: Long = 1_000,
    private val maxAttempts: Int = 12,
) {
    /** [onProgress] is told which ad is about to be shown, before each one. */
    suspend fun run(
        activity: Activity,
        friendshipId: String,
        onProgress: (AdProgress) -> Unit = {},
    ): AdRestoreResult {
        val firstAnswer = restore(friendshipId)
        firstAnswer.getOrNull()?.let { return AdRestoreResult.Restored(it) }
        val refusal = firstAnswer.exceptionOrNull()
        if (refusal !is AdRewardRequiredException) return AdRestoreResult.Failed(refusal?.message)

        var userId: String? = null
        var watched = refusal.watched
        while (watched < refusal.required) {
            onProgress(AdProgress(current = watched + 1, total = refusal.required))
            val ad = ads.load(activity, adUnitId) ?: return AdRestoreResult.AdUnavailable
            val id = userId ?: myUserId().getOrElse { return AdRestoreResult.Failed(it.message) }
            userId = id

            when (ad.show(activity, id, customData = friendshipId)) {
                ShowResult.EARNED -> watched++
                ShowResult.CLOSED_EARLY -> return AdRestoreResult.AdClosedEarly
                ShowResult.FAILED -> return AdRestoreResult.AdUnavailable
            }
        }
        return waitForServerToConfirm(friendshipId)
    }

    /** Google's word reaches the server a moment after the ad closes, usually within a few
     * seconds, so the server's "not yet" is asked again rather than shown to the viewer. */
    private suspend fun waitForServerToConfirm(friendshipId: String): AdRestoreResult {
        repeat(maxAttempts) { attempt ->
            val result = restore(friendshipId)
            result.getOrNull()?.let { return AdRestoreResult.Restored(it) }
            val error = result.exceptionOrNull()
            if (error !is AdRewardRequiredException) return AdRestoreResult.Failed(error?.message)
            if (attempt < maxAttempts - 1) delay(retryDelayMillis)
        }
        return AdRestoreResult.NotConfirmed
    }
}
