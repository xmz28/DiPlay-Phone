package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class DashboardDisplayMotionTest {
    private fun ms(n: Long) = n * 1_000_000L
    @Test fun speedUsesIntermediateFramesAndReachesLatestSampleWithin45ms() {
        val value = DashboardDisplayValue(45, snapZero = true)
        value.retarget(20.0, 0)
        value.retarget(26.0, ms(50))
        assertEquals(22.0, value.sample(ms(65))!!, 0.00001)
        assertEquals(24.0, value.sample(ms(80))!!, 0.00001)
        assertEquals(26.0, value.sample(ms(95))!!, 0.00001)
    }
    @Test fun identicalPacketsDoNotRestartTransition() {
        val value = DashboardDisplayValue(60)
        value.retarget(0.0, 0); value.retarget(1.2, ms(50))
        value.retarget(1.2, ms(80))
        assertEquals(1.2, value.sample(ms(110))!!, 0.00001)
    }
    @Test fun newerTargetTakesOverCurrentDisplayWithoutQueuedOldTargets() {
        val value = DashboardDisplayValue(60)
        value.retarget(0.0, 0); value.retarget(1.2, ms(50))
        val current = value.sample(ms(80))!!
        value.retarget(0.2, ms(80))
        assertEquals(current, value.sample(ms(80))!!, 0.00001)
        assertEquals(0.2, value.sample(ms(140))!!, 0.00001)
    }
    @Test fun stoppingInvalidityAndModeSwitchAreImmediate() {
        val value = DashboardDisplayValue(45, snapZero = true)
        value.retarget(48.0, 0); value.retarget(0.0, ms(1))
        assertEquals(0.0, value.sample(ms(1))!!, 0.0)
        value.retarget(null, ms(2)); assertNull(value.sample(ms(2)))
        value.retarget(60.0, ms(3)); assertEquals(60.0, value.sample(ms(3))!!, 0.0)
        value.retarget(48.0, ms(4), snap = true); assertEquals(48.0, value.sample(ms(4))!!, 0.0)
        value.retarget(Double.NaN, ms(5)); assertNull(value.sample(ms(5)))
    }
    @Test fun recoverySignSwitchClearsOldDriveArcImmediately() {
        val value = DashboardDisplayValue(60, resetOnSignChange = true)
        value.retarget(1.0, 0); value.retarget(-0.6, ms(50))
        assertEquals(0.0, value.sample(ms(50))!!, 0.0)
        assertEquals(-0.3, value.sample(ms(80))!!, 0.00001)
        assertEquals(-0.6, value.sample(ms(110))!!, 0.00001)
    }
    @Test fun displayClockMaintains60HzAndCaps120Hz() {
        for (hz in listOf(60, 90, 120)) {
            val clock = DashboardFrameClock()
            val accepted = (0 until hz * 2).count { clock.accept(it * 1_000_000_000L / hz) }
            assertEquals("display=$hz", 120, accepted)
        }
    }
    @Test fun frameClockDoesNotQueueWorkAfterSuspension() {
        val clock = DashboardFrameClock()
        assertTrue(clock.accept(0)); assertTrue(clock.accept(ms(1000)))
        assertFalse(clock.accept(ms(1001)))
        clock.reset(); assertTrue(clock.accept(ms(1001)))
    }
}
