package io.kunkun.tpsgenerator.factory;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.traffic.*;

import java.io.IOException;

/**
 * Factory for creating traffic patterns from configuration.
 */
public class TrafficPatternFactory {

    /**
     * Creates a traffic pattern based on the configuration.
     *
     * @param trafficConfig the traffic configuration
     * @return the traffic pattern
     * @throws IllegalArgumentException if the pattern type is not supported
     */
    public static TrafficPattern create(TestConfig.TrafficConfig trafficConfig) {
        if (trafficConfig == null) {
            throw new IllegalArgumentException("Traffic configuration cannot be null");
        }

        String patternType = trafficConfig.getType();
        if (patternType == null || patternType.isBlank()) {
            throw new IllegalArgumentException("Traffic pattern type cannot be null or empty");
        }

        switch (patternType.toLowerCase()) {
            case "stable":
                return createStablePattern(trafficConfig);

            case "rampup":
                return createRampUpPattern(trafficConfig);

            case "spike":
                return createSpikePattern(trafficConfig);

            case "custom":
                return createCustomPattern(trafficConfig);

            default:
                throw new IllegalArgumentException("Unsupported traffic pattern type: " + patternType);
        }
    }

    private static StablePattern createStablePattern(TestConfig.TrafficConfig config) {
        return new StablePattern(config.getTargetTps());
    }

    private static RampUpPattern createRampUpPattern(TestConfig.TrafficConfig config) {
        return new RampUpPattern(
                config.getStartTps(),
                config.getTargetTps(),
                config.getRampDuration().toMillis()
        );
    }

    private static SpikePattern createSpikePattern(TestConfig.TrafficConfig config) {
        return new SpikePattern(
                config.getTargetTps(),
                config.getSpikeTps(),
                config.getSpikeStartTime().toMillis(),
                config.getSpikeDuration().toMillis()
        );
    }

    private static CustomPattern createCustomPattern(TestConfig.TrafficConfig config) {
        String patternFile = config.getPatternFile();
        if (patternFile == null || patternFile.isBlank()) {
            throw new IllegalArgumentException("Pattern file must be specified for custom traffic pattern");
        }

        boolean timeInMs = config.isTimeInMilliseconds();
        try {
            return new CustomPattern(patternFile, timeInMs);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load custom pattern file: " + patternFile, e);
        }
    }
}
