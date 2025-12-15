package io.kunkun.tpsgenerator.factory;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.request.parameter.FileParameterSource;
import io.kunkun.tpsgenerator.request.parameter.ParameterSource;
import io.kunkun.tpsgenerator.request.parameter.RandomParameterSource;

/**
 * Factory for creating parameter sources from configuration.
 */
public class ParameterSourceFactory {

    /**
     * Creates a parameter source from configuration.
     *
     * @param config the parameter source configuration
     * @return the parameter source
     * @throws IllegalArgumentException if the configuration is invalid
     */
    public static ParameterSource create(TestConfig.ParameterSourceConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Parameter source configuration cannot be null");
        }

        String type = config.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Parameter source type cannot be null or empty");
        }

        switch (type.toLowerCase()) {
            case "random":
                return createRandomSource(config);

            case "file":
                return createFileSource(config);

            default:
                throw new IllegalArgumentException("Unsupported parameter source type: " + type);
        }
    }

    /**
     * Creates a random parameter source from configuration.
     *
     * @param config the parameter source configuration
     * @return the random parameter source
     */
    private static RandomParameterSource createRandomSource(TestConfig.ParameterSourceConfig config) {
        String distribution = config.getDistribution();

        if (distribution == null || distribution.equalsIgnoreCase("uniform")) {
            int min = getMinValue(config);
            int max = getMaxValue(config);

            if (min > max) {
                throw new IllegalArgumentException(
                        String.format("Invalid range: min (%d) is greater than max (%d)", min, max));
            }

            return new RandomParameterSource.UniformIntegerSource(min, max);

        } else if (distribution.equalsIgnoreCase("normal")) {
            validateNormalDistributionConfig(config);
            return new RandomParameterSource.NormalDistributionSource(
                    config.getMean(), config.getStddev(), config.getMin(), config.getMax());

        } else {
            throw new IllegalArgumentException("Unsupported distribution type: " + distribution);
        }
    }

    /**
     * Gets the minimum value from configuration, supporting both range array and min field.
     */
    private static int getMinValue(TestConfig.ParameterSourceConfig config) {
        if (config.getRange() != null && config.getRange().length > 0) {
            return config.getRange()[0];
        }
        return (int) config.getMin();
    }

    /**
     * Gets the maximum value from configuration, supporting both range array and max field.
     */
    private static int getMaxValue(TestConfig.ParameterSourceConfig config) {
        if (config.getRange() != null && config.getRange().length > 1) {
            return config.getRange()[1];
        }
        return (int) config.getMax();
    }

    /**
     * Validates configuration for normal distribution.
     */
    private static void validateNormalDistributionConfig(TestConfig.ParameterSourceConfig config) {
        if (config.getStddev() <= 0) {
            throw new IllegalArgumentException("Standard deviation must be positive for normal distribution");
        }
        if (config.getMin() >= config.getMax()) {
            throw new IllegalArgumentException(
                    String.format("Invalid range for normal distribution: min (%.2f) >= max (%.2f)",
                            config.getMin(), config.getMax()));
        }
    }

    /**
     * Creates a file parameter source from configuration.
     *
     * @param config the parameter source configuration
     * @return the file parameter source
     */
    private static FileParameterSource createFileSource(TestConfig.ParameterSourceConfig config) {
        String path = config.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("File path must be specified for file parameter source");
        }

        String column = config.getColumn();
        String selection = config.getSelection();
        boolean isRandom = "random".equalsIgnoreCase(selection);

        return new FileParameterSource(path, column, isRandom);
    }
}
