package com.shilapi.xcertplay

import com.shilapi.xcertplay.vehicle.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class VehicleTelemetryTest {
    private fun rt(text: String, previous: VehicleState = VehicleState()) =
        VehicleStateJsonCodec.parseRealtime(JSONObject(text), previous)!!

    @Test fun additiveElectricalFieldsPreserveVersionAndComputeUnroundedPower() {
        val state = rt("""{"t":"rt","v":73,"z":31,"s":48,"p":26,"g":2,"uv":728,"ia":357,"ea":50}""")
        assertEquals(73L, state.stateVersion)
        assertEquals(72.8, state.vehicle.voltageV, 0.00001)
        assertEquals(35.7, state.vehicle.currentA, 0.00001)
        assertEquals(2.59896, state.vehicle.displayPowerKw!!, 0.000001)
        val dashboard = vehicleDashboardSnapshot(state, true)
        assertEquals("2.60", dashboard.vehicle.power)
        assertEquals("72.8", dashboard.vehicle.voltage)
        assertEquals(48, dashboard.vehicle.speed)
        assertFalse(dashboard.indicators.demonstration)
    }
    @Test fun negativeCurrentKeepsRecoveryDirectionEvenWhenLegacyPowerIsInvalid() {
        val state = rt("""{"t":"rt","z":24,"uv":728,"ia":-165,"ea":0,"p":0}""")
        assertFalse(state.vehicle.powerValid)
        assertEquals(-1.2012, state.vehicle.displayPowerKw!!, 0.000001)
        assertEquals("-1.20", vehicleDashboardSnapshot(state, true).vehicle.power)
    }
    @Test fun oldFirmwareRetainsPowerWithoutPretendingToSupplyElectricalData() {
        val state = rt("""{"t":"rt","z":7,"p":26,"s":48,"g":2}""")
        val dashboard = vehicleDashboardSnapshot(state, true)
        assertEquals("2.6", dashboard.vehicle.power)
        assertEquals("--", dashboard.vehicle.voltage)
        assertEquals("--", dashboard.vehicle.current)
        assertNull(dashboard.vehicle.range)
    }
    @Test fun electricalExpiryAndInvalidBitsDoNotShowRetainedNumbers() {
        val good = rt("""{"t":"rt","z":24,"uv":728,"ia":357,"ea":0}""")
        val expired = rt("""{"t":"rt","z":24,"uv":728,"ia":357,"ea":5001}""", good)
        assertFalse(expired.vehicle.voltageValid)
        assertNull(expired.vehicle.displayPowerKw)
        val invalid = rt("""{"t":"rt","z":0,"s":48,"uv":728,"ia":357}""", good)
        val dashboard = vehicleDashboardSnapshot(invalid, true)
        assertNull(dashboard.vehicle.speed)
        assertEquals("--", dashboard.vehicle.power)
        assertEquals("--", dashboard.vehicle.voltage)
    }
    @Test fun olderFullPacketCannotRollBackFreshElectricalSpeedOrIndicators() {
        val state = rt("""{"t":"rt","z":31,"s":48,"p":26,"g":2,"uv":728,"ia":357,"i":4104}""")
        val merged = VehicleStateJsonCodec.parseFullStatus(JSONObject("""{"type":"status","vehicle":{"batteryPercent":78,"batteryValid":true,"odoKm":123.4,"odoValid":true,"speedKmh":0},"icons":{"lowBeam":false}}"""), state, false)!!
        assertEquals(48, merged.vehicle.speedKmh)
        assertTrue(merged.vehicle.voltageValid)
        assertEquals(72.8, merged.vehicle.voltageV, .00001)
        assertTrue(merged.icons.lowBeam)
        assertTrue(merged.icons.sideStand)
        assertEquals(78, merged.vehicle.batteryPercent)
        assertEquals(123.4, merged.vehicle.odoKm!!, .00001)
    }
    @Test fun missingOrNullElectricalValuesAreUnknownRatherThanZero() {
        val state = rt("""{"t":"rt","z":24,"uv":null,"ia":"bad"}""")
        assertFalse(state.vehicle.voltageValid)
        assertFalse(state.vehicle.currentValid)
    }
    @Test fun fullOnlyFallbackInvalidatesElectricalFields() {
        val state = rt("""{"t":"rt","z":24,"uv":728,"ia":357}""")
        val merged = VehicleStateJsonCodec.parseFullStatus(JSONObject("""{"type":"status","vehicle":{"batteryPercent":78}}"""), state, true)!!
        assertFalse(merged.vehicle.voltageValid)
        assertFalse(merged.vehicle.currentValid)
        assertFalse(merged.vehicle.speedValid)
        assertFalse(merged.vehicle.gearValid)
        assertFalse(merged.vehicle.powerValid)
    }
    @Test fun disconnectionAndDisabledSettingRestoreExactlyOriginalDemo() {
        val live = rt("""{"t":"rt","z":31,"s":8,"p":1,"g":1,"uv":720,"ia":10}""")
        for (state in listOf(live.asDisconnected(), VehicleState())) {
            val result = vehicleDashboardSnapshot(state, true)
            assertEquals(FusionDashboardView.VehiclePreview(), result.vehicle)
            assertTrue(result.indicators.demonstration)
        }
        assertEquals(FusionDashboardView.VehiclePreview(), vehicleDashboardSnapshot(live, false).vehicle)
    }
    @Test fun authoritativeIconsAndGearAreMappedWithoutAssetDependency() {
        val live = rt("""{"t":"rt","z":2,"g":4,"o":2,"r":1,"i":4195891}""")
        val result = vehicleDashboardSnapshot(live, true)
        assertEquals("R", result.vehicle.gear)
        assertTrue(result.indicators.signal(DashboardIndicator.LEFT_TURN).active)
        assertTrue(result.indicators.signal(DashboardIndicator.RIGHT_TURN).blinking)
        assertTrue(result.indicators.signal(DashboardIndicator.READY).active)
        assertTrue(result.indicators.signal(DashboardIndicator.TCS).blinking)
        assertTrue(result.indicators.signal(DashboardIndicator.ABS).blinking)
    }
    @Test fun estimatedRangeIsSocHeuristicAndKeepsUnknownUnknown() {
        assertEquals(85, dashboardEstimatedRangeKm(100))
        assertEquals(66, dashboardEstimatedRangeKm(78))
        assertEquals(0, dashboardEstimatedRangeKm(0))
        assertNull(dashboardEstimatedRangeKm(null))
        assertNull(dashboardEstimatedRangeKm(101))
        val connected = VehicleState(connectionStatus = ConnectionStatus.CONNECTED,
            vehicle = VehicleSnapshot(batteryPercent = 80, batteryValid = true))
        assertEquals(68, vehicleDashboardSnapshot(connected, true).vehicle.range)
        assertNull(vehicleDashboardSnapshot(connected.copy(vehicle = connected.vehicle.copy(batteryValid = false)), true).vehicle.range)
    }
    @Test fun freeGearUsesWireValueFourAndColoursMatchBoard() {
        val free = vehicleDashboardSnapshot(rt("""{"t":"rt","z":2,"g":4}"""), true)
        assertEquals("FREE", free.vehicle.gear)
        assertEquals(4, GearState.FREE.ordinal)
        assertEquals(0xff0000ff.toInt(), dashboardGearColor(free.vehicle.gear))
        assertEquals(0xff00ff00.toInt(), dashboardGearColor("ECO"))
        assertEquals(0xffff6500.toInt(), dashboardGearColor("S"))
        assertEquals(0xffffffff.toInt(), dashboardGearColor("A"))
        assertEquals(0xffff0000.toInt(), dashboardGearColor("T"))
        assertEquals(0xffffff00.toInt(), dashboardGearColor("P"))
    }

}
