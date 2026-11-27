package com.gabbah.call2chat

import android.Manifest
import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.gabbah.call2chat.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var billing: Billing

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refresh()
            if (!hasRole()) showRoleHelp()
        }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    private val updateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top + dp(16), bottom = s.bottom + dp(16))
            insets
        }
        prefs = Prefs(this)
        billing = Billing(this) { runOnUiThread { refreshPlan() } }
        billing.start()

        bindSettings()
        checkForUpdate()
        if (intent.getBooleanExtra(EXTRA_PAYWALL, false)) showPaywall()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_PAYWALL, false)) showPaywall()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        billing.refreshPurchases()
        resumeUpdate()
    }

    override fun onDestroy() {
        billing.end()
        super.onDestroy()
    }

    // ---------- Impostazioni ----------

    private fun bindSettings() {
        b.enabled.isChecked = prefs.enabled
        b.enabled.setOnCheckedChangeListener { _, on -> prefs.enabled = on; refresh() }

        b.message.setText(prefs.message)
        b.message.doAfterTextChanged { t ->
            val s = t?.toString().orEmpty()
            if (s.isNotBlank()) prefs.message = s
        }

        b.targetGroup.check(if (prefs.targetPackage == Prefs.WA_BUSINESS) R.id.targetWab else R.id.targetWa)
        b.targetGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) prefs.targetPackage = if (id == R.id.targetWab) Prefs.WA_BUSINESS else Prefs.WA
        }

        b.cooldown.setText(prefs.cooldownHours.toString())
        b.cooldown.doAfterTextChanged { t -> t?.toString()?.toIntOrNull()?.let { prefs.cooldownHours = it } }

        b.hoursOn.isChecked = prefs.hoursEnabled
        b.hoursOn.setOnCheckedChangeListener { _, on -> prefs.hoursEnabled = on; refreshHours() }
        b.hoursStart.setOnClickListener { pickTime(prefs.hoursStart) { prefs.hoursStart = it; refreshHours() } }
        b.hoursEnd.setOnClickListener { pickTime(prefs.hoursEnd) { prefs.hoursEnd = it; refreshHours() } }

        b.excluded.setText(prefs.excluded)
        b.excluded.doAfterTextChanged { t -> prefs.excluded = t?.toString().orEmpty() }

        b.btnRole.setOnClickListener { requestRole() }
        b.btnNotif.setOnClickListener { requestNotifications() }
        b.btnTest.setOnClickListener { testMessage() }
        b.btnPro.setOnClickListener { if (!prefs.isPro) buyPro() }
    }

    private fun refresh() {
        val roleOk = hasRole()
        val notifOk = hasNotifications()
        b.btnRole.isEnabled = !roleOk
        b.btnRole.text = getString(if (roleOk) R.string.btn_role_done else R.string.btn_role)
        b.btnNotif.isEnabled = !notifOk
        b.btnNotif.text = getString(if (notifOk) R.string.btn_notif_done else R.string.btn_notif)
        b.setupCard.visibility = if (roleOk && notifOk) android.view.View.GONE else android.view.View.VISIBLE

        b.status.text = getString(
            when {
                !roleOk || !notifOk -> R.string.status_setup
                !prefs.enabled -> R.string.status_paused
                else -> R.string.status_active
            }
        )
        val waOk = Core.isInstalled(this, Prefs.WA) || Core.isInstalled(this, Prefs.WA_BUSINESS)
        if (!waOk) b.status.text = getString(R.string.status_no_wa)

        refreshHours()
        refreshPlan()
        refreshHistory()
    }

    private fun refreshHours() {
        b.hoursRow.visibility = if (prefs.hoursEnabled) android.view.View.VISIBLE else android.view.View.GONE
        b.hoursStart.text = getString(R.string.hours_from, fmt(prefs.hoursStart))
        b.hoursEnd.text = getString(R.string.hours_to, fmt(prefs.hoursEnd))
    }

    private fun refreshPlan() {
        if (prefs.isPro) {
            b.planText.text = getString(R.string.plan_pro)
            b.planBar.visibility = android.view.View.GONE
            b.btnPro.text = getString(R.string.btn_pro_done)
            b.btnPro.isEnabled = false
        } else {
            b.planText.text = getString(R.string.plan_free, prefs.sentCount, Prefs.FREE_LIMIT)
            b.planBar.visibility = android.view.View.VISIBLE
            b.planBar.setProgressCompat(prefs.sentCount * 100 / Prefs.FREE_LIMIT, false)
            b.btnPro.isEnabled = true
            b.btnPro.text = billing.price?.let { getString(R.string.btn_pro_price, it) }
                ?: getString(R.string.btn_pro)
        }
    }

    private fun refreshHistory() {
        val list = b.historyList
        list.removeAllViews()
        val items = prefs.history()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply { text = getString(R.string.history_empty); alpha = 0.6f })
            return
        }
        items.take(15).forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val label = TextView(this).apply {
                text = Numbers.pretty(e.number) + "\n" +
                    DateUtils.getRelativeTimeSpanString(e.time) +
                    (if (e.sent) " · " + getString(R.string.history_sent) else "")
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.history_send)
                setOnClickListener { startActivity(Intent(this@MainActivity, SendActivity::class.java)
                    .putExtra(SendActivity.EXTRA_NUMBER, e.number)) }
            }
            row.addView(label)
            row.addView(btn)
            list.addView(row)
        }
    }

    // ---------- Permessi ----------

    private fun hasRole(): Boolean {
        val rm = getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, R.string.role_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        try {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        } catch (e: Exception) {
            showRoleHelp()
        }
    }

    /** Il telefono non ha mostrato la richiesta (o è stata rifiutata): si fa dalle impostazioni. */
    private fun showRoleHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.role_help_title)
            .setMessage(R.string.role_help_text)
            .setPositiveButton(R.string.role_help_open) { _, _ -> openDefaultApps() }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun openDefaultApps() {
        val tries = listOf(
            Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (i in tries) {
            try { startActivity(i); return } catch (e: Exception) { }
        }
    }

    private fun hasNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ---------- Azioni ----------

    /** Mostra il messaggio in WhatsApp senza destinatario, per vedere come appare. Non conta. */
    private fun testMessage() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prefs.message)
        val pkg = listOf(prefs.targetPackage, Prefs.WA, Prefs.WA_BUSINESS).firstOrNull { Core.isInstalled(this, it) }
        if (pkg != null) i.setPackage(pkg)
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_whatsapp, Toast.LENGTH_LONG).show()
        }
    }

    private fun showPaywall() {
        if (prefs.isPro) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.paywall_title)
            .setMessage(getString(R.string.paywall_text, Prefs.FREE_LIMIT))
            .setPositiveButton(R.string.btn_pro) { _, _ -> buyPro() }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun buyPro() {
        if (!billing.buy(this)) Toast.makeText(this, R.string.billing_unavailable, Toast.LENGTH_LONG).show()
    }

    // ---------- Aggiornamento automatico all'apertura ----------

    private fun checkForUpdate() {
        val mgr = AppUpdateManagerFactory.create(this)
        mgr.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                mgr.startUpdateFlowForResult(info, updateLauncher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
            }
        }
    }

    private fun resumeUpdate() {
        val mgr = AppUpdateManagerFactory.create(this)
        mgr.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                mgr.startUpdateFlowForResult(info, updateLauncher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
            }
        }
    }

    // ---------- Utilità ----------

    private fun pickTime(current: Int, done: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> done(h * 60 + m) }, current / 60, current % 60,
            android.text.format.DateFormat.is24HourFormat(this)).show()
    }

    private fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAYWALL = "paywall"
    }
}
