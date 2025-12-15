package io.kunkun.tpsgenerator.factory;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.traffic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TrafficPatternFactory.
 */
class TrafficPatternFactoryTest {

    @Test
    @DisplayName("Should create stable pattern")
    void shouldCreateStablePattern() {
        TestConfig.TrafficConfig config = new TestConfig.TrafficConfig();
        config.setType("stable");
        config.setTargetTps(100);

        TrafficPattern pattern = TrafficPatternFactory.create(config);

        assertTrue(pattern instanceof StablePattern);
        assertEquals(100, pattern.getMaxTps());
    }

    @Test
    @DisplayName("Should create ramp up pattern")
    void shouldCreateRampUpPattern() {
        TestConfig.TrafficConfig config = new TestConfig.TrafficConfig();
        config.setType("rampup");
        config.setStartTps(10);
        config.setTargetTps(100);
        config.setRampDuration(Duration.ofMinutes(5));

        TrafficPattern pattern = TrafficPatternFactory.create(config);

        assertTrue(pattern instanceof RampUpPattern);
        assertEquals(100, pattern.getMaxTps());
    }

    @Test
    @DisplayName("Should create spike pattern")
    void shouldCreateSpikePattern() {
        TestConfig.TrafficConfig config = new TestConfig.TrafficConfig();
        config.setType("spike");
        config.setTargetTps(100);
        config.setSpikeTps(500);
        config.setSpikeStartTime(Duration.ofMinutes(1));
        config.setSpikeDuration(Duration.ofSeconds(30));

        TrafficPattern pattern = TrafficPatternFactory.create(config);

        assertTrue(pattern instanceof SpikePattern);
        assertEquals(500, pattern.getMaxTps());
    }

    @Test
    @DisplayName("Should be case insensitive for pattern type")
    void shouldBeCaseInsensitive() {
        TestConfig.TrafficConfig config = new TestConfig.TrafficConfig();
        config.setTargetTps(100);

        config.setType("STABLE");
        assertTrue(TrafficPatternFactory.create(config) instanceof StablePattern);

        config.setType("Stable");
        assertTrue(TrafficPatternFactory.create(config) instanceof StablePattern);

        config.setType("RAMPUP");
        config.setRampDuration(Duration.ofMinutes(1));
        assertTrue(TrafficPatternFactory.create(config) instanceof RampUpPattern);
    }

    @Test
    @DisplayName("Should throw exception for null config")
    void shouldThrowExceptionForNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> TrafficPatternFactory.create(null));
    }

    @Test
    @DisplayName("Should throw exception for null pattern type")
    void shouldThrowExceptionForNullPatternType() {
        TestConfig.TrafficConfig config = new TestConfig.TrafficConfig();
        config.setType(null);

        assertThrows(IllegalArgumentException.class, () -> TrafficPatternFactory.create(config));
    }

    @Test
    @DisplayName("Should throw exception for empty pattern type")
    void shouldThrowExceptionForEmptyPatternType() {
        TestConfig.TrafficConfig config = new TestConfig.TrafficConfig();
        config.setType("  ");

        assertThrows(IllegalArgumentException.class, () -> TrafficPatternFactory.create(config));
    }

    @Test
    @DisplayName("Should throw exception for unsupported pattern type")
    void shouldThrowExceptionForUnsupportedPatternType() {
        TestConfig.TrafficConfig config = new TestConfig.TrafficConfig();
        config.setType("unknown");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TrafficPatternFactory.create(config)
        );
        assertTrue(exception.getMessage().contains("unknown"));
    }
}
