package com.shilapi.xcertplay

import com.shilapi.xcertplay.vehicle.*
import java.util.Locale

internal data class VehicleDashboardSnapshot(
    val vehicle: FusionDashboardView.VehiclePreview,
    val indicators: DashboardIndicatorSnapshot,
)

/** A lost BLE link returns the complete original demo; invalid live fields stay unknown. */
internal fun vehicleDashboardSnapshot(state: VehicleState, enabled: Boolean): VehicleDashboardSnapshot {
    if (!enabled || state.connectionStatus != ConnectionStatus.CONNECTED) {
        return VehicleDashboardSnapshot(FusionDashboardView.VehiclePreview(), DemoDashboardIndicators.snapshot())
    }
    val v = state.vehicle
    fun decimal(value: Double, valid: Boolean) = if (valid) String.format(Locale.US, "%.1f", value) else "--"
    val power = v.displayPowerKw?.let {
        String.format(Locale.US, if (v.voltageValid && v.currentValid) "%.2f" else "%.1f", it)
    } ?: "--"
    val gear = when {
        v.speedOverride.isNotEmpty() -> v.speedOverride
        !v.gearValid -> "--"
        else -> when (v.gear) {
            GearState.P -> "P"
            GearState.ECONOMY -> "ECO"
            GearState.SPORT -> "S"
            GearState.T -> "T"
            GearState.PUSH -> "推行"
        }
    }
    val i = state.icons
    val signals = mutableMapOf<DashboardIndicator, DashboardIndicatorSignal>()
    fun put(id: DashboardIndicator, active: Boolean, blinking: Boolean = false) {
        signals[id] = DashboardIndicatorSignal(active, blinking)
    }
    put(DashboardIndicator.LEFT_TURN, i.leftTurn, i.leftTurn)
    put(DashboardIndicator.RIGHT_TURN, i.rightTurn, i.rightTurn)
    put(DashboardIndicator.HIGH_BEAM, i.highBeam)
    put(DashboardIndicator.LOW_BEAM, i.lowBeam)
    put(DashboardIndicator.POSITION_LIGHT, i.positionLight)
    put(DashboardIndicator.VEHICLE_BLUETOOTH, i.bluetoothIndicator)
    put(DashboardIndicator.PHONE_BLUETOOTH, com.shilapi.xcertplay.glance.CarPlayGlance.snapshot().connected)
    put(DashboardIndicator.GPS, i.gps)
    put(DashboardIndicator.GSM, i.gsm)
    put(DashboardIndicator.READY, v.ready)
    put(DashboardIndicator.FAULT, i.vehicleFault)
    put(DashboardIndicator.ENERGY_RECOVERY, i.energyRecovery)
    put(DashboardIndicator.SIDE_STAND, i.sideStand)
    put(DashboardIndicator.SEAT, i.seat)
    put(DashboardIndicator.ABS, i.abs, i.absBlink)
    put(DashboardIndicator.RIDER_UNSEATED, i.riderUnseated)
    put(DashboardIndicator.HDC, i.hdc, i.hdcBlink)
    put(DashboardIndicator.CRUISE, i.cruise)
    put(DashboardIndicator.TCS, i.tcs, i.tcsBlink)
    put(DashboardIndicator.CHARGING, i.charge)
    return VehicleDashboardSnapshot(
        FusionDashboardView.VehiclePreview(
            speed = v.speedKmh.takeIf { v.speedValid }, gear = gear, power = power,
            battery = v.batteryPercent.takeIf { v.batteryValid },
            voltage = decimal(v.voltageV, v.voltageValid), current = decimal(v.currentA, v.currentValid),
            // No range estimate is supplied by this protocol. Odometer is ESP32's speed integration.
            range = null, odometer = if (v.odoValid && v.odoKm != null) String.format(Locale.US, "%,.1f", v.odoKm) else "--",
        ), DashboardIndicatorSnapshot(signals, demonstration = false),
    )
}
