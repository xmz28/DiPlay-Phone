package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class DashboardPowerRingTest {
    @Test fun driveUsesReference1400WFullScale() {
        assertEquals(0f, dashboardPowerRing(0.0).fraction)
        assertEquals(.5f, dashboardPowerRing(.7).fraction)
        assertEquals(1f, dashboardPowerRing(1.4).fraction)
        assertFalse(dashboardPowerRing(.7).recovery)
    }
    @Test fun recoveryStartsAtSameZeroAndUsesReferenceMinus600WFullScale() {
        assertEquals(.5f, dashboardPowerRing(-.3).fraction)
        assertEquals(1f, dashboardPowerRing(-.6).fraction)
        assertTrue(dashboardPowerRing(-.3).recovery)
    }
    @Test fun overRangeSaturatesWithoutWrappingTheArc() {
        assertEquals(1f, dashboardPowerRing(2.6).fraction)
        assertEquals(1f, dashboardPowerRing(-1.2).fraction)
    }
    @Test fun unknownAndNonFinitePowerDoNotInventAnActiveArc() {
        for (power in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(0f, dashboardPowerRing(power).fraction)
        }
    }
    @Test fun arcUsesUnroundedElectricalPower() {
        assertEquals((1.2012 / 1.4).toFloat(), dashboardPowerRing(1.2012).fraction, .000001f)
    }
}
