package io.kunkun.tpsgenerator.metrics;

import com.example.tpsgenerator.model.ResourceSnapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors system resources (CPU, memory, threads) during test execution.
 */
@Slf4j
public class ResourceMonitor {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setName("resource-monitor");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<ResourceSnapshot> snapshots = Collections.synchronizedList(new ArrayList<>());
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
        if (running.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            log.info("Stopped resource monitoring, collected {} snapshots", snapshots.size());
        }
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

            snapshots.add(snapshot);

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
        return new ArrayList<>(snapshots);
    }
}