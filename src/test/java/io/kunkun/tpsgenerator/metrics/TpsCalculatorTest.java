package io.kunkun.tpsgenerator.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TpsCalculator.
 */
class TpsCalculatorTest {

    private TpsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TpsCalculator();
    }

    @Test
    @DisplayName("Initial TPS should be zero")
    void getCurrentTps_initiallyZero() {
        assertEquals(0, calculator.getCurrentTps());
    }

    @Test
    @DisplayName("updateTps should return zero when no requests were counted")
    void updateTps_returnsZeroWhenNoRequests() {
        long tps = calculator.updateTps();

        assertEquals(0, tps);
        assertEquals(0, calculator.getCurrentTps());
    }

    @Test
    @DisplayName("updateTps should return count of increments since last update")
    void updateTps_returnsIncrementCountSinceLastUpdate() {
        calculator.incrementRequestCount();
        calculator.incrementRequestCount();
        calculator.incrementRequestCount();

        long tps = calculator.updateTps();

        assertEquals(3, tps);
        assertEquals(3, calculator.getCurrentTps());
    }

    @Test
    @DisplayName("updateTps resets the counter so second call returns zero")
    void updateTps_resetsCounterAfterCall() {
        calculator.incrementRequestCount();
        calculator.incrementRequestCount();

        calculator.updateTps(); // first window: 2
        long tps = calculator.updateTps(); // second window: nothing added

        assertEquals(0, tps);
    }

    @Test
    @DisplayName("Increments between updateTps calls are counted in the next window")
    void updateTps_countsOnlyIncrementsSinceLastUpdate() {
        calculator.incrementRequestCount(); // window 1
        calculator.updateTps();

        calculator.incrementRequestCount(); // window 2
        calculator.incrementRequestCount();
        long tps = calculator.updateTps();

        assertEquals(2, tps);
    }

    @Test
    @DisplayName("reset clears currentTps and counter")
    void reset_clearsBothCounters() {
        calculator.incrementRequestCount();
        calculator.incrementRequestCount();
        calculator.updateTps();

        calculator.reset();

        assertEquals(0, calculator.getCurrentTps());
        assertEquals(0, calculator.updateTps());
    }

    @Test
    @DisplayName("getCurrentTps reflects last updateTps result")
    void getCurrentTps_reflectsLastUpdate() {
        calculator.incrementRequestCount();
        calculator.incrementRequestCount();
        calculator.incrementRequestCount();
        calculator.incrementRequestCount();
        calculator.incrementRequestCount();
        calculator.updateTps();

        assertEquals(5, calculator.getCurrentTps());
    }
}
