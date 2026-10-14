package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FusionDisplayPresetTest {
    @Test fun physicalCentreWithRightCutout() {
        val area = fusionDisplayRect(2305, 1080, 2400, 1080, 0, 0, FusionDisplayPreset.CLUSTER_2_1)
        assertEquals(120, area.left); assertEquals(2280, area.right)
        assertEquals(2160, area.width()); assertEquals(1080, area.height())
    }
    @Test fun physicalCentreWithLeftCutout() {
        val area = fusionDisplayRect(2305, 1080, 2400, 1080, 95, 0, FusionDisplayPreset.CLUSTER_2_1)
        assertEquals(25, area.left); assertEquals(2185, area.right)
    }
    @Test fun phoneUsesAvailableWindow() {
        val area = fusionDisplayRect(2305, 1080, 2400, 1080, 0, 0, FusionDisplayPreset.PHONE)
        assertEquals(0, area.left); assertEquals(2305, area.width())
    }
    @Test fun nativeClusterKeepsFullCanvas() {
        val area = fusionDisplayRect(960, 480, 960, 480, 0, 0, FusionDisplayPreset.CLUSTER_2_1)
        assertEquals(0, area.left); assertEquals(960, area.width()); assertEquals(480, area.height())
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun artworkDoesNotInheritGlassBorderOpacity() {
        val view = FusionDashboardView(org.robolectric.RuntimeEnvironment.getApplication())
        val source = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
        source.eraseColor(android.graphics.Color.rgb(220, 40, 20))
        view.setMedia(CarPlayMediaKeys.DashboardMedia(com.shilapi.xcertplay.media.CarPlayNowPlaying(title = "Test"), source))
        view.layout(0, 0, 1920, 960)
        val output = android.graphics.Bitmap.createBitmap(1920, 960, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(output))
        assertEquals(source.getPixel(5, 5), output.getPixel(89, 882))
        source.recycle(); output.recycle()
    }
    @Test fun landscapeSupportsHalfTurnsOnly() {
        val request = CarPlayOrientation.allowLandscapeFlip(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, request)
        org.junit.Assert.assertTrue(CarPlayOrientation.rotationCompatible(1, 3, request))
        org.junit.Assert.assertTrue(CarPlayOrientation.rotationCompatible(3, 1, request))
        org.junit.Assert.assertFalse(CarPlayOrientation.rotationCompatible(1, 0, request))
        org.junit.Assert.assertFalse(CarPlayOrientation.rotationCompatible(1, 3, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE))
    }
}
