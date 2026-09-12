package com.emigo.app.ui.settings

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emigo.app.data.BillingLaunchResult
import com.emigo.app.data.BillingManager
import com.emigo.app.data.GoldPeriod
import com.emigo.app.data.GoldPlan
import com.emigo.app.data.PurchaseEvent
import com.emigo.app.data.SubscriptionRepository
import kotlinx.coroutines.launch

data class GoldUiState(
    val loadingPlans: Boolean = true,
    val plans: List<GoldPlan> = emptyList(),
    val selectedProductId: String? = null,
    /** Yearly price as a share saved vs. 12× the monthly price, e.g. 0.30 → "Save 30%". Null when
     * it can't be computed (only one plan loaded). */
    val yearlySavingsFraction: Double? = null,
    val isGold: Boolean = false,
    /** Plans couldn't be loaded at all — billing unavailable on this build/device, or the
     * products aren't live in Play Console yet. */
    val billingUnavailable: Boolean = false,
    val purchaseInFlight: Boolean = false,
    val verifying: Boolean = false,
    /** Purchase went through a payment method that settles later; Gold unlocks once it clears. */
    val pendingPayment: Boolean = false,
    /** Flipped true for one read after a purchase is confirmed, so the screen can tell the rest
     * of the app to refresh. Cleared via [EmberGoldViewModel.consumeJustActivated]. */
    val justActivated: Boolean = false,
    val error: String? = null,
) {
    val selectedPlan: GoldPlan? get() = plans.firstOrNull { it.productId == selectedProductId }
    val canBuy: Boolean
        get() = !isGold && !billingUnavailable && !loadingPlans && !purchaseInFlight &&
            !pendingPayment && selectedPlan != null
}

/**
 * Drives [EmberGoldScreen]. Talks to Play through [BillingManager] and to our backend through
 * [SubscriptionRepository] — the backend is the only thing that decides Gold; a raw Play purchase
 * is never trusted on its own.
 */
class EmberGoldViewModel(
    private val billingManager: BillingManager,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    var uiState by mutableStateOf(GoldUiState())
        private set

    private var collectingEvents = false

    /** Called every time the paywall becomes visible. Wires the purchase-event collector once,
     * then refreshes prices + current status. */
    fun onShown() {
        if (!collectingEvents) {
            collectingEvents = true
            viewModelScope.launch {
                billingManager.purchaseEvents.collect { handleEvent(it) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loadingPlans = true, error = null)
            val plans = billingManager.loadPlans()
            val isGold = subscriptionRepository.isGoldMemberOrLastKnown()

            val monthly = plans.firstOrNull { it.period == GoldPeriod.MONTHLY }
            val yearly = plans.firstOrNull { it.period == GoldPeriod.YEARLY }
            val savings = if (monthly != null && yearly != null && monthly.priceAmountMicros > 0) {
                val yearOfMonthly = monthly.priceAmountMicros * 12.0
                (1.0 - yearly.priceAmountMicros / yearOfMonthly).takeIf { it > 0.0 }
            } else null

            uiState = uiState.copy(
                loadingPlans = false,
                plans = plans,
                billingUnavailable = plans.isEmpty(),
                isGold = isGold,
                yearlySavingsFraction = savings,
                selectedProductId = uiState.selectedProductId
                    ?: yearly?.productId
                    ?: plans.firstOrNull()?.productId,
            )

            // A subscription Play holds that our backend hasn't been told about yet (reinstall,
            // new device). Verify it quietly so Gold comes back on its own.
            if (!isGold) {
                billingManager.findActivePurchase()?.let { verify(it.productId, it.purchaseToken, silent = true) }
            }
        }
    }

    fun selectPlan(productId: String) {
        uiState = uiState.copy(selectedProductId = productId)
    }

    fun purchase(activity: Activity) {
        val productId = uiState.selectedProductId ?: return
        uiState = uiState.copy(purchaseInFlight = true, error = null, pendingPayment = false)
        when (val result = billingManager.launchPurchase(activity, productId)) {
            BillingLaunchResult.Launched -> Unit // outcome arrives on purchaseEvents
            BillingLaunchResult.Unavailable ->
                uiState = uiState.copy(purchaseInFlight = false, error = "This plan isn't available right now")
            is BillingLaunchResult.Failed ->
                uiState = uiState.copy(purchaseInFlight = false, error = result.message)
        }
    }

    fun consumeJustActivated() {
        uiState = uiState.copy(justActivated = false)
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private fun handleEvent(event: PurchaseEvent) {
        when (event) {
            is PurchaseEvent.Purchased -> verify(event.productId, event.purchaseToken, silent = false)
            PurchaseEvent.Pending ->
                uiState = uiState.copy(purchaseInFlight = false, pendingPayment = true)
            PurchaseEvent.Cancelled ->
                uiState = uiState.copy(purchaseInFlight = false)
            PurchaseEvent.AlreadyOwned -> viewModelScope.launch {
                val existing = billingManager.findActivePurchase()
                if (existing != null) {
                    verify(existing.productId, existing.purchaseToken, silent = false)
                } else {
                    uiState = uiState.copy(
                        purchaseInFlight = false,
                        error = "You already have a subscription. Try reopening the app.",
                    )
                }
            }
            is PurchaseEvent.Failed ->
                uiState = uiState.copy(purchaseInFlight = false, error = event.message)
        }
    }

    private fun verify(productId: String, purchaseToken: String, silent: Boolean) {
        viewModelScope.launch {
            if (!silent) uiState = uiState.copy(purchaseInFlight = true, verifying = true, error = null)
            subscriptionRepository.verifyPurchase(productId, purchaseToken).fold(
                onSuccess = { status ->
                    uiState = uiState.copy(
                        purchaseInFlight = false,
                        verifying = false,
                        isGold = status.isActive,
                        justActivated = uiState.justActivated || (status.isActive && !silent),
                        pendingPayment = if (status.isActive) false else uiState.pendingPayment,
                    )
                },
                onFailure = { e ->
                    if (!silent) {
                        uiState = uiState.copy(
                            purchaseInFlight = false,
                            verifying = false,
                            error = e.message ?: "Couldn't confirm your purchase",
                        )
                    }
                },
            )
        }
    }
}
