package com.shilapi.xcertplay.vehicle

data class DiagnosticsState(
    val enabled: Boolean = false,
    val statusPackets: Long = 0L,
    val lostPackets: Long = 0L,
    val lastSeq: Long = 0L,
    val lastPacketGapMs: Long? = null,
    val lastRttMs: Long? = null,
    val espProcessMs: Long? = null,
    val pendingPings: Int = 0,
) {
    val lossPercent: Double
        get() {
            val total = statusPackets + lostPackets
            return if (total <= 0L) 0.0 else lostPackets * 100.0 / total
        }
}
