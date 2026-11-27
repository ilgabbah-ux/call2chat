package com.gabbah.call2chat

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Scheda che compare sopra la schermata della chiamata in arrivo,
 * con il pulsante per inviare subito il messaggio.
 */
object Overlay {
    private var view: View? = null
    private val main = Handler(Looper.getMainLooper())
    private const val AUTO_HIDE_MS = 90_000L

    fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun show(ctx: Context, number: String) {
        if (!canShow(ctx)) return
        main.post { showNow(ctx.applicationContext, number) }
    }

    fun hide() {
        main.post { removeNow() }
    }

    private fun removeNow() {
        view?.let { v ->
            try {
                (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
            } catch (e: Exception) { }
        }
        view = null
    }

    private fun showNow(ctx: Context, number: String) {
        removeNow()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
        val prefs = Prefs(ctx)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xF2122A24.toInt())
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(8).toFloat()
        }

        val title = TextView(ctx).apply {
            text = ctx.getString(R.string.app_name) + " · " + Numbers.pretty(number)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val sub = TextView(ctx).apply {
            text = if (prefs.canSend) ctx.getString(R.string.overlay_sub) else ctx.getString(R.string.notif_limit)
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, dp(10))
        }

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val close = Button(ctx).apply {
            text = ctx.getString(R.string.overlay_close)
            setTextColor(0xFFFFFFFF.toInt())
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF); cornerRadius = dp(24).toFloat()
            }
            setOnClickListener { hide() }
        }
        val send = Button(ctx).apply {
            text = ctx.getString(R.string.overlay_send)
            setTextColor(0xFF0A2E25.toInt())
            isAllCaps = false
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(0xFF25D366.toInt()); cornerRadius = dp(24).toFloat()
            }
            setOnClickListener {
                hide()
                ctx.startActivity(
                    Intent(ctx, SendActivity::class.java)
                        .putExtra(SendActivity.EXTRA_NUMBER, number)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        row.addView(close, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        row.addView(send, LinearLayout.LayoutParams(0, dp(48), 2f))

        card.addView(title)
        card.addView(sub)
        card.addView(row)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = dp(140)
            horizontalMargin = 0f
        }
        val wrap = LinearLayout(ctx).apply {
            setPadding(dp(12), 0, dp(12), 0)
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        try {
            wm.addView(wrap, lp)
            view = wrap
            main.postDelayed({ if (view === wrap) removeNow() }, AUTO_HIDE_MS)
        } catch (e: Exception) {
            view = null
        }
    }
}
