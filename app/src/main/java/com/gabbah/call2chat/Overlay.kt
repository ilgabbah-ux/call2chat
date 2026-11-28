package com.gabbah.call2chat

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/**
 * Scheda che compare sopra la schermata della chiamata in arrivo:
 * numero, scelta del messaggio, anteprima e pulsante per inviare.
 */
object Overlay {
    private var view: View? = null
    private val main = Handler(Looper.getMainLooper())
    private const val AUTO_HIDE_MS = 90_000L

    fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun show(ctx: Context, number: String, sim: Int = -1) {
        if (!canShow(ctx)) return
        main.post { showNow(ctx.applicationContext, number, sim) }
    }

    fun hide() = main.post { removeNow() }

    private fun removeNow() {
        view?.let { v ->
            try { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) { }
        }
        view = null
    }

    private fun showNow(ctx: Context, number: String, sim: Int) {
        removeNow()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
        fun col(id: Int) = ContextCompat.getColor(ctx, id)
        fun round(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.DEFAULT
        val grotesk = ResourcesCompat.getFont(ctx, R.font.grotesk) ?: Typeface.DEFAULT_BOLD
        val prefs = Prefs(ctx)
        val channel = Channel.resolve(ctx, prefs.channelFor(sim))

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(0xF20C1411.toInt()); cornerRadius = dp(28).toFloat(); setStroke(dp(1), 0xFF23362D.toInt())
            }
            elevation = dp(12).toFloat()
        }

        // Riga numero
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(ctx).apply {
            text = number.takeLast(2)
            gravity = Gravity.CENTER
            setTypeface(grotesk, Typeface.BOLD)
            textSize = 16f
            setTextColor(0xFF9FE8C4.toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1E3329.toInt()) }
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        val who = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        who.addView(TextView(ctx).apply {
            text = Numbers.pretty(number); setTypeface(inter, Typeface.BOLD)
            textSize = 16f; setTextColor(col(R.color.text))
        })
        who.addView(TextView(ctx).apply {
            val simName = if (sim >= 0) Sims.list(ctx).firstOrNull { it.subId == sim }?.label else null
            text = ctx.getString(R.string.overlay_unknown) + (simName?.let { " · $it" } ?: "")
            typeface = inter; textSize = 12.5f; setTextColor(col(R.color.text3))
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        head.addView(who, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(col(R.color.text3))
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x10FFFFFF) }
            contentDescription = ctx.getString(R.string.overlay_close)
            isClickable = true
            setOnClickListener { hide() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        card.addView(head)

        if (!prefs.canSend) {
            card.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.notif_limit); setTextColor(col(R.color.yellow)); textSize = 14f
                setPadding(0, dp(12), 0, 0)
            })
        } else {
            // Scelta del messaggio: parte da quello automatico (secondo l'orario)
            val templates = prefs.templates()
            var selected = prefs.autoIndex()
            val preview = TextView(ctx).apply {
                typeface = inter; textSize = 14f; setTextColor(0xFFE9FDF5.toInt())
                setLineSpacing(0f, 1.15f); maxLines = 4; ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(col(R.color.bubble))
                    val r = dp(16).toFloat(); val s = dp(4).toFloat()
                    cornerRadii = floatArrayOf(r, r, r, r, s, s, r, r)
                }
            }
            val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val chipViews = mutableListOf<TextView>()
            fun paint() {
                preview.text = prefs.messageFor(selected)
                chipViews.forEachIndexed { i, c ->
                    val on = i == selected
                    c.setTextColor(if (on) col(R.color.on_yellow) else col(R.color.text2))
                    c.background = GradientDrawable().apply {
                        cornerRadius = dp(17).toFloat()
                        if (on) setColor(col(R.color.yellow)) else { setColor(0); setStroke(dp(1), 0xFF2B3D35.toInt()) }
                    }
                }
            }
            templates.forEachIndexed { i, t ->
                val c = TextView(ctx).apply {
                    text = t.name; setTypeface(inter, Typeface.BOLD); textSize = 12.5f
                    gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0)
                    setOnClickListener { selected = i; paint() }
                }
                chipViews += c
                chips.addView(c, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
            }
            paint()
            if (templates.size > 1) card.addView(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false; addView(chips)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            card.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

            Attachments.summary(ctx)?.let { s ->
                card.addView(TextView(ctx).apply {
                    text = ctx.getString(R.string.att_with, s); typeface = inter; textSize = 12.5f
                    setTextColor(col(R.color.text2)); setPadding(dp(2), dp(8), 0, 0)
                })
            }

            card.addView(Button(ctx).apply {
                text = ctx.getString(channel.sendLabel)
                setTypeface(inter, Typeface.BOLD); textSize = 16f; isAllCaps = false
                setTextColor(0xFF052E1C.toInt())
                background = round(col(R.color.wa_green), 18)
                stateListAnimator = null
                setOnClickListener {
                    hide()
                    ctx.startActivity(
                        Intent(ctx, SendActivity::class.java)
                            .putExtra(SendActivity.EXTRA_NUMBER, number)
                            .putExtra(SendActivity.EXTRA_TEMPLATE, selected)
                            .putExtra(SendActivity.EXTRA_SIM, sim)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12) })
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP; y = dp(150) }
        val wrap = LinearLayout(ctx).apply {
            setPadding(dp(12), 0, dp(12), 0)
            addView(card, LinearLayout.LayoutParams(-1, -2))
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
