package io.kunkun.tpsgenerator.traffic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StablePattern.
 */
class StablePatternTest {

    @Test
    @DisplayName("Should return configured TPS at time zero")
    void getTpsAtTime_returnsConfiguredTpsAtZero() {
        StablePattern pattern = new StablePattern(100.0);

        assertEquals(100.0, pattern.getTpsAtTime(0, 60_000));
    }

    @Test
    @DisplayName("Should return same TPS at any elapsed time")
    void getTpsAtTime_returnsConstantTpsRegardlessOfElapsedTime() {
        StablePattern pattern = new StablePattern(250.0);

        assertEquals(250.0, pattern.getTpsAtTime(0, 60_000));
        assertEquals(250.0, pattern.getTpsAtTime(15_000, 60_000));
        assertEquals(250.0, pattern.getTpsAtTime(30_000, 60_000));
        assertEquals(250.0, pattern.getTpsAtTime(59_999, 60_000));
    }

    @Test
    @DisplayName("Should return same TPS regardless of total duration")
    void getTpsAtTime_returnsConstantTpsRegardlessOfTotalDuration() {
        StablePattern pattern = new StablePattern(50.0);

        assertEquals(50.0, pattern.getTpsAtTime(1000, 10_000));
        assertEquals(50.0, pattern.getTpsAtTime(1000, 3_600_000));
    }

    @Test
    @DisplayName("getMaxTps should equal configured TPS")
    void getMaxTps_equalsTargetTps() {
        StablePattern pattern = new StablePattern(75.5);

        assertEquals(75.5, pattern.getMaxTps());
    }

    @Test
    @DisplayName("getTargetTps should equal configured TPS")
    void getTargetTps_equalsConstructorArgument() {
        StablePattern pattern = new StablePattern(200.0);

        assertEquals(200.0, pattern.getTargetTps());
    }

    @Test
    @DisplayName("Should work with fractional TPS values")
    void getTpsAtTime_supportsFractionalTps() {
        StablePattern pattern = new StablePattern(1.5);

        assertEquals(1.5, pattern.getTpsAtTime(0, 10_000));
        assertEquals(1.5, pattern.getTpsAtTime(5_000, 10_000));
    }
}
