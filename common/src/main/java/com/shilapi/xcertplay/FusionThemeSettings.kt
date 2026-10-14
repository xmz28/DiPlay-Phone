package com.shilapi.xcertplay

import android.content.Context

internal object FusionThemeSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("fusion_display", Context.MODE_PRIVATE)
    fun duration(context: Context) = prefs(context).getInt("transition_ms", 1000).coerceIn(0, 3000)
    fun saveDuration(context: Context, millis: Int) { prefs(context).edit().putInt("transition_ms", millis.coerceIn(0, 3000)).apply() }
    fun hudNight(context: Context, fallback: Boolean) = prefs(context).getBoolean("hud_night", fallback)
    fun mapIndependent(context: Context) = prefs(context).getBoolean("map_independent", false)
    fun select(context: Context, hudNight: Boolean, mapIndependent: Boolean) {
        prefs(context).edit().putBoolean("hud_night", hudNight).putBoolean("map_independent", mapIndependent).apply()
    }
}
