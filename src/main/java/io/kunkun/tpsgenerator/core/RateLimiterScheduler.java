package io.kunkun.tpsgenerator.core;

import io.kunkun.tpsgenerator.config.Constants;
import io.kunkun.tpsgenerator.metrics.MetricsCollector;
import io.kunkun.tpsgenerator.traffic.TrafficPattern;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drives traffic-pattern-based rate updates on a fixed 1-second interval.
 *
 * <p>Each tick reads the elapsed time from the {@link MetricsCollector}, asks
 * the {@link TrafficPattern} for the desired TPS, and forwards it to the
 * {@link RateLimiter}.  Progress is logged every
 * {@link Constants#PROGRESS_LOG_INTERVAL_MS} ms.
 */
@Slf4j
public class RateLimiterScheduler {

    private final TrafficPattern trafficPattern;
    private final RateLimiter rateLimiter;
    private final ExecutionResourceManager resourceManager;
    private final MetricsCollector metricsCollector;
    private final long totalDurationMs;

    private volatile ScheduledFuture<?> scheduledTask;
    private volatile long lastProgressLogTime = 0;

    /**
     * Creates a new scheduler.
     *
     * @param trafficPattern  the pattern that determines TPS over time
     * @param rateLimiter     the rate limiter whose rate will be updated each tick
     * @param resourceManager provides the {@link java.util.concurrent.ScheduledExecutorService}
     * @param metricsCollector used to read elapsed time and current-TPS metrics for logging
     * @param totalDurationMs total test duration in milliseconds (used to calculate progress %)
     */
    public RateLimiterScheduler(
            TrafficPattern trafficPattern,
            RateLimiter rateLimiter,
            ExecutionResourceManager resourceManager,
            MetricsCollector metricsCollector,
            long totalDurationMs) {
        this.trafficPattern = trafficPattern;
        this.rateLimiter = rateLimiter;
        this.resourceManager = resourceManager;
        this.metricsCollector = metricsCollector;
        this.totalDurationMs = totalDurationMs;
    }

    /**
     * Starts scheduling rate updates at
     * {@link Constants#TPS_UPDATE_INTERVAL_SECONDS}-second intervals, beginning
     * immediately.
     *
     * <p>Calling {@code start()} more than once without an intervening
     * {@link #stop()} is a no-op.
     */
    public void start() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            return;
        }
        scheduledTask = resourceManager.getScheduler().scheduleAtFixedRate(
                this::tick,
                0,
                Constants.TPS_UPDATE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Cancels the scheduled rate-update task.  Safe to call when not started.
     */
    public void stop() {
        ScheduledFuture<?> task = scheduledTask;
        if (task != null) {
            task.cancel(false);
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void tick() {
        try {
            long elapsedTimeMs = System.currentTimeMillis() - metricsCollector.getStartTime();
            double targetTps = trafficPattern.getTpsAtTime(elapsedTimeMs, totalDurationMs);
            rateLimiter.setRate(targetTps);

            long now = System.currentTimeMillis();
            if (now - lastProgressLogTime >= Constants.PROGRESS_LOG_INTERVAL_MS) {
                lastProgressLogTime = now;
                double completionPct = 100.0 * elapsedTimeMs / totalDurationMs;
                double currentTps = metricsCollector.getCurrentTps();
                log.info("Progress: {}% | Target TPS: {} | Actual TPS: {} | Success Rate: {}%",
                        String.format("%.1f", completionPct),
                        String.format("%.2f", targetTps),
                        String.format("%.2f", currentTps),
                        String.format("%.2f",
                                metricsCollector.getTestMetrics().getSuccessRate() * 100));
            }
        } catch (Exception e) {
            log.error("Error updating rate limiter", e);
        }
    }
}
