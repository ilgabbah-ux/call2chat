package com.gabbah.call2chat

import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var billing: Billing

    /** Messaggio che si sta modificando */
    private var editing = 0
    private var loading = false
    private val tabViews = mutableListOf<TextView>()

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "history" || key == "sent" || key == "stats" || key == "pro")
            runOnUiThread { refreshHistory(); refreshPlan(); refreshStats(); refreshHealth() }
    }
    private val updateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    private var pendingKind = Attachments.Kind.PHOTO
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (!Attachments.add(this, pendingKind, uri)) Toast.makeText(this, R.string.att_error, Toast.LENGTH_LONG).show()
            refreshAttachments()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top + dp(8), bottom = s.bottom + dp(16))
            insets
        }
        prefs = Prefs(this)
        billing = Billing(this) { runOnUiThread { refreshPlan() } }
        billing.start()

        editing = prefs.defaultIndex
        buildTabs()
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

    override fun onStart() { super.onStart(); prefs.registerListener(prefsListener) }
    override fun onStop() { prefs.unregisterListener(prefsListener); super.onStop() }

    override fun onResume() {
        super.onResume()
        refresh()
        billing.refreshPurchases()
        resumeUpdate()
    }

    override fun onDestroy() { billing.end(); super.onDestroy() }

    private fun openSetup() = startActivity(Intent(this, SetupActivity::class.java))

    // ---------- Tab ----------

    private fun buildTabs() {
        val labels = listOf(R.string.tab_messages, R.string.tab_hours, R.string.tab_stats, R.string.tab_rules)
        labels.forEachIndexed { i, l ->
            val t = TextView(this).apply {
                setText(l)
                gravity = Gravity.CENTER
                textSize = 13f
                isClickable = true
                isFocusable = true
                maxLines = 1
                setOnClickListener { selectTab(i) }
                minHeight = dp(44)
            }
            tabViews += t
            b.tabs.addView(t, LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(4) })
        }
        selectTab(prefs.lastTab.coerceIn(0, 3))
    }

    private fun selectTab(i: Int) {
        prefs.lastTab = i
        listOf(b.tabMessages, b.tabHours, b.tabStats, b.tabRules).forEachIndexed { j, v ->
            v.visibility = if (i == j) View.VISIBLE else View.GONE
        }
        tabViews.forEachIndexed { j, t ->
            val on = i == j
            t.setTextColor(color(if (on) R.color.text else R.color.text3))
            t.typeface = Typeface.create(font(), if (on) Typeface.BOLD else Typeface.NORMAL)
            t.background = if (on) round(color(R.color.surface2), 12) else null
        }
    }

    // ---------- Impostazioni ----------

    private fun bindSettings() {
        b.enabled.isChecked = prefs.enabled
        b.enabled.setOnCheckedChangeListener { _, on -> prefs.enabled = on; refreshHealth() }
        b.healthAction.setOnClickListener { openSetup() }
        b.btnSetup.setOnClickListener { openSetup() }

        // Messaggi pronti
        b.message.doAfterTextChanged { t ->
            if (loading) return@doAfterTextChanged
            updateTemplate(text = t?.toString().orEmpty())
            refreshPreview()
        }
        b.tplName.doAfterTextChanged { t ->
            if (loading) return@doAfterTextChanged
            updateTemplate(name = t?.toString().orEmpty().ifBlank { "…" })
            buildTemplatePills()
            refreshOff()
        }
        b.btnTplDefault.setOnClickListener {
            prefs.defaultIndex = editing
            buildTemplatePills(); loadEditing()
            Toast.makeText(this, R.string.tpl_default_set, Toast.LENGTH_SHORT).show()
        }
        b.btnTplDelete.setOnClickListener { deleteTemplate() }
        resources.getStringArray(R.array.var_tokens).forEach { v ->
            b.varChips.addView(Chip(this).apply {
                text = v
                typeface = Typeface.MONOSPACE
                chipBackgroundColor = ColorStateList.valueOf(color(R.color.surface2))
                chipStrokeWidth = 0f
                setTextColor(color(R.color.text2))
                setOnClickListener {
                    val e = b.message
                    e.text?.insert(e.selectionStart.coerceAtLeast(0), v)
                }
            })
        }
        b.bubble.background = GradientDrawable().apply {
            setColor(color(R.color.bubble))
            val r = dp(16).toFloat(); val s = dp(4).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, s, s, r, r)
        }
        b.bubbleTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) + " ✓✓"
        buildTemplatePills()
        loadEditing()

        // Allegati
        b.btnAddPhoto.setOnClickListener { pick(Attachments.Kind.PHOTO) }
        b.btnAddVideo.setOnClickListener { pick(Attachments.Kind.VIDEO) }
        b.btnAddPdf.setOnClickListener { pick(Attachments.Kind.PDF) }

        // Canale
        b.channelCard.setOnClickListener { pickChannel() }

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
        refreshChannel()
        refreshAttachments()
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
        val chOk = Channel.isInstalled(this, prefs.channel)
        val state = when {
            essential.isNotEmpty() || !chOk -> 2
            !prefs.enabled -> 3
            missing.isNotEmpty() -> 1
            else -> 0
        }
        b.healthCard.setBackgroundResource(when (state) { 2 -> R.drawable.bg_hero_bad; 1, 3 -> R.drawable.bg_hero_warn; else -> R.drawable.bg_hero })
        val dot = when (state) { 0 -> R.color.brand; 2 -> R.color.danger; else -> R.color.warn }
        b.healthDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color(dot)) }
        b.healthLabel.setTextColor(color(when (state) { 0 -> R.color.mint; 2 -> R.color.danger; else -> R.color.warn }))
        b.healthLabel.setText(when (state) { 0 -> R.string.state_active; 2 -> R.string.state_setup; 3 -> R.string.state_paused; else -> R.string.state_partial })
        b.healthTitle.text = when (state) {
            0 -> getString(R.string.health_ok_title)
            2 -> if (!chOk) getString(R.string.health_no_app_title) else getString(R.string.health_bad_title)
            3 -> getString(R.string.health_paused_title)
            else -> getString(R.string.health_warn_title)
        }
        b.healthText.text = when (state) {
            0 -> getString(R.string.health_ok_text)
            3 -> getString(R.string.health_paused_text)
            else -> if (!chOk) getString(R.string.health_no_app, getString(prefs.channel.label))
                else getString(R.string.health_missing, missing.size)
        }
        b.healthAction.visibility = if (missing.isNotEmpty() && state != 3) View.VISIBLE else View.GONE
    }

    // ---------- Messaggi pronti ----------

    private fun pill(text: String, selected: Boolean, star: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = if (star) "★  $text" else text
        textSize = 13f
        typeface = Typeface.create(font(), if (selected) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(color(if (selected) R.color.on_yellow else R.color.text2))
        gravity = Gravity.CENTER
        minHeight = dp(40)
        setPadding(dp(16), 0, dp(16), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            if (selected) setColor(color(R.color.yellow)) else { setColor(0); setStroke(dp(1), 0xFF2B3D35.toInt()) }
        }
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun buildTemplatePills() {
        val list = prefs.templates()
        editing = editing.coerceIn(0, list.size - 1)
        b.tplRow.removeAllViews()
        list.forEachIndexed { i, t ->
            b.tplRow.addView(pill(t.name, i == editing, i == prefs.defaultIndex) {
                editing = i; buildTemplatePills(); loadEditing()
            }, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(8) })
        }
        b.tplRow.addView(pill("+  " + getString(R.string.tpl_new_name), false, false) {
            prefs.saveTemplates(prefs.templates() + Template(getString(R.string.tpl_new_name), ""))
            editing = prefs.templates().size - 1
            buildTemplatePills(); loadEditing()
            b.message.requestFocus()
        }.apply { setTextColor(color(R.color.brand)) }, LinearLayout.LayoutParams(-2, dp(40)))
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
        b.renderPreview.text = Vars.render(txt).ifBlank { "…" }
    }

    private fun deleteTemplate() {
        val list = prefs.templates()
        if (list.size <= 1) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.tpl_delete_q, list[editing].name))
            .setPositiveButton(R.string.tpl_delete) { _, _ ->
                val removed = editing
                fun shift(i: Int) = when { i == removed -> 0; i > removed -> i - 1; else -> i }
                val def = shift(prefs.defaultIndex)
                val off = shift(prefs.offIndex)
                prefs.saveTemplates(list.filterIndexed { i, _ -> i != removed })
                prefs.defaultIndex = def
                prefs.offIndex = off
                editing = def
                buildTemplatePills(); loadEditing(); refreshOff()
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    // ---------- Allegati ----------

    private fun pick(k: Attachments.Kind) {
        if (!Attachments.canAdd(this, k)) {
            Toast.makeText(this, getString(R.string.att_limit, k.max), Toast.LENGTH_SHORT).show(); return
        }
        pendingKind = k
        pickFile.launch(arrayOf(k.mime))
    }

    private fun refreshAttachments() {
        val items = Attachments.list(this)
        b.attList.removeAllViews()
        items.forEach { a ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = round(color(R.color.surface2), 10)
                clipToOutline = true
                if (a.kind == Attachments.Kind.PHOTO) {
                    val f = Attachments.file(this@MainActivity, a)
                    val opt = BitmapFactory.Options().apply { inSampleSize = 8 }
                    BitmapFactory.decodeFile(f.path, opt)?.let { setImageBitmap(it) }
                } else {
                    setImageResource(if (a.kind == Attachments.Kind.VIDEO) R.drawable.ic_play else R.drawable.ic_doc)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    imageTintList = ColorStateList.valueOf(color(R.color.mint))
                }
            }
            row.addView(thumb, LinearLayout.LayoutParams(dp(48), dp(48)))
            row.addView(TextView(this).apply {
                text = a.name
                setTextColor(color(R.color.text))
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setPadding(dp(12), 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(color(R.color.text3))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                contentDescription = getString(R.string.att_remove)
                isClickable = true
                setOnClickListener { Attachments.remove(this@MainActivity, a); refreshAttachments() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            b.attList.addView(row)
        }
        val photos = Attachments.count(this, Attachments.Kind.PHOTO)
        b.btnAddPhoto.text = getString(R.string.att_add_photo) + " $photos/3"
        b.btnAddPhoto.isEnabled = photos < 3
    }

    // ---------- Canale ----------

    private fun channelText(c: Channel) = if (c == Channel.AUTO)
        getString(R.string.ch_auto_now, getString(Channel.resolve(this, c).label)) else getString(c.label)

    private fun refreshChannel() {
        val sims = Sims.list(this)
        b.channelName.text = if (sims.size > 1) sims.joinToString("\n") { s ->
            s.label.substringBefore(" · ") + ": " +
                if (prefs.simEnabled(s.subId)) channelText(prefs.channelFor(s.subId)) else getString(R.string.sim_off)
        } else channelText(prefs.channel)
    }

    /** Doppia SIM: prima la SIM, poi canale o spegnimento */
    private fun pickChannel() {
        val sims = Sims.list(this)
        if (sims.size <= 1) return pickGlobalChannel()
        MaterialAlertDialogBuilder(this).setTitle(R.string.sims_pick)
            .setItems(sims.map { it.label }.toTypedArray()) { _, i ->
                val sim = sims[i]
                val all = Channel.entries.filter { it != Channel.AUTO }
                val on = prefs.simEnabled(sim.subId)
                val names = all.map { getString(it.label) + if (Channel.isInstalled(this, it)) "" else "  ·  " + getString(R.string.ch_not_installed) } +
                    getString(if (on) R.string.sim_turn_off else R.string.sim_turn_on)
                MaterialAlertDialogBuilder(this).setTitle(sim.label)
                    .setItems(names.toTypedArray()) { _, j ->
                        if (j == all.size) prefs.setSimEnabled(sim.subId, !on)
                        else { prefs.setSimChannel(sim.subId, all[j]); prefs.setSimEnabled(sim.subId, true) }
                        refreshChannel()
                    }.show()
            }.show()
    }

    private fun pickGlobalChannel() {
        val all = Channel.entries
        val names = all.map {
            val n = getString(it.label)
            if (Channel.isInstalled(this, it)) n else n + "  ·  " + getString(R.string.ch_not_installed)
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.send_with)
            .setSingleChoiceItems(names, all.indexOf(prefs.channel)) { d, i ->
                prefs.channel = all[i]
                refreshChannel(); refreshHealth()
                if (all[i] == Channel.WECHAT) Toast.makeText(this, R.string.wechat_note, Toast.LENGTH_LONG).show()
                if (all[i] == Channel.TELEGRAM) Toast.makeText(this, R.string.telegram_note, Toast.LENGTH_LONG).show()
                d.dismiss()
            }.show()
    }

    // ---------- Orari ----------

    private fun refreshOff() {
        b.offBox.visibility = if (prefs.offEnabled) View.VISIBLE else View.GONE
        b.workStart.text = getString(R.string.work_from, fmt(prefs.workStart))
        b.workEnd.text = getString(R.string.work_to, fmt(prefs.workEnd))
        val list = prefs.templates()
        b.offTemplate.text = getString(R.string.off_use, list[prefs.offIndex.coerceIn(0, list.size - 1)].name)
        b.offNow.text = getString(R.string.off_now, list[prefs.autoIndex()].name)
    }

    private fun refreshHours() {
        b.hoursRow.visibility = if (prefs.hoursEnabled) View.VISIBLE else View.GONE
        b.hoursStart.text = getString(R.string.hours_from, fmt(prefs.hoursStart))
        b.hoursEnd.text = getString(R.string.hours_to, fmt(prefs.hoursEnd))
    }

    // ---------- Statistiche ----------

    private fun refreshStats() {
        val s = prefs.stats()
        val rate = if (s.calls7 > 0) (s.sent7 * 100 / s.calls7).coerceAtMost(100) else 0
        b.miniCalls.text = s.calls7.toString()
        b.miniSent.text = s.sent7.toString()
        b.miniRate.text = if (s.calls7 > 0) "$rate%" else "–"
        b.statCalls.text = s.calls7.toString()
        b.statSent.text = s.sent7.toString()
        b.statRate.text = if (s.calls7 > 0) "$rate%" else "–"
        b.statRateBar.setProgressCompat(rate, true)
        b.statRateText.text = getString(R.string.stats_rate_text, s.sent7, s.calls7)
        val prevRate = if (s.callsPrev7 > 0) s.sentPrev7 * 100 / s.callsPrev7 else -1
        b.statTrend.text = if (prevRate >= 0 && s.calls7 > 0) {
            val d = rate - prevRate
            getString(R.string.stats_trend, (if (d >= 0) "+" else "") + d)
        } else ""
        b.statAll.text = getString(R.string.stats_all, s.callsAll, s.sentAll)
        b.topCard.visibility = if (s.topTemplate != null) View.VISIBLE else View.GONE
        b.statTop.text = s.topTemplate?.let { getString(R.string.stats_top_value, it, s.topCount) }

        b.statBars.removeAllViews()
        val max = (s.days.maxOfOrNull { it.calls } ?: 0).coerceAtLeast(1)
        val h = dp(110)
        s.days.forEach { d ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            val bars = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            fun bar(n: Int, c: Int) = View(this).apply { background = round(c, 4) }.also {
                bars.addView(it, LinearLayout.LayoutParams(dp(9), (h * n / max).coerceAtLeast(dp(4))).apply { marginStart = dp(1); marginEnd = dp(1) })
            }
            bar(d.calls, 0xFF2C3D35.toInt())
            bar(d.sent, color(R.color.brand))
            col.addView(bars, LinearLayout.LayoutParams(-1, h))
            col.addView(TextView(this).apply {
                text = d.label
                textSize = 11f
                setTextColor(color(R.color.text3))
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            b.statBars.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun refreshHistory() {
        val list = b.historyList
        list.removeAllViews()
        val items = prefs.history()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply { setText(R.string.history_empty); setTextColor(color(R.color.text3)) })
            return
        }
        items.take(15).forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(TextView(this).apply {
                text = e.number.takeLast(2)
                gravity = Gravity.CENTER
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.grotesk)
                setTextColor(color(R.color.mint))
                textSize = 14f
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1E3329.toInt()) }
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                addView(TextView(this@MainActivity).apply {
                    text = Numbers.pretty(e.number); setTextColor(color(R.color.text)); textSize = 15f
                })
                addView(TextView(this@MainActivity).apply {
                    text = DateUtils.getRelativeTimeSpanString(e.time).toString() +
                        (if (e.sent) " · " + getString(R.string.history_sent) else "")
                    setTextColor(color(if (e.sent) R.color.mint else R.color.text3)); textSize = 12f
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                setText(R.string.history_send)
                cornerRadius = dp(14)
                strokeColor = ColorStateList.valueOf(0xFF2B3D35.toInt())
                setTextColor(color(R.color.brand))
                setOnClickListener { pickAndSend(e.number) }
            })
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

    // ---------- Piano ----------

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
            b.btnPro.text = billing.price?.let { getString(R.string.btn_pro_price, it) } ?: getString(R.string.btn_pro)
        }
    }

    // ---------- Azioni ----------

    /** Mostra la scheda tra 5 secondi, così si può uscire dall'app e vederla sopra le altre. */
    private fun testOverlay() {
        if (!Overlay.canShow(this)) { openSetup(); return }
        Toast.makeText(this, R.string.overlay_test_toast, Toast.LENGTH_LONG).show()
        val ctx = applicationContext
        android.os.Handler(mainLooper).postDelayed({ Overlay.show(ctx, "393331234567") }, 5000)
    }

    /** Mostra il messaggio nell'app scelta, senza destinatario. Non conta. */
    private fun testMessage() {
        if (!Channel.preview(this, prefs.channel, prefs.messageFor(editing)))
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_LONG).show()
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
            ) mgr.startUpdateFlowForResult(info, updateLauncher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
        }
    }

    private fun resumeUpdate() {
        val mgr = AppUpdateManagerFactory.create(this)
        mgr.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                mgr.startUpdateFlowForResult(info, updateLauncher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
        }
    }

    // ---------- Utilità ----------

    private fun pickTime(current: Int, done: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> done(h * 60 + m) }, current / 60, current % 60,
            android.text.format.DateFormat.is24HourFormat(this)).show()
    }

    private fun font() = ResourcesCompat.getFont(this, R.font.inter) ?: Typeface.DEFAULT
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun round(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        const val EXTRA_PAYWALL = "paywall"
    }
}
