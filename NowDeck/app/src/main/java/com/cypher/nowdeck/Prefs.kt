package com.cypher.nowdeck

import android.content.Context

/** Per-widget look settings. */
class Prefs(ctx: Context, private val player: Player) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun k(name: String) = "${player.name}_$name"

    var artBackground: Boolean
        get() = sp.getBoolean(k("art_bg"), true)
        set(v) = sp.edit().putBoolean(k("art_bg"), v).apply()

    /** Corner radius in dp. */
    var cornerRadius: Int
        get() = sp.getInt(k("radius"), 20)
        set(v) = sp.edit().putInt(k("radius"), v).apply()

    /** Text size in percent (80..140). */
    var textScale: Int
        get() = sp.getInt(k("text"), 100)
        set(v) = sp.edit().putInt(k("text"), v).apply()
}
