package com.cypher.nowdeck

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

open class BaseMediaWidget(private val player: Player) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetRenderer.render(ctx, mgr, it, player) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle) {
        WidgetRenderer.render(ctx, mgr, id, player)
    }
}

class YtMusicWidget : BaseMediaWidget(Player.YT_MUSIC)
class AudibleWidget : BaseMediaWidget(Player.AUDIBLE)
