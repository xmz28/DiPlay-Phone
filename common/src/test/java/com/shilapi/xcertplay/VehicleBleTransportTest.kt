package com.shilapi.xcertplay

import com.shilapi.xcertplay.vehicle.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class VehicleBleTransportTest {
    private lateinit var client: Esp32VehicleBleClient
    private fun field(name: String, value: Any) {
        client.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(client, value)
    }
    @Before fun setup() {
        // Each Robolectric test has a new application/preferences sandbox.
        Esp32VehicleBleClient::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        client = Esp32VehicleBleClient.getInstance(RuntimeEnvironment.getApplication())
        client.stop()
        ShadowSystemClock.advanceBy(Duration.ofMillis(10))
        field("running", true)
        field("notificationsReady", true)
    }
    private fun frame(payload: ByteArray, offset: Int, total: Int, flags: Int, seq: Int = 1): ByteArray {
        return ByteBuffer.allocate(12 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(0xA5.toByte()).put(1.toByte()).put(1.toByte()).put(flags.toByte())
            .putInt(seq).putShort(total.toShort()).putShort(offset.toShort()).put(payload).array()
    }
    private fun receive(bytes: ByteArray) {
        client.javaClass.getDeclaredMethod("handleFrame", ByteArray::class.java).apply { isAccessible = true }.invoke(client, bytes)
    }
    @Test fun completeFragmentedPacketProducesLiveStateAndTimeoutRestoresDemo() {
        val payload = """{"t":"rt","q":1,"z":31,"s":48,"p":26,"g":2,"uv":728,"ia":357,"ea":0}""".toByteArray()
        receive(frame(payload.copyOfRange(0, 30), 0, payload.size, 1))
        assertEquals(ConnectionStatus.DISCONNECTED, client.snapshot().connectionStatus)
        receive(frame(payload.copyOfRange(30, payload.size), 30, payload.size, 2))
        assertEquals(ConnectionStatus.CONNECTED, client.snapshot().connectionStatus)
        assertEquals("2.60", vehicleDashboardSnapshot(client.snapshot(), true).vehicle.power)
        ShadowSystemClock.advanceBy(Duration.ofMillis(3_000))
        assertEquals(ConnectionStatus.DISCONNECTED, client.snapshot().connectionStatus)
        assertEquals(FusionDashboardView.VehiclePreview(), vehicleDashboardSnapshot(client.snapshot(), true).vehicle)
    }
    @Test fun missingFragmentAndInvalidHeaderDoNotPublishPartialTelemetry() {
        val payload = """{"t":"rt","z":31,"s":99,"uv":728,"ia":357}""".toByteArray()
        receive(frame(payload.copyOfRange(0, 15), 0, payload.size, 1))
        receive(frame(payload.copyOfRange(25, payload.size), 25, payload.size, 2))
        assertEquals(ConnectionStatus.DISCONNECTED, client.snapshot().connectionStatus)
        val invalid = frame(payload, 0, payload.size, 3, 2).also { it[0] = 0 }
        receive(invalid)
        assertEquals(ConnectionStatus.DISCONNECTED, client.snapshot().connectionStatus)
        receive(frame(payload, 0, payload.size, 3, 3))
        assertEquals(99, client.snapshot().vehicle.speedKmh)
    }
    @Test fun stopClearsOldVehicleAndBindingRequiresAValidExactAddress() {
        val payload = """{"t":"rt","z":4,"s":99}""".toByteArray()
        receive(frame(payload, 0, payload.size, 3))
        client.stop()
        assertEquals(ConnectionStatus.DISCONNECTED, client.snapshot().connectionStatus)
        assertEquals(0, client.snapshot().vehicle.speedKmh)
        client.bindDevice("12:34:56:78:9A:BC")
        assertEquals("12:34:56:78:9A:BC", VehicleBleSettings.address(RuntimeEnvironment.getApplication()))
        assertThrows(IllegalArgumentException::class.java) { client.bindDevice("YADEA") }
    }
}
