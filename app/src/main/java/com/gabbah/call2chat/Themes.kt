package com.gabbah.call2chat

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/** Tema scuro / chiaro / automatico */
object Themes {
    fun mode(theme: String) = when (theme) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "auto" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_YES
    }

    fun apply(ctx: Context) = AppCompatDelegate.setDefaultNightMode(mode(Prefs(ctx).theme))

    /** Contesto con il tema giusto, per la scheda che compare sopra la chiamata */
    fun context(ctx: Context): Context {
        val theme = Prefs(ctx).theme
        if (theme == "auto") return ctx
        val cfg = Configuration(ctx.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (theme == "light") Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES)
        return ctx.createConfigurationContext(cfg)
    }
}
