package com.cypher.nowdeck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the widget buttons. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Player.byPackage(intent.getStringExtra(EXTRA_PKG)) ?: return
        val c = MediaListenerService.controllerFor(ctx, p)
        if (c == null) {
            // Session gone: let the widget switch its play button to "open app"
            MediaCache.setActive(ctx, p, false)
            return
        }
        val tc = c.transportControls
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                val playing = MediaCache.isPlaying(c.playbackState)
                if (playing) tc.pause() else tc.play()
                MediaCache.setPlaying(ctx, p, !playing) // instant feedback, service corrects it
            }
            ACTION_NEXT -> tc.skipToNext()
            ACTION_PREV -> tc.skipToPrevious()
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val ACTION_PLAY_PAUSE = "com.cypher.nowdeck.PLAY_PAUSE"
        const val ACTION_NEXT = "com.cypher.nowdeck.NEXT"
        const val ACTION_PREV = "com.cypher.nowdeck.PREV"
    }
}
