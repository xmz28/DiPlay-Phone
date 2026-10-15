package com.shilapi.xcertplay

import android.animation.ValueAnimator
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FusionDirectionalThemeTest {
    @Test fun independentDefaultsMatchObservedMapDirections() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("fusion_display", Context.MODE_PRIVATE).edit().clear().putInt("transition_ms", 1000).commit()
        assertEquals(1200, FusionThemeSettings.duration(context, false))
        assertEquals(1600, FusionThemeSettings.duration(context, true))
    }
    @Test fun savingOneDirectionRetainsTheOtherAndClampsBounds() {
        val context = RuntimeEnvironment.getApplication()
        FusionThemeSettings.saveDuration(context, false, 800)
        FusionThemeSettings.saveDuration(context, true, 2200)
        assertEquals(800, FusionThemeSettings.duration(context, false))
        assertEquals(2200, FusionThemeSettings.duration(context, true))
        FusionThemeSettings.saveDuration(context, false, -1)
        FusionThemeSettings.saveDuration(context, true, 4000)
        assertEquals(0, FusionThemeSettings.duration(context, false))
        assertEquals(3000, FusionThemeSettings.duration(context, true))
    }
    @Test fun viewChoosesDurationByTargetTheme() {
        val context = RuntimeEnvironment.getApplication()
        FusionThemeSettings.saveDuration(context, false, 1200)
        FusionThemeSettings.saveDuration(context, true, 1600)
        val view = FusionDashboardView(context)
        val field = view.javaClass.getDeclaredField("themeAnimator").apply { isAccessible = true }
        view.setNightMode(false, true)
        assertEquals(1200L, (field.get(view) as ValueAnimator).duration)
        view.setNightMode(true, true)
        assertEquals(1600L, (field.get(view) as ValueAnimator).duration)
        (field.get(view) as ValueAnimator).cancel()
    }
}
