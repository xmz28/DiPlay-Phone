package com.shilapi.xcertplay.vehicle

interface VehicleStateProvider {
    fun setListener(listener: (VehicleState) -> Unit)

    fun setDiagnosticsListener(listener: (DiagnosticsState) -> Unit) {}

    fun setDiagnosticsEnabled(enabled: Boolean) {}

    fun start()

    fun stop()
}
