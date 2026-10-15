package com.shilapi.xcertplay.vehicle

data class VehicleState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val seq: Long = 0L,
    val ms: Long = 0L,
    val stateVersion: Long = 0L,
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val charging: ChargingSnapshot = ChargingSnapshot(),
    val time: VehicleTime = VehicleTime(),
    val icons: VehicleIconState = VehicleIconState(),
    val bluetooth: BluetoothSnapshot = BluetoothSnapshot(),
) {
    fun asConnected(): VehicleState = copy(connectionStatus = ConnectionStatus.CONNECTED)

    fun asDisconnected(): VehicleState = copy(connectionStatus = ConnectionStatus.DISCONNECTED)

}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    MOCK,
}

data class VehicleSnapshot(
    val batteryPercent: Int = 0,
    val batteryValid: Boolean = false,
    val powerKw: Double = 0.0,
    val powerValid: Boolean = false,
    val voltageV: Double = 0.0,
    val currentA: Double = 0.0,
    val voltageValid: Boolean = false,
    val currentValid: Boolean = false,
    val electricalAgeMs: Long = 0L,
    val ready: Boolean = false,
    val lowBattery: Boolean = false,
    val odoKm: Double? = null,
    val odoValid: Boolean = false,
    val gear: GearState = GearState.P,
    val gearValid: Boolean = false,
    val speedKmh: Int = 0,
    val speedOverride: String = "",
    val speedValid: Boolean = false,
    val updatedMs: Long = 0L,
)

/** Prefer unrounded electrical power; older firmware still supplies p / 10 kW. */
val VehicleSnapshot.displayPowerKw: Double?
    get() = when {
        voltageValid && currentValid -> voltageV * currentA / 1000.0
        powerValid -> powerKw
        else -> null
    }

data class ChargingSnapshot(
    val connected: Boolean = false,
    val state: Int = 0,
    val text: String = "",
    val remainingMinutes: Int? = null,
    val remainingValid: Boolean = false,
    val updatedMs: Long = 0L,
)

data class VehicleTime(
    val valid: Boolean = false,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0,
    val updatedMs: Long = 0L,
)

data class VehicleIconState(
    val leftTurn: Boolean = false,
    val rightTurn: Boolean = false,
    val positionLight: Boolean = false,
    val lowBeam: Boolean = false,
    val highBeam: Boolean = false,
    val bluetoothIndicator: Boolean = false,
    val screenWake: Boolean = true,
    val gps: Boolean = false,
    val gsm: Boolean = false,
    val tcs: Boolean = false,
    val tcsBlink: Boolean = false,
    val abs: Boolean = false,
    val absBlink: Boolean = false,
    val cruise: Boolean = false,
    val sideStand: Boolean = false,
    val seat: Boolean = false,
    val riderUnseated: Boolean = false,
    val hdc: Boolean = false,
    val hdcBlink: Boolean = false,
    val charge: Boolean = false,
    val vehicleFault: Boolean = false,
    val energyRecovery: Boolean = false,
    val repairMode: Boolean = false,
)

data class BluetoothSnapshot(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val playing: Boolean = false,
    val phoneCall: Boolean = false,
    val callStatus: String = "IDLE",
    val callNumber: String = "UNKNOWN",
    val remoteName: String = "NO PHONE",
    val trackTitle: String = "NO TRACK",
    val trackArtist: String = "",
    val trackAlbum: String = "",
    val trackPositionMs: Long = 0L,
    val trackDurationMs: Long = 0L,
)

fun Int.toGearState(): GearState {
    return when (this) {
        0 -> GearState.P
        1 -> GearState.ECONOMY
        2 -> GearState.SPORT
        3 -> GearState.T
        4 -> GearState.PUSH
        else -> GearState.P
    }
}

enum class GearState { P, ECONOMY, SPORT, T, PUSH }
