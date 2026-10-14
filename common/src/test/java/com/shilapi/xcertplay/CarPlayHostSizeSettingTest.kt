package com.shilapi.xcertplay

import android.content.pm.PackageInfo
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Range
import com.shilapi.xcertplay.airplay.*
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.MediaCodecInfoBuilder.CodecCapabilitiesBuilder
import org.robolectric.shadows.ShadowMediaCodecList
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CarPlayHostSizeSettingTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var video: MediaCodecInfo.VideoCapabilities
    private val sizeClass = Class.forName("com.shilapi.xcertplay.CarPlayHostActivity\$DisplaySize")

    @Before fun setUp() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        field("airPlayIdentity", AirPlayIdentity.generate())
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 3840, 3840)
        val profile = MediaCodecInfo.CodecProfileLevel().apply {
            this.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
            level = MediaCodecInfo.CodecProfileLevel.AVCLevel52
        }
        val capabilities = CodecCapabilitiesBuilder.newBuilder().setMediaFormat(format)
            .setProfileLevels(arrayOf(profile))
            .setColorFormats(intArrayOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)).build()
        video = requireNotNull(capabilities.videoCapabilities)
        ShadowMediaCodecList.addCodec(MediaCodecInfoBuilder.newBuilder().setName("OMX.test.hardware.avc")
            .setIsHardwareAccelerated(true).setCapabilities(capabilities).build())
    }

    @After fun tearDown() {
        ShadowMediaCodecList.reset()
        (field("teardownExecutor") as ExecutorService).shutdownNow()
        (field("airPlayCommandExecutor") as ExecutorService).shutdownNow()
    }

    @Test fun savedSizeChangesAffectTheAdvertisedCanvasAfterReload() {
        val expected = listOf(1956 to 940, 2250 to 1080, 2648 to 1270)
        CarPlaySize.entries.forEachIndexed { index, preset ->
            select(preset)
            val config = config()
            assertCanvas(config.main, expected[index])
            val screen = (AirPlayInfoPlist.build(config)["displays"] as List<*>).first() as Map<*, *>
            assertEquals(expected[index].first, screen["widthPixels"])
            assertEquals(expected[index].second, screen["heightPixels"])
            assertTrue(DisplayDiagnosticSnapshot.report(activity).contains("selected=${preset.label}"))
        }
    }

    @Test fun sizeComposesWithResolutionAndPortraitRotation() {
        select(CarPlaySize.SMALL, resolutionPercent = 80)
        assertCanvas(config().main, 2118 to 1016)
        assertCanvas(config(width = 1080, height = 2250).main, 1016 to 2118)
    }

    @Test fun customIntegerResolutionComposesWithEverySizeAndPortraitRotation() {
        val expected = listOf(1116 to 536, 1284 to 616, 1510 to 724)
        CarPlaySize.entries.forEachIndexed { index, preset ->
            select(preset, resolutionPercent = 57)
            assertCanvas(config().main, expected[index])
            assertCanvas(config(width = 1080, height = 2250).main,
                expected[index].second to expected[index].first)
            assertEquals(57, AirPlayPersistence.loadDisplayScalePercent(activity))
            assertEquals(preset.widthMillimeters, AirPlayPersistence.loadWidthPhysicalMm(activity))
            assertTrue(DisplayDiagnosticSnapshot.report(activity).contains("resolution=57%"))
        }
    }

    @Test fun changingPhysicalWidthInTheHostAlsoChangesTheCanvas() {
        select(CarPlaySize.MEDIUM)
        field("widthPhysicalMm", CarPlaySize.LARGE.widthMillimeters)
        assertCanvas(config().main, 1956 to 940)
    }

    @Test fun unsupportedSmallCanvasFallsBackWithoutLosingThePreferenceAndCanRetry() {
        select(CarPlaySize.SMALL)
        ReflectionHelpers.setField(video, "mWidthRange", Range(1, 2250))
        assertCanvas(config().main, 2250 to 1080)
        val report = DisplayDiagnosticSnapshot.report(activity)
        assertTrue(report.contains("selected=Small percent=85"))
        assertTrue(report.contains("size=Medium percent=100"))
        assertTrue(report.contains("canvas_dimensions_unsupported"))
        assertEquals(CarPlaySize.SMALL.widthMillimeters, AirPlayPersistence.loadWidthPhysicalMm(activity))

        ReflectionHelpers.setField(video, "mWidthRange", Range(1, 3840))
        assertCanvas(config().main, 2648 to 1270)
    }

    @Test fun unsupportedFrameRateFallsBackAndReportsTheReason() {
        select(CarPlaySize.SMALL)
        ReflectionHelpers.setField(video, "mFrameRateRange", Range(1, 30))
        assertCanvas(config().main, 2250 to 1080)
        assertTrue(DisplayDiagnosticSnapshot.report(activity).contains("frame_rate_unsupported"))
    }

    @Test fun fourKLimitFallsBackBeforeQueryingTheDecoder() {
        select(CarPlaySize.SMALL)
        ShadowMediaCodecList.reset()
        assertCanvas(config(width = 3840, height = 2160).main, 3840 to 2160)
        assertTrue(DisplayDiagnosticSnapshot.report(activity).contains("canvas_4k_limit"))
    }

    @Test fun obsoleteScalePreferenceDoesNotOverrideTheVisibleSizeSetting() {
        AirPlayPersistence.saveUiScalePercent(activity, 75)
        select(CarPlaySize.LARGE)
        assertCanvas(config().main, 1956 to 940)
        select(CarPlaySize.MEDIUM)
        assertCanvas(config().main, 2250 to 1080)
        assertTrue(DisplayDiagnosticSnapshot.report(activity).contains("not_enlarging"))
    }

    @Test fun xiaomiPhoneAdvertisesNativeHomeWithAPngIcon() {
        device("Xiaomi", "Xiaomi", "Xiaomi/umi/umi:11/RKQ1.200826.002/test-keys")
        select(CarPlaySize.MEDIUM)
        assertEquals("仪表", AirPlayPersistence.loadOemLabel(activity))
        val config = config()
        assertEquals("仪表", config.oemLabel)
        assertEquals(192, config.icons.single().widthPixels)
        assertTrue(config.icons.single().data.take(8).toByteArray().contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        val info = AirPlayInfoPlist.build(config)
        assertEquals(true, info["oemIconVisible"])
        assertEquals("仪表", info["oemIconLabel"])
        assertTrue(info.containsKey("oemIcons"))
    }

    @Test fun nativeHomeCanBeDisabledAndKeepsCustomLabelWhenEnabled() {
        device("Xiaomi", "Xiaomi", "test")
        AirPlayPersistence.saveNativeHomeEnabled(activity, false)
        select(CarPlaySize.MEDIUM)
        assertTrue(config().icons.isEmpty())
        assertEquals(false, AirPlayInfoPlist.build(config())["oemIconVisible"])
        AirPlayPersistence.saveNativeHomeEnabled(activity, true)
        AirPlayPersistence.saveOemLabel(activity, "DiPlay")
        select(CarPlaySize.MEDIUM)
        assertEquals("DiPlay", AirPlayInfoPlist.build(config())["oemIconLabel"])
    }

    @Test fun bydManufacturerBrandOrFirmwareRetainsTheReturnIcon() {
        select(CarPlaySize.MEDIUM)
        for ((manufacturer, brand, fingerprint) in listOf(
            Triple("byd", "unknown", "unknown"),
            Triple("unknown", "BYD", "unknown"),
            Triple("unknown", "unknown", DiLink51ClusterLayout.FINGERPRINT),
        )) {
            device(manufacturer, brand, fingerprint)
            select(CarPlaySize.MEDIUM)
            val config = config()
            assertEquals("BYD", config.oemLabel)
            assertTrue(config.icons.single().data.contentEquals(
                activity.resources.openRawResource(com.shilapi.xcertplay.host.R.raw.ic_car_home).use { it.readBytes() },
            ))
            assertEquals(true, AirPlayInfoPlist.build(config)["oemIconVisible"])
        }
    }

    @Test fun aBydNavigationServiceRetainsTheIconOnGenericVendorFirmware() {
        device("unknown", "unknown", "unknown")
        shadowOf(activity.packageManager).installPackage(PackageInfo().apply { packageName = "com.byd.amapservice" })
        select(CarPlaySize.MEDIUM)
        assertEquals(1, config().icons.size)
    }

    private fun device(manufacturer: String, brand: String, fingerprint: String) {
        ShadowBuild.setManufacturer(manufacturer)
        ShadowBuild.setBrand(brand)
        ShadowBuild.setFingerprint(fingerprint)
    }

    @Test fun fullHdAndTwoToOneCanvasKeepRequestedDimensions() {
        select(CarPlaySize.MEDIUM)
        assertCanvas(config(1920, 1080).main, 1920 to 1080)
        assertCanvas(config(960, 480).main, 960 to 480)
    }

    private fun select(size: CarPlaySize, resolutionPercent: Int = 100) {
        AirPlayPersistence.saveWidthPhysicalMm(activity, size.widthMillimeters)
        AirPlayPersistence.saveDisplayScalePercent(activity, resolutionPercent)
        AirPlayPersistence.saveFps(activity, 60)
        activity.javaClass.getDeclaredMethod("loadPersistedSettings").apply { isAccessible = true }.invoke(activity)
    }

    private fun config(width: Int = 2250, height: Int = 1080): AirPlayConfig {
        val size = sizeClass.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(width, height)
        return activity.javaClass.getDeclaredMethod("createAirPlayConfig", sizeClass)
            .apply { isAccessible = true }.invoke(activity, size) as AirPlayConfig
    }

    private fun assertCanvas(display: AirPlayDisplayConfig, expected: Pair<Int, Int>) {
        assertEquals(expected.first, display.widthPixels)
        assertEquals(expected.second, display.heightPixels)
    }

    private fun field(name: String): Any? = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)

    private fun field(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
