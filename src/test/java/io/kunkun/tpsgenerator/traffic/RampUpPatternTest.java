package io.kunkun.tpsgenerator.traffic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RampUpPattern.
 */
class RampUpPatternTest {

    @Test
    @DisplayName("getTpsAtTime(0) should return startTps")
    void getTpsAtTime_atZeroReturnsStartTps() {
        RampUpPattern pattern = new RampUpPattern(10.0, 100.0, 60_000);

        assertEquals(10.0, pattern.getTpsAtTime(0, 120_000));
    }

    @Test
    @DisplayName("getTpsAtTime at ramp duration should return targetTps")
    void getTpsAtTime_atRampDurationReturnsTargetTps() {
        RampUpPattern pattern = new RampUpPattern(10.0, 100.0, 60_000);

        assertEquals(100.0, pattern.getTpsAtTime(60_000, 120_000));
    }

    @Test
    @DisplayName("getTpsAtTime at midpoint should return linear interpolation")
    void getTpsAtTime_atMidpointReturnsLinearInterpolation() {
        RampUpPattern pattern = new RampUpPattern(0.0, 100.0, 60_000);

        // Midpoint of ramp: 50% progress = 50 TPS
        assertEquals(50.0, pattern.getTpsAtTime(30_000, 120_000), 0.001);
    }

    @Test
    @DisplayName("getTpsAtTime beyond ramp duration should return targetTps")
    void getTpsAtTime_beyondRampDurationReturnsTargetTps() {
        RampUpPattern pattern = new RampUpPattern(10.0, 100.0, 60_000);

        assertEquals(100.0, pattern.getTpsAtTime(60_001, 120_000));
        assertEquals(100.0, pattern.getTpsAtTime(90_000, 120_000));
        assertEquals(100.0, pattern.getTpsAtTime(119_999, 120_000));
    }

    @Test
    @DisplayName("TPS should increase monotonically during ramp")
    void getTpsAtTime_increasesDuringRamp() {
        RampUpPattern pattern = new RampUpPattern(0.0, 100.0, 60_000);

        double prev = 0.0;
        for (long t = 0; t <= 60_000; t += 5_000) {
            double tps = pattern.getTpsAtTime(t, 120_000);
            assertTrue(tps >= prev,
                    "TPS should not decrease during ramp: was " + prev + " at t=" + t + " got " + tps);
            prev = tps;
        }
    }

    @Test
    @DisplayName("getMaxTps should return targetTps")
    void getMaxTps_returnsTargetTps() {
        RampUpPattern pattern = new RampUpPattern(10.0, 200.0, 60_000);

        assertEquals(200.0, pattern.getMaxTps());
    }

    @Test
    @DisplayName("Should handle non-zero start TPS correctly at quarter-ramp")
    void getTpsAtTime_nonZeroStartAtQuarterRamp() {
        // startTps=20, targetTps=100, ramp=40_000ms → at 10_000ms progress=25%
        // expected = 20 + (100 - 20) * 0.25 = 20 + 20 = 40
        RampUpPattern pattern = new RampUpPattern(20.0, 100.0, 40_000);

        assertEquals(40.0, pattern.getTpsAtTime(10_000, 80_000), 0.001);
    }
}
