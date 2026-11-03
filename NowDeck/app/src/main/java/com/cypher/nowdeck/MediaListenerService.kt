package com.cypher.nowdeck

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/**
 * Notification access lets us see YouTube Music's and Audible's media sessions.
 * We don't read notifications themselves.
 */
class MediaListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private val tracked = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        msm = m
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, component(this), handler)
            attach(m.getActiveSessions(component(this)))
        } catch (_: SecurityException) {
        }
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        tracked.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        tracked.clear()
        super.onListenerDisconnected()
    }

    private fun attach(list: List<MediaController>) {
        val ctx: Context = this
        val relevant = list.filter { Player.byPackage(it.packageName) != null }
        val tokens = relevant.map { it.sessionToken }.toSet()

        tracked.keys.filter { it !in tokens }.forEach { t ->
            tracked.remove(t)?.let { (c, cb) -> c.unregisterCallback(cb) }
        }

        for (c in relevant) {
            if (c.sessionToken in tracked) continue
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = MediaCache.store(ctx, c)
                override fun onPlaybackStateChanged(state: PlaybackState?) = MediaCache.store(ctx, c)
                override fun onSessionDestroyed() {
                    tracked.remove(c.sessionToken)?.let { (cc, cb2) -> cc.unregisterCallback(cb2) }
                    Player.byPackage(c.packageName)?.let { MediaCache.setActive(ctx, it, false) }
                }
            }
            c.registerCallback(cb, handler)
            tracked[c.sessionToken] = c to cb
            MediaCache.store(ctx, c)
        }

        val activePkgs = relevant.map { it.packageName }.toSet()
        Player.entries.forEach { MediaCache.setActive(ctx, it, it.pkg in activePkgs) }
    }

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, MediaListenerService::class.java)

        fun hasAccess(ctx: Context): Boolean =
            ctx.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(component(ctx)) == true

        fun controllerFor(ctx: Context, p: Player): MediaController? = try {
            ctx.getSystemService(MediaSessionManager::class.java)
                ?.getActiveSessions(component(ctx))
                ?.firstOrNull { it.packageName == p.pkg }
        } catch (_: SecurityException) {
            null
        }
    }
}
