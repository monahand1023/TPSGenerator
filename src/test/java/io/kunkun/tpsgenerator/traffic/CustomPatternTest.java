package io.kunkun.tpsgenerator.traffic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CustomPattern.
 */
class CustomPatternTest {

    @TempDir
    Path tempDir;

    private Path patternFile;

    @BeforeEach
    void setUp() throws IOException {
        patternFile = tempDir.resolve("pattern.csv");
    }

    @Test
    @DisplayName("Should load pattern from CSV file")
    void shouldLoadPatternFromCsv() throws IOException {
        String content = "time,tps\n0,100\n1000,200\n2000,300";
        Files.writeString(patternFile, content);

        CustomPattern pattern = new CustomPattern(patternFile.toString(), false);

        assertEquals(300, pattern.getMaxTps());
    }

    @Test
    @DisplayName("Should interpolate between data points")
    void shouldInterpolateBetweenDataPoints() throws IOException {
        String content = "time,tps\n0,100\n1000,200";
        Files.writeString(patternFile, content);

        CustomPattern pattern = new CustomPattern(patternFile.toString(), false);

        // At time 500ms, should be halfway between 100 and 200
        double tps = pattern.getTpsAtTime(500, 2000);
        assertEquals(150, tps, 1.0);
    }

    @Test
    @DisplayName("Should return first TPS before first data point")
    void shouldReturnFirstTpsBeforeFirstDataPoint() throws IOException {
        String content = "time,tps\n1000,100\n2000,200";
        Files.writeString(patternFile, content);

        CustomPattern pattern = new CustomPattern(patternFile.toString(), false);

        double tps = pattern.getTpsAtTime(500, 5000);
        assertEquals(100, tps);
    }

    @Test
    @DisplayName("Should return last TPS after last data point")
    void shouldReturnLastTpsAfterLastDataPoint() throws IOException {
        String content = "time,tps\n0,100\n1000,200";
        Files.writeString(patternFile, content);

        CustomPattern pattern = new CustomPattern(patternFile.toString(), false);

        double tps = pattern.getTpsAtTime(2000, 5000);
        assertEquals(200, tps);
    }

    @Test
    @DisplayName("Should handle time in milliseconds")
    void shouldHandleTimeInMilliseconds() throws IOException {
        String content = "time,tps\n0,100\n1000,200";
        Files.writeString(patternFile, content);

        CustomPattern pattern = new CustomPattern(patternFile.toString(), true);

        double tps = pattern.getTpsAtTime(1000, 5000);
        assertEquals(200, tps);
    }

    @Test
    @DisplayName("Should handle time in seconds")
    void shouldHandleTimeInSeconds() throws IOException {
        // Time in seconds (will be converted to ms)
        String content = "time,tps\n0,100\n1,200";
        Files.writeString(patternFile, content);

        CustomPattern pattern = new CustomPattern(patternFile.toString(), false);

        // 1 second = 1000ms
        double tps = pattern.getTpsAtTime(1000, 5000);
        assertEquals(200, tps);
    }

    @Test
    @DisplayName("Should handle single data point")
    void shouldHandleSingleDataPoint() throws IOException {
        String content = "time,tps\n0,100";
        Files.writeString(patternFile, content);

        CustomPattern pattern = new CustomPattern(patternFile.toString(), false);

        assertEquals(100, pattern.getTpsAtTime(0, 1000));
        assertEquals(100, pattern.getTpsAtTime(500, 1000));
        assertEquals(100, pattern.getTpsAtTime(1000, 1000));
    }

    @Test
    @DisplayName("Should use binary search efficiently")
    void shouldUseBinarySearchEfficiently() throws IOException {
        // Create pattern with many data points
        StringBuilder content = new StringBuilder("time,tps\n");
        for (int i = 0; i <= 1000; i++) {
            content.append(i).append(",").append(i * 10).append("\n");
        }
        Files.writeString(patternFile, content.toString());

        CustomPattern pattern = new CustomPattern(patternFile.toString(), true);

        // Test multiple interpolations
        for (int i = 0; i < 1000; i += 100) {
            double tps = pattern.getTpsAtTime(i, 1000);
            assertTrue(tps >= 0);
        }

        assertEquals(10000, pattern.getMaxTps());
    }

    @Test
    @DisplayName("Should throw exception for non-existent file")
    void shouldThrowExceptionForNonExistentFile() {
        assertThrows(IOException.class, () ->
            new CustomPattern("/non/existent/file.csv", false)
        );
    }

    @Test
    @DisplayName("Should throw exception for empty file")
    void shouldThrowExceptionForEmptyFile() throws IOException {
        Files.writeString(patternFile, "time,tps\n");

        assertThrows(IOException.class, () ->
            new CustomPattern(patternFile.toString(), false)
        );
    }
}
