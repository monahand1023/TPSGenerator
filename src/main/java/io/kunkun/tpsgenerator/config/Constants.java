package io.kunkun.tpsgenerator.config;

/**
 * Application-wide constants.
 * Centralizes magic numbers and configuration defaults.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    // ============== Histogram Configuration ==============

    /**
     * Default number of significant digits for histogram precision.
     */
    public static final int HISTOGRAM_PRECISION = 3;

    /**
     * Maximum trackable value for response time histogram (1 hour in milliseconds).
     */
    public static final long HISTOGRAM_MAX_VALUE_MS = 3600000L;

    // ============== Error Analyzer Configuration ==============

    /**
     * Maximum number of error samples to store per status code.
     */
    public static final int MAX_ERROR_SAMPLES = 100;

    /**
     * Maximum length of response body to log in errors.
     */
    public static final int MAX_BODY_LOG_SIZE = 1024;

    // ============== File Parameter Source Configuration ==============

    /**
     * Maximum number of lines to load from a parameter source file.
     */
    public static final int MAX_PARAMETER_FILE_LINES = 100_000;

    // ============== TPS Metrics Configuration ==============

    /**
     * Maximum number of TPS samples to retain.
     */
    public static final int MAX_TPS_SAMPLES = 3600;

    // ============== Resource Monitor Configuration ==============

    /**
     * Maximum number of resource snapshots to retain.
     */
    public static final int MAX_RESOURCE_SNAPSHOTS = 7200;

    // ============== Timeout Configuration ==============

    /**
     * Minimum effective offered rate. Guava's RateLimiter rejects a rate of 0, and — more subtly —
     * a permit granted at a very low rate pushes the next-permit time ~1/rate seconds into the
     * future, which a subsequent setRate() does NOT pull back. So a rate well below 1 TPS (e.g. a
     * ramp's near-zero start) would let exactly one request through and then stall. Flooring to
     * 1 TPS keeps 1/rate within the ~1s rate-update interval, so ramps and idle periods behave.
     * Practical limitation: a configured rate below 1 TPS runs at 1 TPS.
     */
    public static final double MIN_RATE_LIMITER_TPS = 1.0;

    /**
     * Maximum time a single submission waits for a rate-limiter permit before returning control to
     * the loop. Bounding the wait (vs a blocking acquire) lets the loop observe rate updates and the
     * test-end deadline even when the current rate is very low.
     */
    public static final long RATE_LIMITER_ACQUIRE_TIMEOUT_MS = 1000;

    /**
     * Default HTTP connection timeout in seconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;

    /**
     * Default HTTP request timeout in seconds.
     */
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 30;

    /**
     * Default executor shutdown timeout in seconds.
     */
    public static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 30;

    /**
     * Graceful shutdown timeout in seconds.
     */
    public static final int GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 5;

    // ============== Scheduler Configuration ==============

    /**
     * TPS update interval in seconds.
     */
    public static final int TPS_UPDATE_INTERVAL_SECONDS = 1;

    /**
     * Progress log interval in milliseconds.
     */
    public static final int PROGRESS_LOG_INTERVAL_MS = 10000;

    // ============== Execution Loop Configuration ==============

    /**
     * How long the main execution loop sleeps between request submissions (ms).
     * Prevents CPU spinning while still allowing rapid request submission.
     */
    public static final long EXECUTOR_LOOP_SLEEP_MS = 1;
}
