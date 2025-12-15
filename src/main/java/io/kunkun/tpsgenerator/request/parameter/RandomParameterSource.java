package io.kunkun.tpsgenerator.request.parameter;

import lombok.AllArgsConstructor;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parameter source that generates random values.
 */
public abstract class RandomParameterSource implements ParameterSource {

    /**
     * Gets a random parameter value.
     *
     * @return the parameter value
     */
    @Override
    public abstract String getValue();

    @Override
    public String getType() {
        return "random";
    }

    /**
     * Parameter source that generates uniform random integers in a range.
     */
    @AllArgsConstructor
    public static class UniformIntegerSource extends RandomParameterSource {
        private final int min;
        private final int max;

        @Override
        public String getValue() {
            int value = ThreadLocalRandom.current().nextInt(min, max + 1);
            return String.valueOf(value);
        }

        @Override
        public String toString() {
            return String.format("UniformIntegerSource(min=%d, max=%d)", min, max);
        }
    }

    /**
     * Parameter source that generates random values following a normal distribution.
     */
    public static class NormalDistributionSource extends RandomParameterSource {
        private final double mean;
        private final double stdDev;
        private final double min;
        private final double max;
        private final Random random = new Random();

        /**
         * Creates a new normal distribution source.
         *
         * @param mean the mean of the distribution
         * @param stdDev the standard deviation
         * @param min the minimum value (inclusive)
         * @param max the maximum value (exclusive)
         */
        public NormalDistributionSource(double mean, double stdDev, double min, double max) {
            this.mean = mean;
            this.stdDev = stdDev;
            this.min = min;
            this.max = max;
        }

        @Override
        public String getValue() {
            // Generate a value from a normal distribution
            double value;
            do {
                value = random.nextGaussian() * stdDev + mean;
            } while (value < min || value >= max);

            // Return as integer or with one decimal place
            if (stdDev % 1 == 0 && mean % 1 == 0) {
                return String.valueOf((int) Math.round(value));
            } else {
                return String.format("%.1f", value);
            }
        }

        @Override
        public String toString() {
            return String.format("NormalDistributionSource(mean=%.2f, stdDev=%.2f, min=%.2f, max=%.2f)",
                    mean, stdDev, min, max);
        }
    }

    /**
     * Parameter source that generates random strings.
     */
    public static class RandomStringSource extends RandomParameterSource {
        private final int minLength;
        private final int maxLength;
        private final String charset;
        private final Random random = new Random();

        private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
        private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private static final String DIGITS = "0123456789";
        private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?";

        /**
         * Creates a new random string source with the specified character sets.
         *
         * @param minLength the minimum length
         * @param maxLength the maximum length
         * @param includeLowercase whether to include lowercase letters
         * @param includeUppercase whether to include uppercase letters
         * @param includeDigits whether to include digits
         * @param includeSpecial whether to include special characters
         */
        public RandomStringSource(int minLength, int maxLength,
                                  boolean includeLowercase, boolean includeUppercase,
                                  boolean includeDigits, boolean includeSpecial) {
            this.minLength = minLength;
            this.maxLength = maxLength;

            StringBuilder charsetBuilder = new StringBuilder();
            if (includeLowercase) charsetBuilder.append(LOWERCASE);
            if (includeUppercase) charsetBuilder.append(UPPERCASE);
            if (includeDigits) charsetBuilder.append(DIGITS);
            if (includeSpecial) charsetBuilder.append(SPECIAL);

            this.charset = charsetBuilder.toString();

            if (charset.isEmpty()) {
                throw new IllegalArgumentException("At least one character set must be included");
            }
        }

        /**
         * Creates a new random string source with alphanumeric characters.
         *
         * @param minLength the minimum length
         * @param maxLength the maximum length
         */
        public RandomStringSource(int minLength, int maxLength) {
            this(minLength, maxLength, true, true, true, false);
        }

        @Override
        public String getValue() {
            int length = ThreadLocalRandom.current().nextInt(minLength, maxLength + 1);

            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                int index = random.nextInt(charset.length());
                sb.append(charset.charAt(index));
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("RandomStringSource(minLength=%d, maxLength=%d, charset='%s')",
                    minLength, maxLength, charset);
        }
    }

    /**
     * Parameter source that selects values from a predefined set.
     */
    public static class RandomSelectionSource extends RandomParameterSource {
        private final String[] values;
        private final Random random = new Random();

        /**
         * Creates a new random selection source.
         *
         * @param values the possible values
         */
        public RandomSelectionSource(String[] values) {
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException("Values array cannot be empty");
            }
            this.values = values;
        }

        @Override
        public String getValue() {
            int index = random.nextInt(values.length);
            return values[index];
        }

        @Override
        public String toString() {
            return String.format("RandomSelectionSource(values=%d options)", values.length);
        }
    }
}