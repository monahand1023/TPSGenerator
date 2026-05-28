package io.kunkun.tpsgenerator.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Constants — verifies that the documented constant values match expectations.
 */
class ConstantsTest {

    @Test
    @DisplayName("EXECUTOR_LOOP_SLEEP_MS should be 1")
    void executorLoopSleepMsShouldBeOne() {
        assertEquals(1L, Constants.EXECUTOR_LOOP_SLEEP_MS);
    }

    @Test
    @DisplayName("HISTOGRAM_PRECISION should be 3")
    void histogramPrecisionShouldBeThree() {
        assertEquals(3, Constants.HISTOGRAM_PRECISION);
    }

    @Test
    @DisplayName("HISTOGRAM_MAX_VALUE_MS should be 3600000 (1 hour)")
    void histogramMaxValueMsShouldBeOneHour() {
        assertEquals(3_600_000L, Constants.HISTOGRAM_MAX_VALUE_MS);
    }

    @Test
    @DisplayName("MAX_ERROR_SAMPLES should be 100")
    void maxErrorSamplesShouldBeOneHundred() {
        assertEquals(100, Constants.MAX_ERROR_SAMPLES);
    }

    @Test
    @DisplayName("MAX_BODY_LOG_SIZE should be 1024")
    void maxBodyLogSizeShouldBe1024() {
        assertEquals(1024, Constants.MAX_BODY_LOG_SIZE);
    }

    @Test
    @DisplayName("MAX_PARAMETER_FILE_LINES should be 100000")
    void maxParameterFileLinesShouldBe100000() {
        assertEquals(100_000, Constants.MAX_PARAMETER_FILE_LINES);
    }

    @Test
    @DisplayName("MAX_TPS_SAMPLES should be 3600")
    void maxTpsSamplesShouldBe3600() {
        assertEquals(3600, Constants.MAX_TPS_SAMPLES);
    }

    @Test
    @DisplayName("MAX_RESOURCE_SNAPSHOTS should be 7200")
    void maxResourceSnapshotsShouldBe7200() {
        assertEquals(7200, Constants.MAX_RESOURCE_SNAPSHOTS);
    }

    @Test
    @DisplayName("DEFAULT_CONNECT_TIMEOUT_SECONDS should be 10")
    void defaultConnectTimeoutShouldBeTen() {
        assertEquals(10, Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("DEFAULT_REQUEST_TIMEOUT_SECONDS should be 30")
    void defaultRequestTimeoutShouldBeThirty() {
        assertEquals(30, Constants.DEFAULT_REQUEST_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS should be 30")
    void executorShutdownTimeoutShouldBeThirty() {
        assertEquals(30, Constants.EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS should be 5")
    void gracefulShutdownTimeoutShouldBeFive() {
        assertEquals(5, Constants.GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("TPS_UPDATE_INTERVAL_SECONDS should be 1")
    void tpsUpdateIntervalShouldBeOne() {
        assertEquals(1, Constants.TPS_UPDATE_INTERVAL_SECONDS);
    }

    @Test
    @DisplayName("PROGRESS_LOG_INTERVAL_MS should be 10000")
    void progressLogIntervalShouldBe10000() {
        assertEquals(10_000, Constants.PROGRESS_LOG_INTERVAL_MS);
    }
}
