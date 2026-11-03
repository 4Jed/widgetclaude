package com.cypher.nowdeck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import java.io.File

/** Last known state of each player, kept on disk so widgets survive process death. */
object MediaCache {

    data class State(
        val title: String,
        val subtitle: String,
        val playing: Boolean,
        val active: Boolean,
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("media_state", Context.MODE_PRIVATE)
    private fun artFile(ctx: Context, p: Player) = File(ctx.filesDir, "art_${p.name}.png")

    fun get(ctx: Context, p: Player): State {
        val s = sp(ctx)
        return State(
            title = s.getString("${p.name}_title", null) ?: "",
            subtitle = s.getString("${p.name}_sub", null) ?: "",
            playing = s.getBoolean("${p.name}_playing", false),
            active = s.getBoolean("${p.name}_active", false),
        )
    }

    fun art(ctx: Context, p: Player): Bitmap? {
        val f = artFile(ctx, p)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** Pull everything from a live controller, save it, refresh widgets. */
    fun store(ctx: Context, c: MediaController) {
        val p = Player.byPackage(c.packageName) ?: return
        val m = c.metadata
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val subtitle = listOfNotNull(artist, album)
            .filter { it.isNotBlank() && it != title }
            .distinct()
            .joinToString(" • ")

        val prev = get(ctx, p)
        val edit = sp(ctx).edit()
            .putString("${p.name}_title", title)
            .putString("${p.name}_sub", subtitle)
            .putBoolean("${p.name}_playing", isPlaying(c.playbackState))
            .putBoolean("${p.name}_active", true)
        edit.apply()

        // Only rewrite artwork when the track changes
        if (m != null && (title != prev.title || !artFile(ctx, p).exists())) {
            loadArt(ctx, m)?.let { saveArt(ctx, p, it) }
        }
        WidgetRenderer.updateAll(ctx, p)
    }

    fun setPlaying(ctx: Context, p: Player, playing: Boolean) {
        sp(ctx).edit().putBoolean("${p.name}_playing", playing).apply()
        WidgetRenderer.updateAll(ctx, p)
    }

    fun setActive(ctx: Context, p: Player, active: Boolean) {
        val s = get(ctx, p)
        if (s.active == active && (active || !s.playing)) return
        val e = sp(ctx).edit().putBoolean("${p.name}_active", active)
        if (!active) e.putBoolean("${p.name}_playing", false)
        e.apply()
        WidgetRenderer.updateAll(ctx, p)
    }

    fun isPlaying(s: PlaybackState?): Boolean = when (s?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING -> true
        else -> false
    }

    private fun loadArt(ctx: Context, m: MediaMetadata): Bitmap? {
        m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        m.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
        val uri = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: m.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: return null
        return try {
            ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveArt(ctx: Context, p: Player, src: Bitmap) {
        try {
            val max = 512
            val scale = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
            val bmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1),
                    (src.height * scale).toInt().coerceAtLeast(1), true)
            } else src
            artFile(ctx, p).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
        }
    }
}
