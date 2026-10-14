package com.shilapi.xcertplay

import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DisplayPageSwipeTest {
    private fun event(action: Int, x1: Float, x2: Float, y1: Float = 100f, y2: Float = 160f): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { x = x1; y = y1; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = x2; y = y2; pressure = 1f; size = 1f })
        return MotionEvent.obtain(0, 100, action, 2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }
    private fun exercise(fusion: Boolean, x1: Float, x2: Float, y1: Float = 100f, y2: Float = 160f): List<Boolean> {
        val pages = mutableListOf<Boolean>()
        val swipe = DisplayPageSwipe(1f) { pages.add(it) }
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 100f, 160f).let { swipe.handle(it, fusion); it.recycle() }
        event(MotionEvent.ACTION_MOVE, x1, x2, y1, y2).let { swipe.handle(it, fusion); it.recycle() }
        return pages
    }
    @Test fun parallelRightSwipeEntersInstrumentAndLeftSwipeReturns() {
        assertEquals(listOf(true), exercise(false, 200f, 260f))
        assertEquals(listOf(false), exercise(true, 0f, 60f))
    }
    @Test fun pinchVerticalAndWrongDirectionDoNotSwitch() {
        assertTrue(exercise(false, 70f, 350f).isEmpty())
        assertTrue(exercise(false, 200f, 260f, 250f, 310f).isEmpty())
        assertTrue(exercise(false, 0f, 60f).isEmpty())
        assertTrue(exercise(true, 200f, 260f).isEmpty())
    }
    @Test fun landscapeLayoutsKeepControlsVisible() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        for ((w, h) in listOf(1920 to 1080, 960 to 480)) {
            val pane = FusionDashboardPane(context, {}, {}, {})
            pane.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            pane.layout(0, 0, w, h)
            // Map stays in the display, and the two local controls remain distinct and reachable.
            val map = pane.getChildAt(0)
            val primary = pane.getChildAt(2).layoutParams as android.widget.FrameLayout.LayoutParams
            val home = pane.getChildAt(3).layoutParams as android.widget.FrameLayout.LayoutParams
            assertTrue(map.layoutParams.width > 0)
            assertEquals(h, map.layoutParams.height)
            assertEquals(0, (map.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin)
            val transform = FloatArray(9)
            (map as android.view.TextureView).getTransform(android.graphics.Matrix()).getValues(transform)
            val displayedAspect = map.layoutParams.width * transform[android.graphics.Matrix.MSCALE_X] /
                (h * transform[android.graphics.Matrix.MSCALE_Y])
            assertEquals(MapMirrors.streamAspect.toFloat(), displayedAspect, .001f)
            assertTrue(primary.topMargin >= 0 && primary.topMargin + primary.height <= h)
            assertTrue(home.leftMargin + home.width <= w && primary.leftMargin + primary.width < home.leftMargin)
            val theme = pane.getChildAt(4).layoutParams as android.widget.FrameLayout.LayoutParams
            assertTrue(theme.leftMargin + theme.width <= home.leftMargin && theme.topMargin + theme.height <= h)
        }
    }
    @Test fun dayNightControlCallsHostAndAnnouncesNextMode() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        var calls = 0
        val pane = FusionDashboardPane(context, {}, {}, {}, { calls++ })
        val button = pane.getChildAt(4)
        pane.setNightMode(false)
        assertEquals("切换到夜间模式", button.contentDescription)
        button.performClick()
        assertEquals(1, calls)
        pane.setNightMode(true)
        assertEquals("切换到日间模式", button.contentDescription)
    }
}
