package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Rect

internal enum class FusionDisplayPreset(val label: String) {
    PHONE("手机默认"), CLUSTER_2_1("六寸仪表 · 2:1");
    companion object {
        fun load(context: Context) = entries.firstOrNull { it.name == context.getSharedPreferences("fusion_display", Context.MODE_PRIVATE).getString("preset", null) } ?: PHONE
        fun save(context: Context, preset: FusionDisplayPreset) {
            context.getSharedPreferences("fusion_display", Context.MODE_PRIVATE).edit().putString("preset", preset.name).apply()
        }
    }
}

/** Physical capture coordinates, translated into the app window, independent of cutout side. */
internal fun fusionDisplayRect(windowWidth: Int, windowHeight: Int, screenWidth: Int,
    screenHeight: Int, windowX: Int, windowY: Int, preset: FusionDisplayPreset): Rect {
    if (preset == FusionDisplayPreset.PHONE || screenWidth <= screenHeight) return Rect(0, 0, windowWidth, windowHeight)
    val h = minOf(screenHeight, screenWidth / 2, windowHeight)
    val w = h * 2
    return Rect((screenWidth - w) / 2 - windowX, (screenHeight - h) / 2 - windowY,
        (screenWidth + w) / 2 - windowX, (screenHeight + h) / 2 - windowY)
}
