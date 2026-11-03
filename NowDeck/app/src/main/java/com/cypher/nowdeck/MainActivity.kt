package com.cypher.nowdeck

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val jungle = Color.parseColor("#1A2421")
    private val card = Color.parseColor("#24302C")
    private val forest = Color.parseColor("#0B6623")
    private val mint = Color.parseColor("#4FD08A")
    private val muted = Color.parseColor("#9FB3AB")

    private lateinit var accessCard: LinearLayout
    private val previews = mutableMapOf<Player, FrameLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(jungle)
            addView(column)
        }
        // Android 15+ draws edge to edge, so pad for the status/nav bars
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)

        column.addView(text("NowDeck", 28f, Color.WHITE, bold = true).apply {
            setPadding(dp(4), dp(8), 0, dp(16))
        })

        accessCard = cardView()
        column.addView(accessCard)

        Player.entries.forEach { column.addView(playerCard(it)) }
    }

    override fun onResume() {
        super.onResume()
        buildAccessCard()
        Player.entries.forEach { refresh(it) }
    }

    // ---------- Notification access ----------

    private fun buildAccessCard() {
        accessCard.removeAllViews()
        val ok = MediaListenerService.hasAccess(this)
        accessCard.addView(text(if (ok) "Media access: on" else "Media access: off", 18f, Color.WHITE, bold = true))
        if (ok) {
            accessCard.addView(text("Widgets can see YouTube Music and Audible.", 14f, muted))
            return
        }
        accessCard.addView(text(
            "Needed so the widgets can see what's playing. " +
            "If the toggle is greyed out: App info > ⋮ menu > Allow restricted settings, then try again.",
            14f, muted).apply { setPadding(0, dp(6), 0, dp(12)) })
        accessCard.addView(pillButton("Grant access") { openAccessSettings() })
        accessCard.addView(pillButton("App info", filled = false) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:$packageName")))
        })
    }

    private fun openAccessSettings() {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    MediaListenerService.component(this).flattenToString())
        } else null
        try {
            startActivity(detail ?: Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    // ---------- Per-widget settings ----------

    private fun playerCard(p: Player): View {
        val prefs = Prefs(this, p)
        val c = cardView()

        c.addView(text(p.label, 22f, Color.WHITE, bold = true))
        c.addView(text(if (p.showSkip) "Back, play/pause, next" else "Play/pause only", 14f, muted)
            .apply { setPadding(0, dp(2), 0, dp(14)) })

        val preview = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80))
        }
        previews[p] = preview
        c.addView(preview)

        // Background choice
        c.addView(label("Background"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val artBtn = pillButton("Artwork") {}
        val blackBtn = pillButton("Black", filled = false) {}
        fun paint() {
            styleToggle(artBtn, prefs.artBackground)
            styleToggle(blackBtn, !prefs.artBackground)
        }
        artBtn.setOnClickListener { prefs.artBackground = true; paint(); changed(p) }
        blackBtn.setOnClickListener { prefs.artBackground = false; paint(); changed(p) }
        paint()
        row.addView(artBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        row.addView(blackBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        c.addView(row)

        c.addView(slider("Corner radius", 0, 32, prefs.cornerRadius, { "$it" }) {
            prefs.cornerRadius = it; changed(p)
        })
        c.addView(slider("Text size", 80, 140, prefs.textScale, { "$it%" }) {
            prefs.textScale = it; changed(p)
        })

        c.addView(pillButton("Add to home screen") { requestPin(p) }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12)
        })
        return c
    }

    private fun changed(p: Player) {
        refresh(p)
        WidgetRenderer.updateAll(this, p)
    }

    private fun refresh(p: Player) {
        val box = previews[p] ?: return
        box.removeAllViews()
        val widthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 72
        val views = WidgetRenderer.build(this, p, widthDp, 80)
        box.addView(views.apply(this, box))
    }

    private fun requestPin(p: Player) {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, p.provider), null, null)
        } else {
            Toast.makeText(this, "Long-press your home screen > Widgets > NowDeck", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Small UI helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun label(s: String) = text(s, 16f, Color.WHITE).apply { setPadding(0, dp(16), 0, dp(8)) }

    private fun cardView() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = GradientDrawable().apply { setColor(card); cornerRadius = dp(28).toFloat() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
    }

    private fun pillButton(s: String, filled: Boolean = true, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        textSize = 16f
        stateListAnimator = null
        styleToggle(this, filled)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
    }

    private fun styleToggle(b: Button, on: Boolean) {
        b.background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            if (on) setColor(forest) else { setColor(Color.TRANSPARENT); setStroke(dp(1), muted) }
        }
        b.setTextColor(Color.WHITE)
    }

    private fun slider(
        name: String, min: Int, max: Int, value: Int,
        fmt: (Int) -> String, onChange: (Int) -> Unit,
    ): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val valueText = text(fmt(value), 18f, mint, bold = true)
        head.addView(label(name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(valueText)
        box.addView(head)
        box.addView(SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            progressTintList = ColorStateList.valueOf(mint)
            thumbTintList = ColorStateList.valueOf(mint)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    valueText.text = fmt(p + min)
                    if (fromUser) onChange(p + min)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
        return box
    }
}
