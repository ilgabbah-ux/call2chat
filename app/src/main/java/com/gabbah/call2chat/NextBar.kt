package com.gabbah.call2chat

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/**
 * Allegati in più passaggi (foto, poi video, poi PDF).
 * Dopo il primo invio WhatsApp resta nella chat e non torna a Call2Chat:
 * per questo i passaggi successivi si lanciano da una barra che resta sopra WhatsApp
 * (o da una notifica, se la barra non è permessa).
 */
object NextBar {
    private val pending = mutableListOf<Intent>()
    private var view: View? = null
    private val main = Handler(Looper.getMainLooper())
    private const val NOTIF_ID = 4711
    private const val AUTO_HIDE_MS = 10 * 60_000L

    fun hasNext() = pending.isNotEmpty()

    /** Salva i passaggi rimasti e mostra la barra (o la notifica) */
    fun offer(ctx: Context, steps: List<Intent>) {
        pending.clear()
        pending += steps
        show(ctx.applicationContext)
    }

    /** Toglie e restituisce il prossimo passaggio */
    fun pop(): Intent? = pending.removeFirstOrNull()

    fun clear(ctx: Context) {
        pending.clear()
        main.post { remove() }
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }

    /** Nome breve di cosa parte col passaggio: "video", "PDF", "2 foto" */
    fun label(ctx: Context, i: Intent): String {
        val type = i.type.orEmpty()
        val n = (i.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.size ?: 1)
        return when {
            type.startsWith("image") -> ctx.resources.getQuantityString(R.plurals.att_photos, n, n)
            type.startsWith("video") -> ctx.getString(R.string.att_video)
            type.contains("pdf") -> ctx.getString(R.string.att_pdf)
            else -> ctx.getString(R.string.att_title)
        }
    }

    fun show(ctx: Context) {
        val next = pending.firstOrNull() ?: return clear(ctx)
        val remaining = pending.joinToString(", ") { label(ctx, it) }
        if (Overlay.canShow(ctx)) main.post { showBar(ctx, label(ctx, next), remaining) }
        else showNotification(ctx, label(ctx, next), remaining)
    }

    private fun nextIntent(ctx: Context) = Intent(ctx, SendActivity::class.java)
        .putExtra(SendActivity.EXTRA_NEXT, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun showNotification(ctx: Context, next: String, remaining: String) {
        val pi = PendingIntent.getActivity(ctx, NOTIF_ID, nextIntent(ctx),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, App.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(ctx.getString(R.string.next_title, remaining))
            .setContentText(ctx.getString(R.string.next_hint))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(R.drawable.ic_send, ctx.getString(R.string.next_send, next), pi)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) } catch (e: SecurityException) { }
    }

    private fun remove() {
        view?.let { v ->
            try { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) { }
        }
        view = null
    }

    private fun showBar(appCtx: Context, next: String, remaining: String) {
        remove()
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val ctx: Context = object : ContextWrapper(Themes.context(appCtx)) {
            override fun getApplicationContext(): Context = appCtx
        }
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()
        fun col(id: Int) = ContextCompat.getColor(ctx, id)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.DEFAULT

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(col(R.color.overlay_bg)); cornerRadius = dp(22).toFloat(); setStroke(dp(1), col(R.color.overlay_stroke))
            }
            elevation = dp(10).toFloat()
        }
        bar.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.next_title, remaining)
            typeface = inter; textSize = 13f; setTextColor(col(R.color.text2))
            maxLines = 2
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(Button(ctx).apply {
            text = ctx.getString(R.string.next_send, next)
            setTypeface(inter, Typeface.BOLD); textSize = 14f; isAllCaps = false
            setTextColor(col(R.color.on_wa)); stateListAnimator = null
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply { setColor(col(R.color.wa_green)); cornerRadius = dp(16).toFloat() }
            setOnClickListener {
                remove()
                appCtx.startActivity(nextIntent(appCtx))
            }
        }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginStart = dp(8) })
        bar.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(col(R.color.text3))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = ctx.getString(R.string.overlay_close)
            setOnClickListener { clear(appCtx) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM; y = dp(96) }
        val wrap = LinearLayout(ctx).apply {
            setPadding(dp(12), 0, dp(12), 0)
            addView(bar, LinearLayout.LayoutParams(-1, -2))
        }
        try {
            wm.addView(wrap, lp)
            view = wrap
            main.postDelayed({ if (view === wrap) remove() }, AUTO_HIDE_MS)
        } catch (e: Exception) {
            view = null
            showNotification(appCtx, next, remaining)
        }
    }
}
