package com.gabbah.call2chat

import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.gabbah.call2chat.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var billing: Billing

    /** Messaggio che si sta modificando */
    private var editing = 0
    private var loading = false

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "history" || key == "sent" || key == "stats") runOnUiThread { refreshHistory(); refreshPlan(); refreshStats() }
    }
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

        editing = prefs.defaultIndex
        bindSettings()
        checkForUpdate()
        Updater.check(this)

        // Al primo avvio parte la configurazione guidata
        if (!prefs.setupSeen || Health.essentialMissing(this).isNotEmpty()) openSetup()
        if (intent.getBooleanExtra(EXTRA_PAYWALL, false)) showPaywall()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_PAYWALL, false)) showPaywall()
    }

    override fun onStart() {
        super.onStart()
        prefs.registerListener(prefsListener)
    }

    override fun onStop() {
        prefs.unregisterListener(prefsListener)
        super.onStop()
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

    private fun openSetup() = startActivity(Intent(this, SetupActivity::class.java))

    // ---------- Impostazioni ----------

    private fun bindSettings() {
        b.enabled.isChecked = prefs.enabled
        b.enabled.setOnCheckedChangeListener { _, on -> prefs.enabled = on; refresh() }
        b.healthCard.setOnClickListener { openSetup() }

        // Messaggi pronti
        b.message.doAfterTextChanged { t ->
            if (loading) return@doAfterTextChanged
            updateTemplate(text = t?.toString().orEmpty())
            refreshPreview()
        }
        b.tplName.doAfterTextChanged { t ->
            if (loading) return@doAfterTextChanged
            val name = t?.toString().orEmpty().ifBlank { "…" }
            updateTemplate(name = name)
            (b.tplChips.getChildAt(editing) as? Chip)?.text = chipLabel(editing, name)
            refreshOff()
        }
        b.btnTplAdd.setOnClickListener {
            val list = prefs.templates() + Template(getString(R.string.tpl_new_name), "")
            prefs.saveTemplates(list)
            editing = list.size - 1
            buildTemplateChips()
            b.message.requestFocus()
        }
        b.btnTplDefault.setOnClickListener {
            prefs.defaultIndex = editing
            buildTemplateChips()
            Toast.makeText(this, R.string.tpl_default_set, Toast.LENGTH_SHORT).show()
        }
        b.btnTplDelete.setOnClickListener { deleteTemplate() }
        listOf("{ora}", "{ora+1}", "{giorno}", "{domani}", "{data}").forEach { v ->
            b.varChips.addView(Chip(this).apply {
                text = v
                setOnClickListener {
                    val e = b.message
                    val pos = e.selectionStart.coerceAtLeast(0)
                    e.text?.insert(pos, v)
                }
            })
        }
        buildTemplateChips()

        b.targetGroup.check(if (prefs.targetPackage == Prefs.WA_BUSINESS) R.id.targetWab else R.id.targetWa)
        b.targetGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) prefs.targetPackage = if (id == R.id.targetWab) Prefs.WA_BUSINESS else Prefs.WA
        }

        // Fuori orario
        b.offOn.isChecked = prefs.offEnabled
        b.offOn.setOnCheckedChangeListener { _, on -> prefs.offEnabled = on; refreshOff() }
        b.workStart.setOnClickListener { pickTime(prefs.workStart) { prefs.workStart = it; refreshOff() } }
        b.workEnd.setOnClickListener { pickTime(prefs.workEnd) { prefs.workEnd = it; refreshOff() } }
        b.weekendOff.isChecked = prefs.weekendOff
        b.weekendOff.setOnCheckedChangeListener { _, on -> prefs.weekendOff = on; refreshOff() }
        b.offTemplate.setOnClickListener {
            val names = prefs.templates().map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.off_pick)
                .setSingleChoiceItems(names, prefs.offIndex.coerceIn(0, names.size - 1)) { d, i ->
                    prefs.offIndex = i; refreshOff(); d.dismiss()
                }.show()
        }

        // Regole
        b.cooldown.setText(prefs.cooldownHours.toString())
        b.cooldown.doAfterTextChanged { t -> t?.toString()?.toIntOrNull()?.let { prefs.cooldownHours = it } }
        b.hoursOn.isChecked = prefs.hoursEnabled
        b.hoursOn.setOnCheckedChangeListener { _, on -> prefs.hoursEnabled = on; refreshHours() }
        b.hoursStart.setOnClickListener { pickTime(prefs.hoursStart) { prefs.hoursStart = it; refreshHours() } }
        b.hoursEnd.setOnClickListener { pickTime(prefs.hoursEnd) { prefs.hoursEnd = it; refreshHours() } }
        b.excluded.setText(prefs.excluded)
        b.excluded.doAfterTextChanged { t -> prefs.excluded = t?.toString().orEmpty() }

        b.btnTest.setOnClickListener { testMessage() }
        b.btnOverlayTest.setOnClickListener { testOverlay() }
        b.btnPro.setOnClickListener { if (!prefs.isPro) buyPro() }
        b.footer.text = getString(R.string.footer) + "\n" + getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME
    }

    private fun refresh() {
        refreshHealth()
        refreshHours()
        refreshOff()
        refreshPlan()
        refreshStats()
        refreshHistory()
    }

    // ---------- Stato ----------

    private fun refreshHealth() {
        val missing = Health.missing(this)
        val essential = Health.essentialMissing(this)
        val waOk = Core.isInstalled(this, Prefs.WA) || Core.isInstalled(this, Prefs.WA_BUSINESS)
        val (icon, title, text, color) = when {
            !waOk -> Quad("⚠️", getString(R.string.status_no_wa), getString(R.string.health_no_wa), 0xFFC62828.toInt())
            essential.isNotEmpty() -> Quad("⛔", getString(R.string.health_bad_title),
                getString(R.string.health_missing, missing.size), 0xFFC62828.toInt())
            missing.isNotEmpty() -> Quad("⚠️", getString(R.string.health_warn_title),
                getString(R.string.health_missing, missing.size), 0xFFE08A00.toInt())
            else -> Quad("✅", getString(R.string.health_ok_title), getString(R.string.health_ok_text), 0xFF0F8B6F.toInt())
        }
        b.healthIcon.text = icon
        b.healthTitle.text = title
        b.healthText.text = text
        b.healthCard.strokeColor = color
        b.healthCard.strokeWidth = dp(2)

        b.status.text = getString(
            when {
                essential.isNotEmpty() -> R.string.status_setup
                !prefs.enabled -> R.string.status_paused
                else -> R.string.status_active
            }
        )
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: Int)

    // ---------- Messaggi pronti ----------

    private fun chipLabel(i: Int, name: String) = if (i == prefs.defaultIndex) "⭐ $name" else name

    private fun buildTemplateChips() {
        val list = prefs.templates()
        editing = editing.coerceIn(0, list.size - 1)
        b.tplChips.setOnCheckedStateChangeListener(null)
        b.tplChips.removeAllViews()
        list.forEachIndexed { i, t ->
            b.tplChips.addView(Chip(this).apply {
                id = View.generateViewId()
                text = chipLabel(i, t.name)
                isCheckable = true
                isChecked = i == editing
            })
        }
        b.tplChips.setOnCheckedStateChangeListener { g, ids ->
            val idx = (0 until g.childCount).firstOrNull { g.getChildAt(it).id == ids.firstOrNull() } ?: return@setOnCheckedStateChangeListener
            editing = idx
            loadEditing()
        }
        loadEditing()
        refreshOff()
    }

    private fun loadEditing() {
        val t = prefs.templates()[editing]
        loading = true
        b.tplName.setText(t.name)
        b.message.setText(t.text)
        loading = false
        b.btnTplDelete.isEnabled = prefs.templates().size > 1
        b.btnTplDefault.visibility = if (editing != prefs.defaultIndex) View.VISIBLE else View.GONE
        refreshPreview()
    }

    private fun updateTemplate(name: String? = null, text: String? = null) {
        val list = prefs.templates().toMutableList()
        if (editing !in list.indices) return
        val t = list[editing]
        list[editing] = t.copy(name = name ?: t.name, text = text ?: t.text)
        prefs.saveTemplates(list)
    }

    private fun refreshPreview() {
        val txt = b.message.text?.toString().orEmpty()
        b.renderPreview.text = if ('{' in txt) getString(R.string.vars_preview, Vars.render(txt)) else ""
    }

    private fun deleteTemplate() {
        val list = prefs.templates()
        if (list.size <= 1) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.tpl_delete_q, list[editing].name))
            .setPositiveButton(R.string.tpl_delete) { _, _ ->
                val removed = editing
                val newList = list.filterIndexed { i, _ -> i != removed }
                fun shift(i: Int) = when { i == removed -> 0; i > removed -> i - 1; else -> i }
                val def = shift(prefs.defaultIndex)
                val off = shift(prefs.offIndex)
                prefs.saveTemplates(newList)
                prefs.defaultIndex = def
                prefs.offIndex = off
                editing = def
                buildTemplateChips()
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun refreshOff() {
        val on = prefs.offEnabled
        b.offBox.visibility = if (on) View.VISIBLE else View.GONE
        b.workStart.text = getString(R.string.work_from, fmt(prefs.workStart))
        b.workEnd.text = getString(R.string.work_to, fmt(prefs.workEnd))
        val list = prefs.templates()
        b.offTemplate.text = getString(R.string.off_use, list[prefs.offIndex.coerceIn(0, list.size - 1)].name)
        b.offNow.text = getString(R.string.off_now, list[prefs.autoIndex()].name)
    }

    // ---------- Statistiche ----------

    private fun refreshStats() {
        val s = prefs.stats()
        b.statCalls.text = s.calls7.toString()
        b.statSent.text = s.sent7.toString()
        b.statAll.text = getString(R.string.stats_all, s.callsAll, s.sentAll)
        b.statBars.removeAllViews()
        val max = (s.days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        s.days.forEach { (label, n) ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = if (n > 0) n.toString() else ""
                textSize = 11f
                gravity = Gravity.CENTER
            })
            col.addView(View(this).apply {
                background = GradientDrawable().apply {
                    setColor(if (n > 0) 0xFF0F8B6F.toInt() else 0x330F8B6F)
                    cornerRadius = dp(4).toFloat()
                }
            }, LinearLayout.LayoutParams(dp(18), (dp(56) * n / max).coerceAtLeast(dp(4))))
            col.addView(TextView(this).apply {
                text = label
                textSize = 11f
                alpha = 0.7f
                gravity = Gravity.CENTER
            })
            b.statBars.addView(col)
        }
    }

    // ---------- Regole, piano, storico ----------

    private fun refreshHours() {
        b.hoursRow.visibility = if (prefs.hoursEnabled) View.VISIBLE else View.GONE
        b.hoursStart.text = getString(R.string.hours_from, fmt(prefs.hoursStart))
        b.hoursEnd.text = getString(R.string.hours_to, fmt(prefs.hoursEnd))
    }

    private fun refreshPlan() {
        if (prefs.isPro) {
            b.planText.text = getString(R.string.plan_pro)
            b.planBar.visibility = View.GONE
            b.btnPro.text = getString(R.string.btn_pro_done)
            b.btnPro.isEnabled = false
        } else {
            b.planText.text = getString(R.string.plan_free, prefs.sentCount, Prefs.FREE_LIMIT)
            b.planBar.visibility = View.VISIBLE
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
                setOnClickListener { pickAndSend(e.number) }
            }
            row.addView(label)
            row.addView(btn)
            list.addView(row)
        }
    }

    /** Dallo storico: scegli quale messaggio mandare */
    private fun pickAndSend(number: String) {
        val list = prefs.templates()
        fun send(i: Int) = startActivity(Intent(this, SendActivity::class.java)
            .putExtra(SendActivity.EXTRA_NUMBER, number).putExtra(SendActivity.EXTRA_TEMPLATE, i))
        if (list.size == 1) { send(0); return }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pick_message)
            .setItems(list.map { it.name }.toTypedArray()) { _, i -> send(i) }
            .show()
    }

    // ---------- Azioni ----------

    /** Mostra la scheda tra 5 secondi, così si può uscire dall'app e vederla sopra le altre. */
    private fun testOverlay() {
        if (!Overlay.canShow(this)) { openSetup(); return }
        Toast.makeText(this, R.string.overlay_test_toast, Toast.LENGTH_LONG).show()
        val ctx = applicationContext
        android.os.Handler(mainLooper).postDelayed({ Overlay.show(ctx, "393331234567") }, 5000)
    }

    /** Mostra il messaggio in WhatsApp senza destinatario, per vedere come appare. Non conta. */
    private fun testMessage() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prefs.messageFor(editing))
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

    // ---------- Aggiornamento automatico all'apertura (Google Play) ----------

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
