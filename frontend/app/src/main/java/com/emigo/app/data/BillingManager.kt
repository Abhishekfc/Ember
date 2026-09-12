package com.emigo.app.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** Which of the two Emigo Gold products a plan is — the id substring the backend keys off is the
 * source of truth ("year" → yearly), this enum is just for ordering and labelling the picker. */
enum class GoldPeriod { MONTHLY, YEARLY }

/** A Gold plan as the paywall should show it — no Play Billing SDK types leak past here. */
data class GoldPlan(
    val productId: String,
    val period: GoldPeriod,
    /** Already localized and currency-formatted by Play, e.g. "₹399.00". */
    val formattedPrice: String,
    val priceAmountMicros: Long,
)

/** An entitlement Play already knows this account holds — surfaced on screen open so a reinstall
 * or a new device re-grants Gold without the user buying again. */
data class ExistingGoldPurchase(val productId: String, val purchaseToken: String)

sealed interface PurchaseEvent {
    data class Purchased(val productId: String, val purchaseToken: String) : PurchaseEvent
    /** Payment method that settles later (some carrier billing, cash). Don't grant Gold yet. */
    data object Pending : PurchaseEvent
    data object Cancelled : PurchaseEvent
    /** Play says this account already owns the product — caller should reconcile via
     * [BillingManager.findActivePurchase]. */
    data object AlreadyOwned : PurchaseEvent
    data class Failed(val message: String) : PurchaseEvent
}

sealed interface BillingLaunchResult {
    data object Launched : BillingLaunchResult
    data object Unavailable : BillingLaunchResult
    data class Failed(val message: String) : BillingLaunchResult
}

/**
 * Process-wide wrapper around the Play Billing client (one connection for the whole app, held in
 * [com.emigo.app.EmberApplication]). Owns nothing about entitlement — that's the backend's job via
 * [SubscriptionRepository.verifyPurchase]; this only talks to Play: load prices, open the buy
 * sheet, report what came back.
 *
 * Acknowledgement is deliberately NOT done here — the backend acknowledges server-side right after
 * it grants the entitlement, so a client crash between "bought" and "acknowledged" can't cause
 * Play's 3-day auto-refund of a purchase the backend already honoured.
 */
class BillingManager(context: Context) {

    // Must match the product IDs created in Play Console → Monetise with Play → Subscriptions.
    // "gold_yearly" contains "year", which is what the backend's derivePlan() keys off.
    private val productIds = listOf("gold_monthly", "gold_yearly")

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents.asSharedFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val relevant = purchases.orEmpty()
                // An OK result should always carry at least one purchase — but if it somehow
                // doesn't (or every entry is in a state we don't recognize), the screen would
                // otherwise be left showing "Opening Google Play…"/"Confirming…" forever with no
                // way out short of leaving and reopening it. Falling through to Failed guarantees
                // every OK response resolves the in-flight state one way or another.
                var handled = false
                relevant.forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED ->
                            purchase.products.firstOrNull()?.let {
                                _purchaseEvents.tryEmit(PurchaseEvent.Purchased(it, purchase.purchaseToken))
                                handled = true
                            }
                        Purchase.PurchaseState.PENDING -> {
                            _purchaseEvents.tryEmit(PurchaseEvent.Pending)
                            handled = true
                        }
                        else -> Unit
                    }
                }
                if (!handled) {
                    _purchaseEvents.tryEmit(PurchaseEvent.Failed("Google Play didn't return a usable purchase"))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _purchaseEvents.tryEmit(PurchaseEvent.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                _purchaseEvents.tryEmit(PurchaseEvent.AlreadyOwned)
            else ->
                _purchaseEvents.tryEmit(PurchaseEvent.Failed(result.debugMessage.ifBlank { "Purchase failed" }))
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Populated by [loadPlans]; needed to actually launch a purchase for a given product id. */
    private var productDetailsById: Map<String, ProductDetails> = emptyMap()

    // The paywall reconnects/reloads on every visit (EmberGoldViewModel.onShown), so a quick
    // leave-and-reopen can call ensureConnected() a second time before the first startConnection()
    // has finished. BillingClient isn't documented as safe to call startConnection() again while a
    // connection attempt is in flight — the second listener can simply never fire, leaving that
    // caller's coroutine (and whatever screen state it was about to set) hung forever with no error
    // and no spinner resolution. Serializing connection attempts through this closes that gap: the
    // second caller waits, then finds isReady already true and returns immediately.
    private val connectMutex = Mutex()

    private suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) return true
        return connectMutex.withLock {
            if (billingClient.isReady) return@withLock true
            suspendCancellableCoroutine { cont ->
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) {
                            cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
        }
    }

    /** Loads both Gold products from Play with real localized prices. Empty means billing is
     * unavailable on this device/build or the products aren't live in the Console yet. */
    suspend fun loadPlans(): List<GoldPlan> {
        if (!ensureConnected()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()

        // Billing Library 8.0.0 changed this callback's second parameter from a bare
        // List<ProductDetails> to a QueryProductDetailsResult wrapper (which also carries
        // unfetchedProductList — not needed here, since a product missing from the response is
        // already handled by it simply not appearing in the plans list below).
        val details: List<ProductDetails> = suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { result, queryProductDetailsResult ->
                if (cont.isActive) {
                    cont.resume(
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            queryProductDetailsResult.productDetailsList
                        } else {
                            emptyList()
                        },
                    )
                }
            }
        }

        productDetailsById = details.associateBy { it.productId }
        return details.mapNotNull { it.toGoldPlan() }.sortedBy { it.period.ordinal }
    }

    /** A purchased-and-not-yet-known-to-us subscription Play is holding for this account. */
    suspend fun findActivePurchase(): ExistingGoldPurchase? {
        if (!ensureConnected()) return null
        val purchases: List<Purchase> = suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            ) { result, list ->
                if (cont.isActive) {
                    cont.resume(
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) list
                        else emptyList(),
                    )
                }
            }
        }
        return purchases
            .firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            ?.let { p -> p.products.firstOrNull()?.let { ExistingGoldPurchase(it, p.purchaseToken) } }
    }

    /** Opens Play's purchase sheet. The outcome arrives asynchronously on [purchaseEvents]. */
    fun launchPurchase(activity: Activity, productId: String): BillingLaunchResult {
        val details = productDetailsById[productId] ?: return BillingLaunchResult.Unavailable
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return BillingLaunchResult.Unavailable

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingLaunchResult.Launched
        } else {
            BillingLaunchResult.Failed(result.debugMessage.ifBlank { "Couldn't open Google Play" })
        }
    }

    private fun ProductDetails.toGoldPlan(): GoldPlan? {
        val offer = subscriptionOfferDetails?.firstOrNull() ?: return null
        // Last phase is the ongoing recurring price (earlier phases, if any, are intro/trial).
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return null
        val period = if (phase.billingPeriod.contains("Y")) GoldPeriod.YEARLY else GoldPeriod.MONTHLY
        return GoldPlan(
            productId = productId,
            period = period,
            formattedPrice = phase.formattedPrice,
            priceAmountMicros = phase.priceAmountMicros,
        )
    }
}
