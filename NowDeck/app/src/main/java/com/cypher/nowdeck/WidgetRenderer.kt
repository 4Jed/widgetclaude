package com.cypher.nowdeck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

object WidgetRenderer {

    fun updateAll(ctx: Context, p: Player) {
        val mgr = AppWidgetManager.getInstance(ctx)
        mgr.getAppWidgetIds(ComponentName(ctx, p.provider)).forEach { render(ctx, mgr, it, p) }
    }

    fun render(ctx: Context, mgr: AppWidgetManager, id: Int, p: Player) {
        val o = mgr.getAppWidgetOptions(id)
        val portrait = ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val wDp = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                   else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)).takeIf { it > 0 } ?: 250
        val hDp = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                   else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)).takeIf { it > 0 } ?: 70
        mgr.updateAppWidget(id, build(ctx, p, wDp, hDp))
    }

    /** Builds the widget views. Also used for the live preview in the app. */
    fun build(ctx: Context, p: Player, wDp: Int, hDp: Int): RemoteViews {
        val prefs = Prefs(ctx, p)
        val state = MediaCache.get(ctx, p)
        val art = MediaCache.art(ctx, p)
        val density = ctx.resources.displayMetrics.density
        val rv = RemoteViews(ctx.packageName, R.layout.widget_media)

        // Background
        val scale = minOf(density, 1200f / wDp) // cap bitmap size
        val bg = background(
            art = if (prefs.artBackground) art else null,
            w = (wDp * scale).toInt().coerceAtLeast(1),
            h = (hDp * scale).toInt().coerceAtLeast(1),
            radius = prefs.cornerRadius * scale,
        )
        rv.setImageViewBitmap(R.id.bg, bg)
        rv.setInt(R.id.root, "setBackgroundColor", Color.TRANSPARENT)

        // Text
        val title = state.title.ifBlank { ctx.getString(R.string.nothing_playing) }
        rv.setTextViewText(R.id.title, title)
        rv.setTextViewText(R.id.subtitle, if (state.title.isBlank()) p.label else state.subtitle)
        val ts = prefs.textScale / 100f
        rv.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, 15f * ts)
        rv.setTextViewTextSize(R.id.subtitle, TypedValue.COMPLEX_UNIT_SP, 12f * ts)
        rv.setViewVisibility(R.id.subtitle, if (hDp >= 56) View.VISIBLE else View.GONE)

        // Fit buttons/text to the width. Skip buttons beat text when space is tight.
        val free = wDp - 28 - 70 // padding + play button
        val skipW = 100
        var showSkip = p.showSkip
        var showText = true
        if (showSkip && free - skipW < 64) {
            if (free >= skipW) showText = false else showSkip = false
        }
        if (!showSkip && free < 56) showText = false
        val skipVis = if (showSkip) View.VISIBLE else View.GONE
        rv.setViewVisibility(R.id.btn_prev, skipVis)
        rv.setViewVisibility(R.id.btn_next, skipVis)
        rv.setViewVisibility(R.id.text_area, if (showText) View.VISIBLE else View.GONE)

        // Play button: round artwork with the icon on top
        val circle = art?.let { circleArt(it, (54 * density).toInt()) }
        if (circle != null) rv.setImageViewBitmap(R.id.play_art, circle)
        else rv.setImageViewResource(R.id.play_art, android.R.color.transparent)
        rv.setImageViewResource(R.id.play_icon, if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)

        // Clicks
        val open = openAppIntent(ctx, p)
        rv.setOnClickPendingIntent(R.id.text_area, open)
        rv.setOnClickPendingIntent(R.id.bg, open)
        rv.setOnClickPendingIntent(
            R.id.btn_play,
            if (state.active) action(ctx, p, ActionReceiver.ACTION_PLAY_PAUSE) else open,
        )
        rv.setOnClickPendingIntent(R.id.btn_prev, action(ctx, p, ActionReceiver.ACTION_PREV))
        rv.setOnClickPendingIntent(R.id.btn_next, action(ctx, p, ActionReceiver.ACTION_NEXT))
        return rv
    }

    private fun action(ctx: Context, p: Player, act: String): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
            .setAction(act)
            .putExtra(ActionReceiver.EXTRA_PKG, p.pkg)
        val code = p.ordinal * 10 + act.hashCode() % 10
        return PendingIntent.getBroadcast(ctx, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppIntent(ctx: Context, p: Player): PendingIntent {
        val launch = ctx.packageManager.getLaunchIntentForPackage(p.pkg)
            ?: Intent(ctx, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 100 + p.ordinal, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Rounded background: blurred, darkened artwork, or plain black. */
    private fun background(art: Bitmap?, w: Int, h: Int, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (art != null) {
            val filled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val fc = Canvas(filled)
            fc.drawBitmap(softBlur(art, w, h), null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            fc.drawColor(Color.argb(140, 0, 0, 0))
            paint.shader = BitmapShader(filled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        } else {
            paint.color = Color.BLACK
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
        return out
    }

    /** Centre-crops to the widget's shape, then shrinks it so upscaling gives a light blur. */
    private fun softBlur(art: Bitmap, w: Int, h: Int): Bitmap {
        val target = w.toFloat() / h
        var cw = art.width
        var ch = (cw / target).toInt()
        if (ch > art.height) { ch = art.height; cw = (ch * target).toInt() }
        val x = (art.width - cw) / 2
        val y = (art.height - ch) / 2
        val cropped = Bitmap.createBitmap(art, x, y, cw.coerceAtLeast(1), ch.coerceAtLeast(1))
        val smallW = 56
        val smallH = (smallW / target).toInt().coerceAtLeast(2)
        return Bitmap.createScaledBitmap(cropped, smallW, smallH, true)
    }

    private fun circleArt(art: Bitmap, size: Int): Bitmap {
        val side = minOf(art.width, art.height)
        val src = Rect((art.width - side) / 2, (art.height - side) / 2,
            (art.width + side) / 2, (art.height + side) / 2)
        val sq = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(sq).apply {
            drawBitmap(art, src, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
            drawColor(Color.argb(90, 0, 0, 0))
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(sq, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return out
    }
}
