package com.gabbah.call2chat

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Sblocco Pro: acquisto unico su Google Play (prodotto "call2chat_pro", da creare nella Play Console).
 */
class Billing(ctx: Context, private val onChange: () -> Unit) {
    private val prefs = Prefs(ctx)
    private var product: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(ctx.applicationContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases?.forEach { handle(it) }
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Prezzo da mostrare sul pulsante, se già caricato */
    val price: String? get() = product?.oneTimePurchaseOfferDetails?.formattedPrice

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                refreshPurchases()
                loadProduct()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun end() = client.endConnection()

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product = details.productDetailsList.firstOrNull()
                onChange()
            }
        }
    }

    /** Controlla gli acquisti già fatti (anche dopo una reinstallazione o un rimborso). */
    fun refreshPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any {
                it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            purchases.forEach { handle(it) }
            if (prefs.isPro != owned) { prefs.isPro = owned; onChange() }
        }
    }

    /** False se il prodotto non è ancora disponibile (es. app non ancora su Play). */
    fun buy(activity: Activity): Boolean {
        val pd = product ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build())
        ).build()
        client.launchBillingFlow(activity, params)
        return true
    }

    private fun handle(p: Purchase) {
        if (!p.products.contains(PRODUCT_ID)) return
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        prefs.isPro = true
        onChange()
        if (!p.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            ) {}
        }
    }

    companion object {
        const val PRODUCT_ID = "call2chat_pro"
    }
}
