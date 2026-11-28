package com.gabbah.call2chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** SIM del telefono: per sapere su quale numero è arrivata la chiamata. */
object Sims {
    data class Sim(val subId: Int, val slot: Int, val label: String)

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Quante SIM può avere il telefono (non serve permesso) */
    @Suppress("DEPRECATION")
    fun slots(ctx: Context): Int {
        val tm = ctx.getSystemService(TelephonyManager::class.java) ?: return 1
        return if (Build.VERSION.SDK_INT >= 30) tm.activeModemCount else tm.phoneCount
    }

    fun isDualSim(ctx: Context) = slots(ctx) > 1

    @SuppressLint("MissingPermission")
    fun list(ctx: Context): List<Sim> {
        if (!hasPermission(ctx)) return emptyList()
        val sm = ctx.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return try {
            sm.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }.map {
                val name = (it.carrierName?.toString()?.takeIf { n -> n.isNotBlank() }
                    ?: it.displayName?.toString()).orEmpty()
                Sim(it.subscriptionId, it.simSlotIndex,
                    ctx.getString(R.string.sim_label, it.simSlotIndex + 1) + if (name.isNotBlank()) " · $name" else "")
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Da quale SIM arriva la chiamata (null se non si sa) */
    @SuppressLint("MissingPermission")
    fun subIdFor(ctx: Context, handle: PhoneAccountHandle?): Int? {
        if (handle == null || !hasPermission(ctx)) return null
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                val tm = ctx.getSystemService(TelephonyManager::class.java)
                val id = tm?.getSubscriptionId(handle) ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
                if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return id
            }
            // Android 10: l'id dell'account è di solito il subId o l'ICCID della SIM
            handle.id.toIntOrNull()?.let { id -> if (list(ctx).any { it.subId == id }) return id }
            val sm = ctx.getSystemService(SubscriptionManager::class.java)
            @Suppress("DEPRECATION")
            sm?.activeSubscriptionInfoList?.firstOrNull { it.iccId != null && handle.id.startsWith(it.iccId) }?.subscriptionId
        } catch (e: Exception) {
            null
        }
    }
}
