package io.kunkun.tpsgenerator.traffic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpikePattern.
 */
class SpikePatternTest {

    @Test
    @DisplayName("Should return baseTps before the spike starts")
    void getTpsAtTime_beforeSpikeReturnsBaseTps() {
        // spike starts at 30_000ms, lasts 5_000ms
        SpikePattern pattern = new SpikePattern(50.0, 500.0, 30_000, 5_000);

        assertEquals(50.0, pattern.getTpsAtTime(0, 120_000));
        assertEquals(50.0, pattern.getTpsAtTime(15_000, 120_000));
        assertEquals(50.0, pattern.getTpsAtTime(29_999, 120_000));
    }

    @Test
    @DisplayName("Should return spikeTps at spike start")
    void getTpsAtTime_atSpikeStartReturnsSpikeTps() {
        SpikePattern pattern = new SpikePattern(50.0, 500.0, 30_000, 5_000);

        assertEquals(500.0, pattern.getTpsAtTime(30_000, 120_000));
    }

    @Test
    @DisplayName("Should return spikeTps throughout the spike window")
    void getTpsAtTime_duringSpikePeriodReturnsSpikeTps() {
        SpikePattern pattern = new SpikePattern(50.0, 500.0, 30_000, 5_000);

        assertEquals(500.0, pattern.getTpsAtTime(30_000, 120_000));
        assertEquals(500.0, pattern.getTpsAtTime(32_500, 120_000));
        assertEquals(500.0, pattern.getTpsAtTime(34_999, 120_000));
    }

    @Test
    @DisplayName("Should return baseTps immediately after the spike ends")
    void getTpsAtTime_afterSpikeEndsReturnsBaseTps() {
        // spike: [30_000, 35_000)
        SpikePattern pattern = new SpikePattern(50.0, 500.0, 30_000, 5_000);

        assertEquals(50.0, pattern.getTpsAtTime(35_000, 120_000));
        assertEquals(50.0, pattern.getTpsAtTime(60_000, 120_000));
    }

    @Test
    @DisplayName("getMaxTps should return the higher of baseTps and spikeTps")
    void getMaxTps_returnsHigherValue() {
        SpikePattern pattern = new SpikePattern(50.0, 500.0, 30_000, 5_000);

        assertEquals(500.0, pattern.getMaxTps());
    }

    @Test
    @DisplayName("getMaxTps should work when baseTps is higher than spikeTps")
    void getMaxTps_returnsBaseTpsWhenHigher() {
        SpikePattern pattern = new SpikePattern(500.0, 50.0, 30_000, 5_000);

        assertEquals(500.0, pattern.getMaxTps());
    }

    @Test
    @DisplayName("Should handle spike at time zero")
    void getTpsAtTime_spikeAtTimeZero() {
        SpikePattern pattern = new SpikePattern(10.0, 100.0, 0, 5_000);

        assertEquals(100.0, pattern.getTpsAtTime(0, 60_000));
        assertEquals(100.0, pattern.getTpsAtTime(4_999, 60_000));
        assertEquals(10.0, pattern.getTpsAtTime(5_000, 60_000));
    }
}
