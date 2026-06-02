package io.kunkun.tpsgenerator.metrics;

import io.kunkun.tpsgenerator.config.Constants;
import io.kunkun.tpsgenerator.model.ResourceSnapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors system resources (CPU, memory, threads) during test execution.
 * Implements Closeable for proper resource cleanup.
 */
@Slf4j
public class ResourceMonitor implements Closeable {

    /**
     * Maximum number of snapshots to retain in memory.
     */
    private static final int MAX_SNAPSHOTS = Constants.MAX_RESOURCE_SNAPSHOTS;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setName("resource-monitor");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    // ArrayDeque ring buffer guarded by its own monitor: evicting the oldest snapshot is
    // O(1) (pollFirst) instead of the array-copy that List.remove(0) incurs.
    private final Deque<ResourceSnapshot> snapshots = new ArrayDeque<>();
    private final OperatingSystemMXBean osMXBean;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final Runtime runtime;

    @Getter
    private double maxCpuUsage;

    @Getter
    private long maxMemoryUsage;

    /**
     * Creates a new ResourceMonitor.
     */
    public ResourceMonitor() {
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.runtime = Runtime.getRuntime();

        log.info("Initialized resource monitor");
    }

    /**
     * Starts monitoring resources.
     *
     * @param sampleInterval the interval between resource samples
     */
    public void start(Duration sampleInterval) {
        if (running.compareAndSet(false, true)) {
            // Start sampling
            scheduler.scheduleAtFixedRate(
                    this::captureSnapshot,
                    0,
                    sampleInterval.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            log.info("Started resource monitoring with sample interval {}", sampleInterval);
        }
    }

    /**
     * Stops monitoring resources.
     */
    public void stop() {
        // Flip the running flag while holding the snapshots lock so it is
        // mutually exclusive with the append in captureSnapshot(). This makes
        // stop() a hard boundary: any snapshot in flight either completes before
        // stop() returns or is dropped, so the count cannot grow afterwards.
        boolean wasRunning;
        synchronized (snapshots) {
            wasRunning = running.compareAndSet(true, false);
        }
        if (wasRunning) {
            scheduler.shutdownNow();
            int count;
            synchronized (snapshots) {
                count = snapshots.size();
            }
            log.info("Stopped resource monitoring, collected {} snapshots", count);
        }
    }

    /**
     * Closes this monitor and releases resources.
     * This method delegates to stop().
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Captures a resource snapshot.
     */
    private void captureSnapshot() {
        try {
            long timestamp = System.currentTimeMillis();

            // Get CPU load
            double cpuLoad = getCpuUsage();

            // Get memory usage
            long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
            long heapCommitted = memoryMXBean.getHeapMemoryUsage().getCommitted();
            long nonHeapUsed = memoryMXBean.getNonHeapMemoryUsage().getUsed();
            long totalMemoryUsed = heapUsed + nonHeapUsed;

            // Get thread stats
            int activeThreads = Thread.activeCount();
            int totalThreads = threadMXBean.getThreadCount();
            int daemonThreads = threadMXBean.getDaemonThreadCount();

            // Create snapshot
            ResourceSnapshot snapshot = new ResourceSnapshot(
                    timestamp,
                    cpuLoad,
                    heapUsed,
                    heapCommitted,
                    nonHeapUsed,
                    runtime.totalMemory(),
                    runtime.freeMemory(),
                    activeThreads,
                    totalThreads,
                    daemonThreads
            );

            // Add snapshot, removing oldest if at capacity. The running check is
            // inside the lock — and stop() flips running under the same lock — so
            // a snapshot captured concurrently with stop() can never be appended
            // after stop() has returned.
            synchronized (snapshots) {
                if (!running.get()) {
                    return;
                }
                if (snapshots.size() >= MAX_SNAPSHOTS) {
                    snapshots.pollFirst();
                }
                snapshots.addLast(snapshot);
            }

            // Update max values
            maxCpuUsage = Math.max(maxCpuUsage, cpuLoad);
            maxMemoryUsage = Math.max(maxMemoryUsage, totalMemoryUsed);

        } catch (Exception e) {
            log.error("Error capturing resource snapshot", e);
        }
    }

    /**
     * Gets the current CPU usage as a percentage (0-100).
     *
     * @return the CPU usage percentage
     */
    private double getCpuUsage() {
        double cpuLoad;

        // Try to get CPU load from MXBean
        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            cpuLoad = ((com.sun.management.OperatingSystemMXBean) osMXBean).getProcessCpuLoad() * 100;
        } else {
            // Fallback to system load average
            cpuLoad = osMXBean.getSystemLoadAverage() * 100 / osMXBean.getAvailableProcessors();
        }

        // Ensure value is valid
        if (Double.isNaN(cpuLoad) || cpuLoad < 0) {
            cpuLoad = 0;
        }

        return cpuLoad;
    }

    /**
     * Gets the resource snapshots.
     *
     * @return the resource snapshots
     */
    public List<ResourceSnapshot> getSnapshots() {
        synchronized (snapshots) {
            return new ArrayList<>(snapshots);
        }
    }
}