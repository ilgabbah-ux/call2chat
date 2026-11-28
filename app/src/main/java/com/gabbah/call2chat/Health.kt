package com.gabbah.call2chat

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Tutti i passaggi necessari perché Call2Chat funzioni anche ad app chiusa,
 * con il loro stato e la pagina delle impostazioni da aprire.
 */
object Health {

    enum class Step { ROLE, SIMS, CHANNEL, NOTIFICATIONS, OVERLAY, KEEP_ACTIVE, BATTERY, MAKER }

    /** Produttori che chiudono le app in background con impostazioni proprie */
    enum class Maker { XIAOMI, SAMSUNG, OPPO, VIVO, HUAWEI, OTHER }

    fun maker(): Maker {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> Maker.XIAOMI
            "samsung" in m -> Maker.SAMSUNG
            listOf("oppo", "realme", "oneplus").any { it in m } -> Maker.OPPO
            listOf("vivo", "iqoo").any { it in m } -> Maker.VIVO
            listOf("huawei", "honor").any { it in m } -> Maker.HUAWEI
            else -> Maker.OTHER
        }
    }

    /** I passaggi che servono su questo telefono, in ordine */
    /** Ci sono sia WhatsApp sia WhatsApp Business? */
    fun bothWhatsApp(ctx: Context) =
        Core.isInstalled(ctx, "com.whatsapp") && Core.isInstalled(ctx, "com.whatsapp.w4b")

    fun steps(ctx: Context): List<Step> = buildList {
        add(Step.ROLE)
        if (Sims.isDualSim(ctx)) add(Step.SIMS) else if (bothWhatsApp(ctx)) add(Step.CHANNEL)
        if (Build.VERSION.SDK_INT >= 33) add(Step.NOTIFICATIONS)
        add(Step.OVERLAY)
        if (Build.VERSION.SDK_INT >= 30) add(Step.KEEP_ACTIVE)
        add(Step.BATTERY)
        if (maker() != Maker.OTHER) add(Step.MAKER)
    }

    fun isOk(ctx: Context, s: Step): Boolean = when (s) {
        Step.ROLE -> hasRole(ctx)
        Step.SIMS -> Sims.hasPermission(ctx) && Prefs(ctx).isConfirmed(s.name)
        Step.CHANNEL -> Prefs(ctx).isConfirmed(s.name) || Prefs(ctx).channel != Channel.AUTO
        Step.NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        Step.OVERLAY -> Settings.canDrawOverlays(ctx)
        Step.KEEP_ACTIVE -> Build.VERSION.SDK_INT < 30 || ctx.packageManager.isAutoRevokeWhitelisted
        Step.BATTERY -> ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true ||
            Prefs(ctx).isConfirmed(s.name)
        // Non si può leggere: vale la conferma della persona
        Step.MAKER -> Prefs(ctx).isConfirmed(s.name)
    }

    /** Passaggi che si possono solo confermare a mano */
    fun needsManualConfirm(s: Step) = s == Step.MAKER || s == Step.BATTERY || s == Step.SIMS || s == Step.CHANNEL

    fun missing(ctx: Context) = steps(ctx).filterNot { isOk(ctx, it) }

    /** Senza questi l'app non funziona proprio */
    fun essentialMissing(ctx: Context) =
        missing(ctx).filter { it == Step.ROLE || it == Step.NOTIFICATIONS || it == Step.OVERLAY }

    fun hasRole(ctx: Context): Boolean {
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))

    /** Pagine da provare, in ordine, per il passaggio del produttore */
    fun makerIntents(ctx: Context): List<Intent> {
        val pkg = ctx.packageName
        fun comp(p: String, c: String) = Intent().setComponent(ComponentName(p, c))
        val list = when (maker()) {
            Maker.XIAOMI -> listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", pkg),
                comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            Maker.OPPO -> listOf(
                comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                comp("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity")
            )
            Maker.VIVO -> listOf(
                comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            )
            Maker.HUAWEI -> listOf(
                comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            Maker.SAMSUNG, Maker.OTHER -> emptyList()
        }
        return list + appDetails(ctx)
    }
}
