package com.shilapi.xcertplay

import kotlin.math.roundToInt

/** Causal presentation only: no queued samples or predicted vehicle values. */
internal class DashboardDisplayValue(
    private val durationMs: Long,
    private val snapZero: Boolean = false,
    private val resetOnSignChange: Boolean = false,
) {
    private var from: Double? = null
    private var target: Double? = null
    private var startedNs = 0L

    fun retarget(value: Double?, nowNs: Long, snap: Boolean = false) {
        val next = value?.takeIf { it.isFinite() }
        if (snap || next == null || target == null || (snapZero && next == 0.0)) {
            from = next; target = next; startedNs = nowNs
            return
        }
        // Repeated telemetry must not restart an unfinished transition.
        if (next == target) return
        val current = sample(nowNs)
        from = if (resetOnSignChange && current != null && current * next < 0.0) 0.0 else current
        target = next; startedNs = nowNs
    }

    fun sample(nowNs: Long): Double? {
        val end = target ?: return null
        val start = from ?: return end
        val fraction = if (durationMs <= 0) 1.0 else
            ((nowNs - startedNs).coerceAtLeast(0).toDouble() / (durationMs * 1_000_000.0)).coerceIn(0.0, 1.0)
        return start + (end - start) * fraction
    }
}

/** Follow vsync, capped at 60 Hz even on 90/120 Hz panels. Tolerate timestamp rounding. */
internal class DashboardFrameClock {
    private var nextNs = Long.MIN_VALUE
    fun reset() { nextNs = Long.MIN_VALUE }
    fun accept(nowNs: Long): Boolean {
        if (nextNs != Long.MIN_VALUE && nowNs + 1_000_000L < nextNs) return false
        nextNs = if (nextNs == Long.MIN_VALUE || nowNs - nextNs > 16_666_667L)
            nowNs + 16_666_667L else nextNs + 16_666_667L
        return true
    }
}

/** Heuristic, not a BMS/route-based range prediction. */
internal fun dashboardEstimatedRangeKm(batteryPercent: Int?): Int? =
    batteryPercent?.takeIf { it in 0..100 }?.let { (it * 0.85).roundToInt() }

internal fun dashboardGearColor(gear: String): Int = when (gear) {
    "ECO" -> 0xff00ff00.toInt()
    "S" -> 0xffff6500.toInt() // RGB565 0xFB20 from the ESP32 instrument.
    "FREE" -> 0xff0000ff.toInt()
    "T" -> 0xffff0000.toInt()
    "P" -> 0xffffff00.toInt()
    else -> 0xffffffff.toInt() // A/R/unknown override labels remain white.
}
