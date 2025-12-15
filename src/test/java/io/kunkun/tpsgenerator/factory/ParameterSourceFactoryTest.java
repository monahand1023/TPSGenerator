package io.kunkun.tpsgenerator.factory;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.request.parameter.ParameterSource;
import io.kunkun.tpsgenerator.request.parameter.RandomParameterSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ParameterSourceFactory.
 */
class ParameterSourceFactoryTest {

    @Test
    @DisplayName("Should create uniform random source")
    void shouldCreateUniformRandomSource() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setType("random");
        config.setDistribution("uniform");
        config.setRange(new int[]{1, 100});

        ParameterSource source = ParameterSourceFactory.create(config);

        assertTrue(source instanceof RandomParameterSource);

        // Should produce values in range
        for (int i = 0; i < 100; i++) {
            String value = source.getValue();
            int intValue = Integer.parseInt(value);
            assertTrue(intValue >= 1 && intValue <= 100);
        }
    }

    @Test
    @DisplayName("Should create normal distribution source")
    void shouldCreateNormalDistributionSource() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setType("random");
        config.setDistribution("normal");
        config.setMean(50);
        config.setStddev(10);
        config.setMin(0);
        config.setMax(100);

        ParameterSource source = ParameterSourceFactory.create(config);

        assertTrue(source instanceof RandomParameterSource);

        // Should produce values in range
        for (int i = 0; i < 100; i++) {
            String value = source.getValue();
            double doubleValue = Double.parseDouble(value);
            assertTrue(doubleValue >= 0 && doubleValue <= 100);
        }
    }

    @Test
    @DisplayName("Should default to uniform distribution when not specified")
    void shouldDefaultToUniformDistribution() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setType("random");
        config.setRange(new int[]{1, 10});

        ParameterSource source = ParameterSourceFactory.create(config);

        assertNotNull(source);
        assertEquals("random", source.getType());
    }

    @Test
    @DisplayName("Should throw exception for null config")
    void shouldThrowExceptionForNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> ParameterSourceFactory.create(null));
    }

    @Test
    @DisplayName("Should throw exception for null type")
    void shouldThrowExceptionForNullType() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setType(null);

        assertThrows(IllegalArgumentException.class, () -> ParameterSourceFactory.create(config));
    }

    @Test
    @DisplayName("Should throw exception for unsupported type")
    void shouldThrowExceptionForUnsupportedType() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setType("database");

        assertThrows(IllegalArgumentException.class, () -> ParameterSourceFactory.create(config));
    }

    @Test
    @DisplayName("Should throw exception for invalid range")
    void shouldThrowExceptionForInvalidRange() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setType("random");
        config.setDistribution("uniform");
        config.setRange(new int[]{100, 1}); // min > max

        assertThrows(IllegalArgumentException.class, () -> ParameterSourceFactory.create(config));
    }

    @Test
    @DisplayName("Should throw exception for invalid normal distribution config")
    void shouldThrowExceptionForInvalidNormalDistribution() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setType("random");
        config.setDistribution("normal");
        config.setMean(50);
        config.setStddev(-10); // Invalid stddev
        config.setMin(0);
        config.setMax(100);

        assertThrows(IllegalArgumentException.class, () -> ParameterSourceFactory.create(config));
    }

    @Test
    @DisplayName("Should throw exception for file source without path")
    void shouldThrowExceptionForFileSourceWithoutPath() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setType("file");
        config.setPath(null);

        assertThrows(IllegalArgumentException.class, () -> ParameterSourceFactory.create(config));
    }

    @Test
    @DisplayName("Should be case insensitive for type")
    void shouldBeCaseInsensitiveForType() {
        TestConfig.ParameterSourceConfig config = new TestConfig.ParameterSourceConfig();
        config.setRange(new int[]{1, 10});

        config.setType("RANDOM");
        assertNotNull(ParameterSourceFactory.create(config));

        config.setType("Random");
        assertNotNull(ParameterSourceFactory.create(config));
    }
}
