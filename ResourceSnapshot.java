package com.example.tpsgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A snapshot of system resource usage at a point in time.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceSnapshot {

    /**
     * The timestamp of the snapshot in milliseconds.
     */
    private long timestampMs;

    /**
     * The CPU usage as a percentage (0-100).
     */
    private double cpuPercentage;

    /**
     * The heap memory used in bytes.
     */
    private long heapUsedBytes;

    /**
     * The heap memory committed in bytes.
     */
    private long heapCommittedBytes;

    /**
     * The non-heap memory used in bytes.
     */
    private long nonHeapUsedBytes;

    /**
     * The total memory allocated to the JVM in bytes.
     */
    private long totalMemoryBytes;

    /**
     * The free memory available to the JVM in bytes.
     */
    private long freeMemoryBytes;

    /**
     * The number of active threads.
     */
    private int activeThreads;

    /**
     * The total number of threads.
     */
    private int totalThreads;

    /**
     * The number of daemon threads.
     */
    private int daemonThreads;

    /**
     * Gets the total memory used (heap + non-heap) in bytes.
     *
     * @return the total memory used
     */
    public long getTotalMemoryUsed() {
        return heapUsedBytes + nonHeapUsedBytes;
    }

    /**
     * Gets the heap memory used in megabytes.
     *
     * @return the heap memory used in MB
     */
    public double getHeapUsedMB() {
        return heapUsedBytes / (1024.0 * 1024.0);
    }

    /**
     * Gets the total memory used in megabytes.
     *
     * @return the total memory used in MB
     */
    public double getTotalMemoryUsedMB() {
        return getTotalMemoryUsed() / (1024.0 * 1024.0);
    }

    /**
     * Gets the free memory in megabytes.
     *
     * @return the free memory in MB
     */
    public double getFreeMemoryMB() {
        return freeMemoryBytes / (1024.0 * 1024.0);
    }
}