package io.kunkun.tpsgenerator.request;

import io.kunkun.tpsgenerator.request.parameter.RandomParameterSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the concrete RandomParameterSource implementations.
 */
class RandomParameterSourceTest {

    // -------- UniformIntegerSource --------

    @Test
    @DisplayName("UniformIntegerSource.getValue returns non-null string")
    void uniformIntegerGetValueNonNull() {
        RandomParameterSource source = new RandomParameterSource.UniformIntegerSource(1, 100);
        assertNotNull(source.getValue());
    }

    @Test
    @DisplayName("UniformIntegerSource.getValue returns integer in expected range")
    void uniformIntegerGetValueInRange() {
        RandomParameterSource source = new RandomParameterSource.UniformIntegerSource(10, 20);
        for (int i = 0; i < 50; i++) {
            int value = Integer.parseInt(source.getValue());
            assertTrue(value >= 10 && value <= 20,
                    "Expected value in [10,20] but got " + value);
        }
    }

    @Test
    @DisplayName("UniformIntegerSource.getType returns 'random'")
    void uniformIntegerGetTypeIsRandom() {
        assertEquals("random", new RandomParameterSource.UniformIntegerSource(0, 1).getType());
    }

    @Test
    @DisplayName("UniformIntegerSource.toString includes bounds")
    void uniformIntegerToStringIncludesBounds() {
        String str = new RandomParameterSource.UniformIntegerSource(5, 15).toString();
        assertTrue(str.contains("5"));
        assertTrue(str.contains("15"));
    }

    // -------- NormalDistributionSource --------

    @Test
    @DisplayName("NormalDistributionSource.getValue returns non-null string")
    void normalDistributionGetValueNonNull() {
        RandomParameterSource source = new RandomParameterSource.NormalDistributionSource(
                50.0, 10.0, 0.0, 100.0);
        assertNotNull(source.getValue());
    }

    @Test
    @DisplayName("NormalDistributionSource.getValue stays in [min, max)")
    void normalDistributionValueInBounds() {
        RandomParameterSource source = new RandomParameterSource.NormalDistributionSource(
                50.0, 5.0, 30.0, 70.0);
        for (int i = 0; i < 30; i++) {
            double v = Double.parseDouble(source.getValue());
            assertTrue(v >= 30.0 && v < 70.0,
                    "Expected value in [30,70) but got " + v);
        }
    }

    // -------- RandomStringSource --------

    @Test
    @DisplayName("RandomStringSource.getValue returns non-null string")
    void randomStringGetValueNonNull() {
        RandomParameterSource source = new RandomParameterSource.RandomStringSource(5, 10);
        assertNotNull(source.getValue());
    }

    @Test
    @DisplayName("RandomStringSource.getValue returns string in length range")
    void randomStringGetValueInLengthRange() {
        RandomParameterSource source = new RandomParameterSource.RandomStringSource(4, 8);
        for (int i = 0; i < 20; i++) {
            String val = source.getValue();
            assertTrue(val.length() >= 4 && val.length() <= 8,
                    "Expected length [4,8] but got " + val.length());
        }
    }

    @Test
    @DisplayName("RandomStringSource: throws when no character set selected")
    void randomStringThrowsWhenNoCharset() {
        assertThrows(IllegalArgumentException.class,
                () -> new RandomParameterSource.RandomStringSource(1, 5, false, false, false, false));
    }

    // -------- RandomSelectionSource --------

    @Test
    @DisplayName("RandomSelectionSource.getValue returns non-null string")
    void randomSelectionGetValueNonNull() {
        RandomParameterSource source = new RandomParameterSource.RandomSelectionSource(
                new String[]{"a", "b", "c"});
        assertNotNull(source.getValue());
    }

    @Test
    @DisplayName("RandomSelectionSource.getValue returns only values from the array")
    void randomSelectionGetValueFromArray() {
        String[] options = {"alpha", "beta", "gamma"};
        RandomParameterSource source = new RandomParameterSource.RandomSelectionSource(options);
        Set<String> allowed = Set.of(options);
        for (int i = 0; i < 30; i++) {
            assertTrue(allowed.contains(source.getValue()));
        }
    }

    @Test
    @DisplayName("RandomSelectionSource: throws when values array is empty")
    void randomSelectionThrowsWhenEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> new RandomParameterSource.RandomSelectionSource(new String[]{}));
    }

    @Test
    @DisplayName("RandomSelectionSource: throws when values array is null")
    void randomSelectionThrowsWhenNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new RandomParameterSource.RandomSelectionSource(null));
    }

    @Test
    @DisplayName("RandomSelectionSource: produces variety over many calls")
    void randomSelectionProducesVariety() {
        RandomParameterSource source = new RandomParameterSource.RandomSelectionSource(
                new String[]{"x", "y", "z"});
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(source.getValue());
        }
        // Should see more than 1 unique value with high probability over 100 draws from 3 options
        assertTrue(seen.size() > 1, "Expected variety but only saw: " + seen);
    }
}
