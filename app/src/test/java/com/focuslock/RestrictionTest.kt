package com.focuslock

import org.junit.Assert.*
import org.junit.Test

class RestrictionTest {
    private val h2 = 2 * 3600_000L
    private val wall0 = 1_700_000_000_000L
    private val el0 = 5_000_000L

    @Test fun createSetsExpiryTwoHoursAfterStart() {
        val r = Restriction.create(wall0, el0, 7, h2)
        assertEquals(wall0 + h2, r.endWall); assertEquals(el0 + h2, r.endElapsed)
    }
    @Test fun remainingCountsDownWithinSameBoot() {
        val r = Restriction.create(wall0, el0, 7, h2)
        assertEquals(h2 - 600_000, r.evaluate(wall0 + 600_000, el0 + 600_000, 7).remainingMs)
    }
    @Test fun expiresAtOriginalTime() {
        val r = Restriction.create(wall0, el0, 7, h2)
        assertTrue(r.evaluate(wall0 + h2, el0 + h2, 7).remainingMs <= 0)
        assertTrue(r.evaluate(wall0 + h2 - 1000, el0 + h2 - 1000, 7).remainingMs > 0)
    }
    @Test fun clockMovedForwardDoesNotUnblockInSameBoot() {
        val r = Restriction.create(wall0, el0, 7, h2)
        assertEquals(h2 - 600_000, r.evaluate(wall0 + 5 * 3600_000L, el0 + 600_000, 7).remainingMs)
    }
    @Test fun clockMovedBackwardDoesNotExtendInSameBoot() {
        val r = Restriction.create(wall0, el0, 7, h2)
        assertEquals(h2 - 600_000, r.evaluate(wall0 - 3600_000L, el0 + 600_000, 7).remainingMs)
    }
    @Test fun afterRebootFallsBackToWallClockAndRebases() {
        val r = Restriction.create(wall0, el0, 7, h2)
        val ev = r.evaluate(wall0 + 3600_000L, 1_000L, 8)
        assertEquals(3600_000L, ev.remainingMs)
        assertEquals(8, ev.rebased!!.boot); assertEquals(1_000L + 3600_000L, ev.rebased!!.endElapsed)
    }
    @Test fun afterRebootRemainingCappedAtDuration() {
        val r = Restriction.create(wall0, el0, 7, h2)
        assertEquals(h2, r.evaluate(wall0 - 10 * 3600_000L, 1_000L, 8).remainingMs)
    }
    @Test fun afterRebootExpiredIsNonPositive() {
        val r = Restriction.create(wall0, el0, 7, h2)
        assertTrue(r.evaluate(wall0 + h2 + 1, 1_000L, 8).remainingMs <= 0)
    }
    @Test fun unknownBootCountUsesWallClockWithoutRebase() {
        val r = Restriction.create(wall0, el0, -1, h2)
        val ev = r.evaluate(wall0 + 1000, el0 + 99, -1)
        assertEquals(h2 - 1000, ev.remainingMs); assertNull(ev.rebased)
    }
    @Test fun serializeRoundTrip() {
        val r = Restriction.create(wall0, el0, 7, h2)
        assertEquals(r, Restriction.parse(r.serialize()))
    }
    @Test fun parseGarbageReturnsNull() {
        assertNull(Restriction.parse("")); assertNull(Restriction.parse("a|b|c|d|e")); assertNull(Restriction.parse("1|2|3"))
    }
    @Test fun formatHmsValues() {
        assertEquals("00:00:00", formatHms(0)); assertEquals("00:00:01", formatHms(1))
        assertEquals("02:00:00", formatHms(h2)); assertEquals("01:59:59", formatHms(h2 - 1000)); assertEquals("00:00:00", formatHms(-5))
    }
}
