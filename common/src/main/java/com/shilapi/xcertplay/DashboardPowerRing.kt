package com.shilapi.xcertplay

import kotlin.math.abs

internal data class DashboardPowerRing(val fraction: Float, val recovery: Boolean)

/** Same full-scale values as the reference instrument; both arcs start at zero. */
internal fun dashboardPowerRing(powerKw: Double?): DashboardPowerRing {
    if (powerKw == null || !powerKw.isFinite()) return DashboardPowerRing(0f, false)
    val recovery = powerKw < 0.0
    val fullScale = if (recovery) 0.6 else 1.4
    return DashboardPowerRing((abs(powerKw) / fullScale).coerceIn(0.0, 1.0).toFloat(), recovery)
}
