package io.kunkun.tpsgenerator.traffic;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A traffic pattern that loads TPS values from a custom pattern file.
 * The file should be a CSV with columns for time (in seconds or milliseconds) and TPS.
 */
@Slf4j
public class CustomPattern implements TrafficPattern {

    /**
     * The data points in the pattern.
     */
    private final List<DataPoint> dataPoints = new ArrayList<>();

    /**
     * The maximum TPS value in the pattern.
     */
    private double maxTps = 0;

    /**
     * Whether the time units in the file are in milliseconds (true) or seconds (false).
     */
    private final boolean timeInMilliseconds;

    /**
     * Creates a custom traffic pattern from a file.
     *
     * @param patternFile the path to the pattern file
     * @param timeInMilliseconds whether the time units in the file are in milliseconds
     * @throws IOException if reading the file fails
     */
    public CustomPattern(String patternFile, boolean timeInMilliseconds) throws IOException {
        this.timeInMilliseconds = timeInMilliseconds;
        loadPatternFile(patternFile);
        log.info("Loaded custom traffic pattern with {} data points", dataPoints.size());
    }

    /**
     * Loads the pattern file.
     *
     * @param patternFile the path to the pattern file
     * @throws IOException if reading the file fails
     */
    private void loadPatternFile(String patternFile) throws IOException {
        try (CSVParser parser = CSVParser.parse(
                new FileReader(patternFile),
                CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build())) {

            // Find column names (case-insensitive)
            String timeColumn = null;
            String tpsColumn = null;

            for (String header : parser.getHeaderNames()) {
                String headerLower = header.toLowerCase();
                if (headerLower.contains("time") || headerLower.equals("t")) {
                    timeColumn = header;
                } else if (headerLower.contains("tps") || headerLower.contains("rate")) {
                    tpsColumn = header;
                }
            }

            if (timeColumn == null || tpsColumn == null) {
                throw new IOException("Pattern file must have columns for time and TPS rate");
            }

            // Parse data points
            for (CSVRecord record : parser) {
                try {
                    double time = Double.parseDouble(record.get(timeColumn));
                    double tps = Double.parseDouble(record.get(tpsColumn));

                    // Convert time to milliseconds if it's in seconds
                    long timeMs = timeInMilliseconds ? (long) time : (long) (time * 1000);

                    dataPoints.add(new DataPoint(timeMs, tps));
                    maxTps = Math.max(maxTps, tps);
                } catch (NumberFormatException e) {
                    log.warn("Skipping invalid data point in pattern file: {}", record);
                }
            }

            if (dataPoints.isEmpty()) {
                throw new IOException("No valid data points found in pattern file");
            }

            // Sort data points by time
            dataPoints.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        }
    }

    @Override
    public double getTpsAtTime(long elapsedTimeMs, long totalDurationMs) {
        // If we have no data points or elapsed time is before the first point,
        // use the first data point's TPS
        if (dataPoints.isEmpty() || elapsedTimeMs < dataPoints.get(0).timeMs) {
            return dataPoints.isEmpty() ? 0 : dataPoints.get(0).tps;
        }

        // If elapsed time is after the last point, use the last data point's TPS
        if (elapsedTimeMs >= dataPoints.get(dataPoints.size() - 1).timeMs) {
            return dataPoints.get(dataPoints.size() - 1).tps;
        }

        // Find the surrounding data points
        DataPoint before = dataPoints.get(0);
        DataPoint after = dataPoints.get(dataPoints.size() - 1);

        for (int i = 0; i < dataPoints.size() - 1; i++) {
            if (dataPoints.get(i).timeMs <= elapsedTimeMs && dataPoints.get(i + 1).timeMs > elapsedTimeMs) {
                before = dataPoints.get(i);
                after = dataPoints.get(i + 1);
                break;
            }
        }

        // Interpolate between the two points
        if (after.timeMs == before.timeMs) {
            return before.tps;
        }

        double ratio = (double) (elapsedTimeMs - before.timeMs) / (after.timeMs - before.timeMs);
        return before.tps + ratio * (after.tps - before.tps);
    }

    @Override
    public double getMaxTps() {
        return maxTps;
    }

    @Override
    public String toString() {
        return String.format("CustomPattern(points=%d, maxTps=%.2f)",
                dataPoints.size(), maxTps);
    }

    /**
     * A data point in the custom pattern.
     */
    private static class DataPoint {
        /**
         * The time in milliseconds.
         */
        private final long timeMs;

        /**
         * The TPS rate.
         */
        private final double tps;

        /**
         * Creates a new data point.
         *
         * @param timeMs the time in milliseconds
         * @param tps the TPS rate
         */
        public DataPoint(long timeMs, double tps) {
            this.timeMs = timeMs;
            this.tps = tps;
        }
    }
}