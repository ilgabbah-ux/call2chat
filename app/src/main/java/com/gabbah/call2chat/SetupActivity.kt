package com.gabbah.call2chat

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.gabbah.call2chat.Health.Maker
import com.gabbah.call2chat.Health.Step
import com.gabbah.call2chat.databinding.ActivitySetupBinding

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
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = s.top, bottom = s.bottom)
            insets
        }
        prefs = Prefs(this)
        b.primary.setOnClickListener { current?.let { act(it) } ?: testCard() }
        b.confirm.setOnClickListener {
            val s = current
            if (s == null) { finishSetup(); return@setOnClickListener }
            prefs.setConfirmed(s.name, true)
            render()
        }
        b.skip.setOnClickListener {
            val s = current
            if (s == null) { finishSetup(); return@setOnClickListener }
            skipped += s
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val steps = Health.steps()
        val next = steps.firstOrNull { !Health.isOk(this, it) && it !in skipped }
        current = next
        val done = steps.count { Health.isOk(this, it) }
        b.bar.max = steps.size
        b.bar.setProgressCompat(done, true)

        if (next == null) {
            val stillMissing = Health.missing(this)
            b.progress.text = getString(R.string.setup_progress_done, done, steps.size)
            b.icon.text = if (stillMissing.isEmpty()) "✅" else "⚠️"
            b.title.text = getString(if (stillMissing.isEmpty()) R.string.setup_done_title else R.string.setup_partial_title)
            b.text.text = getString(if (stillMissing.isEmpty()) R.string.setup_done_text else R.string.setup_partial_text)
            b.tip.text = getString(R.string.setup_tip_recents)
            b.primary.text = getString(R.string.btn_overlay_test)
            b.primary.isEnabled = Overlay.canShow(this)
            b.confirm.text = getString(R.string.setup_go_app)
            b.confirm.visibility = View.VISIBLE
            b.skip.visibility = View.GONE
            return
        }

        val idx = steps.indexOf(next) + 1
        b.progress.text = getString(R.string.setup_progress, idx, steps.size)
        b.primary.isEnabled = true
        b.skip.visibility = View.VISIBLE
        b.skip.text = getString(R.string.setup_skip)
        b.confirm.visibility = if (Health.needsManualConfirm(next)) View.VISIBLE else View.GONE
        b.confirm.text = getString(R.string.setup_confirm)
        b.tip.text = ""

        when (next) {
            Step.ROLE -> show("📞", R.string.step_role_title, R.string.step_role_text, R.string.step_role_btn)
            Step.NOTIFICATIONS -> show("🔔", R.string.step_notif_title, R.string.step_notif_text, R.string.step_notif_btn)
            Step.OVERLAY -> show("💬", R.string.step_overlay_title, R.string.step_overlay_text, R.string.step_open)
            Step.KEEP_ACTIVE -> show("⏸️", R.string.step_keep_title, R.string.step_keep_text, R.string.step_open)
            Step.BATTERY -> show("🔋", R.string.step_battery_title, R.string.step_battery_text, R.string.step_open)
            Step.MAKER -> {
                val (t, x) = when (Health.maker()) {
                    Maker.XIAOMI -> R.string.step_xiaomi_title to R.string.step_xiaomi_text
                    Maker.SAMSUNG -> R.string.step_samsung_title to R.string.step_samsung_text
                    Maker.OPPO -> R.string.step_oppo_title to R.string.step_oppo_text
                    Maker.VIVO -> R.string.step_vivo_title to R.string.step_vivo_text
                    Maker.HUAWEI -> R.string.step_huawei_title to R.string.step_huawei_text
                    Maker.OTHER -> R.string.step_xiaomi_title to R.string.step_xiaomi_text
                }
                show("⚙️", t, x, R.string.step_open)
                b.tip.text = getString(R.string.setup_tip_recents)
            }
        }
    }

    private fun show(icon: String, title: Int, text: Int, btn: Int) {
        b.icon.text = icon
        b.title.setText(title)
        b.text.setText(text)
        b.primary.setText(btn)
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
            Step.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            Step.OVERLAY -> openFirst(listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))))
            Step.KEEP_ACTIVE -> openFirst(listOf(
                Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.parse("package:$packageName"))))
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
}
