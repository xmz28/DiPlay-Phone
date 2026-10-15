package com.shilapi.xcertplay

import android.content.Context

internal object FusionThemeSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("fusion_display", Context.MODE_PRIVATE)
    // Independent targets: map timing itself remains controlled by the iPhone.
    fun duration(context: Context, toNight: Boolean) = prefs(context)
        .getInt(if (toNight) "transition_to_night_ms" else "transition_to_day_ms", if (toNight) 1600 else 1200)
        .coerceIn(0, 3000)
    fun saveDuration(context: Context, toNight: Boolean, millis: Int) {
        prefs(context).edit().putInt(if (toNight) "transition_to_night_ms" else "transition_to_day_ms", millis.coerceIn(0, 3000)).apply()
    }
    fun hudNight(context: Context, fallback: Boolean) = prefs(context).getBoolean("hud_night", fallback)
    fun mapIndependent(context: Context) = prefs(context).getBoolean("map_independent", false)
    fun select(context: Context, hudNight: Boolean, mapIndependent: Boolean) {
        prefs(context).edit().putBoolean("hud_night", hudNight).putBoolean("map_independent", mapIndependent).apply()
    }
}
