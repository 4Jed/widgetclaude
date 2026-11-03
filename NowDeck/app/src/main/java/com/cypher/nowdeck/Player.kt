package com.cypher.nowdeck

import android.appwidget.AppWidgetProvider

/** Each widget is pinned to one app. */
enum class Player(
    val pkg: String,
    val label: String,
    val showSkip: Boolean,
    val provider: Class<out AppWidgetProvider>,
) {
    YT_MUSIC("com.google.android.apps.youtube.music", "YouTube Music", true, YtMusicWidget::class.java),
    AUDIBLE("com.audible.application", "Audible", false, AudibleWidget::class.java);

    companion object {
        fun byPackage(pkg: String?): Player? = entries.firstOrNull { it.pkg == pkg }
    }
}
