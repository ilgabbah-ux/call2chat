package com.gabbah.call2chat

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.gabbah.call2chat.Health.Maker
import com.gabbah.call2chat.Health.Step
import com.gabbah.call2chat.databinding.ActivitySetupBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** Configurazione guidata: un passaggio alla volta, con il segno di spunta quando è fatto. */
class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding
    private lateinit var prefs: Prefs
    private val skipped = mutableSetOf<Step>()
    private var current: Step? = null

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!Health.hasRole(this)) openFirst(listOf(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)))
        else render()
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top + dp(8), bottom = s.bottom)
            insets
        }
        prefs = Prefs(this)
        b.primary.setOnClickListener { current?.let { act(it) } ?: testCard() }
        b.confirm.setOnClickListener {
            val s = current ?: return@setOnClickListener finishSetup()
            prefs.setConfirmed(s.name, true)
            render()
        }
        b.skip.setOnClickListener {
            val s = current ?: return@setOnClickListener finishSetup()
            skipped += s
            render()
        }
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val steps = Health.steps(this)
        val next = steps.firstOrNull { !Health.isOk(this, it) && it !in skipped }
        current = next
        val done = steps.filter { Health.isOk(this, it) }

        // Barra a segmenti: verde = fatto, giallo = adesso
        b.segments.removeAllViews()
        steps.forEachIndexed { i, s ->
            val c = when { Health.isOk(this, s) -> R.color.brand; s == next -> R.color.yellow; else -> R.color.surface2 }
            b.segments.addView(View(this).apply { background = round(color(c), 3) },
                LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = dp(6) })
        }

        // Passaggi già fatti
        b.doneList.removeAllViews()
        done.filter { it != next }.forEach { s ->
            b.doneList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
                addView(ImageView(this@SetupActivity).apply {
                    setImageResource(R.drawable.ic_check); imageTintList = ColorStateList.valueOf(color(R.color.brand))
                }, LinearLayout.LayoutParams(dp(18), dp(18)))
                addView(TextView(this@SetupActivity).apply {
                    setText(titleOf(s)); setTextColor(color(R.color.mint)); textSize = 14f
                    setPadding(dp(10), 0, 0, 0)
                })
            })
        }
        b.extra.removeAllViews()

        if (next == null) {
            val missing = Health.missing(this)
            b.progress.text = getString(R.string.setup_progress_done, done.size, steps.size)
            b.illustration.setImageResource(R.drawable.ill_done)
            b.title.setText(if (missing.isEmpty()) R.string.setup_done_title else R.string.setup_partial_title)
            b.text.setText(if (missing.isEmpty()) R.string.setup_done_text else R.string.setup_partial_text)
            b.tip.setText(R.string.setup_tip_recents)
            b.primary.setText(R.string.btn_overlay_test)
            b.primary.isEnabled = Overlay.canShow(this)
            b.confirm.setText(R.string.setup_go_app)
            b.confirm.visibility = View.VISIBLE
            b.skip.visibility = View.GONE
            b.doneList.visibility = View.GONE
            return
        }

        b.doneList.visibility = View.VISIBLE
        b.progress.text = getString(R.string.setup_progress, steps.indexOf(next) + 1, steps.size)
        b.primary.isEnabled = true
        b.primary.visibility = View.VISIBLE
        b.skip.visibility = View.VISIBLE
        b.skip.setText(R.string.setup_skip)
        b.confirm.visibility = if (Health.needsManualConfirm(next)) View.VISIBLE else View.GONE
        b.confirm.setText(R.string.setup_confirm)
        b.tip.text = ""
        b.title.setText(titleOf(next))

        when (next) {
            Step.ROLE -> show(R.drawable.ill_role, R.string.step_role_text, R.string.step_role_btn)
            Step.SIMS -> {
                show(R.drawable.ill_role, R.string.step_sims_text, R.string.step_sims_btn)
                if (Sims.hasPermission(this)) { b.primary.visibility = View.GONE; buildSimRows() }
            }
            Step.CHANNEL -> {
                show(R.drawable.ill_notif, R.string.step_channel_text, R.string.step_open)
                b.primary.visibility = View.GONE
                b.confirm.visibility = View.GONE
                buildChannelChoice()
            }
            Step.NOTIFICATIONS -> show(R.drawable.ill_notif, R.string.step_notif_text, R.string.step_notif_btn)
            Step.OVERLAY -> show(R.drawable.ill_overlay, R.string.step_overlay_text, R.string.step_open)
            Step.KEEP_ACTIVE -> show(R.drawable.ill_keep, R.string.step_keep_text, R.string.step_open)
            Step.BATTERY -> show(R.drawable.ill_battery, R.string.step_battery_text, R.string.step_open)
            Step.MAKER -> {
                show(R.drawable.ill_maker, when (Health.maker()) {
                    Maker.SAMSUNG -> R.string.step_samsung_text
                    Maker.OPPO -> R.string.step_oppo_text
                    Maker.VIVO -> R.string.step_vivo_text
                    Maker.HUAWEI -> R.string.step_huawei_text
                    else -> R.string.step_xiaomi_text
                }, R.string.step_open)
                b.tip.setText(R.string.setup_tip_recents)
            }
        }
    }

    private fun titleOf(s: Step) = when (s) {
        Step.ROLE -> R.string.step_role_title
        Step.SIMS -> R.string.step_sims_title
        Step.CHANNEL -> R.string.step_channel_title
        Step.NOTIFICATIONS -> R.string.step_notif_title
        Step.OVERLAY -> R.string.step_overlay_title
        Step.KEEP_ACTIVE -> R.string.step_keep_title
        Step.BATTERY -> R.string.step_battery_title
        Step.MAKER -> when (Health.maker()) {
            Maker.SAMSUNG -> R.string.step_samsung_title
            Maker.OPPO -> R.string.step_oppo_title
            Maker.VIVO -> R.string.step_vivo_title
            Maker.HUAWEI -> R.string.step_huawei_title
            else -> R.string.step_xiaomi_title
        }
    }

    private fun show(ill: Int, text: Int, btn: Int) {
        b.illustration.setImageResource(ill)
        b.text.setText(text)
        b.primary.setText(btn)
    }

    /** Doppia SIM: per ogni SIM, attiva/spenta e con quale app rispondere */
    private fun buildSimRows() {
        val sims = Sims.list(this)
        if (sims.isEmpty()) {
            b.extra.addView(TextView(this).apply { setText(R.string.sims_none); setTextColor(color(R.color.text3)) })
            return
        }
        sims.forEach { sim ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(color(R.color.surface)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), color(R.color.stroke))
                }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(TextView(this).apply {
                text = sim.label; setTextColor(color(R.color.text)); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val sw = MaterialSwitch(this).apply {
                isChecked = prefs.simEnabled(sim.subId)
                contentDescription = sim.label
            }
            top.addView(sw)
            row.addView(top)
            val ch = TextView(this).apply {
                textSize = 14f; setTextColor(color(R.color.brand_text)); setPadding(0, dp(6), 0, dp(2))
                minHeight = dp(36); gravity = Gravity.CENTER_VERTICAL
                isClickable = true
            }
            fun paint() {
                val c = prefs.simChannel(sim.subId) ?: prefs.channel
                ch.text = getString(R.string.sim_reply_with, getString(Channel.resolve(this, c).label)) + "  ›"
                ch.alpha = if (sw.isChecked) 1f else 0.4f
            }
            ch.setOnClickListener { pickChannel { c -> prefs.setSimChannel(sim.subId, c); paint() } }
            sw.setOnCheckedChangeListener { _, on -> prefs.setSimEnabled(sim.subId, on); paint() }
            paint()
            row.addView(ch)
            b.extra.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    /** WhatsApp e WhatsApp Business sullo stesso telefono: si sceglie a mano */
    private fun buildChannelChoice() {
        listOf(Channel.WHATSAPP, Channel.WA_BUSINESS).forEach { c ->
            b.extra.addView(com.google.android.material.button.MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                setText(c.label)
                minHeight = dp(56)
                cornerRadius = dp(16)
                textSize = 15f
                setTextColor(color(R.color.text))
                strokeColor = ColorStateList.valueOf(color(R.color.outline))
                setOnClickListener {
                    prefs.channel = c
                    prefs.setConfirmed(Step.CHANNEL.name, true)
                    render()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun pickChannel(done: (Channel) -> Unit) {
        val all = Channel.entries.filter { it != Channel.AUTO }
        val names = all.map {
            getString(it.label) + if (Channel.isInstalled(this, it)) "" else "  ·  " + getString(R.string.ch_not_installed)
        }.toTypedArray()
        MaterialAlertDialogBuilder(this).setTitle(R.string.send_with)
            .setItems(names) { _, i -> done(all[i]) }.show()
    }

    private fun act(s: Step) {
        when (s) {
            Step.ROLE -> {
                val rm = getSystemService(RoleManager::class.java)
                if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    Toast.makeText(this, R.string.role_unavailable, Toast.LENGTH_LONG).show(); return
                }
                try { roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }
                catch (e: Exception) { openFirst(listOf(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) }
            }
            Step.SIMS -> permLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            Step.CHANNEL -> {}
            Step.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            Step.OVERLAY -> openFirst(listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))))
            Step.KEEP_ACTIVE -> openFirst(listOf(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.parse("package:$packageName"))))
            Step.BATTERY -> openFirst(emptyList())
            Step.MAKER -> openFirst(Health.makerIntents(this))
        }
    }

    /** Apre la prima pagina che esiste su questo telefono; in fondo c'è sempre "Info app". */
    private fun openFirst(list: List<Intent>) {
        for (i in list + Health.appDetails(this)) {
            try { startActivity(i); return } catch (e: Exception) { }
        }
    }

    private fun testCard() {
        Toast.makeText(this, R.string.overlay_test_toast, Toast.LENGTH_LONG).show()
        val ctx = applicationContext
        android.os.Handler(mainLooper).postDelayed({ Overlay.show(ctx, "393331234567") }, 5000)
    }

    private fun finishSetup() {
        prefs.setupSeen = true
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        prefs.setupSeen = true
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun round(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
